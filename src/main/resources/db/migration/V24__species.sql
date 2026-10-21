-- Species (implementation plan of the Species, S1, D1-D5): the race of a character (Human,
-- Fish-Man, Mink, Giant...). Keyed by its romaji as an Affiliation, with an optional picture
-- as a fruit's image and a first appearance as the fruit's.

ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC', 'VOLUME', 'CHAPTER', 'EPISODE',
                               'BOUNTY', 'AFFILIATION', 'SPECIES'));

-- The first appearance points to a chapter's or an episode's content, kept to that entity
-- by the constant entity type of the composite foreign key (V17, V18). The image is stored
-- like a fruit's (V10): no cascade, an image still used is never deleted.
CREATE TABLE species_version
(
    version_id                           UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    romaji                               VARCHAR(100),
    romaji_slug                          VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    image_id                             VARCHAR(64) REFERENCES image (id),
    first_appearance_chapter_id          UUID,
    first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_species_version_first_appearance_entity
            CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    first_appearance_episode_id          UUID,
    first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_species_version_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    CONSTRAINT fk_species_version_first_appearance FOREIGN KEY (first_appearance_chapter_id,
                                                                    first_appearance_chapter_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_species_version_first_appearance_episode FOREIGN KEY (first_appearance_episode_id,
                                                                            first_appearance_episode_entity_type)
        REFERENCES content (id, entity_type)
);

-- The uniqueness check (slug), the first appearances, and whether any version still uses
-- an image, asked on every replace or removal.
CREATE INDEX idx_species_version_romaji_slug ON species_version (romaji_slug);

CREATE INDEX idx_species_version_first_appearance ON species_version (first_appearance_chapter_id);

CREATE INDEX idx_species_version_first_appearance_episode ON species_version (first_appearance_episode_id);

CREATE INDEX idx_species_version_image_id ON species_version (image_id);

-- Its per-language name and description. A row is absent for a language nothing was saved
-- for yet. The reference to the catalog is what keeps a language in use from being deleted.
CREATE TABLE species_version_translation
(
    version_id    UUID       NOT NULL REFERENCES species_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);
