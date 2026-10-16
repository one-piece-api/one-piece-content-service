-- Reveals stored forward (implementation plan of the timeline, T13; ADR-0010, addendum):
-- a reveal names the fields the reader learns at its chapter, with the value learned for
-- those not yet the truth; what the reader knows at the first appearance is the fruit's
-- initial knowledge. The reveals saved so far say what was believed before them, which
-- no longer reads the same: nothing is online yet, so they are dropped, not converted.
DELETE FROM devil_fruit_version_reveal;

-- The fields a reveal names as learned there.
CREATE TABLE devil_fruit_version_reveal_field
(
    reveal_id UUID        NOT NULL REFERENCES devil_fruit_version_reveal (id) ON DELETE CASCADE,
    field     VARCHAR(16) NOT NULL
        CONSTRAINT ck_devil_fruit_version_reveal_field
            CHECK (field IN ('NAME', 'ROMAJI', 'TYPE', 'DESCRIPTION', 'ADVANTAGES', 'DISADVANTAGES')),
    PRIMARY KEY (reveal_id, field)
);

-- What the reader knows at the first appearance: a row only for a version holding some of
-- it. The type points to a content of constant entity type (V17); the subcategory has no
-- foreign key, like the fruit's own (V12).
CREATE TABLE devil_fruit_version_initial_knowledge
(
    version_id       UUID PRIMARY KEY
        REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    romaji           VARCHAR(100),
    type_content_id  UUID,
    type_entity_type VARCHAR(32) NOT NULL DEFAULT 'DEVIL_FRUIT_TYPE'
        CONSTRAINT ck_devil_fruit_version_initial_knowledge_type_entity
            CHECK (type_entity_type = 'DEVIL_FRUIT_TYPE'),
    subcategory_id   UUID,
    CONSTRAINT fk_devil_fruit_version_initial_knowledge_type
        FOREIGN KEY (type_content_id, type_entity_type) REFERENCES content (id, entity_type)
);

-- What the retirement of a type looks up: the fruits online knowing it at first.
CREATE INDEX idx_devil_fruit_version_initial_knowledge_type
    ON devil_fruit_version_initial_knowledge (type_content_id);

-- The translated values known at the first appearance, per language.
CREATE TABLE devil_fruit_version_initial_knowledge_translation
(
    version_id    UUID       NOT NULL REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    advantages    VARCHAR(2000),
    disadvantages VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);
