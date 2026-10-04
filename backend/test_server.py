import unittest
import os
import json
import threading
from unittest.mock import patch
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from http.server import ThreadingHTTPServer
from server import validate_intent, Handler

class IntentTests(unittest.TestCase):
    def test_allowed_actions(self):
        for action in ("call", "media", "qr", "help", "home", "unsupported"):
            self.assertEqual(validate_intent({"action": action}), {"action": action})
    def test_reject_arbitrary_execution(self):
        for action in ("pay", "shell", "send_message", "", None):
            with self.assertRaises(ValueError):
                validate_intent({"action": action})
    def test_discard_extra_model_fields(self):
        self.assertEqual(validate_intent({"action": "help", "command": "pay"}), {"action": "help"})
    def test_malformed_model_json_is_rejected(self):
        for value in (None, [], "call", 123, {"action": []}, {"action": {"command": "pay"}}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_intent(value)

class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.environment=patch.dict(os.environ,{"MAA_ACCESS_TOKEN":"test-token-for-local-tests-only"})
        self.environment.start()
        self.server=ThreadingHTTPServer(("127.0.0.1",0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True)
        self.thread.start()
        self.url=f"http://127.0.0.1:{self.server.server_port}/intent"
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.environment.stop()
    def request(self, value, token="test-token-for-local-tests-only"):
        return Request(self.url,json.dumps(value).encode(),{"Authorization":"Bearer "+token,"Content-Type":"application/json"})
    def test_unauthorized_never_calls_inference(self):
        with patch("server.infer") as inference:
            with self.assertRaises(HTTPError) as caught: urlopen(self.request({"text":"फोन करो"},"wrong"))
            self.assertEqual(caught.exception.code,401); caught.exception.close(); inference.assert_not_called()
    def test_invalid_transcript_never_calls_inference(self):
        with patch("server.infer") as inference:
            with self.assertRaises(HTTPError) as caught: urlopen(self.request({"text": {"command":"pay"}}))
            self.assertEqual(caught.exception.code,400); caught.exception.close(); inference.assert_not_called()
    def test_non_object_json_returns_400_without_inference(self):
        with patch("server.infer") as inference:
            for value in (None, [], "फोन करो", 123):
                with self.subTest(value=value), self.assertRaises(HTTPError) as caught:
                    urlopen(self.request(value))
                self.assertEqual(caught.exception.code,400); caught.exception.close()
            inference.assert_not_called()
    def test_non_ascii_token_returns_401_without_inference(self):
        with patch("server.infer") as inference:
            with self.assertRaises(HTTPError) as caught: urlopen(self.request({"text":"मदद"},"café"))
            self.assertEqual(caught.exception.code,401); caught.exception.close(); inference.assert_not_called()
    def test_valid_transcript_reaches_inference(self):
        with patch("server.infer",return_value={"action":"call"}) as inference:
            with urlopen(self.request({"text":"बेटे को फोन करो"})) as response:
                self.assertEqual(json.load(response),{"action":"call"})
            inference.assert_called_once_with("बेटे को फोन करो")
    def test_inference_failure_does_not_claim_completion(self):
        with patch("server.infer",side_effect=TimeoutError):
            with self.assertRaises(HTTPError) as caught: urlopen(self.request({"text":"मदद"}))
            self.assertEqual(caught.exception.code,502); caught.exception.close()

if __name__ == "__main__":
    unittest.main()
