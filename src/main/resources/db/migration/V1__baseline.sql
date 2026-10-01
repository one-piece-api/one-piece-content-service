-- Baseline of the content service schema: what exists before the version model of
-- docs/user-flows/content-editorial-workflow.md is built (docs/implementation-plan-content.md
-- §2). It replaces the migration history of the previous editorial model, squashed while
-- the project had no data worth keeping; every database was recreated from this file.

-- Audit trail for every mutating action (flows document §7), mirroring
-- one-piece-user-service's audit_log shape. target_item_id is null for an action with no
-- content item as its target, e.g. a change to the language catalog.
CREATE TABLE audit_log
(
    id             BIGSERIAL PRIMARY KEY,
    action         VARCHAR(64)  NOT NULL,
    actor_user_id  UUID         NOT NULL,
    actor_email    VARCHAR(255) NOT NULL,
    target_item_id UUID,
    target_label   VARCHAR(255),
    detail         VARCHAR(2000),
    occurred_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_log_target_item_id ON audit_log (target_item_id);

-- The ADMIN-managed language catalog (flows document §3.2). Presence in this table is what
-- "active" means. The code is two lowercase letters (ISO 639-1 style): the format is
-- enforced by LanguageService, not by the column.
CREATE TABLE language
(
    code VARCHAR(8) PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

INSERT INTO language (code, name)
VALUES ('it', 'Italiano'),
       ('en', 'English');
