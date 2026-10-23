-- The relatives of a character (implementation plan of the Character, C12, D13, D14).

-- The relatives of a character in order, each with the kind of relative it is to the
-- character: "relative is the kind of this character". Part of the version, as the
-- species. Each entry names a character's content, kept to that entity by the constant
-- entity type of the composite foreign key. A relative appears once per version; the
-- check waits for the commit, since reordering the list rewrites it row by row and may
-- name a relative twice in between. That a pair is linked once, by one of the two
-- characters, is checked in Java: it spans two versions.
CREATE TABLE character_version_relative
(
    version_id           UUID        NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position             INT         NOT NULL,
    relative_content_id  UUID        NOT NULL,
    relative_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHARACTER'
        CONSTRAINT ck_character_version_relative_entity CHECK (relative_entity_type = 'CHARACTER'),
    kind                 VARCHAR(32) NOT NULL
        CONSTRAINT ck_character_version_relative_kind CHECK (kind IN (
            'PARENT', 'CHILD', 'GRANDPARENT', 'GRANDCHILD', 'UNCLE_AUNT', 'NEPHEW_NIECE',
            'ADOPTIVE_PARENT', 'ADOPTIVE_CHILD', 'SIBLING', 'COUSIN', 'SPOUSE', 'SWORN_SIBLING')),
    PRIMARY KEY (version_id, position),
    CONSTRAINT uq_character_version_relative UNIQUE (version_id, relative_content_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_character_version_relative FOREIGN KEY (relative_content_id, relative_entity_type)
        REFERENCES content (id, entity_type)
);

-- What a character's retirement and the characters naming it as relative look up.
CREATE INDEX idx_character_version_relative ON character_version_relative (relative_content_id);
