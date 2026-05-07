-- V4__pr_review_body.sql
-- Adds the optional body column to pr_reviews for review text content.
-- This column was specified in the PR module design but omitted from V1.

ALTER TABLE pr_reviews ADD COLUMN IF NOT EXISTS body TEXT;
