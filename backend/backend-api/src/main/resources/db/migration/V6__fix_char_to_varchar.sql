-- =============================================================================
-- V6__fix_char_to_varchar.sql
-- Converts CHAR(n) columns to VARCHAR(n) to match Hibernate's expected type.
-- PostgreSQL stores CHAR(n) as bpchar, which Hibernate maps as varchar —
-- causing schema validation failures at startup.
-- =============================================================================

ALTER TABLE git_objects   ALTER COLUMN sha        TYPE VARCHAR(64);
ALTER TABLE branches      ALTER COLUMN head_sha   TYPE VARCHAR(64);
ALTER TABLE commits_meta  ALTER COLUMN sha        TYPE VARCHAR(64);
ALTER TABLE labels        ALTER COLUMN color      TYPE VARCHAR(7);
ALTER TABLE pipeline_runs ALTER COLUMN commit_sha TYPE VARCHAR(64);
