# Design Document — DVCS Platform

## Overview

This document describes the technical design for a production-grade, web-based Distributed Version Control System (DVCS) inspired by Git and GitHub. The system is a modular monolith structured for future microservice extraction, built on Java 21 / Spring Boot 3.x (backend), React/TypeScript/Vite (frontend), PostgreSQL (metadata), Redis (cache + pub/sub), and a content-addressable object store backed by local filesystem or MinIO.

---

## System Architecture

### High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser (React SPA)                       │
│  Vite + TypeScript + TailwindCSS + React Router v6              │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS / WSS
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        nginx (reverse proxy)                     │
│  /api/*  → backend:8080   /ws/* → backend:8080   /* → SPA       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Boot 3.x Application (port 8080)            │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │   Auth   │ │  Repo    │ │   Git    │ │   Diff / Merge   │  │
│  │ Module   │ │ Module   │ │Transport │ │    Engine        │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │    PR    │ │  Issues  │ │Webhooks  │ │    Pipeline      │  │
│  │ Module   │ │ Module   │ │ Module   │ │    Engine        │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────────────┐   │
│  │Notif.    │ │ Search   │ │   Cross-cutting: Security,   │   │
│  │ Module   │ │ Module   │ │   Rate Limiting, Caching     │   │
│  └──────────┘ └──────────┘ └──────────────────────────────┘   │
└──────┬──────────────┬──────────────────┬────────────────────────┘
       │              │                  │
       ▼              ▼                  ▼
┌────────────┐ ┌────────────┐  ┌─────────────────────┐
│ PostgreSQL │ │   Redis    │  │  Object Store       │
│ (metadata) │ │(cache/pub) │  │  (local FS / MinIO) │
└────────────┘ └────────────┘  └─────────────────────┘
```

### Deployment Topology (Docker Compose)

```
docker-compose.yml
├── frontend   (nginx:alpine, port 80/443)
├── backend    (distroless JVM 21, port 8080)
├── postgres   (postgres:16, port 5432)
├── redis      (redis:7-alpine, port 6379)
└── minio      (minio/minio, ports 9000/9001)
```

---

## Backend Package Structure

```
com.dvcs
├── auth/
│   ├── controller/   AuthController, OAuthController
│   ├── service/      AuthService, TokenService, OAuthService
│   ├── domain/       User, SshKey, PersonalToken
│   └── repository/   UserRepository, SshKeyRepository, TokenRepository
├── repository/
│   ├── controller/   RepoController
│   ├── service/      RepoService, ForkService, StatsService
│   ├── domain/       Repository, Collaborator
│   └── repository/   RepoRepository, CollaboratorRepository
├── git/
│   ├── object/       GitObject, Blob, Tree, Commit, Ref (core engine)
│   ├── storage/      ObjectStore, LocalFsBackend, S3Backend
│   ├── transport/    GitTransportController, UploadPackService, ReceivePackService
│   ├── pack/         PackFileEncoder, PackFileDecoder, DeltaCompressor
│   └── ref/          RefService, BranchService, TagService
├── diff/
│   ├── service/      DiffService, MergeService
│   ├── algorithm/    MyersDiff, PatchApplier, ThreeWayMerge
│   └── model/        DiffHunk, DiffLine, ConflictMarker
├── pullrequest/
│   ├── controller/   PullRequestController
│   ├── service/      PullRequestService, ReviewService, MergeStrategyService
│   └── domain/       PullRequest, PrReview, PrComment
├── issue/
│   ├── controller/   IssueController
│   ├── service/      IssueService, LabelService
│   └── domain/       Issue, IssueComment, Label
├── webhook/
│   ├── controller/   WebhookController
│   ├── service/      WebhookService, WebhookDeliveryService
│   └── domain/       Webhook, WebhookDelivery
├── pipeline/
│   ├── controller/   PipelineController
│   ├── service/      PipelineEngine, StageExecutor
│   └── domain/       PipelineRun, PipelineStage
├── notification/
│   ├── controller/   NotificationController, NotificationWebSocketHandler
│   ├── service/      NotificationService, NotificationFanout
│   └── domain/       Notification
├── search/
│   ├── controller/   SearchController
│   └── service/      SearchService
└── common/
    ├── security/     JwtFilter, RateLimitFilter, RepoAccessGuard
    ├── cache/        CacheService, CacheKeys
    ├── error/        GlobalExceptionHandler, ErrorEnvelope
    └── config/       SecurityConfig, RedisConfig, WebSocketConfig, OpenApiConfig
```

---

## Database Schema

### Entity-Relationship Overview

```
users ──< repositories ──< collaborators >── users
      ──< ssh_keys
      ──< personal_tokens
      ──< notifications
      ──< audit_logs

repositories ──< branches
             ──< commits_meta
             ──< git_objects
             ──< pull_requests ──< pr_reviews
                               ──< pr_comments
             ──< issues ──< issue_comments
                        ──< labels (via issue_labels join)
             ──< labels
             ──< webhooks
             ──< pipeline_runs
```

### Full DDL (Flyway V1__init.sql)

```sql
-- users
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(512),
    bio           TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);

-- repositories
CREATE TABLE repositories (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(128) NOT NULL,
    description    TEXT,
    is_private     BOOLEAN      NOT NULL DEFAULT FALSE,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    fork_of        BIGINT       REFERENCES repositories(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(owner_id, name)
);
CREATE INDEX idx_repos_owner    ON repositories(owner_id);
CREATE INDEX idx_repos_name     ON repositories(name);

-- collaborators
CREATE TABLE collaborators (
    repo_id BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(16) NOT NULL CHECK (role IN ('OWNER','WRITE','READ')),
    PRIMARY KEY (repo_id, user_id)
);
CREATE INDEX idx_collabs_user ON collaborators(user_id);

-- git_objects
CREATE TABLE git_objects (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    sha         CHAR(64)    NOT NULL,
    type        VARCHAR(8)  NOT NULL CHECK (type IN ('BLOB','TREE','COMMIT')),
    size        BIGINT      NOT NULL,
    stored_path VARCHAR(512) NOT NULL,
    UNIQUE(repo_id, sha)
);
CREATE INDEX idx_gitobj_repo_sha ON git_objects(repo_id, sha);

-- branches
CREATE TABLE branches (
    id         BIGSERIAL PRIMARY KEY,
    repo_id    BIGINT       NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    head_sha   CHAR(64)     NOT NULL,
    protected  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(repo_id, name)
);
CREATE INDEX idx_branches_repo ON branches(repo_id);

-- commits_meta
CREATE TABLE commits_meta (
    id           BIGSERIAL PRIMARY KEY,
    repo_id      BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    sha          CHAR(64)    NOT NULL,
    author_id    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    message      TEXT        NOT NULL,
    authored_at  TIMESTAMPTZ NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(repo_id, sha)
);
CREATE INDEX idx_commits_repo_sha  ON commits_meta(repo_id, sha);
CREATE INDEX idx_commits_authored  ON commits_meta(repo_id, authored_at DESC);

-- pull_requests
CREATE TABLE pull_requests (
    id           BIGSERIAL PRIMARY KEY,
    repo_id      BIGINT       NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    number       INT          NOT NULL,
    title        VARCHAR(512) NOT NULL,
    body         TEXT,
    head_branch  VARCHAR(255) NOT NULL,
    base_branch  VARCHAR(255) NOT NULL,
    author_id    BIGINT       NOT NULL REFERENCES users(id),
    status       VARCHAR(16)  NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed','merged')),
    merged_at    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(repo_id, number)
);
CREATE INDEX idx_pr_repo_status ON pull_requests(repo_id, status);

-- pr_reviews
CREATE TABLE pr_reviews (
    id           BIGSERIAL PRIMARY KEY,
    pr_id        BIGINT      NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    reviewer_id  BIGINT      NOT NULL REFERENCES users(id),
    verdict      VARCHAR(24) NOT NULL CHECK (verdict IN ('APPROVE','CHANGES_REQUESTED','COMMENT')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prreviews_pr ON pr_reviews(pr_id);

-- pr_comments
CREATE TABLE pr_comments (
    id          BIGSERIAL PRIMARY KEY,
    pr_id       BIGINT       NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    review_id   BIGINT       REFERENCES pr_reviews(id) ON DELETE SET NULL,
    file_path   VARCHAR(512),
    line_number INT,
    body        TEXT         NOT NULL,
    author_id   BIGINT       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prcomments_pr ON pr_comments(pr_id);

-- issues
CREATE TABLE issues (
    id         BIGSERIAL PRIMARY KEY,
    repo_id    BIGINT       NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    number     INT          NOT NULL,
    title      VARCHAR(512) NOT NULL,
    body       TEXT,
    author_id  BIGINT       NOT NULL REFERENCES users(id),
    status     VARCHAR(16)  NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(repo_id, number)
);
CREATE INDEX idx_issues_repo_status ON issues(repo_id, status);

-- issue_comments
CREATE TABLE issue_comments (
    id         BIGSERIAL PRIMARY KEY,
    issue_id   BIGINT      NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    author_id  BIGINT      NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_issuecomments_issue ON issue_comments(issue_id);

-- labels
CREATE TABLE labels (
    id      BIGSERIAL PRIMARY KEY,
    repo_id BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    name    VARCHAR(64) NOT NULL,
    color   CHAR(7)     NOT NULL,
    UNIQUE(repo_id, name)
);

-- issue_labels (join table)
CREATE TABLE issue_labels (
    issue_id BIGINT NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, label_id)
);

-- webhooks
CREATE TABLE webhooks (
    id         BIGSERIAL PRIMARY KEY,
    repo_id    BIGINT   NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    url        TEXT     NOT NULL,
    secret     TEXT     NOT NULL,
    events     TEXT[]   NOT NULL,
    active     BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhooks_repo ON webhooks(repo_id);

-- notifications
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_type VARCHAR(32) NOT NULL,
    subject_id   BIGINT      NOT NULL,
    reason       VARCHAR(64) NOT NULL,
    read         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_user_read ON notifications(user_id, read);

-- ssh_keys
CREATE TABLE ssh_keys (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(128) NOT NULL,
    public_key  TEXT         NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, fingerprint)
);

-- personal_tokens
CREATE TABLE personal_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    scopes     TEXT[]       NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tokens_user ON personal_tokens(user_id);

-- audit_logs
CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    actor_id      BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    action        VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   BIGINT,
    ip            INET,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_time  ON audit_logs(created_at DESC);

-- pipeline_runs
CREATE TABLE pipeline_runs (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    commit_sha  CHAR(64)    NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILURE')),
    stages_json JSONB,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pipeline_repo       ON pipeline_runs(repo_id);
CREATE INDEX idx_pipeline_commit_sha ON pipeline_runs(repo_id, commit_sha);
```

---

## Git Object Storage Engine

### Object Model

All four object types are serialized to a canonical byte format, SHA-256 hashed, and stored at `objects/{sha[0..1]}/{sha[2..]}` relative to the repository root.

```
┌─────────────────────────────────────────────────────┐
│  GitObject (abstract)                               │
│  + sha: String          (SHA-256 hex, 64 chars)     │
│  + type: ObjectType     (BLOB | TREE | COMMIT)      │
│  + serialize(): byte[]  (canonical byte encoding)   │
└─────────────────────────────────────────────────────┘
         ▲              ▲              ▲
    ┌────┴───┐    ┌─────┴──┐    ┌─────┴──────┐
    │  Blob  │    │  Tree  │    │   Commit   │
    │ bytes[]│    │entries │    │ treeSha    │
    │        │    │ name   │    │ parentShas │
    │        │    │ mode   │    │ author     │
    │        │    │ sha    │    │ committer  │
    └────────┘    └────────┘    │ message    │
                                └────────────┘
```

### Serialization Format

**Blob:** `blob {size}\0{raw bytes}`

**Tree entry (repeated per entry):** `{mode} {name}\0{20-byte binary sha}`
Full tree: `tree {total-size}\0{entries...}`

**Commit:**
```
commit {size}\0
tree {tree-sha}\n
[parent {parent-sha}\n]...
author {name} <{email}> {unix-ts} {tz}\n
committer {name} <{email}> {unix-ts} {tz}\n
\n
{message}
```

### Storage Backend Interface

```java
public interface ObjectStoreBackend {
    void   write(String repoId, String sha, byte[] data) throws IOException;
    byte[] read(String repoId, String sha)               throws IOException;
    boolean exists(String repoId, String sha);
    void   delete(String repoId, String sha)             throws IOException;
    long   size(String repoId, String sha)               throws IOException;
}
```

Two implementations:
- `LocalFsBackend` — writes to `${storage.root}/{repoId}/objects/{prefix}/{rest}`
- `S3Backend` — uses AWS SDK v2 with MinIO endpoint; key = `{repoId}/objects/{prefix}/{rest}`

### Pack-File Transfer Format

For clone/fetch, objects are bundled into a pack stream:

```
Pack header:  PACK + version(4 bytes) + object-count(4 bytes)
Per object:   type+size (variable-length encoding) + compressed-data (zlib)
Pack trailer: SHA-256 of all preceding bytes (32 bytes)
```

`PackFileEncoder` builds the pack from a list of object SHAs.
`PackFileDecoder` parses the incoming stream during `git-receive-pack`.

---

## HTTP Smart Git Transport

### Protocol Flow — Clone (upload-pack)

```
Client                                    Server
  │                                          │
  │── GET /info/refs?service=git-upload-pack ──▶│
  │◀── 200 + ref advertisement ──────────────│
  │                                          │
  │── POST /git-upload-pack ─────────────────▶│
  │   (want {sha}, have {sha}...)             │
  │◀── 200 + pack stream ────────────────────│
```

### Protocol Flow — Push (receive-pack)

```
Client                                    Server
  │                                          │
  │── GET /info/refs?service=git-receive-pack ─▶│
  │◀── 200 + ref advertisement ──────────────│
  │                                          │
  │── POST /git-receive-pack ────────────────▶│
  │   (old-sha new-sha refname + pack data)   │
  │◀── 200 + status report ──────────────────│
```

### ReceivePackService — Push Processing Steps

1. Parse pkt-line ref updates from request body
2. Authenticate caller (JWT or PAT); verify WRITE/OWNER role
3. Check target branch is not protected
4. Decode pack stream via `PackFileDecoder`; write objects to `ObjectStoreBackend`
5. Update `branches.head_sha` in PostgreSQL
6. Insert rows into `commits_meta` for each new commit
7. Invalidate Redis cache keys: `repo:{id}:commits:*`, `repo:{id}:branches`
8. Publish push event to Redis pub/sub channel `events:{repoId}`
9. Trigger webhook delivery (async) for `push` event
10. Trigger `PipelineEngine` (async) for new commit SHA

---

## Diff Engine Design

### Myers Diff Algorithm

The `MyersDiff` class implements the Myers O(ND) shortest-edit-script algorithm:

```
Input:  sequence A (lines of base file)
        sequence B (lines of head file)
Output: List<DiffHunk>

DiffHunk {
    type:    ADD | REMOVE | CONTEXT
    baseStart, baseEnd   // line range in A
    headStart, headEnd   // line range in B
    lines: List<DiffLine>
}

DiffLine {
    type:    ADD | REMOVE | CONTEXT
    content: String
    baseLineNo: int
    headLineNo: int
}
```

Algorithm steps:
1. Split both files into line arrays
2. Run Myers forward search to find edit script (sequence of insert/delete ops)
3. Group consecutive edits into hunks with ±3 context lines
4. Return unified diff representation

### Binary Detection

A file is classified as binary if:
- Any of the first 8,000 bytes is a null byte (`\0`), OR
- The byte sequence matches known binary magic bytes (PNG, PDF, ZIP, etc.)

Binary diff response: `{ binary: true, baseSizeBytes: N, headSizeBytes: M }`

### Three-Way Merge

```
        BASE
       /    \
    OURS   THEIRS
```

Algorithm:
1. Compute `diff(BASE, OURS)` → edit script E1
2. Compute `diff(BASE, THEIRS)` → edit script E2
3. For each hunk in E1 and E2:
   - If ranges don't overlap → apply both (auto-merge)
   - If ranges overlap and content differs → emit conflict markers
4. Return merged text with `<<<<<<< OURS`, `=======`, `>>>>>>> THEIRS` markers

---

## Authentication & Security Design

### JWT Token Flow

```
┌──────────┐   POST /api/auth/login   ┌──────────────┐
│  Client  │ ─────────────────────── ▶│  AuthService │
│          │◀── accessToken (15m) ────│              │
│          │◀── refreshToken cookie ──│              │
└──────────┘   (HttpOnly, Secure)     └──────────────┘

Subsequent requests:
Authorization: Bearer {accessToken}

Token refresh:
POST /api/auth/refresh  (sends refreshToken cookie)
→ new accessToken + rotated refreshToken cookie
```

### JWT Claims Structure

```json
{
  "sub": "userId",
  "username": "alice",
  "roles": ["USER"],
  "iat": 1700000000,
  "exp": 1700000900
}
```

### Spring Security Filter Chain

```
Request
  │
  ▼
RateLimitFilter          (Bucket4j — per IP or per user)
  │
  ▼
JwtAuthenticationFilter  (extract + validate Bearer token)
  │
  ▼
PersonalTokenFilter      (fallback: validate PAT from Authorization header)
  │
  ▼
RepoAccessGuard          (SpEL @PreAuthorize on controller methods)
  │
  ▼
Controller
```

### Rate Limiting Strategy

| Endpoint type         | Limit              | Key        |
|-----------------------|--------------------|------------|
| Unauthenticated reads | 60 req/min         | IP address |
| Authenticated API     | 5,000 req/hour     | user ID    |
| Git push              | 100 req/hour       | user ID    |
| Auth endpoints        | 10 req/min         | IP address |

Counters stored in Redis with TTL matching the window.

---

## Caching Design (Redis)

### Key Schema

```
repo:{repoId}:meta                    → JSON, TTL 60s
repo:{repoId}:branches                → JSON list, TTL 60s, invalidated on push
repo:{repoId}:commits:{branch}:{page} → JSON list, TTL 30s, invalidated on push
blob:{repoId}:{sha}                   → raw bytes, TTL 3600s (immutable)
user:{userId}:session                 → session data, TTL 30d
ratelimit:{type}:{key}                → counter, TTL = window size
```

### Cache Invalidation on Push

When `ReceivePackService` accepts a push to branch `B` in repo `R`:
```
DEL repo:{R}:branches
DEL repo:{R}:commits:{B}:*   (pattern delete via SCAN)
```

### Pub/Sub for Notifications

```
Publisher (any service)
  PUBLISH events:{userId}  {notificationJson}

Subscriber (NotificationFanout — one per server instance)
  SUBSCRIBE events:*
  → on message: look up active WebSocket sessions for userId
  → send STOMP frame to each session
```

---

## WebSocket Notifications Design

### STOMP Topology

```
Client connects: ws://host/ws/notifications
  CONNECT frame with Authorization header

Server:
  /topic/notifications/{userId}  → user-specific topic
  /app/notifications/read        → mark-as-read endpoint

Flow:
  1. Event occurs (PR review, issue comment, etc.)
  2. NotificationService creates DB record
  3. NotificationService publishes to Redis channel events:{userId}
  4. NotificationFanout (subscribed to Redis) receives message
  5. SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload)
  6. Browser receives STOMP MESSAGE frame
  7. NotificationBell component increments badge count
```

---

## Pull Request Merge Strategies

### Merge Commit
```
Before:  main: A─B─C
         feature: A─B─D─E

After:   main: A─B─C─M   (M has parents C and E)
```
Creates a new commit M with two parents. Preserves full history.

### Squash Merge
```
Before:  main: A─B─C
         feature: A─B─D─E

After:   main: A─B─C─S   (S contains all changes from D+E, single parent C)
```
Creates one new commit S. Feature branch history is not preserved in main.

### Rebase Merge
```
Before:  main: A─B─C
         feature: A─B─D─E

After:   main: A─B─C─D'─E'  (D', E' are replayed commits, linear history)
```
Replays each feature commit on top of base. No merge commit.

### Merge Execution Steps (all strategies)

1. Resolve head and base branch tip SHAs
2. Find merge base (lowest common ancestor via BFS on commit graph)
3. Run `ThreeWayMerge(base, ours=base-tip, theirs=head-tip)`
4. If conflicts → return HTTP 422 with conflict details
5. Write new Tree and Commit objects to ObjectStore
6. Update `branches.head_sha` for base branch
7. Mark PR status = `merged`, set `merged_at`
8. Trigger `pull_request` webhook event
9. Notify PR author and reviewers

---

## CI/CD Pipeline Engine Design

### Pipeline State Machine

```
PENDING → RUNNING → SUCCESS
                 → FAILURE
```

### Stage Execution (Simulated)

```java
// PipelineEngine triggered async on push event
PipelineRun run = createRun(repoId, commitSha);  // status=PENDING

// Stage 1: build
updateStage(run, "build", RUNNING);
Thread.sleep(randomBetween(1000, 3000));  // simulate build time
updateStage(run, "build", SUCCESS);       // or FAILURE (10% chance)

// Stage 2: test (only if build succeeded)
updateStage(run, "test", RUNNING);
Thread.sleep(randomBetween(2000, 5000));
updateStage(run, "test", SUCCESS);        // or FAILURE (15% chance)

run.setStatus(overallStatus);
run.setFinishedAt(now());
notifyOpenPRs(run);
```

### stages_json Structure

```json
{
  "stages": [
    { "name": "build", "status": "SUCCESS", "startedAt": "...", "finishedAt": "...", "log": "..." },
    { "name": "test",  "status": "SUCCESS", "startedAt": "...", "finishedAt": "...", "log": "..." }
  ]
}
```

---

## Frontend Architecture

### Route Map

```
/                          → LandingPage
/login                     → LoginPage
/register                  → RegisterPage
/explore                   → ExplorePage
/settings                  → UserSettingsPage
/:owner                    → UserProfilePage
/:owner/:repo              → RepoHomePage
/:owner/:repo/tree/:ref/*  → FileTreePage
/:owner/:repo/blob/:ref/*  → FileBlobPage
/:owner/:repo/commits/:ref → CommitListPage
/:owner/:repo/commit/:sha  → CommitDetailPage
/:owner/:repo/branches     → BranchListPage
/:owner/:repo/pulls        → PullRequestListPage
/:owner/:repo/pulls/:id    → PullRequestDetailPage
/:owner/:repo/issues       → IssueListPage
/:owner/:repo/issues/:id   → IssueDetailPage
/:owner/:repo/settings     → RepoSettingsPage
/:owner/:repo/pipelines    → PipelineListPage
```

### State Management

- **Auth state**: React Context (`AuthContext`) — stores JWT, user info, expiry
- **Server state**: React Query (`@tanstack/react-query`) — all API calls, caching, invalidation
- **WebSocket**: custom `useNotifications` hook wrapping `@stomp/stompjs`
- **Local UI state**: `useState` / `useReducer` per component

### Key Component Designs

#### CommitGraph (SVG DAG)

```
Data: List<CommitNode> where CommitNode = { sha, parents[], branch, x, y }

Layout algorithm:
1. Topological sort commits (newest first)
2. Assign each branch a lane (x-coordinate)
3. Assign y-coordinate by commit order
4. Render:
   - <circle> per commit node
   - <line> or <path> (cubic bezier) per parent edge
   - Branch label <text> at lane head
   - Color per lane (deterministic from branch name hash)
```

#### DiffViewer

```
Props: { hunks: DiffHunk[], mode: 'unified' | 'split' }

Unified mode:
  - Single column
  - Green background for ADD lines (+)
  - Red background for REMOVE lines (-)
  - Gray for CONTEXT lines

Split mode:
  - Two columns (base | head)
  - Synchronized scrolling
  - Empty rows on the side that has no change

Line comments:
  - Click line number → inline CommentForm appears
  - Submitted comments appear threaded below the line
```

#### BranchSelector

```
Props: { repoOwner, repoName, currentRef, onChange }

Behavior:
  - Fetches branches + tags on mount
  - Fuzzy filters on keystroke (client-side, fuse.js)
  - Groups: Branches / Tags
  - Keyboard navigable (↑↓ Enter Escape)
```

### API Client Layer

All API calls go through a typed `apiClient` built on `fetch`:

```typescript
// src/api/client.ts
const apiClient = {
  get:    <T>(path: string) => authenticatedFetch<T>('GET', path),
  post:   <T>(path: string, body: unknown) => authenticatedFetch<T>('POST', path, body),
  patch:  <T>(path: string, body: unknown) => authenticatedFetch<T>('PATCH', path, body),
  delete: <T>(path: string) => authenticatedFetch<T>('DELETE', path),
};
```

JWT is read from `AuthContext` and injected as `Authorization: Bearer {token}`. On 401, the client attempts one silent refresh before redirecting to `/login`.

---

## Error Envelope

All 4xx and 5xx responses return:

```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Repository 'alice/myrepo' does not exist or is not accessible.",
  "details": {},
  "timestamp": "2026-05-06T12:00:00Z"
}
```

Implemented via `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping:
- `EntityNotFoundException` → 404
- `AccessDeniedException` → 403
- `ConflictException` → 409
- `MethodArgumentNotValidException` → 400 (with field-level `details`)
- `RateLimitExceededException` → 429
- `Exception` (catch-all) → 500

---

## Infrastructure Configuration

### docker-compose.yml Structure

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment: POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
    volumes: postgres_data:/var/lib/postgresql/data
    healthcheck: pg_isready

  redis:
    image: redis:7-alpine
    volumes: redis_data:/data
    healthcheck: redis-cli ping

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment: MINIO_ROOT_USER, MINIO_ROOT_PASSWORD
    volumes: minio_data:/data
    healthcheck: curl -f http://localhost:9000/minio/health/live

  backend:
    build: ./backend
    depends_on: [postgres, redis, minio]
    environment: (all Spring env vars from .env)
    ports: "8080:8080"

  frontend:
    build: ./frontend
    depends_on: [backend]
    ports: "80:80"
```

### Backend Dockerfile (multi-stage)

```dockerfile
# Stage 1: build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

# Stage 2: runtime (distroless)
FROM gcr.io/distroless/java21-debian12
COPY --from=builder /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Frontend Dockerfile

```dockerfile
# Stage 1: build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: serve
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### nginx.conf Key Directives

```nginx
server {
  listen 80;

  # API proxy
  location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }

  # WebSocket proxy
  location /ws/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
  }

  # SPA static assets
  location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;
    gzip on;
    gzip_types text/plain text/css application/javascript application/json;
    location ~* \.(js|css|png|jpg|svg|woff2)$ {
      expires 1y;
      add_header Cache-Control "public, immutable";
    }
  }
}
```

---

## Testing Strategy

### Unit Tests (JUnit 5 + Mockito)

| Class | Key test cases |
|---|---|
| `MyersDiff` | Empty files, identical files, single-line change, multi-hunk, round-trip patch property |
| `ThreeWayMerge` | No conflict, conflict on same line, conflict on adjacent lines |
| `ObjectStoreService` | Write+read round-trip, SHA mismatch detection, path traversal rejection |
| `AuthService` | Register success, duplicate username, login success, login failure, token refresh rotation |
| `PackFileEncoder/Decoder` | Encode N objects, decode same N objects, SHA trailer validation |

### Integration Tests (Spring Boot Test + Testcontainers)

Each module has an `@SpringBootTest` test class with a real PostgreSQL container (via Testcontainers) and a real Redis container:

- `AuthControllerIT` — register, login, refresh, duplicate user
- `RepoControllerIT` — create, get, fork, delete, access control
- `GitTransportIT` — info/refs, upload-pack (clone), receive-pack (push)
- `BranchControllerIT` — create, list, protect, delete
- `PullRequestControllerIT` — open, list, review, merge (all 3 strategies)
- `IssueControllerIT` — CRUD, comments, labels
- `DiffControllerIT` — text diff, binary diff, 3-way merge
- `WebhookControllerIT` — create, test delivery, HMAC verification
- `PipelineControllerIT` — trigger on push, list, detail
- `NotificationControllerIT` — create, list, mark-read

### Frontend Tests (Vitest + React Testing Library)

- `DiffViewer.test.tsx` — renders unified diff, renders split diff, toggles mode, renders binary diff message
- `CommitGraph.test.tsx` — renders nodes, renders edges, renders merge commit with two parent edges
- `BranchSelector.test.tsx` — fuzzy filter, keyboard navigation
- `AuthContext.test.tsx` — login stores token, logout clears token, refresh on 401

### Load Tests (k6)

```javascript
// scenarios:
// 1. clone: GET info/refs + POST git-upload-pack (50 VUs, 5 min)
// 2. push:  GET info/refs + POST git-receive-pack (20 VUs, 5 min)
// 3. pr_merge: POST /pulls + POST /pulls/{id}/merge (10 VUs, 5 min)

// thresholds:
// http_req_duration p(95) < 2000ms
// http_req_failed < 1%
```

---

## API Documentation

- Springdoc OpenAPI 3 auto-generates spec from controller annotations
- Available at `GET /api/docs` (JSON) and `GET /api/swagger-ui` (UI)
- All DTOs annotated with `@Schema(description = "...")` and `@Schema(example = "...")`
- Security scheme: `BearerAuth` (JWT) declared globally; applied to all secured endpoints

---

## Implementation Sequence

| Step | Deliverable |
|------|-------------|
| 1 | Flyway migrations (all tables above) |
| 2 | Auth module: register, login, JWT, refresh, PAT, SSH key |
| 3 | Git object engine: Blob/Tree/Commit serialization, ObjectStore, LocalFsBackend |
| 4 | HTTP Git transport: info/refs, upload-pack, receive-pack, PackFile codec |
| 5 | Repository, Branch, Commit REST APIs + Redis caching |
| 6 | Diff engine: MyersDiff, binary detection, ThreeWayMerge |
| 7 | Pull Request module: open, review, merge (3 strategies) |
| 8 | Issues module: CRUD, comments, labels |
| 9 | Webhooks + Pipeline Engine |
| 10 | React frontend: all routes + key components |
| 11 | WebSocket notifications (STOMP + Redis pub/sub) |
| 12 | Docker Compose + Dockerfiles + nginx.conf + .env.example |
| 13 | Unit + integration + frontend + load tests |
| 14 | OpenAPI annotations + README |
