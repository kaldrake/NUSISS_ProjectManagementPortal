# Project Management Portal

A web portal for managing software projects and scanning their GitHub repositories for security vulnerabilities. A scan clones the repository, analyses it with SonarQube, and asks an LLM (Gemini by default) for a fix suggestion for each BLOCKER/CRITICAL finding.

## Architecture

| Component | Tech | Port | Responsibility |
|---|---|---|---|
| `project-portal-frontend` | React + TypeScript, served by nginx | 3000 | UI. Calls the three services directly. |
| `springBoot_login` (login-service) | Spring Boot 3, Java 17 | 8081 | Registration, login, JWT issuing (`login_db`) |
| `springBoot_project` (project-service) | Spring Boot 3, Java 17 | 8082 | Projects, repositories, dashboard, scan trigger (`project_db`) |
| `springBoot_scan` (scan-service) | Spring Boot 3, Java 17 | 8083 | Clone + SonarQube scan, vulnerabilities, AI suggestions (`scan_db`) |
| SonarQube Community 9.9 + PostgreSQL | Docker | 9001 | Static analysis engine used by scan-service |
| MySQL 8.x | External (e.g. Amazon RDS) | 3306 | One server, three databases: `login_db`, `project_db`, `scan_db` |

All services validate the same JWT (shared `JWT_SECRET`). Access is owner-based: a user only sees and changes their own projects, scans and vulnerabilities.

Diagrams (Mermaid) are in [`docs/`](docs): architecture, microservice communication, auth flow, scan workflow, scan results sequence, scan class diagram, entity relation, use cases and CI/CD pipeline.

## Prerequisites

- Docker and Docker Compose
- A MySQL 8.x server reachable from the containers (the compose file does **not** start one)
- A free Gemini API key from [Google AI Studio](https://aistudio.google.com/) (optional: without it, scans still complete with template fix suggestions at low confidence)

To run services or tests outside Docker you also need JDK 17 and Node 18.

## Quick start

### 1. Create the databases

Run the schema script against your MySQL server. **It drops and recreates `login_db`, `project_db` and `scan_db`, so all existing data in them is lost.**

```bash
mysql -h <DB_HOST> -u root -p < docs/DDL.txt
```

The services run with `spring.jpa.hibernate.ddl-auto=validate` (login and project), so the tables must come from this script.

### 2. Configure `.env`

Create a `.env` file in the repository root (it is git-ignored). Docker Compose reads it automatically.

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT` | MySQL server host and port |
| `LOGIN_DB_NAME`, `PROJECT_DB_NAME`, `SCAN_DB_NAME` | Database names (`login_db`, `project_db`, `scan_db`) |
| `LOGIN_DB_USER` / `_PASSWORD`, `PROJECT_DB_USER` / `_PASSWORD`, `SCAN_DB_USER` / `_PASSWORD` | Credentials per service. `docs/DDL.txt` creates `login_user`, `project_user` and `scan_user`; the user defaults to `root` if unset. |
| `JWT_SECRET` | **Base64-encoded** HMAC key shared by all three services. Generate one with `openssl rand -base64 48`. |
| `JWT_EXPIRATION` | Token lifetime in milliseconds, e.g. `86400000` |
| `LOGIN_SERVICE_URL`, `SCAN_SERVICE_URL` | Inter-service URLs (default to the compose service names) |
| `SONAR_TOKEN` | SonarQube **User Token** (see step 4) |
| `AI_PROVIDER` | `gemini` (default), `deepseek` or `claude` |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Gemini key and model. The free tier is per Google Cloud project; check AI Studio for models with a free quota. |
| `DEEPSEEK_API_KEY`, `CLAUDE_API_KEY`, `CLAUDE_MODEL` | Only needed if you switch `AI_PROVIDER` |

### 3. Start the stack

```bash
docker compose up --build -d
```

| URL | What |
|---|---|
| http://localhost:3000 | Frontend |
| http://localhost:8081, 8082, 8083 | Login, project and scan APIs |
| http://localhost:9001 | SonarQube |

### 4. Set up SonarQube (first run only)

1. Open http://localhost:9001 and log in as `admin` / `admin`, then set a new password.
2. Go to **My Account → Security** and generate a **User Token** (not a Project or Global Analysis token: scan-service also reads results via the API).
3. Put it in `.env` as `SONAR_TOKEN` and restart scan-service:

```bash
docker compose up -d scan-service
```

### 5. Use the app

Register a user, log in, create a project, add a GitHub repository (public repositories; the branch defaults to the repository's default branch), then click **Scan Now**. Results appear on the project page and dashboard once the scan completes.

## Running the tests

Backend tests run in Docker and need no database or API keys:

```bash
docker compose --profile test run --rm login-tests
docker compose --profile test run --rm project-tests
docker compose --profile test run --rm scan-tests
```

Or all three together:

```bash
docker compose --profile test up --abort-on-container-exit
```

Frontend tests (Jest + React Testing Library):

```bash
cd project-portal-frontend
npm ci
npm test -- --watchAll=false --coverage
```

JaCoCo coverage reports are written to `target/site/jacoco` in each backend service.

## CI/CD

[`.github/workflows/build.yml`](.github/workflows/build.yml) runs on pushes to `main` and `fix-vulnerabilities` and on pull requests to `main`:

- **backend-tests**: `mvn -B test` for the three services (matrix), uploads surefire reports
- **sonarcloud-scan**: tests with JaCoCo and Jest coverage, then one SonarCloud analysis of the whole repository with a quality gate on new code
- **Snyk** dependency scans are defined in the workflow but currently commented out (free-tier scan limit)

Required GitHub configuration for the SonarCloud job: secret `SONARCLOUD_TOKEN`, variables `SONARCLOUD_ORGANIZATION` and `SONARCLOUD_PROJECT_KEY` (these are repository *Variables*, not Secrets).

## Project layout

```
project-portal-backend/
  springBoot_login/demo     login-service
  springBoot_project/demo   project-service
  springBoot_scan/demo      scan-service
project-portal-frontend/    React app + nginx Dockerfile
docs/                       DDL.txt and Mermaid diagrams
docker-compose.yml          frontend, 3 services, SonarQube, test profile
```

## Known limitations

- CORS is restricted to `http://localhost:3000` in all three services; change it when deploying under a real domain.
- Only the `VULNERABILITY` issue type is fetched from SonarQube, so counts can differ from the SonarQube UI.
- Deleting a project does not delete its scan data in `scan_db`.
- The frontend has no button to regenerate a single AI suggestion (the backend endpoint exists); re-scan instead.
- All services share one MySQL server (separate databases), and the three services currently connect with the credentials you put in `.env`.
