# DVCS Platform

A self-hosted, GitHub-like distributed version control platform built with Java 21 Spring Boot and React/TypeScript.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Docker Compose Setup](#docker-compose-setup)
- [First-Push Walkthrough](#first-push-walkthrough)
- [Environment Variable Reference](#environment-variable-reference)

---

## Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Java | 21 | Required to run the Spring Boot backend |
| Node.js | 20 | Required to run the React frontend dev server |
| Docker | 24+ | Required for Docker Compose setup and infrastructure services |
| git | 2.x | Required for repository operations |

> The Maven wrapper (`mvnw`) is bundled in `backend/` — you do not need to install Maven separately.

---

## Local Development Setup

This approach runs the backend and frontend directly on your machine, with infrastructure services (PostgreSQL, Redis, MinIO) running in Docker.

### 1. Start infrastructure services

```bash
docker compose up postgres redis minio -d
```

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` if you need to change any defaults (the defaults work out of the box for local development).

### 3. Start the backend

Run from the `backend/` directory:

```bash
cd backend
./mvnw spring-boot:run
```

The API server starts on `http://localhost:8080`. You can verify it is healthy at `http://localhost:8080/actuator/health`.

On Windows, use `mvnw.cmd` instead:

```cmd
cd backend
mvnw.cmd spring-boot:run
```

### 4. Start the frontend

In a separate terminal, run from the `frontend/` directory:

```bash
cd frontend
npm install   # first time only
npm run dev
```

The dev server starts on `http://localhost:3000` with hot-module replacement enabled.

### Project structure

```
DVCS/
├── backend/                  # Java 21 Spring Boot multi-module Maven project
│   ├── backend-api/          # Entry point module (Spring Boot application)
│   ├── backend-core/         # Domain logic, auth, repositories, services
│   ├── backend-git/          # Git transport (smart HTTP protocol)
│   ├── backend-pipeline/     # CI/CD pipeline engine
│   ├── backend-storage/      # Storage abstraction (local / S3)
│   ├── Dockerfile
│   └── mvnw                  # Maven wrapper
├── frontend/                 # React + TypeScript + Vite SPA
│   ├── src/
│   ├── Dockerfile
│   └── package.json
├── docker-compose.yml
└── .env.example
```

---

## Docker Compose Setup

This approach builds and runs the entire stack — backend, frontend, and all infrastructure — in containers.

### 1. Configure environment

```bash
cp .env.example .env
```

For production-like deployments, update `JWT_SECRET` with a strong random value:

```bash
openssl rand -base64 64
```

### 2. Build and start all services

```bash
docker compose up --build
```

This builds the backend and frontend Docker images and starts all services. On first run, Docker pulls base images and compiles the project, which may take a few minutes.

### 3. Verify services are running

| Service | URL | Notes |
|---------|-----|-------|
| Frontend | http://localhost | React SPA served by nginx |
| Backend API | http://localhost:8080 | Spring Boot REST API |
| Backend health | http://localhost:8080/actuator/health | Health check endpoint |
| MinIO Console | http://localhost:9001 | Object storage admin UI |
| PostgreSQL | localhost:5432 | Database (internal) |
| Redis | localhost:6379 | Cache / pub-sub (internal) |

### 4. Stop all services

```bash
docker compose down
```

To also remove persistent volumes (wipes all data):

```bash
docker compose down -v
```

---

## First-Push Walkthrough

This walkthrough assumes the platform is running (either via Docker Compose or local dev setup) and you have registered an account.

### 1. Register and create a repository

Open `http://localhost` (Docker Compose) or `http://localhost:3000` (local dev) in your browser, register a new account (e.g., username `alice`), and create a new repository named `myrepo`.

### 2. Clone the repository

```bash
git clone http://localhost/api/git/alice/myrepo
cd myrepo
```

> For local dev, the backend is on port 8080, so use `http://localhost:8080/api/git/alice/myrepo`.

### 3. Make your first commit and push

```bash
echo "hello" > README.md
git add . && git commit -m "init" && git push
```

Git will prompt for your DVCS username and password on the first push. You can also use a personal access token instead of your password — generate one from your account settings.

### 4. Verify

Refresh the repository page in the browser. Your `README.md` should appear in the file tree.

---

## Environment Variable Reference

Copy `.env.example` to `.env` and adjust values as needed. All variables have sensible defaults for local development.

### PostgreSQL

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_HOST` | Hostname of the PostgreSQL server | `localhost` |
| `POSTGRES_PORT` | Port the PostgreSQL server listens on | `5432` |
| `POSTGRES_DB` | Database name | `dvcs` |
| `POSTGRES_USER` | Database username | `dvcs` |
| `POSTGRES_PASSWORD` | Database password | `dvcs` |

### Redis

| Variable | Description | Default |
|----------|-------------|---------|
| `REDIS_HOST` | Hostname of the Redis server | `localhost` |
| `REDIS_PORT` | Port the Redis server listens on | `6379` |

### MinIO / S3 Object Storage

| Variable | Description | Default |
|----------|-------------|---------|
| `MINIO_ROOT_USER` | MinIO root (admin) username | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | MinIO root (admin) password | `minioadmin` |
| `S3_ENDPOINT` | S3-compatible endpoint URL | `http://localhost:9000` |
| `S3_BUCKET` | Bucket name for Git objects | `dvcs-objects` |

### Storage Backend

| Variable | Description | Default |
|----------|-------------|---------|
| `STORAGE_BACKEND` | Storage driver to use: `local` (filesystem) or `s3` (MinIO/S3) | `s3` |
| `STORAGE_ROOT` | Filesystem path for local storage (used when `STORAGE_BACKEND=local`) | `/data/objects` |

### JWT Authentication

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Secret key used to sign JWT access tokens. **Change this in production.** Generate with `openssl rand -base64 64` | `changeme-in-production-use-a-long-random-string` |
| `JWT_EXPIRY_SECONDS` | Access token lifetime in seconds | `900` (15 minutes) |
| `REFRESH_TOKEN_EXPIRY_DAYS` | Refresh token lifetime in days | `30` |

### CORS

| Variable | Description | Default |
|----------|-------------|---------|
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of origins allowed to make cross-origin requests to the API | `http://localhost:3000` |
