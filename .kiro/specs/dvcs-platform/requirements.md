# Requirements Document

## Introduction

This document defines the requirements for a production-grade, web-based Distributed Version Control System (DVCS) inspired by Git and GitHub. The platform provides repository hosting, version tracking, team collaboration, and basic DevOps workflows. It is implemented end-to-end with a React/TypeScript frontend, a Java 21 Spring Boot backend, PostgreSQL for metadata, Redis for caching and pub/sub, and a content-addressable object store for Git objects. The system is packaged for deployment via Docker Compose with optional Kubernetes manifests.

---

## Glossary

- **System**: The DVCS platform as a whole.
- **Platform**: Synonym for System.
- **User**: An authenticated human actor interacting with the Platform.
- **Owner**: The User who created a Repository and holds the OWNER collaborator role.
- **Collaborator**: A User with an assigned role (OWNER, WRITE, or READ) on a Repository.
- **Repository**: A named, versioned collection of files and their history, owned by a User.
- **Git_Engine**: The subsystem responsible for content-addressable object storage, hashing, and Git protocol transport.
- **Object_Store**: The content-addressable storage layer that persists BLOB, TREE, and COMMIT objects keyed by SHA-256 hash.
- **Blob**: A Git object representing raw file content, keyed by its SHA-256 hash.
- **Tree**: A Git object representing a directory snapshot, mapping filenames to Blob or Tree hashes.
- **Commit**: A Git object pointing to a root Tree, zero or more parent Commit hashes, and author/committer metadata.
- **Ref**: A named pointer (branch or tag) resolving to a Commit hash.
- **Branch**: A mutable Ref tracking the tip of a line of development.
- **Tag**: An immutable Ref pointing to a specific Commit.
- **Auth_Service**: The subsystem handling user registration, login, JWT issuance, and OAuth2.
- **Repo_Service**: The subsystem managing Repository lifecycle (create, fork, delete, stats).
- **Transport_Service**: The subsystem implementing the HTTP Smart Git protocol (upload-pack / receive-pack).
- **Diff_Service**: The subsystem computing line-by-line unified diffs, binary detection, and 3-way merge conflict detection.
- **PR_Service**: The subsystem managing Pull Requests, reviews, inline comments, and merge operations.
- **Issue_Service**: The subsystem managing Issues, issue comments, and labels.
- **Webhook_Service**: The subsystem managing webhook registrations, deliveries, and HMAC signing.
- **Pipeline_Engine**: The lightweight CI/CD simulation subsystem triggered by push events.
- **Notification_Service**: The subsystem managing in-app notifications and real-time WebSocket delivery.
- **Search_Service**: The subsystem providing full-text search across repositories, code, and users.
- **Cache**: The Redis-backed caching layer.
- **JWT**: JSON Web Token used for stateless authentication.
- **Pack_File**: A binary bundle format for efficient batch transfer of Git objects over the network.
- **Myers_Diff**: The Myers diff algorithm used for byte-level delta computation.
- **HMAC**: Hash-based Message Authentication Code used to sign webhook payloads.
- **STOMP**: Simple Text Oriented Messaging Protocol used over WebSocket for real-time notifications.
- **OpenAPI**: The OpenAPI 3 specification standard used for API documentation.
- **Flyway**: The database migration tool used to manage PostgreSQL schema versions.
- **Bucket4j**: The rate-limiting library used for per-user and per-IP request throttling.
- **Testcontainers**: A Java library providing throwaway Docker containers for integration tests.
- **Pipeline_Run**: A record of a CI/CD pipeline execution, including status and stage results.

---

## Requirements

### Requirement 1: User Registration and Authentication

**User Story:** As a visitor, I want to register an account and log in securely, so that I can access and manage repositories on the Platform.

#### Acceptance Criteria

1. WHEN a registration request is received with a unique username, valid email, and password, THE Auth_Service SHALL create a new User record with the password stored as a bcrypt hash and return HTTP 201.
2. IF a registration request contains a username or email that already exists, THEN THE Auth_Service SHALL return HTTP 409 with an error envelope containing a descriptive message.
3. WHEN a login request is received with valid credentials, THE Auth_Service SHALL return a short-lived JWT access token (15-minute expiry) and a long-lived refresh token (30-day expiry) stored in an HttpOnly cookie.
4. IF a login request contains invalid credentials, THEN THE Auth_Service SHALL return HTTP 401 with an error envelope and SHALL NOT reveal whether the username or email was the incorrect field.
5. WHEN a token refresh request is received with a valid refresh token, THE Auth_Service SHALL rotate the refresh token and return a new access token and a new refresh token.
6. IF a token refresh request contains an expired or revoked refresh token, THEN THE Auth_Service SHALL return HTTP 401 and invalidate the token.
7. WHERE OAuth2 GitHub integration is enabled, THE Auth_Service SHALL redirect the User through the GitHub OAuth2 flow and create or link a User account on successful callback.
8. THE Auth_Service SHALL never log JWT secrets, password hashes, or raw token values.

