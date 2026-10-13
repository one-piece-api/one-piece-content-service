-- Saga and Arc (implementation plan of the story structure, S1-S5, step SS3): the first
-- two story entities, keyed by a number instead of a romaji. An arc belongs to one saga.

-- The kinds of content, now four (see V9 for how the check was widened the first time).
ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC'));

-- What a version of a saga says outside translations. The number is its natural key
-- (S2): unique within the entity, checked by the service across every version (flows
-- 3.3), not by a constraint - two drafts of different contents may hold the same number
-- until one is submitted. Gaps are allowed (S3). Nullable: an incomplete draft has none.
-- The slug is the number itself, through the one definition of a slug (V8).
CREATE TABLE saga_version
(
    version_id  UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    number      INTEGER CONSTRAINT ck_saga_version_number CHECK (number >= 0),
    number_slug VARCHAR(200) GENERATED ALWAYS AS (slug_of(number::text)) STORED
);

CREATE INDEX idx_saga_version_number ON saga_version (number);

CREATE INDEX idx_saga_version_number_slug ON saga_version (number_slug);

CREATE TABLE saga_version_translation
(
    version_id    UUID       NOT NULL REFERENCES saga_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(200),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);

-- An arc points to the content of its saga, not to one of its versions, as a fruit to its
-- type (DF-D1): the constant entity type in the composite foreign key keeps an arc from
-- pointing to anything but a saga. Nullable while the draft is incomplete; no cascade.
CREATE TABLE arc_version
(
    version_id       UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    number           INTEGER CONSTRAINT ck_arc_version_number CHECK (number >= 0),
    number_slug      VARCHAR(200) GENERATED ALWAYS AS (slug_of(number::text)) STORED,
    saga_content_id  UUID,
    saga_entity_type VARCHAR(32) NOT NULL DEFAULT 'SAGA'
        CONSTRAINT ck_arc_version_saga_entity CHECK (saga_entity_type = 'SAGA'),
    CONSTRAINT fk_arc_version_saga FOREIGN KEY (saga_content_id, saga_entity_type)
        REFERENCES content (id, entity_type)
);

-- The arcs of a saga ("children of"), and the uniqueness checks.
CREATE INDEX idx_arc_version_saga_content_id ON arc_version (saga_content_id);

CREATE INDEX idx_arc_version_number ON arc_version (number);

CREATE INDEX idx_arc_version_number_slug ON arc_version (number_slug);

CREATE TABLE arc_version_translation
(
    version_id    UUID       NOT NULL REFERENCES arc_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(200),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);
