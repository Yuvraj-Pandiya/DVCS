-- =============================================================================
-- V1__init.sql — Initial schema migration for PR integration tests
-- =============================================================================

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
CREATE INDEX idx_repos_owner ON repositories(owner_id);
CREATE INDEX idx_repos_name  ON repositories(name);

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
    id          BIGSERIAL    PRIMARY KEY,
    repo_id     BIGINT       NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    sha         CHAR(64)     NOT NULL,
    type        VARCHAR(8)   NOT NULL CHECK (type IN ('BLOB','TREE','COMMIT')),
    size        BIGINT       NOT NULL,
    stored_path VARCHAR(512) NOT NULL,
    UNIQUE(repo_id, sha)
);
CREATE INDEX idx_gitobj_repo_sha ON git_objects(repo_id, sha);

-- branches
CREATE TABLE branches (
    id         BIGSERIAL    PRIMARY KEY,
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
    id           BIGSERIAL   PRIMARY KEY,
    repo_id      BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    sha          CHAR(64)    NOT NULL,
    author_id    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    message      TEXT        NOT NULL,
    authored_at  TIMESTAMPTZ NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    UNIQUE(repo_id, sha)
);
CREATE INDEX idx_commits_repo_sha ON commits_meta(repo_id, sha);
CREATE INDEX idx_commits_authored  ON commits_meta(repo_id, authored_at DESC);

-- pull_requests
CREATE TABLE pull_requests (
    id          BIGSERIAL    PRIMARY KEY,
    repo_id     BIGINT       NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    number      INT          NOT NULL,
    title       VARCHAR(512) NOT NULL,
    body        TEXT,
    head_branch VARCHAR(255) NOT NULL,
    base_branch VARCHAR(255) NOT NULL,
    author_id   BIGINT       NOT NULL REFERENCES users(id),
    status      VARCHAR(16)  NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed','merged')),
    merged_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(repo_id, number)
);
CREATE INDEX idx_pr_repo_status ON pull_requests(repo_id, status);

-- pr_reviews
CREATE TABLE pr_reviews (
    id           BIGSERIAL   PRIMARY KEY,
    pr_id        BIGINT      NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    reviewer_id  BIGINT      NOT NULL REFERENCES users(id),
    verdict      VARCHAR(24) NOT NULL CHECK (verdict IN ('APPROVE','CHANGES_REQUESTED','COMMENT')),
    body         TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prreviews_pr ON pr_reviews(pr_id);

-- pr_comments
CREATE TABLE pr_comments (
    id          BIGSERIAL    PRIMARY KEY,
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
    id         BIGSERIAL    PRIMARY KEY,
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
    id         BIGSERIAL   PRIMARY KEY,
    issue_id   BIGINT      NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    body       TEXT        NOT NULL,
    author_id  BIGINT      NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_issuecomments_issue ON issue_comments(issue_id);

-- labels
CREATE TABLE labels (
    id      BIGSERIAL   PRIMARY KEY,
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
    id         BIGSERIAL   PRIMARY KEY,
    repo_id    BIGINT      NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    url        TEXT        NOT NULL,
    secret     TEXT        NOT NULL,
    events     TEXT[]      NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhooks_repo ON webhooks(repo_id);

-- notifications
CREATE TABLE notifications (
    id           BIGSERIAL   PRIMARY KEY,
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
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(128) NOT NULL,
    public_key  TEXT         NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, fingerprint)
);

-- personal_tokens
CREATE TABLE personal_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    scopes     TEXT[]       NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tokens_user ON personal_tokens(user_id);

-- audit_logs
CREATE TABLE audit_logs (
    id            BIGSERIAL    PRIMARY KEY,
    actor_id      BIGINT       REFERENCES users(id) ON DELETE SET NULL,
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
    id          BIGSERIAL   PRIMARY KEY,
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