---

### Requirement 2: SSH Key and Personal Access Token Management

**User Story:** As a developer, I want to register SSH public keys and create personal access tokens, so that I can authenticate Git operations from the command line without using my password.

#### Acceptance Criteria

1. WHEN a User submits a valid SSH public key with a title, THE System SHALL store the key with its computed fingerprint and return HTTP 201.
2. IF a submitted SSH public key is malformed or duplicates an existing key for that User, THEN THE System SHALL return HTTP 422 with a descriptive error envelope.
3. WHEN a User creates a personal access token with a name, scopes, and optional expiry, THE System SHALL store a hash of the token, return the raw token value exactly once, and never expose it again.
4. WHEN a Git transport request is authenticated via a valid personal access token, THE Transport_Service SHALL enforce the token's declared scopes against the requested operation.
5. IF a personal access token has passed its expiry date, THEN THE System SHALL reject the request with HTTP 401.

---

### Requirement 3: Repository Lifecycle Management

**User Story:** As a User, I want to create, view, fork, and delete repositories, so that I can manage my projects and collaborate on others' work.

#### Acceptance Criteria

1. WHEN a User submits a repository creation request with a name, optional description, and visibility setting, THE Repo_Service SHALL create the Repository, initialize a default branch, and return HTTP 201 with repository metadata.
2. IF a repository creation request uses a name that already exists within the same owner namespace, THEN THE Repo_Service SHALL return HTTP 409.
3. WHEN a request is received for repository metadata by owner and name, THE Repo_Service SHALL return the metadata if the requesting User has at least READ access or the Repository is public.
4. IF a request for a private Repository is made by a User without a collaborator role, THEN THE Repo_Service SHALL return HTTP 404 to avoid disclosing the Repository's existence.
5. WHEN an Owner submits a delete request for a Repository, THE Repo_Service SHALL permanently remove all associated objects, branches, commits metadata, pull requests, issues, and webhooks, and return HTTP 204.
6. IF a delete request is submitted by a User who is not the Owner, THEN THE Repo_Service SHALL return HTTP 403.
7. WHEN a User submits a fork request for a Repository, THE Repo_Service SHALL create a new Repository in the caller's namespace referencing the source Repository, copy all Git objects by reference, and return HTTP 201.
8. WHEN a stats request is received for a Repository, THE Repo_Service SHALL return the total object storage size, commit count, and contributor count.

---

### Requirement 4: Git Object Storage Engine

**User Story:** As a developer, I want the Platform to store file content, directory snapshots, and commit history using a content-addressable model, so that data integrity is guaranteed and deduplication is automatic.

#### Acceptance Criteria

1. THE Git_Engine SHALL compute the SHA-256 hash of every object's content and use that hash as the object's unique key.
2. WHEN a Blob is written, THE Object_Store SHALL persist it at the path `objects/{2-char-prefix}/{remaining-hash}` relative to the Repository's storage root.
3. WHEN a Tree is written, THE Object_Store SHALL serialize the filename-to-hash mapping and persist it using the same path convention as Blobs.
4. WHEN a Commit is written, THE Object_Store SHALL serialize the root Tree hash, parent Commit hash(es), author, committer, timestamp, and message, then persist it using the same path convention.
5. THE Git_Engine SHALL NOT use any external Git library (such as JGit) for the core object model; all SHA-256 hashing and content-addressable storage logic SHALL be implemented from scratch.
6. WHEN an object is requested by its SHA-256 hash, THE Object_Store SHALL return the stored bytes if the object exists, or HTTP 404 if it does not.
7. THE Object_Store SHALL expose an S3-compatible interface so that the storage backend can be switched between local filesystem and an S3-compatible service (such as MinIO) without changing application code.
8. FOR ALL objects written then read back, THE Object_Store SHALL return bytes that are identical to the bytes that were written (round-trip integrity property).

