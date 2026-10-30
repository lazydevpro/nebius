/**
 * Maa Assistant - Cloudflare Worker Gateway for Nebius Token Factory
 * Provides a serverless, global HTTPS endpoint for the Maa Android app.
 *
 * Environment Secrets / Variables needed:
 *   - NEBIUS_API_KEY: Your Nebius Token Factory API key
 *   - NEBIUS_MODEL: "Qwen/Qwen3-30B-A3B-Instruct-2507" (or your chosen model)
 *   - MAA_DEVICE_TOKEN: Bearer token configured in the Maa app settings (>= 24 chars)
 */

const ALLOWED_ACTIONS = new Set([
  "call", "media", "qr", "help", "home", "unsupported",
  "apps", "alarm", "notifications", "messages",
  "missed_calls", "gallery", "camera", "info", "chat"
]);

const ALLOWED_CHANNELS = new Set(["phone", "whatsapp"]);
const ALLOWED_TARGET_TYPES = new Set(["name", "relationship", "number", "unknown"]);
const MAX_BODY_BYTES = 16384;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 1. Health check endpoint
    if (request.method === "GET" && (url.pathname === "/health" || url.pathname === "/")) {
      return jsonResponse({
        status: "ok",
        service: "maa-intent-gateway",
        model: env.NEBIUS_MODEL || "Qwen/Qwen3-30B-A3B-Instruct-2507",
        ready: Boolean(env.NEBIUS_API_KEY)
      });
    }

    // 2. Only allow POST /intent
    if (request.method !== "POST" || url.pathname !== "/intent") {
      return jsonResponse({ error: "Not Found" }, 404);
    }

    // 3. Authenticate Bearer Token
    const authHeader = request.headers.get("Authorization") || "";
    if (!authHeader.startsWith("Bearer ")) {
      return jsonResponse({ error: "Missing or invalid Authorization header" }, 401);
    }
    const token = authHeader.slice(7).trim();
    if (!isValidToken(token, env)) {
      return jsonResponse({ error: "Forbidden: invalid device token" }, 403);
    }

    // 4. Validate Content-Type and Body size
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("application/json")) {
      return jsonResponse({ error: "Content-Type must be application/json" }, 415);
    }

    const contentLength = parseInt(request.headers.get("Content-Length") || "0", 10);
    if (contentLength > MAX_BODY_BYTES) {
      return jsonResponse({ error: "Request payload too large" }, 413);
    }

    let payload;
    try {
      const text = await request.text();
      if (text.length > MAX_BODY_BYTES) {
        return jsonResponse({ error: "Request payload too large" }, 413);
      }
      payload = JSON.parse(text);
    } catch {
      return jsonResponse({ error: "Malformed JSON payload" }, 400);
    }

    const queryText = (payload.text || "").trim();
    if (!queryText) {
      return jsonResponse({ error: "Missing or empty 'text' field" }, 400);
    }
    if (queryText.length > 1000) {
      return jsonResponse({ error: "Text exceeds 1000 characters" }, 400);
    }

    const candidates = Array.isArray(payload.candidates) ? payload.candidates : [];
    const phoneContext = payload.phone_context && typeof payload.phone_context === "object" ? payload.phone_context : null;

    // 5. Call Nebius Token Factory upstream
    try {
      const result = await callNebius(queryText, candidates, phoneContext, env);
      return jsonResponse(result);
    } catch (err) {
      return jsonResponse({
        error: "Upstream inference failure",
        message: err.message || "Unknown error"
      }, 502);
    }
  }
};

/** Verify device token against configured secrets using constant-time comparison */
function isValidToken(token, env) {
  if (!token || token.length < 16) return false;

  // Single token secret
  if (env.MAA_DEVICE_TOKEN && safeCompare(token, env.MAA_DEVICE_TOKEN)) {
    return true;
  }

  // Multi-device JSON map: {"device1": "token1", "device2": "token2"}
  if (env.MAA_DEVICE_TOKENS) {
    try {
      const tokens = typeof env.MAA_DEVICE_TOKENS === "string"
        ? JSON.parse(env.MAA_DEVICE_TOKENS)
        : env.MAA_DEVICE_TOKENS;
      for (const t of Object.values(tokens)) {
        if (typeof t === "string" && safeCompare(token, t)) return true;
      }
    } catch {
      // Ignore parse failure
    }
  }

  return false;
}

function safeCompare(a, b) {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i++) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}

