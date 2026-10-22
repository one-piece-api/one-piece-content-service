-- The memberships of a character (implementation plan of the Character, C8, D10, D14): its
-- affiliations in order, each with an optional role and, for the Navy only, a rank. Part of
-- the version, as the species: a row per entry, with an id of its own, the parent of the
-- translations of its role.

-- Each entry names an affiliation's content, kept to that entity by the constant entity
-- type of the composite foreign key. The rank is one of the 19 of the Navy, highest
-- first; that the affiliation is a Navy one is checked in Java, against its latest
-- approved version on save and its online one on publish. An affiliation appears once per
-- version; the check waits for the commit, since reordering the list rewrites it row by
-- row and may name an affiliation twice in between.
CREATE TABLE character_version_affiliation
(
    id                       UUID PRIMARY KEY,
    version_id               UUID        NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position                 INT         NOT NULL,
    affiliation_content_id   UUID        NOT NULL,
    affiliation_entity_type  VARCHAR(32) NOT NULL DEFAULT 'AFFILIATION'
        CONSTRAINT ck_character_version_affiliation_entity CHECK (affiliation_entity_type = 'AFFILIATION'),
    marine_rank              VARCHAR(32)
        CONSTRAINT ck_character_version_affiliation_marine_rank CHECK (marine_rank IN
            ('FLEET_ADMIRAL', 'ADMIRAL', 'VICE_ADMIRAL', 'REAR_ADMIRAL', 'COMMODORE', 'CAPTAIN', 'COMMANDER',
             'LIEUTENANT_COMMANDER', 'LIEUTENANT', 'LIEUTENANT_JUNIOR_GRADE', 'ENSIGN', 'WARRANT_OFFICER',
             'MASTER_CHIEF_PETTY_OFFICER', 'CHIEF_PETTY_OFFICER', 'PETTY_OFFICER', 'SEAMAN_FIRST_CLASS',
             'SEAMAN_APPRENTICE', 'SEAMAN_RECRUIT', 'CHORE_BOY')),
    former                   BOOLEAN     NOT NULL DEFAULT false,
    CONSTRAINT uq_character_version_affiliation_position UNIQUE (version_id, position),
    CONSTRAINT uq_character_version_affiliation UNIQUE (version_id, affiliation_content_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_character_version_affiliation FOREIGN KEY (affiliation_content_id, affiliation_entity_type)
        REFERENCES content (id, entity_type)
);

-- What an affiliation's retirement, its members and the list's filters look up.
CREATE INDEX idx_character_version_affiliation ON character_version_affiliation (affiliation_content_id);

-- The role of a membership per language; a row is absent for a language nothing was
-- written in. The reference to the catalog keeps a language in use from being deleted.
CREATE TABLE character_version_affiliation_translation
(
    membership_id UUID         NOT NULL REFERENCES character_version_affiliation (id) ON DELETE CASCADE,
    language_code VARCHAR(8)   NOT NULL REFERENCES language (code),
    role          VARCHAR(100) NOT NULL,
    PRIMARY KEY (membership_id, language_code)
);