---

### Requirement 5: Branch and Tag Reference Management

**User Story:** As a developer, I want to create, list, protect, and delete branches and tags, so that I can manage parallel lines of development and mark releases.

#### Acceptance Criteria

1. WHEN a branch creation request is received with a name and a source Ref, THE System SHALL create a new Branch pointing to the resolved Commit hash and return HTTP 201.
2. IF a branch creation request names a Branch that already exists in the Repository, THEN THE System SHALL return HTTP 409.
3. WHEN a list-branches request is received, THE System SHALL return all Branch names and their head Commit SHAs for the Repository.
4. WHEN a branch deletion request is received for an unprotected Branch, THE System SHALL delete the Branch and return HTTP 204.
5. IF a branch deletion request targets a protected Branch, THEN THE System SHALL return HTTP 403 with a message indicating the Branch is protected.
6. WHEN a branch protection toggle request is received from a User with OWNER or WRITE role, THE System SHALL update the Branch's protected flag and return HTTP 200.
7. IF a push to a protected Branch is attempted via the Transport_Service, THEN THE Transport_Service SHALL reject the push with a descriptive error message.

---

### Requirement 6: HTTP Smart Git Transport (Clone and Push)

**User Story:** As a developer, I want to clone repositories and push commits using standard Git clients over HTTP, so that I can use familiar tooling without installing custom software.

#### Acceptance Criteria

1. WHEN a Git client sends a `GET /api/git/{owner}/{repo}/info/refs?service=git-upload-pack` request, THE Transport_Service SHALL respond with the repository's advertised references in the Git smart HTTP protocol format.
2. WHEN a Git client sends a `POST /api/git/{owner}/{repo}/git-upload-pack` request, THE Transport_Service SHALL negotiate and stream the requested objects as a Pack_File.
3. WHEN a Git client sends a `POST /api/git/{owner}/{repo}/git-receive-pack` request with valid objects and a non-protected target Branch, THE Transport_Service SHALL unpack the objects, update the Branch Ref, record commit metadata, and trigger push-event webhooks.
4. IF a receive-pack request is sent by a User without WRITE or OWNER role, THEN THE Transport_Service SHALL reject the request with HTTP 403.
5. THE Transport_Service SHALL implement Pack_File style batching for all object transfers to minimize round trips.
6. WHEN a push is accepted, THE Transport_Service SHALL invalidate the Cache entries for the affected Repository's commit log and branch list.

---

### Requirement 7: File Tree and Blob Retrieval

**User Story:** As a developer, I want to browse the file tree and view file contents at any commit or branch, so that I can explore the repository without cloning it locally.

#### Acceptance Criteria

1. WHEN a tree request is received for a valid Ref and path, THE System SHALL return a directory listing including entry names, types (blob/tree), sizes, and the last-commit SHA that touched each entry.
2. WHEN a blob request is received for a valid Ref and file path, THE System SHALL return the file content, size, encoding, and the last-commit SHA.
3. WHEN a raw request is received for a valid Ref and file path, THE System SHALL stream the raw bytes with the appropriate `Content-Type` header.
4. IF a requested Ref or path does not exist in the Repository, THEN THE System SHALL return HTTP 404.
5. THE System SHALL reject any file path containing path-traversal sequences (e.g., `../`) with HTTP 400.
6. WHILE a blob is cached in the Cache, THE System SHALL serve the cached content and SHALL NOT re-read from the Object_Store; the Cache TTL for blobs SHALL be 1 hour.

---

### Requirement 8: Commit History and Comparison

**User Story:** As a developer, I want to view paginated commit logs and compare commits or branches, so that I can understand the history and changes in a repository.

#### Acceptance Criteria

1. WHEN a commit log request is received for a Branch, THE System SHALL return a paginated list of Commit metadata (SHA, author, message, timestamp) ordered from newest to oldest.
2. WHEN a single commit detail request is received for a SHA, THE System SHALL return the full Commit metadata including parent SHAs, author, committer, and the diff against the first parent.
3. WHEN a compare request is received for a base Ref and a head Ref, THE System SHALL return the list of Commits reachable from head but not from base, and the combined diff.
4. WHILE commit log pages are cached in the Cache, THE System SHALL serve cached pages; the Cache TTL for commit log pages SHALL be 30 seconds and SHALL be invalidated on any push to the affected Branch.

---

