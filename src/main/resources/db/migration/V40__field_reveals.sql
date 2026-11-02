-- Field reveals (workflow revision 17, 3.7.1; reveals plan R4, D3, D7, D9): what the reader
-- believes of a Devil Fruit, a Character or a Bounty at its first appearance (initial
-- knowledge) and what the reader learns later, at each reveal. The main fields stay the
-- current value. A field a reveal names with a value is learned there; named without one,
-- the reveal reveals the current value (R4, 2026-11-01).
--
-- Per entity:
--   <entity>_version_initial_knowledge            one row per version knowing something
--   <entity>_version_initial_knowledge_translation the translated values known (fruit, character)
--   <entity>_version_reveal                       the reveals in order, each with its point
--   <entity>_version_reveal_field                 the fields a reveal names
--   <entity>_version_reveal_translation           its title and translated values per language
-- A romaji held here also gives a slug (3.3): generated as on the version, indexed for the
-- uniqueness check. No data conversion: the content database restarts empty (D11).

-- Devil Fruit: name, romaji, type and subcategory together. The type points to a content of
-- constant entity type (V17); the subcategory has no foreign key, like the fruit's own (V12).
CREATE TABLE devil_fruit_version_initial_knowledge
(
    version_id       UUID PRIMARY KEY REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    romaji           VARCHAR(100),
    romaji_slug      VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    type_content_id  UUID,
    type_entity_type VARCHAR(32) NOT NULL DEFAULT 'DEVIL_FRUIT_TYPE'
        CONSTRAINT ck_devil_fruit_version_initial_knowledge_type_entity
            CHECK (type_entity_type = 'DEVIL_FRUIT_TYPE'),
    subcategory_id   UUID,
    CONSTRAINT fk_devil_fruit_version_initial_knowledge_type
        FOREIGN KEY (type_content_id, type_entity_type) REFERENCES content (id, entity_type)
);

CREATE INDEX idx_devil_fruit_version_initial_knowledge_romaji_slug
    ON devil_fruit_version_initial_knowledge (romaji_slug);

CREATE INDEX idx_devil_fruit_version_initial_knowledge_type
    ON devil_fruit_version_initial_knowledge (type_content_id);

CREATE TABLE devil_fruit_version_initial_knowledge_translation
(
    version_id    UUID       NOT NULL REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    PRIMARY KEY (version_id, language_code)
);

CREATE TABLE devil_fruit_version_reveal
(
    id               UUID PRIMARY KEY,
    version_id       UUID        NOT NULL REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    position         INT         NOT NULL,
    romaji           VARCHAR(100),
    romaji_slug      VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    type_content_id  UUID,
    type_entity_type VARCHAR(32) NOT NULL DEFAULT 'DEVIL_FRUIT_TYPE'
        CONSTRAINT ck_devil_fruit_version_reveal_type_entity CHECK (type_entity_type = 'DEVIL_FRUIT_TYPE'),
    subcategory_id   UUID,
    CONSTRAINT fk_devil_fruit_version_reveal_type
        FOREIGN KEY (type_content_id, type_entity_type) REFERENCES content (id, entity_type),
    CONSTRAINT uq_devil_fruit_version_reveal_position UNIQUE (version_id, position)
);

CREATE INDEX idx_devil_fruit_version_reveal_romaji_slug ON devil_fruit_version_reveal (romaji_slug);

CREATE INDEX idx_devil_fruit_version_reveal_type ON devil_fruit_version_reveal (type_content_id);

CREATE TABLE devil_fruit_version_reveal_field
(
    reveal_id UUID        NOT NULL REFERENCES devil_fruit_version_reveal (id) ON DELETE CASCADE,
    field     VARCHAR(16) NOT NULL
        CONSTRAINT ck_devil_fruit_version_reveal_field CHECK (field IN ('NAME', 'ROMAJI', 'TYPE')),
    PRIMARY KEY (reveal_id, field)
);

