-- Audit trail for every mutating content action (docs/user-flows/authentication-and-user-management.md
-- §6), mirroring one-piece-user-service's audit_log shape. Write-only from the application's
-- perspective for now: no read endpoint exists yet.
CREATE TABLE audit_log
(
    id             BIGSERIAL PRIMARY KEY,
    action         VARCHAR(64)  NOT NULL,
    actor_user_id  UUID         NOT NULL,
    actor_email    VARCHAR(255) NOT NULL,
    target_item_id UUID         NOT NULL,
    target_label   VARCHAR(255),
    detail         VARCHAR(2000),
    occurred_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_log_target_item_id ON audit_log (target_item_id);