### Requirement 9: Diff Engine

**User Story:** As a developer, I want to see line-by-line diffs between any two commits or file versions, so that I can review exactly what changed.

#### Acceptance Criteria

1. WHEN a diff request is received for a base SHA, a head SHA, and an optional file path, THE Diff_Service SHALL compute and return a unified diff with added, removed, and context lines.
2. THE Diff_Service SHALL implement the Myers_Diff algorithm (or a functionally equivalent algorithm) for byte-level delta computation; it SHALL NOT delegate this computation to an external Git library.
3. WHEN a diff is requested for a binary file, THE Diff_Service SHALL detect the binary content, skip line-level diffing, and return the size delta between the two versions.
4. WHEN a 3-way merge is requested with a base, ours, and theirs Commit, THE Diff_Service SHALL detect conflicting hunks and return conflict markers identifying the conflicting regions.
5. FOR ALL text files where `diff(A, B)` is computed and then `patch(A, diff(A, B))` is applied, THE Diff_Service SHALL produce output identical to B (round-trip patch property).

---

### Requirement 10: Pull Request Lifecycle

**User Story:** As a developer, I want to open pull requests, request reviews, leave inline comments, and merge changes, so that my team can collaborate on code before it reaches the main branch.

#### Acceptance Criteria

1. WHEN a User opens a Pull Request with a head branch, base branch, title, and body, THE PR_Service SHALL create the PR record, compute the initial diff, and return HTTP 201.
2. IF the head branch and base branch are identical, THEN THE PR_Service SHALL return HTTP 422.
3. WHEN a list-PRs request is received, THE PR_Service SHALL return PRs filtered by the requested status (open, closed, or merged) with pagination.
4. WHEN a PR detail request is received, THE PR_Service SHALL return the PR metadata, the full diff between head and base, and the review timeline.
5. WHEN a reviewer submits a review with verdict APPROVE or CHANGES_REQUESTED, THE PR_Service SHALL record the review and notify the PR author via the Notification_Service.
6. WHEN a merge request is received for an open PR with at least one APPROVE review and no unresolved CHANGES_REQUESTED reviews, THE PR_Service SHALL perform the merge using the requested strategy (squash, merge commit, or rebase), update the base Branch Ref, mark the PR as merged, and trigger a pull_request webhook event.
7. IF a merge request is received for a PR that has unresolved CHANGES_REQUESTED reviews, THEN THE PR_Service SHALL return HTTP 422 with a message indicating the PR is not mergeable.
8. WHEN an inline comment is posted on a PR with a file path and line number, THE PR_Service SHALL store the comment and notify relevant participants.
9. WHEN a push is made to the head branch of an open PR, THE PR_Service SHALL recompute the diff and update the PR's diff snapshot.

---

### Requirement 11: Issue Tracker

**User Story:** As a project maintainer, I want to create and manage issues with comments and labels, so that I can track bugs, feature requests, and tasks.

#### Acceptance Criteria

1. WHEN a User creates an issue with a title and body, THE Issue_Service SHALL assign it a sequential number within the Repository, set its status to open, and return HTTP 201.
2. WHEN an issue update request is received from the issue author or a Collaborator with WRITE or OWNER role, THE Issue_Service SHALL apply the changes and return HTTP 200.
3. IF an issue update request is submitted by a User without the required role, THEN THE Issue_Service SHALL return HTTP 403.
4. WHEN a comment is added to an issue, THE Issue_Service SHALL store the comment, increment the comment count, and notify the issue author and previous commenters via the Notification_Service.
5. WHEN a label is applied to an issue, THE Issue_Service SHALL associate the label with the issue; the label SHALL have been previously created for the Repository.
6. IF a label that does not exist in the Repository is applied to an issue, THEN THE Issue_Service SHALL return HTTP 422.
7. WHEN an issue close request is received from an authorized User, THE Issue_Service SHALL set the issue status to closed and trigger an issues webhook event.

---

### Requirement 12: Webhook Management and Delivery

**User Story:** As a repository owner, I want to configure webhooks so that external services are notified when events occur in my repository.

#### Acceptance Criteria

