-- Reader time (implementation plan of the timeline, T7; ADR-0008): the chapter where the
-- reader first meets a Devil Fruit, a Devil Fruit Type or a subcategory of a type. Part
-- of the version, optional in a draft, required to go online for the fruit and the type
-- (the rules say so, not the schema). It points to the chapter's content, not to one of
-- its versions; the constant entity type in the composite foreign key keeps it from
-- pointing to anything else, as an arc of a chapter (V15). No cascade. The anime part (an
-- episode) is an open door: one more nullable column of the same shape.
ALTER TABLE devil_fruit_version
    ADD COLUMN first_appearance_chapter_id          UUID,
    ADD COLUMN first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_devil_fruit_version_first_appearance_entity
            CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    ADD CONSTRAINT fk_devil_fruit_version_first_appearance
        FOREIGN KEY (first_appearance_chapter_id, first_appearance_chapter_entity_type)
            REFERENCES content (id, entity_type);

ALTER TABLE devil_fruit_type_version
    ADD COLUMN first_appearance_chapter_id          UUID,
    ADD COLUMN first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_devil_fruit_type_version_first_appearance_entity
            CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    ADD CONSTRAINT fk_devil_fruit_type_version_first_appearance
        FOREIGN KEY (first_appearance_chapter_id, first_appearance_chapter_entity_type)
            REFERENCES content (id, entity_type);

-- Always optional for a subcategory (workflow 3.1.1).
ALTER TABLE devil_fruit_type_version_subcategory
    ADD COLUMN first_appearance_chapter_id          UUID,
    ADD COLUMN first_appearance_chapter_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHAPTER'
        CONSTRAINT ck_devil_fruit_type_version_subcategory_first_appearance_entity
            CHECK (first_appearance_chapter_entity_type = 'CHAPTER'),
    ADD CONSTRAINT fk_devil_fruit_type_version_subcategory_first_appearance
        FOREIGN KEY (first_appearance_chapter_id, first_appearance_chapter_entity_type)
            REFERENCES content (id, entity_type);

-- What a chapter's retirement looks up: the items online that first appear in it.
CREATE INDEX idx_devil_fruit_version_first_appearance ON devil_fruit_version (first_appearance_chapter_id);

CREATE INDEX idx_devil_fruit_type_version_first_appearance
    ON devil_fruit_type_version (first_appearance_chapter_id);

CREATE INDEX idx_devil_fruit_type_version_subcategory_first_appearance
    ON devil_fruit_type_version_subcategory (first_appearance_chapter_id);
