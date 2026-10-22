-- The Devil Fruits of a character and the fruits shared by several users (implementation
-- plan of the Character, C10, D12, D14).

-- A fruit with more than one current user, as the SMILE fruits. Part of the version: a
-- different value is a different version. Versions written before it existed keep false.
ALTER TABLE devil_fruit_version
    ADD COLUMN multiple_users BOOLEAN NOT NULL DEFAULT false;

-- The fruits of a character in order, each with a former flag: the fruit it ate, or no
-- longer eats. Part of the version, as the species. Each entry names a fruit's content,
-- kept to that entity by the constant entity type of the composite foreign key. A fruit
-- appears once per version; the check waits for the commit, since reordering the list
-- rewrites it row by row and may name a fruit twice in between. That a fruit has one
-- current user is checked in Java, on publish and restore only.
CREATE TABLE character_version_devil_fruit
(
    version_id              UUID        NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position                INT         NOT NULL,
    devil_fruit_content_id  UUID        NOT NULL,
    devil_fruit_entity_type VARCHAR(32) NOT NULL DEFAULT 'DEVIL_FRUIT'
        CONSTRAINT ck_character_version_devil_fruit_entity CHECK (devil_fruit_entity_type = 'DEVIL_FRUIT'),
    former                  BOOLEAN     NOT NULL DEFAULT false,
    PRIMARY KEY (version_id, position),
    CONSTRAINT uq_character_version_devil_fruit UNIQUE (version_id, devil_fruit_content_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_character_version_devil_fruit FOREIGN KEY (devil_fruit_content_id, devil_fruit_entity_type)
        REFERENCES content (id, entity_type)
);

-- What a fruit's retirement, its users and the list's filter look up.
CREATE INDEX idx_character_version_devil_fruit ON character_version_devil_fruit (devil_fruit_content_id);