1. WHEN a User with OWNER role creates a webhook with a URL, secret, and event list, THE Webhook_Service SHALL store the webhook configuration and return HTTP 201.
2. WHEN a subscribed event occurs (push, pull_request, issues, issue_comment, or release), THE Webhook_Service SHALL deliver an HTTP POST to the configured URL with a JSON payload signed using HMAC-SHA256 with the webhook secret.
3. IF a webhook delivery receives a non-2xx HTTP response or times out, THEN THE Webhook_Service SHALL retry delivery with exponential backoff for up to 3 attempts.
4. WHEN a webhook test request is received, THE Webhook_Service SHALL deliver a synthetic ping payload to the configured URL and return the delivery result.
5. THE Webhook_Service SHALL never log the raw webhook secret value.
6. WHEN a webhook is deleted by a User with OWNER role, THE Webhook_Service SHALL remove the configuration and return HTTP 204.

---

### Requirement 13: CI/CD Pipeline Simulation

**User Story:** As a developer, I want push events to automatically trigger a build and test pipeline, so that I can see the status of my changes on commits and pull requests.

#### Acceptance Criteria

1. WHEN a push event is received by the Pipeline_Engine, THE Pipeline_Engine SHALL create a Pipeline_Run record with status PENDING and the triggering Commit SHA.
2. WHEN a Pipeline_Run is created, THE Pipeline_Engine SHALL execute a "build" stage followed by a "test" stage, updating the Pipeline_Run status to RUNNING, then to SUCCESS or FAILURE based on the simulated outcome.
3. THE Pipeline_Engine SHALL store each Pipeline_Run with its repo ID, commit SHA, status, stages JSON, start time, and finish time.
4. WHEN a pipeline list request is received for a Repository, THE System SHALL return all Pipeline_Runs for that Repository with pagination.
5. WHEN a pipeline detail request is received for a Pipeline_Run ID, THE System SHALL return the full Pipeline_Run record including per-stage status and timing.
6. WHEN a Pipeline_Run completes, THE Pipeline_Engine SHALL update the associated Commit's status indicator and notify open PRs targeting the affected Branch.

---

### Requirement 14: Real-Time Notifications

**User Story:** As a User, I want to receive real-time notifications for events that involve me, so that I can respond promptly without polling.

#### Acceptance Criteria

1. WHEN an event that involves a User occurs (PR review, issue comment, mention, pipeline completion), THE Notification_Service SHALL create a notification record for that User.
2. WHEN a User is connected via the WebSocket endpoint `/ws/notifications`, THE Notification_Service SHALL push new notifications to that User's session in real time using STOMP.
3. WHEN a User requests their notification list, THE System SHALL return all unread notifications for that User with pagination.
4. WHEN a User marks a notification as read, THE System SHALL update the notification record and return HTTP 200.
5. THE Notification_Service SHALL use Redis pub/sub to fan out notifications to all server instances serving the target User's WebSocket session.

---

### Requirement 15: Search

**User Story:** As a User, I want to search for repositories, code, and users, so that I can discover projects and contributors on the Platform.

#### Acceptance Criteria

1. WHEN a search request is received with a query string and type `repositories`, THE Search_Service SHALL return repositories whose name or description matches the query, ordered by relevance.
2. WHEN a search request is received with a query string and type `code`, THE Search_Service SHALL return file matches within public repositories, including the file path, repository, and matching line snippet.
3. WHEN a search request is received with a query string and type `users`, THE Search_Service SHALL return users whose username or bio matches the query.
4. IF a search request is received without a query string or with a query string shorter than 2 characters, THEN THE Search_Service SHALL return HTTP 400.

---

### Requirement 16: Repository Access Control

**User Story:** As a repository owner, I want to control who can read, write, or administer my repository, so that private projects remain confidential and contributions are managed.

#### Acceptance Criteria

1. THE System SHALL enforce collaborator role checks on every git transport, blob, tree, commit, branch, PR, and issue endpoint before processing the request.
2. WHEN a User is added as a Collaborator with a specified role (OWNER, WRITE, or READ), THE System SHALL record the association and apply the role's permissions immediately.
3. IF a User attempts an operation that exceeds their collaborator role (e.g., a READ Collaborator attempting a push), THEN THE System SHALL return HTTP 403.
4. WHEN a Repository is public, THE System SHALL allow unauthenticated Users to perform read operations (clone, browse tree, view commits, view issues) without requiring authentication.

---

### Requirement 17: Rate Limiting

**User Story:** As a platform operator, I want API endpoints to be rate-limited per user and per IP, so that abusive clients cannot degrade service for others.

#### Acceptance Criteria

