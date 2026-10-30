import contextlib
import io
import json
import socket
import threading
import time
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from server import ConfigurationError, GatewayConfig, GatewayServer, MAX_BODY, MAX_UPSTREAM, MAX_WIRE_REQUEST, NoRedirect, SlidingLimit, infer, main, validate_intent

TOKEN_A = "local-test-phone-a-token-123456"
TOKEN_B = "local-test-phone-b-token-123456"


def environment(**overrides):
    env = {"NEBIUS_API_KEY": "mock-api-key-not-a-credential", "NEBIUS_MODEL": "mock-model",
           "MAA_DEVICE_TOKENS": json.dumps({"phone-a": TOKEN_A, "phone-b": TOKEN_B}),
           "MAA_REQUEST_TIMEOUT": "0.2", "MAA_RESPONSE_TIMEOUT": "0.5",
           "MAA_UPSTREAM_TIMEOUT": "0.2"}
    env.update(overrides)
    return env


class ConfigTests(unittest.TestCase):
    def test_configuration_is_an_immutable_snapshot(self):
        env = environment()
        config = GatewayConfig.from_env(env)
        env["MAA_DEVICE_TOKENS"] = "{}"
        self.assertEqual(config.device_tokens["phone-a"], TOKEN_A)
        self.assertNotIn(TOKEN_A, repr(config))
        self.assertNotIn("mock-api-key", repr(config))
        with self.assertRaises(TypeError):
            config.device_tokens["phone-a"] = "replacement"

    def test_invalid_configuration_never_includes_secret_values(self):
        cases = [
            {"NEBIUS_API_KEY": ""}, {"NEBIUS_MODEL": ""},
            {"NEBIUS_BASE_URL": "http://example.invalid/v1"},
            {"NEBIUS_BASE_URL": "https://secret-user:secret-pass@example.invalid/v1"},
            {"NEBIUS_BASE_URL": "https://example.invalid/v1?secret=private"},
            {"NEBIUS_BASE_URL": "https://example.invalid:invalid/v1"},
            {"NEBIUS_BASE_URL": "https://example.invalid:0/v1"},
            {"MAA_DEVICE_TOKENS": "{}"}, {"MAA_DEVICE_TOKENS": "[]"},
            {"MAA_DEVICE_TOKENS": json.dumps({"phone": "short-private-value"})},
            {"MAA_DEVICE_TOKENS": json.dumps({"one": TOKEN_A, "two": TOKEN_A})},
            {"MAA_DEVICE_TOKENS": '{"one":"' + TOKEN_A + '","one":"' + TOKEN_B + '"}'},
            {"MAA_DEVICE_TOKENS": json.dumps({"phone": TOKEN_A + "\n"})},
            {"MAA_DEVICE_TOKENS": json.dumps({"bad name": TOKEN_A})},
            {"MAA_REQUEST_TIMEOUT": "nan"}, {"MAA_REQUEST_TIMEOUT": "0"},
            {"MAA_MAX_CONNECTIONS": "1", "MAA_MAX_INFERENCES": "2"},
            {"MAA_MAX_CONNECTIONS": "1.5"}, {"MAA_INFERENCE_LIMIT": "0"}, {"PORT": "65536"},
        ]
        for change in cases:
            with self.subTest(change=list(change)), self.assertRaises(ConfigurationError) as caught:
                GatewayConfig.from_env(environment(**change))
            self.assertNotIn(TOKEN_A, str(caught.exception))
            self.assertNotIn("secret-pass", str(caught.exception))
            self.assertNotIn("short-private-value", str(caught.exception))

    def test_legacy_requires_explicit_local_pilot_opt_in(self):
        env = environment(MAA_DEVICE_TOKENS="", MAA_ACCESS_TOKEN=TOKEN_A)
        with self.assertRaises(ConfigurationError):
            GatewayConfig.from_env(env)
        config = GatewayConfig.from_env(dict(env, MAA_ALLOW_LEGACY_TOKEN="1"))
        self.assertTrue(config.legacy_pilot)
        self.assertEqual(dict(config.device_tokens), {"legacy-local-pilot": TOKEN_A})
        with self.assertRaises(ConfigurationError):
            GatewayServer(("0.0.0.0", 0), config)
        with self.assertRaises(ConfigurationError):
            GatewayConfig.from_env(environment(MAA_ACCESS_TOKEN=TOKEN_A))

    def test_redirect_handler_refuses_to_forward_credentials(self):
        with self.assertRaises(ValueError):
            NoRedirect().redirect_request(None, None, 302, "", {}, "https://other.invalid/")

    def test_startup_errors_are_structured_and_do_not_echo_credentials(self):
        with patch("server.GatewayConfig.from_env", side_effect=ConfigurationError("Set NEBIUS_MODEL")):
            with self.assertRaises(SystemExit) as failure:
                main()
            self.assertEqual(json.loads(str(failure.exception))["error"], "invalid_configuration")
        with patch("server.GatewayConfig.from_env", return_value=GatewayConfig.from_env(environment())), patch("server.GatewayServer", side_effect=OSError("private operating system details")):
            with self.assertRaises(SystemExit) as failure:
                main()
            self.assertEqual(json.loads(str(failure.exception))["error"], "startup_failed")
            self.assertNotIn("private", str(failure.exception))

    def test_deeply_nested_token_config_is_a_validation_error(self):
        with self.assertRaises(ConfigurationError):
            GatewayConfig.from_env(environment(MAA_DEVICE_TOKENS="[" * 1200 + "0" + "]" * 1200))


