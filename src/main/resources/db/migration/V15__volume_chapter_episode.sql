-- Volume, Chapter and Episode (implementation plan of the story structure, S1-S6, S13,
-- step SS4): the three story entities below the arc, keyed by a number as V14 does.
-- Titles may repeat (no uniqueness is stored for them, as for any text); numbers are
-- checked by the service across every version, gaps allowed.

ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC', 'VOLUME', 'CHAPTER', 'EPISODE'));

-- A volume: no arc (its arcs are derived from its chapters, S4), no description. The
-- Japanese title is one for every language; the cover is an image like a fruit's (V10),
-- no cascade: an image still used is never deleted.
CREATE TABLE volume_version
(
    version_id      UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    number          INTEGER CONSTRAINT ck_volume_version_number CHECK (number >= 0),
    number_slug     VARCHAR(200) GENERATED ALWAYS AS (slug_of(number::text)) STORED,
    japanese_title  VARCHAR(200),
    cover_image_id  VARCHAR(64) REFERENCES image (id)
);

CREATE INDEX idx_volume_version_number ON volume_version (number);

CREATE INDEX idx_volume_version_number_slug ON volume_version (number_slug);

-- Whether any version still uses a cover, asked on every replace or removal.
CREATE INDEX idx_volume_version_cover_image_id ON volume_version (cover_image_id);

CREATE TABLE volume_version_translation
(
    version_id    UUID       NOT NULL REFERENCES volume_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(200),
    PRIMARY KEY (version_id, language_code)
);

-- The release day per country (S13), part of the version: one per country.
CREATE TABLE volume_version_release
(
    version_id   UUID       NOT NULL REFERENCES volume_version (version_id) ON DELETE CASCADE,
    country      VARCHAR(2) NOT NULL CONSTRAINT ck_volume_version_release_country CHECK (country ~ '^[A-Z]{2}$'),
    release_date DATE       NOT NULL,
    PRIMARY KEY (version_id, country)
);

-- A chapter points to the content of its arc (required to be reviewed, so nullable while
-- a draft) and optionally of its volume, as an arc to its saga (V14): the constant
-- entity types in the composite foreign keys keep a chapter from pointing to anything
-- else. No cascade.
CREATE TABLE chapter_version
(
    version_id         UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    number             INTEGER CONSTRAINT ck_chapter_version_number CHECK (number >= 0),
    number_slug        VARCHAR(200) GENERATED ALWAYS AS (slug_of(number::text)) STORED,
    japanese_title     VARCHAR(200),
    page_count         INTEGER CONSTRAINT ck_chapter_version_page_count CHECK (page_count >= 1),
    arc_content_id     UUID,
    arc_entity_type    VARCHAR(32) NOT NULL DEFAULT 'ARC'
        CONSTRAINT ck_chapter_version_arc_entity CHECK (arc_entity_type = 'ARC'),
    volume_content_id  UUID,
    volume_entity_type VARCHAR(32) NOT NULL DEFAULT 'VOLUME'
        CONSTRAINT ck_chapter_version_volume_entity CHECK (volume_entity_type = 'VOLUME'),
    CONSTRAINT fk_chapter_version_arc FOREIGN KEY (arc_content_id, arc_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_chapter_version_volume FOREIGN KEY (volume_content_id, volume_entity_type)
        REFERENCES content (id, entity_type)
);

-- The chapters of an arc and of a volume ("children of"), and the uniqueness checks.
CREATE INDEX idx_chapter_version_arc_content_id ON chapter_version (arc_content_id);

CREATE INDEX idx_chapter_version_volume_content_id ON chapter_version (volume_content_id);

CREATE INDEX idx_chapter_version_number ON chapter_version (number);

CREATE INDEX idx_chapter_version_number_slug ON chapter_version (number_slug);

CREATE TABLE chapter_version_translation
(
    version_id    UUID       NOT NULL REFERENCES chapter_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(200),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);

CREATE TABLE chapter_version_release
(
    version_id   UUID       NOT NULL REFERENCES chapter_version (version_id) ON DELETE CASCADE,
    country      VARCHAR(2) NOT NULL CONSTRAINT ck_chapter_version_release_country CHECK (country ~ '^[A-Z]{2}$'),
    release_date DATE       NOT NULL,
    PRIMARY KEY (version_id, country)
);

-- An episode: its arc is optional - a filler belongs to none.
CREATE TABLE episode_version
(
    version_id      UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    number          INTEGER CONSTRAINT ck_episode_version_number CHECK (number >= 0),
    number_slug     VARCHAR(200) GENERATED ALWAYS AS (slug_of(number::text)) STORED,
    japanese_title  VARCHAR(200),
    arc_content_id  UUID,
    arc_entity_type VARCHAR(32) NOT NULL DEFAULT 'ARC'
        CONSTRAINT ck_episode_version_arc_entity CHECK (arc_entity_type = 'ARC'),
    CONSTRAINT fk_episode_version_arc FOREIGN KEY (arc_content_id, arc_entity_type)
        REFERENCES content (id, entity_type)
);

CREATE INDEX idx_episode_version_arc_content_id ON episode_version (arc_content_id);

CREATE INDEX idx_episode_version_number ON episode_version (number);

CREATE INDEX idx_episode_version_number_slug ON episode_version (number_slug);

CREATE TABLE episode_version_translation
(
    version_id    UUID       NOT NULL REFERENCES episode_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(200),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);

CREATE TABLE episode_version_broadcast
(
    version_id     UUID       NOT NULL REFERENCES episode_version (version_id) ON DELETE CASCADE,
    country        VARCHAR(2) NOT NULL CONSTRAINT ck_episode_version_broadcast_country CHECK (country ~ '^[A-Z]{2}$'),
    broadcast_date DATE       NOT NULL,
    PRIMARY KEY (version_id, country)
);