1. THE System SHALL apply per-user rate limits to all authenticated API endpoints using Bucket4j.
2. THE System SHALL apply per-IP rate limits to all unauthenticated API endpoints using Bucket4j.
3. WHEN a request exceeds the applicable rate limit, THE System SHALL return HTTP 429 with a `Retry-After` header indicating when the client may retry.
4. THE System SHALL store rate-limit counters in the Cache to ensure limits are enforced consistently across all server instances.

---

### Requirement 18: Input Validation and Security

**User Story:** As a platform operator, I want all user-supplied input to be validated and sanitized, so that the Platform is protected against injection, traversal, and other common attacks.

#### Acceptance Criteria

1. THE System SHALL validate all request bodies against defined schemas and return HTTP 400 with a structured error envelope for any validation failure.
2. THE System SHALL reject any file path parameter containing path-traversal sequences (e.g., `../`, `..\\`) with HTTP 400.
3. THE System SHALL sanitize all user-supplied text content (issue bodies, PR descriptions, commit messages displayed in UI) to prevent cross-site scripting.
4. THE System SHALL return a consistent error envelope `{ error, message, details, timestamp }` for all 4xx and 5xx responses.
5. THE System SHALL never include secrets, password hashes, or raw token values in any API response or log output.

---

### Requirement 19: Caching Strategy

**User Story:** As a platform operator, I want frequently accessed data to be cached in Redis, so that database load is reduced and response times are fast.

#### Acceptance Criteria

1. WHEN repository metadata is fetched, THE Cache SHALL store the result with a TTL of 60 seconds.
2. WHEN a Blob is fetched from the Object_Store, THE Cache SHALL store the result with a TTL of 1 hour, given that Blob content is immutable once written.
3. WHEN a commit log page is fetched, THE Cache SHALL store the result with a TTL of 30 seconds.
4. WHEN a push is accepted by the Transport_Service, THE Cache SHALL invalidate all commit log page entries and branch list entries for the affected Repository.
5. THE Cache SHALL store user session data and rate-limit counters to support stateless horizontal scaling.

---

### Requirement 20: Frontend — Authentication and User Profile

**User Story:** As a visitor, I want a web interface for registration, login, and profile management, so that I can access the Platform from a browser without using the API directly.

#### Acceptance Criteria

1. THE System SHALL provide a registration page at `/register` and a login page at `/login` that submit credentials to the Auth_Service and store the returned JWT for subsequent requests.
2. WHEN a User navigates to `/:owner`, THE System SHALL display the User's profile including avatar, bio, public activity feed, and list of public repositories.
3. WHEN a User navigates to `/settings`, THE System SHALL display forms for updating profile information, managing SSH keys, and managing personal access tokens.
4. WHEN a User logs out, THE System SHALL clear the stored JWT and redirect to the landing page.

---

### Requirement 21: Frontend — Repository Browser

**User Story:** As a developer, I want a web interface to browse repository files, commits, branches, and diffs, so that I can explore a project without using the command line.

#### Acceptance Criteria

1. WHEN a User navigates to `/:owner/:repo`, THE System SHALL display the repository home page including the README, default branch file tree, clone URLs, and repository statistics.
2. WHEN a User navigates to `/:owner/:repo/tree/:ref/*`, THE System SHALL display a collapsible file tree with file-type icons, breadcrumb navigation, and the last-commit message for each entry.
3. WHEN a User navigates to `/:owner/:repo/blob/:ref/*`, THE System SHALL display the file content with syntax highlighting using Prism.js.
4. WHEN a User navigates to `/:owner/:repo/commits/:ref`, THE System SHALL display a paginated commit list with author, message, timestamp, and SHA.
5. WHEN a User navigates to `/:owner/:repo/commit/:sha`, THE System SHALL display the commit detail including the diff viewer with added/removed lines highlighted.
6. THE System SHALL provide a BranchSelector component with fuzzy search across branches and tags on all repository pages that display branch-scoped content.

---

### Requirement 22: Frontend — Pull Request and Issue UI

**User Story:** As a developer, I want a web interface for creating, reviewing, and merging pull requests and managing issues, so that collaboration workflows are accessible from the browser.

#### Acceptance Criteria

