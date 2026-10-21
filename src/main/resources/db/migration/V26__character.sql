-- Character (implementation plan of the Character, C1, D1-D9, D14): a person of the story,
-- as of today. Keyed by its official Latin-script name (the column keeps the framework's
-- name, romaji), with an optional portrait and a first appearance as a Species. Its lists
-- (epithets, aliases, species) are part of the version: one child table each, in order.

ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC', 'VOLUME', 'CHAPTER', 'EPISODE',
                               'BOUNTY', 'AFFILIATION', 'SPECIES', 'CHARACTER'));

-- Every column but the key is nullable: a draft may lack them, completeness is the rules'.
-- The birthday is a day and a month, both or neither; whether they make a real date
-- (29 February allowed) is checked in Java.
CREATE TABLE character_version
(
    version_id                           UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    romaji                               VARCHAR(100),
    romaji_slug                          VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    japanese_name                        VARCHAR(100),
    status                               VARCHAR(16)
        CONSTRAINT ck_character_version_status CHECK (status IN ('ALIVE', 'DECEASED', 'UNKNOWN')),
    gender                               VARCHAR(16)
        CONSTRAINT ck_character_version_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN')),
    birthday_month                       SMALLINT
        CONSTRAINT ck_character_version_birthday_month CHECK (birthday_month BETWEEN 1 AND 12),
    birthday_day                         SMALLINT
        CONSTRAINT ck_character_version_birthday_day CHECK (birthday_day BETWEEN 1 AND 31),
    image_id                             VARCHAR(64) REFERENCES image (id),
    first_appearance_chapter_id          UUID,
    first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_character_version_first_appearance_entity
            CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    first_appearance_episode_id          UUID,
    first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_character_version_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    CONSTRAINT ck_character_version_birthday CHECK ((birthday_month IS NULL) = (birthday_day IS NULL)),
    CONSTRAINT fk_character_version_first_appearance FOREIGN KEY (first_appearance_chapter_id,
                                                                  first_appearance_chapter_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_character_version_first_appearance_episode FOREIGN KEY (first_appearance_episode_id,
                                                                          first_appearance_episode_entity_type)
        REFERENCES content (id, entity_type)
);

-- The uniqueness check (slug), the first appearances, and whether any version still uses
-- an image, asked on every replace or removal.
CREATE INDEX idx_character_version_romaji_slug ON character_version (romaji_slug);

CREATE INDEX idx_character_version_first_appearance ON character_version (first_appearance_chapter_id);

CREATE INDEX idx_character_version_first_appearance_episode ON character_version (first_appearance_episode_id);

CREATE INDEX idx_character_version_image_id ON character_version (image_id);

-- Its per-language name and description. A row is absent for a language nothing was saved
-- for yet. The reference to the catalog is what keeps a language in use from being deleted.
CREATE TABLE character_version_translation
(
    version_id    UUID       NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);

-- Its epithets, in order, each translated (D6). A row has an id of its own, the parent of
-- its translations; a save rewrites the rows in place by position, so the unique position
-- is never taken twice.
CREATE TABLE character_version_epithet
(
    id         UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position   INT  NOT NULL,
    CONSTRAINT uq_character_version_epithet_position UNIQUE (version_id, position)
);

CREATE TABLE character_version_epithet_translation
(
    epithet_id    UUID         NOT NULL REFERENCES character_version_epithet (id) ON DELETE CASCADE,
    language_code VARCHAR(8)   NOT NULL REFERENCES language (code),
    text          VARCHAR(100) NOT NULL,
    PRIMARY KEY (epithet_id, language_code)
);

-- Its aliases, in order, the same in every language (D6). Unique within the version
-- regardless of case: checked in Java.
CREATE TABLE character_version_alias
(
    version_id UUID         NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position   INT          NOT NULL,
    alias      VARCHAR(100) NOT NULL,
    PRIMARY KEY (version_id, position)
);

-- Its species, in order (D8): each a species' content, kept to that entity by the
-- constant entity type of the composite foreign key, as the first appearance. A species
-- appears once per version; the check waits for the commit, since reordering the list
-- rewrites it row by row and may name a species twice in between.
CREATE TABLE character_version_species
(
    version_id          UUID        NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position            INT         NOT NULL,
    species_content_id  UUID        NOT NULL,
    species_entity_type VARCHAR(32) NOT NULL DEFAULT 'SPECIES'
        CONSTRAINT ck_character_version_species_entity CHECK (species_entity_type = 'SPECIES'),
    PRIMARY KEY (version_id, position),
    CONSTRAINT uq_character_version_species UNIQUE (version_id, species_content_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_character_version_species FOREIGN KEY (species_content_id, species_entity_type)
        REFERENCES content (id, entity_type)
);

-- What a species' retirement and the list's species filter look up.
CREATE INDEX idx_character_version_species ON character_version_species (species_content_id);