/** Calls Nebius Token Factory chat completion API */
async function callNebius(queryText, candidates, phoneContext, env) {
  const apiKey = env.NEBIUS_API_KEY;
  if (!apiKey) throw new Error("NEBIUS_API_KEY secret is not configured on Cloudflare Worker");

  const model = env.NEBIUS_MODEL || "Qwen/Qwen3-30B-A3B-Instruct-2507";
  const baseUrl = (env.NEBIUS_BASE_URL || "https://api.tokenfactory.nebius.com/v1").replace(/\/+$/, "");

  let prompt =
    'You are Asha (आशा), a warm, caring, and respectful AI voice assistant and companion for an elderly Indian parent (Maa).\n' +
    'Classify the user request and return ONLY valid JSON with schema: {"action":"...", "target":"...", "target_type":"...", "channel":"...", "spoken_response":"..."}.\n' +
    'Allowed actions:\n' +
    '- "call": user wants to call someone (set "target" to contact name or relationship)\n' +
    '- "media": user wants to open WhatsApp or view media\n' +
    '- "messages": user wants to check SMS or messages\n' +
    '- "missed_calls": user asks about missed calls or who called\n' +
    '- "gallery": user wants to open photo gallery\n' +
    '- "camera": user wants to take photos, videos, or open camera\n' +
    '- "apps": user wants to see all apps\n' +
    '- "alarm": user wants alarms or reminders\n' +
    '- "notifications": user wants to check notifications\n' +
    '- "home": user wants to go to home screen\n' +
    '- "help": user needs family help or emergency\n' +
    '- "info": user asks about phone status (battery, time, date, network, etc.)\n' +
    '- "chat": friendly conversation, greetings, companionship, how are you, etc.\n' +
    '- "unsupported": other requests\n\n' +
    'Allowed channels: "phone", "whatsapp".\n' +
    'Allowed target_types: "name", "relationship", "number", "unknown".\n';

  if (candidates && candidates.length > 0) {
    const safeCandidates = candidates
      .map(c => String(c).trim().slice(0, 50))
      .filter(Boolean);
    if (safeCandidates.length > 0) {
      prompt += `Family contacts on this phone: ${safeCandidates.join(', ')}.\n`;
    }
  }

  if (phoneContext) {
    prompt += 'Live phone status:\n';
    if (phoneContext.battery) prompt += `- Battery: ${String(phoneContext.battery).slice(0, 50)}\n`;
    if (phoneContext.missed_calls) prompt += `- Missed Calls: ${String(phoneContext.missed_calls).slice(0, 100)}\n`;
    if (phoneContext.time) prompt += `- Current Time: ${String(phoneContext.time).slice(0, 50)}\n`;
    if (phoneContext.date) prompt += `- Current Date: ${String(phoneContext.date).slice(0, 50)}\n`;
    if (phoneContext.network) prompt += `- Network: ${String(phoneContext.network).slice(0, 50)}\n`;
  }

  prompt +=
    '\nGuidelines:\n' +
    '1. If user wants to call someone, extract their name or relationship in "target".\n' +
    '2. Always provide a natural, respectful spoken confirmation in "spoken_response" in the user\'s language (Hindi in Devanagari or English).\n' +
    '3. For phone status (battery, time, missed calls, date), answer accurately from the live phone status provided above with action="info" or action="missed_calls".\n' +
    '4. For greetings or conversation, respond warmly as Asha (addressing the user respectfully as "माँ जी" / "जी").\n' +
    '5. Keep "spoken_response" concise (1-2 sentences) and natural for Text-To-Speech.\n' +
    '6. Never output markdown or extra commentary. Return ONLY the JSON object.';

  const upstreamBody = {
    model: model,
    temperature: 0.2,
    max_tokens: 150,
    messages: [
      { role: "system", content: prompt },
      { role: "user", content: queryText }
    ]
  };

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);

  let upstreamResponse;
  try {
    upstreamResponse = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(upstreamBody),
      signal: controller.signal
    });
  } finally {
    clearTimeout(timeoutId);
  }

  if (!upstreamResponse.ok) {
    const errText = await upstreamResponse.text().catch(() => "");
    throw new Error(`Nebius API returned ${upstreamResponse.status}: ${errText.slice(0, 200)}`);
  }

  const rawJson = await upstreamResponse.json();
  let content = rawJson?.choices?.[0]?.message?.content;
  if (typeof content !== "string") {
    throw new Error("Invalid response structure from Nebius");
  }

  content = content.trim();
  // Strip Markdown ```json ... ``` blocks if present
  if (content.startsWith("```")) {
    const lines = content.split("\n");
    lines.shift(); // remove leading ```
    if (lines.length && lines[lines.length - 1].trim().startsWith("```")) {
      lines.pop(); // remove trailing ```
    }
    content = lines.join("\n").trim();
  }

  let parsed;
  try {
    parsed = JSON.parse(content);
  } catch {
    throw new Error(`Model did not emit valid JSON: ${content.slice(0, 100)}`);
  }

  return sanitizeIntent(parsed);
}

/** Sanitize and validate intent matching backend rules */
function sanitizeIntent(value) {
  if (!value || typeof value !== "object" || typeof value.action !== "string" || !ALLOWED_ACTIONS.has(value.action)) {
    throw new Error(`Invalid intent action: ${value?.action}`);
  }

  const result = { action: value.action };

  if (typeof value.target === "string") {
    const clean = value.target.trim().slice(0, 100);
    if (clean) result.target = clean;
  }

  if (typeof value.target_type === "string" && ALLOWED_TARGET_TYPES.has(value.target_type)) {
    result.target_type = value.target_type;
  }

  if (typeof value.channel === "string" && ALLOWED_CHANNELS.has(value.channel)) {
    result.channel = value.channel;
  }

  if (typeof value.spoken_response === "string") {
    const clean = value.spoken_response.trim().slice(0, 250);
    if (clean) result.spoken_response = clean;
  }

  return result;
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store"
    }
  });
}
