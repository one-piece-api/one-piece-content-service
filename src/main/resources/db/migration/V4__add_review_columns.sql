-- Step 3 (docs/implementation-plan-content.md): claim/release/approve/reject. author_email
-- and claimed_by_email are denormalized from each caller's own JWT at write time -
-- content-service has no user directory to resolve a bare id against, and the shared
-- review queue must show every author's/claimant's identity to every REVIEWER. All four
-- columns are nullable: rows created before this migration have no author_email, and
-- claimed_by/claimed_by_email/rejection_reason are naturally absent until the
-- corresponding action happens.
ALTER TABLE devil_fruit_type_working_revision
    ADD COLUMN author_email     VARCHAR(320),
    ADD COLUMN claimed_by       UUID,
    ADD COLUMN claimed_by_email VARCHAR(320),
    ADD COLUMN rejection_reason TEXT;
