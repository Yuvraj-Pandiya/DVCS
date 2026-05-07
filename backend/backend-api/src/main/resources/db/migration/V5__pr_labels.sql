-- V5__pr_labels.sql
-- Add support for labels on pull requests

-- pr_labels (join table)
CREATE TABLE pr_labels (
    pr_id    BIGINT NOT NULL REFERENCES pull_requests(id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (pr_id, label_id)
);

CREATE INDEX idx_pr_labels_pr ON pr_labels(pr_id);
CREATE INDEX idx_pr_labels_label ON pr_labels(label_id);
