-- "My latest activity" on the dashboard (UF-CNT-19) reads a caller's own records, most
-- recent first: this index serves that query without scanning the whole log.
CREATE INDEX idx_audit_log_actor_occurred_at ON audit_log (actor_user_id, occurred_at DESC, id DESC);
