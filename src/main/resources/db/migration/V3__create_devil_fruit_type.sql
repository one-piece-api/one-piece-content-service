-- First concrete content entity (docs/user-flows/authentication-and-user-management.md).
-- An item is the stable identity; it may have several concurrent working revisions, one per
-- author (§4.1/§7.5) - only DRAFT is reachable yet (Step 1), IN_REVIEW/REVIEWED land in
-- later steps but the column's full value set is declared now to avoid a later migration
-- just to widen it.
CREATE TABLE devil_fruit_type_item
(
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

-- romaji has no length cap here on purpose: a draft may be saved incomplete or over the
-- eventual 100-char submission limit (§3.3) - that limit is enforced only at
-- submit-for-review time (Step 2), not at the database level.
CREATE TABLE devil_fruit_type_working_revision
(
    id          UUID PRIMARY KEY,
    item_id     UUID        NOT NULL REFERENCES devil_fruit_type_item (id),
    author_id   UUID        NOT NULL,
    romaji      TEXT,
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_working_revision_author_status
    ON devil_fruit_type_working_revision (author_id, status);

-- One row per (working revision, language) the author has started - absent rather than
-- empty-stringed for a language never touched, though the edit endpoint may in practice
-- store an empty string once a language tab has been opened and saved. No length cap on
-- name/description for the same reason as romaji above.
CREATE TABLE devil_fruit_type_translation
(
    working_revision_id UUID NOT NULL REFERENCES devil_fruit_type_working_revision (id),
    language_code        VARCHAR(8) NOT NULL REFERENCES language (code),
    name                  TEXT,
    description           TEXT,
    PRIMARY KEY (working_revision_id, language_code)
);
