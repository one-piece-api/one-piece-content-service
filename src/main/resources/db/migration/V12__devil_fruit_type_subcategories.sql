-- The subcategories of a Devil Fruit Type (implementation plan of the subcategories, S1):
-- part of the type's version, in order, each with a stable id the fruits point to. A row
-- has an id of its own, as a child of its version; the subcategory id is the same in every
-- version of the type that keeps it.
CREATE TABLE devil_fruit_type_version_subcategory
(
    id             UUID PRIMARY KEY,
    version_id     UUID NOT NULL REFERENCES devil_fruit_type_version (version_id) ON DELETE CASCADE,
    subcategory_id UUID NOT NULL,
    position       INT  NOT NULL,
    CONSTRAINT uq_devil_fruit_type_version_subcategory UNIQUE (version_id, subcategory_id)
);

-- Its per-language name and description (S3). The reference to the catalog is what keeps a
-- language in use from being deleted.
CREATE TABLE devil_fruit_type_version_subcategory_translation
(
    subcategory_row_id UUID       NOT NULL REFERENCES devil_fruit_type_version_subcategory (id) ON DELETE CASCADE,
    language_code      VARCHAR(8) NOT NULL REFERENCES language (code),
    name               VARCHAR(100),
    description        VARCHAR(2000),
    PRIMARY KEY (subcategory_row_id, language_code)
);

-- The subcategory of its type a fruit names, optional (S5). No foreign key: subcategories
-- belong to the type's versions, and the rules of S5-S7 keep the link valid.
ALTER TABLE devil_fruit_version
    ADD COLUMN subcategory_id UUID;
