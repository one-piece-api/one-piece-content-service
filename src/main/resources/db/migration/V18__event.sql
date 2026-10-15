-- Events (implementation plan of the timeline, T2-T5, TL2): what happened in the world,
-- and when. A new kind of content, with no natural key: addressed by its id, its title
-- may repeat.
ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC', 'VOLUME', 'CHAPTER', 'EPISODE',
                               'EVENT'));

-- When it happens (occurs_*) and, for a long event, when it ends (ends_*): each a tagged
-- union (T3), either a chapter - everything from chapter 1 on - or a year of the world
-- calendar (T4) before the story starts; at most one of the two per group, none while a
-- draft. Which shapes are allowed together, and their order, is the validator's to say.
-- The chapters point to their content, as a first appearance does (V17): the constant
-- entity types in the composite foreign keys keep them from pointing to anything else.
-- revealed_at is where the reader learns of it (reader axis, ADR-0008): only the manga
-- part exists; when none is written, the chapter it occurs in stands for it. No cascade.
CREATE TABLE event_version
(
    version_id                  UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    occurs_chapter_id           UUID,
    occurs_chapter_entity_type  VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_event_version_occurs_chapter_entity CHECK (occurs_chapter_entity_type = 'CHAPTER'),
    occurs_year_from            INTEGER,
    occurs_year_to              INTEGER,
    occurs_approximate          BOOLEAN,
    ends_chapter_id             UUID,
    ends_chapter_entity_type    VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_event_version_ends_chapter_entity CHECK (ends_chapter_entity_type = 'CHAPTER'),
    ends_year_from              INTEGER,
    ends_year_to                INTEGER,
    ends_approximate            BOOLEAN,
    revealed_at_chapter_id          UUID,
    revealed_at_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_event_version_revealed_at_chapter_entity CHECK (revealed_at_chapter_entity_type = 'CHAPTER'),
    CONSTRAINT fk_event_version_occurs_chapter FOREIGN KEY (occurs_chapter_id, occurs_chapter_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_event_version_ends_chapter FOREIGN KEY (ends_chapter_id, ends_chapter_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_event_version_revealed_at_chapter
        FOREIGN KEY (revealed_at_chapter_id, revealed_at_chapter_entity_type) REFERENCES content (id, entity_type),
    -- A world date is whole: a first year and whether it is approximate, the last year
    -- optional; and a group is a chapter or a world date, never both.
    CONSTRAINT ck_event_version_occurs_world_date
        CHECK ((occurs_year_from IS NULL) = (occurs_approximate IS NULL)
            AND (occurs_year_to IS NULL OR occurs_year_from IS NOT NULL)),
    CONSTRAINT ck_event_version_ends_world_date
        CHECK ((ends_year_from IS NULL) = (ends_approximate IS NULL)
            AND (ends_year_to IS NULL OR ends_year_from IS NOT NULL)),
    CONSTRAINT ck_event_version_occurs_one_part
        CHECK (occurs_chapter_id IS NULL OR occurs_year_from IS NULL),
    CONSTRAINT ck_event_version_ends_one_part
        CHECK (ends_chapter_id IS NULL OR ends_year_from IS NULL),
    CONSTRAINT ck_event_version_occurs_years CHECK (occurs_year_to IS NULL OR occurs_year_to >= occurs_year_from),
    CONSTRAINT ck_event_version_ends_years CHECK (ends_year_to IS NULL OR ends_year_to >= ends_year_from)
);

-- What a chapter's retirement looks up: the events online that use it.
CREATE INDEX idx_event_version_occurs_chapter_id ON event_version (occurs_chapter_id);

CREATE INDEX idx_event_version_ends_chapter_id ON event_version (ends_chapter_id);

CREATE INDEX idx_event_version_revealed_at_chapter_id ON event_version (revealed_at_chapter_id);

CREATE TABLE event_version_translation
(
    version_id    UUID       NOT NULL REFERENCES event_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    title         VARCHAR(200),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);

-- The Devil Fruits taking part in it (T5): typed participants, no role, part of the
-- version. Characters will be a table of the same shape.
CREATE TABLE event_version_devil_fruit
(
    version_id                  UUID        NOT NULL REFERENCES event_version (version_id) ON DELETE CASCADE,
    devil_fruit_content_id      UUID        NOT NULL,
    devil_fruit_entity_type     VARCHAR(32) NOT NULL DEFAULT 'DEVIL_FRUIT'
        CONSTRAINT ck_event_version_devil_fruit_entity CHECK (devil_fruit_entity_type = 'DEVIL_FRUIT'),
    PRIMARY KEY (version_id, devil_fruit_content_id),
    CONSTRAINT fk_event_version_devil_fruit FOREIGN KEY (devil_fruit_content_id, devil_fruit_entity_type)
        REFERENCES content (id, entity_type)
);

-- The events of a fruit (US 55), and what its retirement and its first appearance look up.
CREATE INDEX idx_event_version_devil_fruit_content_id ON event_version_devil_fruit (devil_fruit_content_id);
