# Cloudflare Gateway for Maa & Nebius Token Factory

The Maa assistant backend is designed to run seamlessly on **Cloudflare**. You can deploy it as a serverless **Cloudflare Worker** (recommended) or expose your local Python server via **Cloudflare Tunnel**.

---

## Option 1: Cloudflare Worker (Recommended · Serverless Edge)

A Cloudflare Worker gives you a free, highly available, global HTTPS endpoint with zero servers to manage.

### Why it's best for Maa:
- **Instant HTTPS:** Android requires HTTPS for assistant endpoints (`https://...`). Cloudflare provides automatic SSL on `*.workers.dev`.
- **Global Edge Latency:** Runs in 300+ cities worldwide, delivering sub-second response times directly to the phone.
- **Generous Free Tier:** 100,000 free requests per day (far more than any family usage).
- **100% Native:** Zero dependencies; uses native Web standard `fetch`, `crypto`, and ES modules.

---

### Method A: 60-Second Web Dashboard Setup (No CLI Required)

1. Log into your free [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. In the left sidebar, click **Workers & Pages** → **Create Application** → **Create Worker**.
3. Name it `maa-assistant` and click **Deploy**.
4. Click **Edit Code**, replace the entire contents of `worker.js` with the code in [`cloudflare/worker.js`](./worker.js), and click **Deploy**.
5. Go to **Settings** → **Variables and Secrets**:
   - Add Secret: `NEBIUS_API_KEY` = *your Nebius Token Factory API key*
   - Add Secret: `MAA_DEVICE_TOKEN` = `your-device-token` *(or your custom token)*
   - Add Variable (optional): `NEBIUS_MODEL` = `Qwen/Qwen3-30B-A3B-Instruct-2507`
6. Copy your Worker URL:
   ```
   https://maa-assistant.<your-subdomain>.workers.dev
   ```
7. In the Maa Android app:
   - Hold the clock on the Home screen to open **Settings**.
   - Set **Backend URL** to `https://maa-assistant.<your-subdomain>.workers.dev`
   - Tap **Save**. Done!

---

### Method B: Wrangler CLI Deployment

From this project directory:

```bash
cd cloudflare

# 1. Set secrets (entered securely in terminal)
npx wrangler secret put NEBIUS_API_KEY
npx wrangler secret put MAA_DEVICE_TOKEN

# 2. Deploy
npx wrangler deploy
```

---

## Option 2: Cloudflare Tunnel (Keep Local Python Server)

If you prefer to keep running [`backend/server.py`](../backend/server.py) locally on your PC, you can expose `localhost:8080` to the internet via Cloudflare Tunnel without opening router ports or having a static IP:

1. Download [`cloudflared`](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/) (or `winget install Cloudflare.cloudflared`).
2. Start the Python gateway:
   ```powershell
   .\backend\run_gateway.ps1
   ```
3. In another terminal, run a free Quick Tunnel:
   ```bash
   cloudflared tunnel --url http://localhost:8080
   ```
4. Cloudflare outputs a temporary URL:
   ```
   https://random-words.trycloudflare.com
   ```
5. Enter `https://random-words.trycloudflare.com` in the Maa app settings.

---

## Testing Your Cloudflare Endpoint

Test the `/health` endpoint:
```bash
curl https://your-worker.workers.dev/health
```
Output:
```json
{"status":"ok","service":"maa-intent-gateway","model":"Qwen/Qwen3-30B-A3B-Instruct-2507","ready":true}
```

Test the `/intent` endpoint:
```bash
curl -X POST https://your-worker.workers.dev/intent \
  -H "Authorization: Bearer your-device-token" \
  -H "Content-Type: application/json" \
  -d '{"text": "बेटे को फोन लगाओ", "candidates": ["बेटा"]}'
```
Output:
```json
{
  "action": "call",
  "target": "बेटा",
  "target_type": "relationship",
  "channel": "phone",
  "spoken_response": "ठीक है, बेटे को फोन लगाया जा रहा है।"
}
```
