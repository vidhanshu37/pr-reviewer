# Setup guide

Run the whole system locally: Spring Boot app, Postgres (pgvector), Redis and the agent service, in Docker. About 20 minutes.

## 1. Prerequisites

- Docker Desktop (Compose v2)
- Git, and `openssl` (included with Git for Windows)
- A GitHub account
- An [ngrok](https://ngrok.com) account (free plan works)
- API keys: NVIDIA ([build.nvidia.com](https://build.nvidia.com)), Groq, Google AI (embeddings)

## 2. Get the code

Clone both repos **side by side**; the compose file builds the agent from `../pr-reviewer-agent`.

```bash
mkdir work && cd work
git clone https://github.com/vidhanshu37/pr-reviewer
git clone https://github.com/vidhanshu37/pr-reviewer-agent
```

## 3. Create the GitHub App

GitHub → Settings → Developer settings → GitHub Apps → New GitHub App.

| Field | Value |
|---|---|
| Webhook URL | filled in step 4 (ngrok URL + `/webhook/github`) |
| Webhook secret | a random string; the same value goes into `.env` as `GITHUB_WEBHOOK_SECRET` |
| Repository permissions | Pull requests: **Read & write**, Contents: **Read** (Metadata is read-only by default) |
| Subscribe to events | **Pull request** |

Posting a PR comment needs Issues or Pull requests write access (GitHub docs). The Contents permission is my assumption for reading files; if file reads fail, check it first.

Then:
1. Note the **App ID** (`GITHUB_APP_ID`).
2. **Generate a private key**; GitHub downloads a `.pem` file.
3. **Install** the app on the repositories you want reviewed.

### The private key (`.pem`)

The app reads the key as **PKCS#8**. Check the first line:

```bash
head -1 github-app.pem
```

- `-----BEGIN PRIVATE KEY-----`: ready to use.
- `-----BEGIN RSA PRIVATE KEY-----`: convert (GitHub keys are usually in this older format):

```bash
openssl pkcs8 -topk8 -inform PEM -outform PEM -in github-app.pem -out github-app-pkcs8.pem -nocrypt
```

Put the final file at **`pr-reviewer/secrets/github-app.pem`** (the compose file mounts that exact path read-only). Never commit it; `secrets/` and `.env` belong in `.gitignore`.

> Create the file **before** the first `docker compose up`. If the path is missing, Docker creates an empty *folder* with that name and the app fails with `Is a directory` (see Troubleshooting).

## 4. Start ngrok

GitHub needs a public URL for the webhook.

```bash
ngrok config add-authtoken <YOUR_NGROK_TOKEN>    # once
ngrok http 8081
```

Copy the `https://....ngrok-free.dev` URL from the `Forwarding` line, and set the GitHub App's webhook URL to:

```
https://<your-id>.ngrok-free.dev/webhook/github
```

Content type: `application/json`. The ngrok inspector at `http://127.0.0.1:4040` shows every request and lets you replay it. Free-plan URLs may change when ngrok restarts; check the Domains page in the ngrok dashboard, and update the webhook URL if it changed.

## 5. Configure

**`pr-reviewer/.env`** (all values yours; never commit):

| Variable | Purpose |
|---|---|
| `DB_PASSWORD` | Postgres password (used by Postgres and the app) |
| `REDIS_PASSWORD` | Redis password |
| `GITHUB_APP_ID` | App ID from step 3 |
| `GITHUB_WEBHOOK_SECRET` | the webhook secret from step 3 |
| `GITHUB_APP_PRIVATE_KEY_PATH` | `/run/secrets/github-app.pem` (path inside the container) |
| `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET` | OAuth App for the dashboard login; required to boot (no default), use real or placeholder values |
| `GROQ_API_KEY` | single-LLM fallback pipeline |
| `GOOGLE_API_KEY` | embeddings for code indexing |
| `MCP_JWT_SECRET` | signs the per-review job tokens; use `openssl rand -hex 32` |
| `AGENT_ENABLED` | `true` (default) uses the agent; `false` uses only the old pipeline |
| `AGENT_BASE_URL` | agent URL; compose sets `http://agent:8000` |

Variables with no default must be set, or the app will not start (inferred from the Spring placeholders, it matched my run).

**`pr-reviewer-agent/.env`**:

| Variable | Purpose |
|---|---|
| `LLM_PROVIDER` | `nvidia` or `groq` |
| `NVIDIA_API_KEY` | when using NVIDIA |
| `GROQ_API_KEY` | when using Groq |
| `LLM_TPM_LIMIT` | `10000000` for NVIDIA; Groq needs a low value (see that repo's `.env.example`) |

Copy `.env.example` from each repo to `.env` and fill it in. `MCP_URL` is set by compose.

## 6. Run

```bash
cd pr-reviewer
docker compose up --build
```

First build takes a few minutes (Maven dependencies and images). Later runs: `docker compose up -d`.

## 7. Verify

```bash
docker compose ps
docker compose logs -f app agent | grep -v "GET /health"
```

Expected in the logs: `Started PrReviewerApplication`, `Registered tools: 4`, and the agent's `Uvicorn running on http://0.0.0.0:8000`.

Then open or update a pull request in an installed repository. In the logs you should see `Received pull_request event`, and after a while `Agent review posted ... status=done`; the comment appears on the PR. Typical review time on a medium PR is one to a few minutes (NVIDIA hosted model).

To resend a webhook without a new commit: ngrok inspector → Replay, or GitHub App → Advanced → Recent deliveries → Redeliver.

Stop everything: `docker compose down` (data stays in the named volumes; add `-v` to wipe it).

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `env file .../.env not found`, `DB_PASSWORD is not set` | create `pr-reviewer/.env` (step 5) |
| `Bind for 0.0.0.0:5433 failed: port is already allocated` | another Postgres uses host port 5433. Stop it (`docker stop <name>`), or change the host port in `docker-compose.yml` (the containers talk over the internal network, so only your host access changes) |
| `IOException: Is a directory` in `GitHubAppAuthService` | `secrets/github-app.pem` did not exist at start, so Docker created a folder. `docker compose down`, `rmdir secrets/github-app.pem`, add the real key file, start again |
| `Failed to generate GitHub App JWT` with a parse error (likely) | key is not PKCS#8; convert it (step 3) |
| ngrok shows no request | webhook URL not saved, or ngrok restarted with a new URL |
| webhook returns 401/403 (likely) | `GITHUB_WEBHOOK_SECRET` differs from the secret in the GitHub App |
| `200` but no review | only `opened` and `synchronize` events are handled; check the app is installed on that repo |
| Review comment is the old-style single summary | agent disabled or unreachable; check `AGENT_ENABLED` and `docker compose logs agent` |
| Agent returns `partial` | a specialist failed (quota or bad reply); the comment says which one |

## Notes

- The MCP dev-token printer is for local debugging only; keep it disabled outside development.
- Do not commit `.env`, `secrets/` or any `.pem`. If a key was ever pasted into a chat or commit, rotate it.