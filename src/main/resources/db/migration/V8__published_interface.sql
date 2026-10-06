-- The published interface read by one-piece-public-api (docs/implementation-plan-public-api.md
-- D3, D5, D13): a schema of views showing only what is online, the slug of every content,
-- and a counter that moves whenever what is online changes. See
-- docs/adr/0003-published-interface.md.

-- The slug of a romaji (flows document 3.3): lowercase, accents and macrons dropped (NFKD,
-- then the combining marks removed), every other run of non-alphanumerics turned into one
-- hyphen, none at either end. Null when nothing is left. The one definition of a slug:
-- the generated column below, the uniqueness check and the slug history all use it.
CREATE FUNCTION slug_of(text) RETURNS VARCHAR
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
RETURN nullif(btrim(regexp_replace(lower(regexp_replace(normalize($1, NFKD), '[̀-ͯ]', '', 'g')),
                                   '[^a-z0-9]+', '-', 'g'), '-'), '');

-- The slug each version's romaji gives. Generated, so it can never disagree with the romaji;
-- indexed for the uniqueness check, which compares slugs.
ALTER TABLE devil_fruit_type_version
    ADD COLUMN romaji_slug VARCHAR(200) GENERATED ALWAYS AS (slug_of(romaji)) STORED;

CREATE INDEX idx_devil_fruit_type_version_romaji_slug ON devil_fruit_type_version (romaji_slug);

-- Every slug a content has had online, the current one included. Written when a version
-- goes online (publish or restore); never deleted, so an old slug keeps pointing to its
-- content and stays reserved to it. Unique per entity type: each type has its own addresses.
CREATE TABLE content_slug
(
    entity_type VARCHAR(32)  NOT NULL,
    slug        VARCHAR(200) NOT NULL,
    content_id  UUID         NOT NULL REFERENCES content (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (entity_type, slug)
);

CREATE INDEX idx_content_slug_content_id ON content_slug (content_id);

-- What is online today gets its slug. Fails if two contents online share a slug: that
-- data must be corrected before the public API can address them.
INSERT INTO content_slug (entity_type, slug, content_id, assigned_at)
SELECT c.entity_type, d.romaji_slug, c.id, v.updated_at
FROM content_version v
         JOIN content c ON c.id = v.content_id
         JOIN devil_fruit_type_version d ON d.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND d.romaji_slug IS NOT NULL;

-- One number that changes whenever what is online changes: the public API's ETag. A single
-- row, forced by the always-true key.
CREATE TABLE published_revision
(
    id         BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    revision   BIGINT      NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

INSERT INTO published_revision (revision, changed_at)
VALUES (1, now());

CREATE FUNCTION bump_published_revision() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE published_revision SET revision = revision + 1, changed_at = now();
    RETURN NULL;
END
$$;

-- Moved by the database, not by the services: every path that puts a version online or
-- takes it offline - publish, restore, retire, supersede, an administrator's override, the
-- entities still to come - is covered, none can forget it. Published versions never change
-- their content (4.1), so their status is all there is to watch.
CREATE TRIGGER trg_content_version_online_inserted
    AFTER INSERT
    ON content_version
    FOR EACH ROW
    WHEN (NEW.status = 'PUBLISHED')
EXECUTE FUNCTION bump_published_revision();

CREATE TRIGGER trg_content_version_online_changed
    AFTER UPDATE OF status
    ON content_version
    FOR EACH ROW
    WHEN ((OLD.status = 'PUBLISHED') <> (NEW.status = 'PUBLISHED'))
EXECUTE FUNCTION bump_published_revision();

CREATE TRIGGER trg_content_version_online_deleted
    AFTER DELETE
    ON content_version
    FOR EACH ROW
    WHEN (OLD.status = 'PUBLISHED')
EXECUTE FUNCTION bump_published_revision();

-- The public languages are the catalog (public API plan, D6).
CREATE TRIGGER trg_language_changed
    AFTER INSERT OR UPDATE OR DELETE
    ON language
    FOR EACH STATEMENT
EXECUTE FUNCTION bump_published_revision();

-- The schema the public API reads, and nothing else. Its views run with this service's
-- rights, so the reader needs no access to the tables behind them.
CREATE SCHEMA published;

-- One row per Devil Fruit Type online and language it is written in. A published version is
-- never changed, so its last update is the moment it went online.
CREATE VIEW published.devil_fruit_type AS
SELECT v.content_id    AS id,
       d.romaji_slug   AS slug,
       d.romaji,
       t.language_code AS language,
       t.name,
       t.description,
       t.advantages,
       t.disadvantages,
       v.updated_at    AS published_at
FROM content_version v
         JOIN devil_fruit_type_version d ON d.version_id = v.id
         JOIN devil_fruit_type_version_translation t ON t.version_id = v.id
WHERE v.status = 'PUBLISHED';

-- Every slug, current or old, of the contents online: nothing about a content that is not.
CREATE VIEW published.content_slug AS
SELECT s.entity_type, s.slug, s.content_id
FROM content_slug s
WHERE EXISTS (SELECT 1 FROM content_version v WHERE v.content_id = s.content_id AND v.status = 'PUBLISHED');

CREATE VIEW published.language AS
SELECT code, name
FROM language;

CREATE VIEW published.revision AS
SELECT revision
FROM published_revision;

-- The reader role is created by onepiece-infrastructure (login, password, limits); this
-- service only says what it may read - this schema, today's views and tomorrow's.
GRANT USAGE ON SCHEMA published TO public_api_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA published TO public_api_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA published GRANT SELECT ON TABLES TO public_api_reader;