-- Title nullable: a draft may lack it, the submission asks for it.
CREATE TABLE devil_fruit_version_reveal_translation
(
    reveal_id     UUID       NOT NULL REFERENCES devil_fruit_version_reveal (id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(60),
    name          VARCHAR(100),
    PRIMARY KEY (reveal_id, language_code)
);

-- Character: the identity (name, romaji, Japanese name together) and the status.
CREATE TABLE character_version_initial_knowledge
(
    version_id    UUID PRIMARY KEY REFERENCES character_version (version_id) ON DELETE CASCADE,
    romaji        VARCHAR(100),
    romaji_slug   VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    japanese_name VARCHAR(100),
    status        VARCHAR(16)
        CONSTRAINT ck_character_version_initial_knowledge_status CHECK (status IN ('ALIVE', 'DECEASED', 'UNKNOWN'))
);

CREATE INDEX idx_character_version_initial_knowledge_romaji_slug
    ON character_version_initial_knowledge (romaji_slug);

CREATE TABLE character_version_initial_knowledge_translation
(
    version_id    UUID       NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    PRIMARY KEY (version_id, language_code)
);

CREATE TABLE character_version_reveal
(
    id            UUID PRIMARY KEY,
    version_id    UUID NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position      INT  NOT NULL,
    romaji        VARCHAR(100),
    romaji_slug   VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    japanese_name VARCHAR(100),
    status        VARCHAR(16)
        CONSTRAINT ck_character_version_reveal_status CHECK (status IN ('ALIVE', 'DECEASED', 'UNKNOWN')),
    CONSTRAINT uq_character_version_reveal_position UNIQUE (version_id, position)
);

CREATE INDEX idx_character_version_reveal_romaji_slug ON character_version_reveal (romaji_slug);

CREATE TABLE character_version_reveal_field
(
    reveal_id UUID        NOT NULL REFERENCES character_version_reveal (id) ON DELETE CASCADE,
    field     VARCHAR(16) NOT NULL
        CONSTRAINT ck_character_version_reveal_field CHECK (field IN ('IDENTITY', 'STATUS')),
    PRIMARY KEY (reveal_id, field)
);

CREATE TABLE character_version_reveal_translation
(
    reveal_id     UUID       NOT NULL REFERENCES character_version_reveal (id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(60),
    name          VARCHAR(100),
    PRIMARY KEY (reveal_id, language_code)
);

-- Bounty: whether it is inactive.
CREATE TABLE bounty_version_initial_knowledge
(
    version_id UUID PRIMARY KEY REFERENCES bounty_version (version_id) ON DELETE CASCADE,
    inactive   BOOLEAN
);

CREATE TABLE bounty_version_reveal
(
    id         UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES bounty_version (version_id) ON DELETE CASCADE,
    position   INT  NOT NULL,
    inactive   BOOLEAN,
    CONSTRAINT uq_bounty_version_reveal_position UNIQUE (version_id, position)
);

CREATE TABLE bounty_version_reveal_field
(
    reveal_id UUID        NOT NULL REFERENCES bounty_version_reveal (id) ON DELETE CASCADE,
    field     VARCHAR(16) NOT NULL
        CONSTRAINT ck_bounty_version_reveal_field CHECK (field IN ('INACTIVE')),
    PRIMARY KEY (reveal_id, field)
);

CREATE TABLE bounty_version_reveal_translation
(
    reveal_id     UUID       NOT NULL REFERENCES bounty_version_reveal (id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(60),
    PRIMARY KEY (reveal_id, language_code)
);

-- The point of each reveal: a chapter and an episode, the shape of V38 - a composite foreign
-- key to the content kept to that entity by a constant entity type, no cascade, each part
-- indexed for what a chapter's or an episode's retirement looks up.
DO
$$
    DECLARE
        entity TEXT;
        axis   TEXT;
    BEGIN
        FOREACH entity IN ARRAY ARRAY ['devil_fruit', 'character', 'bounty']
            LOOP
                FOREACH axis IN ARRAY ARRAY ['chapter', 'episode']
                    LOOP
                        EXECUTE format('ALTER TABLE %1$I
                            ADD COLUMN revealed_%2$s_id UUID,
                            ADD COLUMN revealed_%2$s_entity_type VARCHAR(32) NOT NULL DEFAULT %3$L
                                CONSTRAINT %4$I CHECK (revealed_%2$s_entity_type = %3$L),
                            ADD CONSTRAINT %5$I FOREIGN KEY (revealed_%2$s_id, revealed_%2$s_entity_type)
                                REFERENCES content (id, entity_type)',
                                       entity || '_version_reveal', axis, upper(axis),
                                       'ck_' || entity || '_version_reveal_' || axis,
                                       'fk_' || entity || '_version_reveal_' || axis);
                        EXECUTE format('CREATE INDEX %1$I ON %2$I (revealed_%3$s_id) WHERE revealed_%3$s_id IS NOT NULL',
                                       'idx_' || entity || '_version_reveal_' || axis,
                                       entity || '_version_reveal', axis);
                    END LOOP;
            END LOOP;
    END
$$;
