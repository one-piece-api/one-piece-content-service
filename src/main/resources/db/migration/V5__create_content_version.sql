-- Step 5 (docs/implementation-plan-content.md): Publish. Append-only version history -
-- content_version rows are never updated or deleted, only inserted (by Publish today,
-- by Rollback from Step 7 onward). publisher_email is denormalized for the same reason
-- author_email/claimed_by_email are (V4): no user directory to resolve a bare id against.
CREATE TABLE content_version
(
    id               UUID PRIMARY KEY,
    item_id          UUID        NOT NULL REFERENCES devil_fruit_type_item (id),
    sequence_number  INT         NOT NULL,
    romaji           TEXT,
    publisher_id     UUID        NOT NULL,
    publisher_email  VARCHAR(320),
    published_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_content_version_item ON content_version (item_id);

CREATE TABLE content_version_translation
(
    content_version_id UUID       NOT NULL REFERENCES content_version (id),
    language_code       VARCHAR(8) NOT NULL REFERENCES language (code),
    name                 TEXT,
    description           TEXT,
    PRIMARY KEY (content_version_id, language_code)
);

-- The "live pointer" (4.1): null until a first Publish, later repointed by Rollback
-- (Step 7) or cleared by Retire (Step 8). References content_version, so this column is
-- added only now that the table exists.
ALTER TABLE devil_fruit_type_item
    ADD COLUMN live_version_id UUID REFERENCES content_version (id);
