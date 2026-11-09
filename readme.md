# PR Reviewer

A GitHub App that automatically reviews pull requests using an LLM. When a PR is opened or updated, it fetches the diff, grounds the review in the existing codebase using RAG, and posts a structured review comment back on GitHub — all asynchronously so GitHub's webhook never times out.

A companion React dashboard lets you browse your repos, past reviews, and run an on-demand review of any single file.

## How it works

1. GitHub sends a webhook to `/webhook/github` when a PR is opened or updated.
2. The signature is verified (HMAC-SHA256) and the request is handed off to an async worker, so GitHub gets an immediate response.
3. The diff is fetched and filtered — lockfiles, binaries, and files with no patch are dropped.
4. The diff is compressed to fit a fixed token budget: deletion-only hunks are stripped, files are sorted by size, and anything that doesn't fit the budget is listed by filename only rather than dropped silently.
5. In parallel, changed files are chunked and embedded (Gemini embeddings) into a pgvector store, so the codebase's existing patterns stay searchable.
6. A similarity search retrieves related code chunks from the same repo (excluding files already in the PR) to ground the review in real context, not just the diff in isolation.
7. The diff + retrieved context are sent to an LLM (Groq), wrapped in a Resilience4j circuit breaker so a slow or failing LLM call degrades gracefully instead of cascading.
8. The review is posted as a PR comment and saved to Postgres.

## Architecture

```
GitHub ── webhook ──▶ WebhookController ──▶ (async) ReviewOrchestratorService
                                                   │
                        ┌──────────────────────────┼───────────────────────────┐
                        ▼                           ▼                           ▼
                DiffFilterService         RepoIndexingService          DiffCompressionService
                (drop noise files)      (chunk + embed changed            (fit token budget)
                                          files into pgvector)
                        │                           │                           │
                        └──────────────┬────────────┴─────────────┬─────────────┘
                                       ▼                           ▼
                             RetrievalService              LLMReviewService
                          (similarity search,           (Groq + circuit breaker)
                           top-5 related chunks)
                                       └─────────────┬─────────────┘
                                                      ▼
                                          GitHubClientService.postComment
                                                      │
                                                      ▼
                                           Review saved to Postgres
```

## Tech stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.3.4, Java 17 |
| Auth (dashboard users) | Spring Security OAuth2 login (GitHub) |
| Auth (as the GitHub App) | JWT signed with the app's private key, exchanged for a cached installation token |
| LLM | Groq API, via `WebClient` |
| Embeddings & vector search | Spring AI, Google `gemini-embedding-001`, PostgreSQL + pgvector |
| Database | PostgreSQL (JPA/Hibernate) |
| Resilience | Resilience4j (circuit breaker + time limiter around the LLM call) |
| Async | Bounded thread pool with MDC-propagating `TaskDecorator`, so correlation IDs survive the async hop |
| Cache | Redis (diff-hash dedup and webhook-delivery idempotency — see Known limitations) |
| Observability | Structured logging keyed on GitHub's delivery ID as a correlation ID |

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/webhook/github` | HMAC signature | GitHub's entry point; triggers a review on `opened`/`synchronize` |
| `GET` | `/api/me` | — | Current session info |
| `GET` | `/api/repos` | Session | Synced repos (10-minute cache, or force with `/refresh`) |
| `POST` | `/api/repos/refresh` | Session | Force a re-sync from GitHub |
| `GET` | `/api/repos/{repoId}/pulls` | Session | PRs for a repo |
| `GET` | `/api/repos/{repoId}/files` | Session | File tree of a repo |
| `GET` | `/api/repos/{repoId}/files/review?path=...` | Session | On-demand review of a single file |
| `GET` | `/api/reviews` | — | Paginated review summaries |
| `GET` | `/api/reviews/{id}` | — | Full review detail |

## Running locally

### Prerequisites
- Java 17, Maven
- PostgreSQL with the `pgvector` extension (`pgvector/pgvector:pg16` Docker image works out of the box)
- Redis
- A GitHub App (for the private key, app ID, and webhook secret) and a GitHub OAuth App (for dashboard login)
- A Groq API key and a Google Gemini API key

### Environment variables

```bash
GITHUB_APP_ID=
GITHUB_APP_PRIVATE_KEY_PATH=
GITHUB_WEBHOOK_SECRET=
GITHUB_OAUTH_CLIENT_ID=
GITHUB_OAUTH_CLIENT_SECRET=
GROQ_API_KEY=
GOOGLE_GENAI_API_KEY=
DB_PASSWORD=
REDIS_PASSWORD=
```

### Run with Docker Compose

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: prreviewer
    ports: ["5433:5432"]
    volumes: ["pgvector_data:/var/lib/postgresql/data"]

  redis:
    image: redis:7
    ports: ["6379:6379"]
    volumes: ["redis_data:/data"]

volumes:
  pgvector_data:
  redis_data:
```

```bash
docker compose up -d
./mvnw spring-boot:run
```

The server starts on port 8081. For local webhook testing, tunnel it with `ngrok http 8081` and point your GitHub App's webhook URL at the resulting HTTPS address.

### Frontend

The dashboard lives in a separate repo (`pr-reviewer-ui`) — a Vite + React + TypeScript app.

```bash
cd pr-reviewer-ui
npm install
npm run dev
```

## Known limitations

- **Currently scoped to local development** — no production deployment yet (see `.env` setup below); webhook testing relies on a local tunnel (e.g. `ngrok`).
- **Chunking is line-based, not AST-aware**, so a chunk boundary can occasionally land mid-function.
- **No automated test suite yet** — `DiffCompressionService` and `WebhookSignatureVerifier` are the two highest-value places to start.

## What I'd improve next

- Deploy to a real environment with a stable public webhook endpoint
- Move to AST-aware chunking for more coherent retrieval context
- Add a CI pipeline and a baseline test suite
- Migrate schema management from `ddl-auto=update` to Flyway