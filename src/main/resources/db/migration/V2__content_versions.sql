-- The version model of docs/user-flows/content-editorial-workflow.md §4. Two kinds of
-- tables: the editorial workflow is the same for every content entity and lives in shared
-- tables (content_item, content_version); what a version says is specific to its entity and
-- lives in that entity's own tables, with real columns, keyed by version. The first entity
-- is Devil Fruit Type. See docs/adr/0002-version-table-with-status.md.

-- One encyclopedia entry, of one entity type. It carries no content of its own.
CREATE TABLE content_item
(
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL CHECK (entity_type IN ('DEVIL_FRUIT_TYPE')),
    created_at  TIMESTAMPTZ NOT NULL
);

-- One numbered revision of an item, born with its draft (§4.1): workflow only. Publishing,
-- retiring and restoring only change its status. Author and claimant keep username and
-- e-mail next to the id because this service has no user directory to resolve an id against.
CREATE TABLE content_version
(
    id                UUID         PRIMARY KEY,
    item_id           UUID         NOT NULL REFERENCES content_item (id) ON DELETE CASCADE,
    version_number    INTEGER      NOT NULL CHECK (version_number >= 1),
    based_on_number   INTEGER,
    author_user_id    UUID         NOT NULL,
    author_username   VARCHAR(255) NOT NULL,
    author_email      VARCHAR(255) NOT NULL,
    status            VARCHAR(32)  NOT NULL CHECK (status IN
                                                   ('DRAFT', 'IN_REVIEW', 'REJECTED', 'READY_TO_PUBLISH',
                                                    'PUBLISHED', 'ARCHIVED', 'RETIRED', 'SUPERSEDED')),
    claimant_user_id  UUID,
    claimant_username VARCHAR(255),
    claimant_email    VARCHAR(255),
    rejection_reason  VARCHAR(2000),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_content_version_number UNIQUE (item_id, version_number),
    CONSTRAINT ck_content_version_based_on CHECK (based_on_number IS NULL OR based_on_number < version_number)
);

-- "An item has at most one open version at a time" (§4.1).
CREATE UNIQUE INDEX uq_content_version_open ON content_version (item_id)
    WHERE status IN ('DRAFT', 'IN_REVIEW', 'REJECTED', 'READY_TO_PUBLISH');

-- "At most one online version per item" (§4.1).
CREATE UNIQUE INDEX uq_content_version_online ON content_version (item_id)
    WHERE status = 'PUBLISHED';

-- What a version of a Devil Fruit Type says, outside translations (§3.1): one row per
-- version of that entity, sharing its id.
CREATE TABLE devil_fruit_type_version
(
    version_id UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    romaji     VARCHAR(100)
);

-- Its per-language name and description (§3.1). A row is absent for a language nothing was
-- saved for yet. The reference to the catalog is what keeps a language in use from being
-- deleted.
CREATE TABLE devil_fruit_type_version_translation
(
    version_id    UUID       NOT NULL REFERENCES devil_fruit_type_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);

-- The audit log is the single source of a version's history (implementation plan, D4): a
-- record about one version points to it. By id, not by number: a number is reused once a
-- draft is deleted, and that draft's records must not join the next version's history. No
-- foreign key: the records outlive a deleted draft. Null for item-level and catalog actions.
-- The actor gains the username, shown in that history; null on the records written before.
ALTER TABLE audit_log
    ADD COLUMN target_version_id UUID,
    ADD COLUMN actor_username    VARCHAR(255);

CREATE INDEX idx_audit_log_target_version_id ON audit_log (target_version_id);
