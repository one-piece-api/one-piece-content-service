-- What a Devil Fruit Type gives and what it costs, per language (3.1). Nullable like the
-- description: a draft may leave them empty, and the versions written before stay as they are.
ALTER TABLE devil_fruit_type_version_translation
    ADD COLUMN advantages    VARCHAR(2000),
    ADD COLUMN disadvantages VARCHAR(2000);
