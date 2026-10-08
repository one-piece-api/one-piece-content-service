-- Images of contents (implementation plan of the Devil Fruit, D4): stored in the database,
-- addressed by the SHA-256 of their normalized bytes, so one upload sent twice - or the
-- same image in two versions - is one row.
CREATE TABLE image
(
    id           VARCHAR(64) PRIMARY KEY
        CONSTRAINT ck_image_id_sha256 CHECK (id ~ '^[0-9a-f]{64}$'),
    content_type VARCHAR(64) NOT NULL,
    width        INTEGER     NOT NULL CHECK (width > 0),
    height       INTEGER     NOT NULL CHECK (height > 0),
    size_bytes   INTEGER     NOT NULL CHECK (size_bytes > 0),
    bytes        BYTEA       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

-- The image of a version of a Devil Fruit, optional. No cascade: an image still used by a
-- version cannot be deleted; the service deletes it once no version uses it (D5).
ALTER TABLE devil_fruit_version
    ADD COLUMN image_id VARCHAR(64) REFERENCES image (id);

-- Whether any version still uses an image, asked on every replace or removal.
CREATE INDEX idx_devil_fruit_version_image_id ON devil_fruit_version (image_id);