class IntentTests(unittest.TestCase):
    def test_rate_window_expires_without_unbounded_key_growth(self):
        limit = SlidingLimit(1, 60, ("device",))
        with patch("server.time.monotonic", side_effect=[0, 59, 60]):
            self.assertTrue(limit.allow("device"))
            self.assertFalse(limit.allow("device"))
            self.assertTrue(limit.allow("device"))
        self.assertEqual(len(limit.events["device"]), 1)
        with self.assertRaises(KeyError):
            limit.allow("arbitrary-attacker-key")
        self.assertEqual(set(limit.events), {"device"})

    def test_allowed_actions(self):
        for action in ("call", "media", "qr", "help", "home", "unsupported"):
            self.assertEqual(validate_intent({"action": action}), {"action": action})

    def test_reject_arbitrary_execution(self):
        for action in ("pay", "shell", "send_message", "", None):
            with self.assertRaises(ValueError):
                validate_intent({"action": action})

    def test_discard_extra_model_fields(self):
        self.assertEqual(validate_intent({"action": "help", "command": "pay"}), {"action": "help"})

    def test_recipient_and_spoken_fields_are_preserved(self):
        intent = {
            "action": "call",
            "target": "Rahul",
            "target_type": "name",
            "channel": "phone",
            "spoken_response": "Calling Rahul",
            "command": "malicious_script",
        }
        validated = validate_intent(intent)
        self.assertEqual(validated, {
            "action": "call",
            "target": "Rahul",
            "target_type": "name",
            "channel": "phone",
            "spoken_response": "Calling Rahul",
        })
        self.assertNotIn("command", validated)

    def test_malformed_model_json_is_rejected(self):
        for value in (None, [], "call", 123, {"action": []}, {"action": {"command": "pay"}}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_intent(value)


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.server = None
        self.inference = Mock(return_value={"action": "call"})
        self.start()

    def start(self, **changes):
        self.stop()
        self.config = GatewayConfig.from_env(environment(**changes))
        self.server = GatewayServer(("127.0.0.1", 0), self.config, self.inference)
        self.thread = threading.Thread(target=lambda: self.server.serve_forever(poll_interval=0.01), daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}/intent"

    def stop(self):
        if self.server is not None:
            self.server.shutdown()
            self.server.server_close()
            self.thread.join(timeout=1)
            self.server = None

    def tearDown(self):
        self.stop()

    def request(self, value=None, token=TOKEN_A, body=None, headers=None):
        data = json.dumps({"text": "बेटे को फोन करो"} if value is None else value).encode() if body is None else body
        all_headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        all_headers.update(headers or {})
        request = Request(self.url, data, all_headers)
        try:
            response = urlopen(request, timeout=2)
        except HTTPError as error:
            response = error
        except (ConnectionResetError, ConnectionAbortedError):
            return 503, {}
        with response:
            return response.code, json.load(response)

    def connect(self):
        client = socket.create_connection(("127.0.0.1", self.server.server_port), timeout=1)
        client.settimeout(1)
        return client

    def headers(self, length, extras=b""):
        return (b"POST /intent HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + TOKEN_A.encode() +
                b"\r\nContent-Type: application/json\r\nContent-Length: " + str(length).encode() +
                b"\r\n" + extras + b"\r\n")

    def receive(self, client):
        output = bytearray()
        while True:
            try:
                chunk = client.recv(4096)
            except ConnectionResetError:
                break
            if not chunk:
                break
            output.extend(chunk)
        return bytes(output)

    def test_candidates_in_request_are_passed_to_inference(self):
        entered = threading.Event()
        def mock_infer(text, config, candidates=None):
            self.assertEqual(candidates, ["Rahul", "Priya"])
            entered.set()
            return {"action": "call", "target": "Rahul", "spoken_response": "Calling Rahul"}
        self.inference.side_effect = mock_infer
        code, body = self.request(value={"text": "Call Rahul", "candidates": ["Rahul", "Priya"]})
        self.assertEqual(code, 200)
        self.assertEqual(body["action"], "call")
        self.assertEqual(body["target"], "Rahul")
        self.assertTrue(entered.is_set())

    def test_valid_request_reaches_mock_inference(self):
        self.assertEqual(self.request(), (200, {"action": "call"}))
        self.inference.assert_called_once_with("बेटे को फोन करो", self.config)

    def test_unauthorized_and_non_ascii_tokens_never_infer(self):
        for token in ("wrong", "café"):
            self.assertEqual(self.request(token=token)[0], 401)
        self.inference.assert_not_called()

    def test_all_configured_tokens_are_constant_time_compared(self):
        import server
        with patch("server.hmac.compare_digest", wraps=server.hmac.compare_digest) as compare:
            self.assertEqual(self.server.authenticate("Bearer " + TOKEN_A), "phone-a")
            self.assertEqual(compare.call_count, 2)

    def test_invalid_schema_and_duplicate_json_keys_never_infer(self):
        for value in ([], "फोन करो", 123, {"text": {}}, {"text": ""}, {"text": " "}, {"text": "x" * 1001},
                      {"text": "help", "command": "pay"}):
            with self.subTest(value=type(value).__name__):
                self.assertEqual(self.request(value)[0], 400)
        for raw in (b"null", b'{"text":"one","text":"two"}', b'{"text":NaN}', b"\xff"):
            self.assertEqual(self.request(body=raw)[0], 400)
        self.inference.assert_not_called()

    def test_oversized_body_and_unsupported_content_never_infer(self):
        self.assertEqual(self.request(body=b"x" * (MAX_BODY + 1))[0], 413)
        self.assertEqual(self.request(headers={"Content-Type": "text/plain"})[0], 415)
        self.assertEqual(self.request(headers={"Content-Encoding": "gzip"})[0], 415)
        self.inference.assert_not_called()

    def test_deeply_nested_body_is_rejected_as_invalid_json(self):
        self.assertEqual(self.request(body=b'{"text":' + b"[" * 1200 + b"0" + b"]" * 1200 + b"}")[0], 400)
        self.inference.assert_not_called()

    def test_total_header_bytes_are_bounded(self):
        with self.connect() as client:
            client.sendall(b"GET /health HTTP/1.1\r\nX-Large: " + b"a" * MAX_WIRE_REQUEST + b"\r\n\r\n")
            response = self.receive(client)
        self.assertIn(b" 431 ", response.split(b"\r\n", 1)[0])
        self.inference.assert_not_called()

    def test_duplicate_auth_length_and_chunked_requests_are_rejected(self):
        body = b'{"text":"hello"}'
        cases = [
            (b"Authorization: Bearer " + TOKEN_A.encode() + b"\r\n", 401),
            (b"Content-Length: " + str(len(body)).encode() + b"\r\n", 400),
            (b"Transfer-Encoding: chunked\r\n", 400),
        ]
        for extra, code in cases:
            with self.connect() as client:
                client.sendall(self.headers(len(body), extra) + body)
                response = self.receive(client)
                self.assertIn(f" {code} ".encode(), response.split(b"\r\n", 1)[0])
        self.inference.assert_not_called()

    def test_partial_body_has_a_real_deadline(self):
        start = time.monotonic()
        with self.connect() as client:
            client.sendall(self.headers(200) + b'{"text":')
            response = self.receive(client)
        self.assertIn(b" 408 ", response.split(b"\r\n", 1)[0])
        self.assertLess(time.monotonic() - start, 0.9)
        self.inference.assert_not_called()

    def test_slow_drip_cannot_reset_total_body_deadline(self):
        stop = threading.Event()
        sent = []
        start = time.monotonic()
        with self.connect() as client:
            client.sendall(self.headers(200))
            def drip():
                while not stop.wait(0.025):
                    try:
                        client.sendall(b" ")
                        sent.append(1)
                    except OSError:
                        return
            thread = threading.Thread(target=drip, daemon=True)
            thread.start()
            try:
                response = self.receive(client)
            finally:
                stop.set()
                thread.join(timeout=1)
        self.assertGreaterEqual(len(sent), 3)
        self.assertIn(b" 408 ", response.split(b"\r\n", 1)[0])
        self.assertLess(time.monotonic() - start, 0.9)
        self.inference.assert_not_called()

    def test_partial_headers_are_closed_by_deadline(self):
        start = time.monotonic()
        with self.connect() as client:
            client.sendall(b"POST /intent HTTP/1.1\r\nHost: local")
            self.assertEqual(self.receive(client), b"")
        self.assertLess(time.monotonic() - start, 0.9)

    def test_connection_capacity_rejects_excess_without_spawning_inference(self):
        self.start(MAA_MAX_CONNECTIONS="1", MAA_MAX_INFERENCES="1", MAA_REQUEST_TIMEOUT="0.5")
        with self.connect() as occupied:
            occupied.sendall(self.headers(200))
            deadline = time.monotonic() + 0.3
            while not self.server.global_limit.events["global"] and time.monotonic() < deadline:
                time.sleep(0.002)
            self.assertTrue(self.server.global_limit.events["global"])
            self.assertEqual(self.request()[0], 503)
        self.inference.assert_not_called()

    def test_per_device_rate_limit_does_not_spend_another_devices_allowance(self):
        self.start(MAA_DEVICE_REQUEST_LIMIT="1")
        self.assertEqual(self.request(token=TOKEN_A)[0], 200)
        self.assertEqual(self.request(token=TOKEN_A)[0], 429)
        self.assertEqual(self.request(token=TOKEN_B)[0], 200)
        self.assertEqual(self.inference.call_count, 2)

    def test_global_rate_limit_includes_unauthorized_attempts(self):
        self.start(MAA_GLOBAL_REQUEST_LIMIT="2")
        self.assertEqual(self.request(token="wrong")[0], 401)
        self.assertEqual(self.request(token="wrong")[0], 401)
        self.assertEqual(self.request()[0], 429)
        self.inference.assert_not_called()

    def test_inference_quota_is_shared_across_devices(self):
        self.start(MAA_INFERENCE_LIMIT="1")
        self.assertEqual(self.request(token=TOKEN_A)[0], 200)
        self.assertEqual(self.request(token=TOKEN_B), (429, {"error": "inference_quota_exceeded"}))
        self.assertEqual(self.inference.call_count, 1)

    def test_inference_capacity_is_bounded(self):
        self.start(MAA_MAX_INFERENCES="1")
        entered, release = threading.Event(), threading.Event()
        def blocked(text, config):
            entered.set()
            release.wait(1)
            return {"action": "help"}
        self.inference.side_effect = blocked
        first_result = []
        thread = threading.Thread(target=lambda: first_result.append(self.request()), daemon=True)
        thread.start()
        try:
            self.assertTrue(entered.wait(0.5))
            self.assertEqual(self.request(), (503, {"error": "inference_busy"}))
        finally:
            release.set()
            thread.join(timeout=1)
        self.assertEqual(first_result, [(200, {"action": "help"})])
        self.assertEqual(self.inference.call_count, 1)

    def test_timeout_does_not_release_capacity_while_work_is_still_running(self):
        self.start(MAA_MAX_INFERENCES="1", MAA_RESPONSE_TIMEOUT="0.08")
        entered, release, ended = threading.Event(), threading.Event(), threading.Event()
        def blocked(text, config):
            entered.set()
            release.wait(1)
            ended.set()
            return {"action": "help"}
        self.inference.side_effect = blocked
        try:
            self.assertEqual(self.request(), (504, {"error": "inference_timeout"}))
            self.assertTrue(entered.is_set())
            self.assertFalse(ended.is_set())
            self.assertEqual(self.request(), (503, {"error": "inference_busy"}))
        finally:
            release.set()
            self.assertTrue(ended.wait(0.5))

    def test_removed_token_is_revoked_after_restart(self):
        self.assertEqual(self.request(token=TOKEN_A)[0], 200)
        self.start(MAA_DEVICE_TOKENS=json.dumps({"phone-b": TOKEN_B}))
        self.assertEqual(self.request(token=TOKEN_A)[0], 401)
        self.assertEqual(self.request(token=TOKEN_B)[0], 200)

    def test_inference_failure_and_invalid_result_never_claim_completion_or_log_secrets(self):
        output = io.StringIO()
        with contextlib.redirect_stderr(output):
            self.inference.side_effect = TimeoutError("private transcript " + TOKEN_A)
            self.assertEqual(self.request(), (502, {"error": "inference_unavailable"}))
            self.inference.side_effect = None
            self.inference.return_value = {"action": "send_message"}
            self.assertEqual(self.request(), (502, {"error": "inference_unavailable"}))
        self.assertEqual(output.getvalue(), "")

    def test_environment_edits_do_not_silently_change_running_auth(self):
        with patch.dict("os.environ", {"MAA_DEVICE_TOKENS": json.dumps({"phone-b": TOKEN_B})}):
            self.assertEqual(self.request(token=TOKEN_A)[0], 200)


class UpstreamTests(unittest.TestCase):
    class Response(io.BytesIO):
        def __init__(self, body, headers=None):
            super().__init__(body)
            self.headers = headers or {}
            self.read_sizes = []
        def read(self, size=-1):
            self.read_sizes.append(size)
            return super().read(size)

    def setUp(self):
        self.config = GatewayConfig.from_env(environment())

    def test_candidates_included_in_system_prompt(self):
        response = self.Response(json.dumps({"choices": [{"message": {"content": '{"action":"call","target":"Rahul"}'}}]}).encode())
        with patch("server.open_upstream", return_value=response) as upstream:
            result = infer("Call Rahul", self.config, candidates=["Rahul", "Mom"])
            self.assertEqual(result, {"action": "call", "target": "Rahul"})
        request = upstream.call_args.args[0]
        payload = json.loads(request.data)
        system_content = payload["messages"][0]["content"]
        self.assertIn("Rahul, Mom", system_content)

    def test_valid_mock_response_and_request_schema(self):
        response = self.Response(json.dumps({"choices": [{"message": {"content": '{"action":"help","command":"ignored"}'}}]}).encode())
        with patch("server.open_upstream", return_value=response) as upstream:
            self.assertEqual(infer("मदद", self.config), {"action": "help"})
        request = upstream.call_args.args[0]
        payload = json.loads(request.data)
        self.assertEqual(payload["messages"][1], {"role": "user", "content": "मदद"})
        self.assertEqual(payload["max_tokens"], 80)
        self.assertEqual(upstream.call_args.kwargs["timeout"], 0.2)
        self.assertEqual(response.read_sizes, [MAX_UPSTREAM + 1])

    def test_oversized_declared_response_is_rejected_without_reading(self):
        response = self.Response(b"", {"Content-Length": str(MAX_UPSTREAM + 1)})
        with patch("server.open_upstream", return_value=response), self.assertRaises(ValueError):
            infer("help", self.config)
        self.assertEqual(response.read_sizes, [])

    def test_oversized_stream_is_bounded_even_without_a_length_header(self):
        response = self.Response(b"x" * (MAX_UPSTREAM + 5000))
        with patch("server.open_upstream", return_value=response), self.assertRaises(ValueError):
            infer("help", self.config)
        self.assertEqual(response.read_sizes, [MAX_UPSTREAM + 1])

    def test_compressed_or_invalid_upstream_payload_is_rejected(self):
        cases = [
            self.Response(b"{}", {"Content-Encoding": "gzip"}),
            self.Response(b'{"choices":[]}'),
            self.Response(b'{"choices":[{"message":{"content":null}}]}'),
            self.Response(b'{"choices":[{"message":{"content":"{\\"action\\":\\"pay\\"}"}}]}'),
            self.Response(b'{"choices":[], "choices":[]}'),
        ]
        for response in cases:
            with self.subTest(), patch("server.open_upstream", return_value=response), self.assertRaises(ValueError):
                infer("help", self.config)


if __name__ == "__main__":
    unittest.main()
