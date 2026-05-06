-- =============================================================================
-- V2__seed_data.sql — Seed data for tests and demos
-- Inserts a demo user, a demo repository, and the four default label colors.
-- =============================================================================

-- Demo user (password_hash is bcrypt of 'demo1234')
INSERT INTO users (id, username, email, password_hash, avatar_url, bio, created_at)
VALUES (
    1,
    'demo',
    'demo@example.com',
    '$2a$12$WQHGzpPBMBpPDGGGGGGGGOeK1234567890abcdefghijklmnopqrstu',
    NULL,
    'Demo user for tests and local development',
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Reset the users sequence so future inserts don't collide with the seeded row
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- Demo repository owned by the demo user
INSERT INTO repositories (id, owner_id, name, description, is_private, default_branch, created_at)
VALUES (
    1,
    1,
    'demo-repo',
    'Demo repository for tests and local development',
    FALSE,
    'main',
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- Reset the repositories sequence
SELECT setval('repositories_id_seq', (SELECT MAX(id) FROM repositories));

-- Default labels for the demo repository (GitHub-style colors)
INSERT INTO labels (repo_id, name, color) VALUES
    (1, 'bug',           '#d73a4a'),
    (1, 'enhancement',   '#a2eeef'),
    (1, 'documentation', '#0075ca'),
    (1, 'question',      '#d876e3')
ON CONFLICT (repo_id, name) DO NOTHING;
