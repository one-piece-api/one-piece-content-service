-- Knowledge phases (implementation plan of the timeline, T9; ADR-0010): what the reader
-- believed about a Devil Fruit before each reveal. Part of the version, as its
-- subcategories are part of a type's (V12): rows of their own, children of the version,
-- in the order of the story. Each holds the chapter that supersedes it and only the
-- fields revealed there; every one of them is nullable, a null being a field the phase
-- does not hold - the rules, not the schema, ask for one at least. The chapter and the
-- type point to contents, the constant entity type keeping each to its kind (V17); the
-- subcategory has no foreign key, like the fruit's own (V12). No cascade towards them.
CREATE TABLE devil_fruit_version_phase
(
    id                                 UUID PRIMARY KEY,
    version_id                         UUID         NOT NULL
        REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    position                           INT          NOT NULL,
    superseded_at_chapter_id           UUID,
    superseded_at_chapter_entity_type  VARCHAR(32)  NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_devil_fruit_version_phase_chapter_entity
            CHECK (superseded_at_chapter_entity_type = 'CHAPTER'),
    romaji                             VARCHAR(100),
    type_content_id                    UUID,
    type_entity_type                   VARCHAR(32)  NOT NULL DEFAULT 'DEVIL_FRUIT_TYPE'
        CONSTRAINT ck_devil_fruit_version_phase_type_entity
            CHECK (type_entity_type = 'DEVIL_FRUIT_TYPE'),
    subcategory_id                     UUID,
    CONSTRAINT fk_devil_fruit_version_phase_chapter
        FOREIGN KEY (superseded_at_chapter_id, superseded_at_chapter_entity_type)
            REFERENCES content (id, entity_type),
    CONSTRAINT fk_devil_fruit_version_phase_type
        FOREIGN KEY (type_content_id, type_entity_type) REFERENCES content (id, entity_type)
);

CREATE INDEX idx_devil_fruit_version_phase_version ON devil_fruit_version_phase (version_id);

-- What the retirement of a chapter or of a type looks up: the fruits online naming it.
CREATE INDEX idx_devil_fruit_version_phase_chapter ON devil_fruit_version_phase (superseded_at_chapter_id);

CREATE INDEX idx_devil_fruit_version_phase_type ON devil_fruit_version_phase (type_content_id);

-- What was believed per language; a field null is not part of the phase.
CREATE TABLE devil_fruit_version_phase_translation
(
    phase_id      UUID       NOT NULL REFERENCES devil_fruit_version_phase (id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    advantages    VARCHAR(2000),
    disadvantages VARCHAR(2000),
    PRIMARY KEY (phase_id, language_code)
);
