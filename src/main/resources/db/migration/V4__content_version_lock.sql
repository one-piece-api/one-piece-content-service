-- Optimistic locking on the workflow of a version: every change bumps lock_version, and a
-- change written from an older read finds no row to update and fails - two reviewers
-- claiming at once, or a claim racing a pull back, can no longer both succeed.
ALTER TABLE content_version
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

-- A claim only exists while the version is in review (4.2).
ALTER TABLE content_version
    ADD CONSTRAINT ck_content_version_claim_in_review CHECK (status = 'IN_REVIEW' OR claimant_user_id IS NULL);
