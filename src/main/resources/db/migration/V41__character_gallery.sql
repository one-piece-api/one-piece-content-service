-- Character gallery (workflow revision 17, 3.5.1; reveals plan R5, D6, D9, D11): the single
-- portrait becomes an ordered list of images, each an appearance (ASPETTO) or a form
-- (FORMA), with a window on the reader axis and a caption per language. At most 20, a
-- rule of the service. No data conversion: the content database restarts empty (D11).

-- The images of a version, in order. Each names a stored image: the foreign key keeps an
-- image in use from being deleted, and an index serves the lookups by image (who uses it,
-- whether it may be deleted). Rewritten in place position by position, as the epithets.
CREATE TABLE character_version_image
(
    id         UUID PRIMARY KEY,
    version_id UUID        NOT NULL REFERENCES character_version (version_id) ON DELETE CASCADE,
    position   INT         NOT NULL,
    image_id   VARCHAR(64) NOT NULL REFERENCES image (id),
    kind       VARCHAR(16) NOT NULL
        CONSTRAINT ck_character_version_image_kind CHECK (kind IN ('ASPETTO', 'FORMA')),
    CONSTRAINT uq_character_version_image_position UNIQUE (version_id, position)
);

CREATE INDEX idx_character_version_image_image_id ON character_version_image (image_id);

-- The caption of an image per language; a row is absent for a language nothing was
-- written in. The reference to the catalog keeps a language in use from being deleted.
CREATE TABLE character_version_image_translation
(
    gallery_image_id UUID         NOT NULL REFERENCES character_version_image (id) ON DELETE CASCADE,
    language_code    VARCHAR(8)   NOT NULL REFERENCES language (code),
    caption          VARCHAR(100) NOT NULL,
    PRIMARY KEY (gallery_image_id, language_code)
);

-- The four parts of the window, the shape of V38: a composite foreign key to the
-- chapter's or the episode's content kept to that entity by a constant entity type, no
-- cascade, each part indexed for what a chapter's or an episode's retirement looks up.
DO
$$
    DECLARE
        point TEXT;
        axis  TEXT;
    BEGIN
        FOREACH point IN ARRAY ARRAY ['discovered', 'ended']
            LOOP
                FOREACH axis IN ARRAY ARRAY ['chapter', 'episode']
                    LOOP
                        EXECUTE format('ALTER TABLE character_version_image
                            ADD COLUMN %1$s_%2$s_id UUID,
                            ADD COLUMN %1$s_%2$s_entity_type VARCHAR(32) NOT NULL DEFAULT %3$L
                                CONSTRAINT %4$I CHECK (%1$s_%2$s_entity_type = %3$L),
                            ADD CONSTRAINT %5$I FOREIGN KEY (%1$s_%2$s_id, %1$s_%2$s_entity_type)
                                REFERENCES content (id, entity_type)',
                                       point, axis, upper(axis),
                                       'ck_character_version_image_' || point || '_' || axis,
                                       'fk_character_version_image_' || point || '_' || axis);
                        EXECUTE format('CREATE INDEX %1$I ON character_version_image (%2$s_%3$s_id)
                            WHERE %2$s_%3$s_id IS NOT NULL',
                                       'idx_character_version_image_' || point || '_' || axis, point, axis);
                    END LOOP;
            END LOOP;
    END
$$;

-- The published interface keeps its columns until R6: the character's image_id is the
-- main portrait read without a filter (3.5.1, library Gallery.mainPortrait): the last
-- appearance without an end on any axis, in list order; else the last appearance. The
-- views reading the dropped column are dropped and created again; the default privileges
-- of V8 grant them to public-api again. Nothing reads them but public-api.
DROP VIEW published.character;
DROP VIEW published.image;

ALTER TABLE character_version DROP COLUMN image_id;

CREATE VIEW published.character AS
SELECT v.content_id     AS id,
       c.romaji_slug    AS slug,
       c.romaji,
       c.japanese_name,
       t.language_code  AS language,
       t.name,
       p.description,
       c.status,
       c.gender,
       c.birthday_month,
       c.birthday_day,
       (SELECT g.image_id
        FROM character_version_image g
        WHERE g.version_id = v.id
          AND g.kind = 'ASPETTO'
        ORDER BY (g.ended_chapter_id IS NULL AND g.ended_episode_id IS NULL) DESC, g.position DESC
        LIMIT 1)        AS image_id,
       v.updated_at     AS published_at,
       c.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       c.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug          AS first_appearance_episode_slug,
       fe.number        AS first_appearance_episode_number,
       fe.name          AS first_appearance_episode_title
FROM content_version v
         JOIN character_version c ON c.version_id = v.id
         JOIN character_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY cp.position) AS description
                             FROM character_version_paragraph cp
                                      JOIN character_version_paragraph_translation pt ON pt.paragraph_id = cp.id
                             WHERE cp.version_id = v.id
                               AND pt.language_code = t.language_code) p
         LEFT JOIN published.chapter fa
              ON fa.id = c.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = c.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

-- Every image of an online gallery is reachable, not only the main portrait (D12: images
-- are not filtered, a filtered response never lists the ones it hides).
CREATE VIEW published.image AS
SELECT i.id, i.content_type, i.bytes
FROM image i
WHERE EXISTS (SELECT 1
              FROM devil_fruit_version f
                       JOIN content_version v ON v.id = f.version_id
              WHERE f.image_id = i.id
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM volume_version o
                       JOIN content_version v ON v.id = o.version_id
              WHERE o.cover_image_id = i.id
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM bounty_version b
                       JOIN content_version v ON v.id = b.version_id
              WHERE b.image_id = i.id
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM affiliation_version a
                       JOIN content_version v ON v.id = a.version_id
              WHERE a.image_id = i.id
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM species_version s
                       JOIN content_version v ON v.id = s.version_id
              WHERE s.image_id = i.id
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM character_version_image g
                       JOIN content_version v ON v.id = g.version_id
              WHERE g.image_id = i.id
                AND v.status = 'PUBLISHED');