1. WHEN a User navigates to `/:owner/:repo/pulls`, THE System SHALL display a filterable list of pull requests by status (open, closed, merged).
2. WHEN a User navigates to `/:owner/:repo/pulls/:id`, THE System SHALL display the PR detail including the diff viewer, review timeline, inline comments, and merge controls.
3. THE DiffViewer component SHALL support toggling between side-by-side and unified diff views.
4. WHEN a User navigates to `/:owner/:repo/issues`, THE System SHALL display a filterable issue list with labels and status.
5. WHEN a User navigates to `/:owner/:repo/settings`, THE System SHALL display repository settings including danger-zone actions (delete), webhook management, and collaborator access management.

---

### Requirement 23: Frontend — Commit Graph Visualization

**User Story:** As a developer, I want to see an SVG-based visualization of the branch and merge history, so that I can understand the commit DAG at a glance.

#### Acceptance Criteria

1. THE CommitGraph component SHALL render an SVG-based directed acyclic graph (DAG) of commits, with each commit represented as a node and parent relationships as edges.
2. WHEN a merge commit is displayed in the CommitGraph, THE CommitGraph component SHALL render edges to all parent commits to accurately represent the merge topology.
3. THE CommitGraph component SHALL visually distinguish branch lanes using different colors or offsets.

---

### Requirement 24: Infrastructure and Deployment

**User Story:** As a platform operator, I want the entire system to be runnable with a single Docker Compose command, so that local development and deployment are straightforward.

#### Acceptance Criteria

1. THE System SHALL provide a `docker-compose.yml` that starts the backend application, PostgreSQL, Redis, and MinIO (S3-compatible object store) as named services with health checks.
2. THE System SHALL provide a multi-stage `Dockerfile` for the backend that compiles the application with JDK 21 and produces a distroless runtime image.
3. THE System SHALL provide a `Dockerfile` for the frontend that builds the Vite application and serves it via an nginx:alpine image.
4. THE System SHALL provide an `nginx.conf` that reverse-proxies API requests to the backend, enables gzip compression, and sets appropriate cache headers for static assets.
5. THE System SHALL provide a `.env.example` file documenting all required environment variables with descriptions.
6. THE System SHALL provide a `README.md` with instructions for local setup, Docker Compose setup, and a first-push walkthrough.
7. WHERE Kubernetes deployment is required, THE System SHALL provide Deployment, Service, Ingress, and PersistentVolumeClaim manifests in a `k8s/` directory.

---

### Requirement 25: Database Schema and Migrations

**User Story:** As a platform operator, I want the database schema to be managed by versioned migrations, so that schema changes are reproducible and auditable across environments.

#### Acceptance Criteria

1. THE System SHALL manage all PostgreSQL schema changes using Flyway versioned migration scripts.
2. THE System SHALL define tables for: users, repositories, collaborators, git_objects, branches, commits_meta, pull_requests, pr_reviews, pr_comments, issues, issue_comments, labels, webhooks, notifications, ssh_keys, personal_tokens, audit_logs, and pipeline_runs.
3. THE System SHALL define foreign key constraints between all related tables and create indexes on all columns used in WHERE clauses or JOIN conditions in the most frequent queries.
4. WHEN the application starts, THE System SHALL apply any pending Flyway migrations before accepting requests.

---

### Requirement 26: API Documentation

**User Story:** As an integrator, I want interactive API documentation, so that I can explore and test all endpoints without reading source code.

#### Acceptance Criteria

1. THE System SHALL expose an OpenAPI 3 specification at `/api/docs`.
2. THE System SHALL expose a Swagger UI at `/api/swagger-ui` that renders the OpenAPI specification interactively.
3. THE System SHALL annotate all request and response DTOs with `@Schema` descriptions so that the generated specification is self-documenting.

---

### Requirement 27: Testing

**User Story:** As a developer, I want a comprehensive test suite covering unit, integration, and load scenarios, so that regressions are caught before deployment.

#### Acceptance Criteria

1. THE System SHALL include JUnit 5 unit tests with Mockito for Diff_Service, Object_Store, Merge_Service, and Auth_Service covering all primary code paths.
2. THE System SHALL include Spring Boot integration tests using Testcontainers for all REST endpoints, verifying correct HTTP status codes and response bodies.
3. THE System SHALL include Vitest and React Testing Library tests for the DiffViewer and CommitGraph frontend components.
4. THE System SHALL include a load test script (k6 or Gatling) covering clone, push, and PR merge operations under concurrent load.
5. FOR ALL Diff_Service round-trip tests, WHEN `patch(A, diff(A, B))` is computed for arbitrary text inputs A and B, THE Diff_Service SHALL produce output identical to B.
