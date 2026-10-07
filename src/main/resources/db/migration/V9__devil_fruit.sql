-- The Devil Fruit (implementation plan of the Devil Fruit, D1, D7): the second content
-- entity, with a required many-to-one link to a Devil Fruit Type.

-- A content of a Devil Fruit Type, and only of one, can be pointed to: the composite key
-- below makes the entity part of what a foreign key refers to.
ALTER TABLE content
    ADD CONSTRAINT uq_content_id_entity_type UNIQUE (id, entity_type);

-- The kinds of content, now two. The check created with the table (V2) names only the
-- first one; its name depends on the PostgreSQL version, so it is found, not guessed, and
-- the widened check keeps the name PostgreSQL gave it.
DO
$$
    DECLARE
        old_check record;
    BEGIN
        FOR old_check IN
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = 'content'::regclass
              AND contype = 'c'
              AND pg_get_constraintdef(oid) LIKE '%entity_type%'
            LOOP
                EXECUTE format('ALTER TABLE content DROP CONSTRAINT %I', old_check.conname);
            END LOOP;
    END
$$;

ALTER TABLE content
    ADD CONSTRAINT content_entity_type_check CHECK (entity_type IN ('DEVIL_FRUIT_TYPE', 'DEVIL_FRUIT'));

-- What a version of a Devil Fruit says outside translations: one row per version of that
-- entity, sharing its id. The type is part of the version (changing it is a new version,
-- reviewed) and points to the type's content, not to one of its versions (D1). Nullable:
-- an incomplete draft has none yet; the foreign key ignores a null. The entity type of the
-- link is a constant, so a fruit can never point to a fruit. No cascade: a type with
-- fruits linked cannot be deleted.
CREATE TABLE devil_fruit_version
(
    version_id       UUID PRIMARY KEY REFERENCES content_version (id) ON DELETE CASCADE,
    romaji           VARCHAR(100),
    romaji_slug      VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED,
    type_content_id  UUID,
    type_entity_type VARCHAR(32) NOT NULL DEFAULT 'DEVIL_FRUIT_TYPE'
        CONSTRAINT ck_devil_fruit_version_type_entity CHECK (type_entity_type = 'DEVIL_FRUIT_TYPE'),
    CONSTRAINT fk_devil_fruit_version_type FOREIGN KEY (type_content_id, type_entity_type)
        REFERENCES content (id, entity_type)
);

-- The fruits of a type, and the uniqueness check, which compares slugs.
CREATE INDEX idx_devil_fruit_version_type_content_id ON devil_fruit_version (type_content_id);

CREATE INDEX idx_devil_fruit_version_romaji_slug ON devil_fruit_version (romaji_slug);

-- Its per-language texts. A row is absent for a language nothing was saved for yet. The
-- reference to the catalog is what keeps a language in use from being deleted.
CREATE TABLE devil_fruit_version_translation
(
    version_id    UUID       NOT NULL REFERENCES devil_fruit_version (version_id) ON DELETE CASCADE,
    language_code VARCHAR(8) NOT NULL REFERENCES language (code),
    name          VARCHAR(100),
    description   VARCHAR(2000),
    advantages    VARCHAR(2000),
    disadvantages VARCHAR(2000),
    PRIMARY KEY (version_id, language_code)
);
