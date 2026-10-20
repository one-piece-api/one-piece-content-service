-- First appearance, anime part (ADR-0008): the episode where the viewer first meets a
-- Devil Fruit, a Devil Fruit Type or a subcategory of a type, next to the manga chapter
-- of V17. Each part is optional and independent; at least one of the two is required to
-- submit and to go online for the fruit and the type (the rules say so, not the schema).
-- Same shape as V17: a composite foreign key to the episode's content, kept by a constant
-- entity type. No cascade.
ALTER TABLE devil_fruit_version
    ADD COLUMN first_appearance_episode_id          UUID,
    ADD COLUMN first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_devil_fruit_version_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    ADD CONSTRAINT fk_devil_fruit_version_first_appearance_episode
        FOREIGN KEY (first_appearance_episode_id, first_appearance_episode_entity_type)
            REFERENCES content (id, entity_type);

ALTER TABLE devil_fruit_type_version
    ADD COLUMN first_appearance_episode_id          UUID,
    ADD COLUMN first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_devil_fruit_type_version_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    ADD CONSTRAINT fk_devil_fruit_type_version_first_appearance_episode
        FOREIGN KEY (first_appearance_episode_id, first_appearance_episode_entity_type)
            REFERENCES content (id, entity_type);

ALTER TABLE devil_fruit_type_version_subcategory
    ADD COLUMN first_appearance_episode_id          UUID,
    ADD COLUMN first_appearance_episode_entity_type VARCHAR(32) NOT NULL DEFAULT 'EPISODE'
        CONSTRAINT ck_devil_fruit_type_version_subcategory_first_appearance_episode_entity
            CHECK (first_appearance_episode_entity_type = 'EPISODE'),
    ADD CONSTRAINT fk_devil_fruit_type_version_subcategory_first_appearance_episode
        FOREIGN KEY (first_appearance_episode_id, first_appearance_episode_entity_type)
            REFERENCES content (id, entity_type);

-- What an episode's retirement looks up: the items online that first appear in it.
CREATE INDEX idx_devil_fruit_version_first_appearance_episode
    ON devil_fruit_version (first_appearance_episode_id);

CREATE INDEX idx_devil_fruit_type_version_first_appearance_episode
    ON devil_fruit_type_version (first_appearance_episode_id);

CREATE INDEX idx_devil_fruit_type_version_subcategory_first_appearance_episode
    ON devil_fruit_type_version_subcategory (first_appearance_episode_id);
