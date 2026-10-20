-- Bounty (implementation plan of the Bounty, B2, D1-D5): one item per wanted poster, keyed
-- by the poster's name and the amount together (the composite natural key of B1), with an
-- optional link to the bounty it replaces. Validity (active, superseded, inactive) is
-- derived from the links and the flag, never stored.

ALTER TABLE content
    DROP CONSTRAINT content_entity_type_check;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check
        CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT', 'SAGA', 'ARC', 'VOLUME', 'CHAPTER', 'EPISODE',
                               'BOUNTY'));

-- The key text and its slug are generated: null while the poster name or the amount is
-- missing (a concatenation with null is null). The links point to a content, not to one of
-- its versions, and their constant entity type keeps them from pointing to anything else;
-- no cascade. The image is stored like a fruit's (V10): no cascade, an image still used is
-- never deleted.
CREATE TABLE bounty_version
(
    version_id                          UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    poster_name                         VARCHAR(100),
    amount                              BIGINT
        CONSTRAINT ck_bounty_version_amount CHECK (amount >= 0),
    condition                           VARCHAR(16)
        CONSTRAINT ck_bounty_version_condition CHECK (condition IN ('DEAD_OR_ALIVE', 'ONLY_ALIVE')),
    inactive                            BOOLEAN     NOT NULL DEFAULT false,
    natural_key                         VARCHAR(130) GENERATED ALWAYS AS (poster_name || ' ' || amount::text) STORED,
    key_slug                            VARCHAR(200) GENERATED ALWAYS AS (slug_of(poster_name || ' ' || amount::text)) STORED,
    previous_content_id                 UUID,
    previous_entity_type                VARCHAR(32) NOT NULL DEFAULT 'BOUNTY'
        CONSTRAINT ck_bounty_version_previous_entity CHECK (previous_entity_type = 'BOUNTY'),
    image_id                            VARCHAR(64) REFERENCES image (id),
    first_appearance_chapter_id         UUID,
    first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_bounty_version_first_appearance_entity CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    first_appearance_episode_id         UUID,
    first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_bounty_version_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    CONSTRAINT fk_bounty_version_previous FOREIGN KEY (previous_content_id, previous_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_bounty_version_first_appearance FOREIGN KEY (first_appearance_chapter_id,
                                                               first_appearance_chapter_entity_type)
        REFERENCES content (id, entity_type),
    CONSTRAINT fk_bounty_version_first_appearance_episode FOREIGN KEY (first_appearance_episode_id,
                                                                       first_appearance_episode_entity_type)
        REFERENCES content (id, entity_type)
);

-- The uniqueness checks (key and slug), the successor of a bounty, the first appearances,
-- and whether any version still uses an image, asked on every replace or removal.
CREATE INDEX idx_bounty_version_natural_key ON bounty_version (natural_key);

CREATE INDEX idx_bounty_version_key_slug ON bounty_version (key_slug);

CREATE INDEX idx_bounty_version_previous_content_id ON bounty_version (previous_content_id);

CREATE INDEX idx_bounty_version_first_appearance ON bounty_version (first_appearance_chapter_id);

CREATE INDEX idx_bounty_version_first_appearance_episode ON bounty_version (first_appearance_episode_id);

CREATE INDEX idx_bounty_version_image_id ON bounty_version (image_id);

-- Its per-language texts. A row is absent for a language nothing was saved for yet.
CREATE TABLE bounty_version_translation
(
    version_id    UUID       NOT NULL REFERENCES bounty_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    description   VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);
