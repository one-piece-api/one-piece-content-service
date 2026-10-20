-- Affiliation (implementation plan of the Affiliation, A1, D1-D5): a group a character can
-- belong to (a pirate crew, the Navy, the World Government...). Keyed by its romaji as a
-- Devil Fruit Type, with an optional emblem as a fruit's image and a first appearance as
-- the fruit's.

ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC', 'VOLUME', 'CHAPTER', 'EPISODE',
                               'BOUNTY', 'AFFILIATION'));

-- The first appearance points to a chapter's or an episode's content, kept to that entity
-- by the constant entity type of the composite foreign key (V17, V18). The image is stored
-- like a fruit's (V10): no cascade, an image still used is never deleted.
CREATE TABLE affiliation_version
(
    version_id                           UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    romaji                               VARCHAR(100),
    romaji_slug                          VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    image_id                             VARCHAR(64) REFERENCES image (id),
    first_appearance_chapter_id          UUID,
    first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_affiliation_version_first_appearance_entity
            CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    first_appearance_episode_id          UUID,
    first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_affiliation_version_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    CONSTRAINT fk_affiliation_version_first_appearance FOREIGN KEY (first_appearance_chapter_id,
                                                                    first_appearance_chapter_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_affiliation_version_first_appearance_episode FOREIGN KEY (first_appearance_episode_id,
                                                                            first_appearance_episode_entity_type)
        REFERENCES content (id, entity_type)
);

-- The uniqueness check (slug), the first appearances, and whether any version still uses
-- an image, asked on every replace or removal.
CREATE INDEX idx_affiliation_version_romaji_slug ON affiliation_version (romaji_slug);

CREATE INDEX idx_affiliation_version_first_appearance ON affiliation_version (first_appearance_chapter_id);

CREATE INDEX idx_affiliation_version_first_appearance_episode ON affiliation_version (first_appearance_episode_id);

CREATE INDEX idx_affiliation_version_image_id ON affiliation_version (image_id);

-- Its per-language name and description. A row is absent for a language nothing was saved
-- for yet. The reference to the catalog is what keeps a language in use from being deleted.
CREATE TABLE affiliation_version_translation
(
    version_id    UUID       NOT NULL REFERENCES affiliation_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);
