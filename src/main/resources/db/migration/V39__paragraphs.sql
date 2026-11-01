-- Paragraphs (workflow revision 17, 3.7.3; reveals plan R3, D7, D9): every long text of
-- Species, Affiliation, Bounty, Devil Fruit, Devil Fruit Type, Character, Saga and Arc
-- becomes a list of paragraphs, each localized and with a window on the reader axis.
-- One table per entity with a section column: the sections of a version are one ordered
-- list, the order of a section is the order of its rows. No data conversion: the content
-- database restarts empty (D11).

-- A paragraph table and its texts per entity, each paragraph with the four parts of its
-- window, the shape of V38: a composite foreign key to the chapter's or the episode's
-- content kept to that entity by a constant entity type, no cascade, each part indexed
-- for what a chapter's or an episode's retirement looks up.
DO
$$
    DECLARE
        entity   TEXT;
        sections TEXT;
        point    TEXT;
        axis     TEXT;
    BEGIN
        FOREACH entity IN ARRAY ARRAY ['species', 'affiliation', 'bounty', 'devil_fruit', 'devil_fruit_type',
            'character', 'saga', 'arc']
            LOOP
                sections := CASE
                                WHEN entity IN ('devil_fruit', 'devil_fruit_type')
                                    THEN $s$('DESCRIPTION', 'ADVANTAGES', 'DISADVANTAGES')$s$
                                ELSE $s$('DESCRIPTION')$s$
                    END;
                EXECUTE format('CREATE TABLE %1$I
                    (
                        id         UUID PRIMARY KEY,
                        version_id UUID        NOT NULL REFERENCES %2$I (version_id) ON DELETE CASCADE,
                        position   INT         NOT NULL,
                        section    VARCHAR(16) NOT NULL CONSTRAINT %3$I CHECK (section IN %4$s),
                        CONSTRAINT %5$I UNIQUE (version_id, position)
                    )',
                               entity || '_version_paragraph', entity || '_version',
                               'ck_' || entity || '_version_paragraph_section', sections,
                               'uq_' || entity || '_version_paragraph_position');
                -- A row is absent for a language nothing was written in; the reference to
                -- the catalog keeps a language in use from being deleted.
                EXECUTE format('CREATE TABLE %1$I
                    (
                        paragraph_id  UUID          NOT NULL REFERENCES %2$I (id) ON DELETE CASCADE,
                        language_code VARCHAR(8)    NOT NULL REFERENCES language (code),
                        text          VARCHAR(2000) NOT NULL,
                        PRIMARY KEY (paragraph_id, language_code)
                    )',
                               entity || '_version_paragraph_translation', entity || '_version_paragraph');
                FOREACH point IN ARRAY ARRAY ['discovered', 'ended']
                    LOOP
                        FOREACH axis IN ARRAY ARRAY ['chapter', 'episode']
                            LOOP
                                EXECUTE format('ALTER TABLE %1$I
                                    ADD COLUMN %2$s_%3$s_id UUID,
                                    ADD COLUMN %2$s_%3$s_entity_type VARCHAR(32) NOT NULL DEFAULT %4$L
                                        CONSTRAINT %5$I CHECK (%2$s_%3$s_entity_type = %4$L),
                                    ADD CONSTRAINT %6$I FOREIGN KEY (%2$s_%3$s_id, %2$s_%3$s_entity_type)
                                        REFERENCES content (id, entity_type)',
                                               entity || '_version_paragraph', point, axis, upper(axis),
                                               'ck_' || entity || '_version_paragraph_' || point || '_' || axis,
                                               'fk_' || entity || '_version_paragraph_' || point || '_' || axis);
                                EXECUTE format('CREATE INDEX %1$I ON %2$I (%3$s_%4$s_id) WHERE %3$s_%4$s_id IS NOT NULL',
                                               'idx_' || entity || '_version_paragraph_' || point || '_' || axis,
                                               entity || '_version_paragraph', point, axis);
                            END LOOP;
                    END LOOP;
            END LOOP;
    END
$$;

-- The published interface keeps its columns until R6 (R3, decision 1): each long text is
-- every paragraph of its section joined in order, blank line between, unfiltered by the
-- windows. The type of the column becomes text, so the views are dropped and created
-- again; the default privileges of V8 grant them to public-api again. Nothing reads them
-- but public-api.
DROP VIEW published.species;
DROP VIEW published.affiliation;
DROP VIEW published.bounty;
DROP VIEW published.devil_fruit;
DROP VIEW published.devil_fruit_type;
DROP VIEW published.character;
DROP VIEW published.saga;
DROP VIEW published.arc;

ALTER TABLE species_version_translation DROP COLUMN description;
ALTER TABLE affiliation_version_translation DROP COLUMN description;
ALTER TABLE character_version_translation DROP COLUMN description;
ALTER TABLE devil_fruit_version_translation
    DROP COLUMN description,
    DROP COLUMN advantages,
    DROP COLUMN disadvantages;
ALTER TABLE devil_fruit_type_version_translation
    DROP COLUMN description,
    DROP COLUMN advantages,
    DROP COLUMN disadvantages;
ALTER TABLE saga_version_translation DROP COLUMN description;
ALTER TABLE arc_version_translation DROP COLUMN description;
-- A bounty had only its description to localize.
DROP TABLE bounty_version_translation;

CREATE VIEW published.species AS
SELECT v.content_id     AS id,
       s.romaji_slug    AS slug,
       s.romaji,
       t.language_code  AS language,
       t.name,
       p.description,
       s.image_id,
       v.updated_at     AS published_at,
       s.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       s.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug          AS first_appearance_episode_slug,
       fe.number        AS first_appearance_episode_number,
       fe.name          AS first_appearance_episode_title
FROM content_version v
         JOIN species_version s ON s.version_id = v.id
         JOIN species_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY sp.position) AS description
                             FROM species_version_paragraph sp
                                      JOIN species_version_paragraph_translation pt ON pt.paragraph_id = sp.id
                             WHERE sp.version_id = v.id
                               AND pt.language_code = t.language_code) p
         LEFT JOIN published.chapter fa
              ON fa.id = s.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = s.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.affiliation AS
SELECT v.content_id     AS id,
       a.romaji_slug    AS slug,
       a.romaji,
       t.language_code  AS language,
       t.name,
       p.description,
       a.image_id,
       v.updated_at     AS published_at,
       a.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       a.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug          AS first_appearance_episode_slug,
       fe.number        AS first_appearance_episode_number,
       fe.name          AS first_appearance_episode_title,
       a.kind
FROM content_version v
         JOIN affiliation_version a ON a.version_id = v.id
         JOIN affiliation_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY ap.position) AS description
                             FROM affiliation_version_paragraph ap
                                      JOIN affiliation_version_paragraph_translation pt ON pt.paragraph_id = ap.id
                             WHERE ap.version_id = v.id
                               AND pt.language_code = t.language_code) p
         LEFT JOIN published.chapter fa
              ON fa.id = a.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = a.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

-- A row per language its paragraphs are written in: a bounty has no other localized field.
CREATE VIEW published.bounty AS
SELECT v.content_id     AS id,
       b.key_slug       AS slug,
       b.poster_name,
       b.amount,
       b.condition,
       CASE
           WHEN b.inactive THEN 'INACTIVE'
           WHEN EXISTS (SELECT 1
                        FROM content_version sv
                                 JOIN bounty_version s ON s.version_id = sv.id
                        WHERE sv.status = 'PUBLISHED'
                          AND s.previous_content_id = v.content_id) THEN 'SUPERSEDED'
           ELSE 'ACTIVE'
           END          AS validity,
       b.previous_content_id AS previous_id,
       b.image_id,
       t.language_code  AS language,
       b.natural_key    AS name,
       p.description,
       v.updated_at     AS published_at,
       b.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       b.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug          AS first_appearance_episode_slug,
       fe.number        AS first_appearance_episode_number,
       fe.name          AS first_appearance_episode_title,
       b.holder_content_id AS holder_id
FROM content_version v
         JOIN bounty_version b ON b.version_id = v.id
         JOIN LATERAL (SELECT DISTINCT pt.language_code
                       FROM bounty_version_paragraph bp
                                JOIN bounty_version_paragraph_translation pt ON pt.paragraph_id = bp.id
                       WHERE bp.version_id = v.id) t ON true
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY bp.position) AS description
                             FROM bounty_version_paragraph bp
                                      JOIN bounty_version_paragraph_translation pt ON pt.paragraph_id = bp.id
                             WHERE bp.version_id = v.id
                               AND pt.language_code = t.language_code) p
         LEFT JOIN published.chapter fa
              ON fa.id = b.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = b.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.devil_fruit AS
SELECT v.content_id     AS id,
       f.romaji_slug    AS slug,
       f.romaji,
       t.language_code  AS language,
       t.name,
       p.description,
       p.advantages,
       p.disadvantages,
       f.type_content_id AS type_id,
       d.romaji_slug    AS type_slug,
       d.romaji         AS type_romaji,
       dt.name          AS type_name,
       f.image_id,
       v.updated_at     AS published_at,
       s.subcategory_id AS subcategory_id,
       st.name          AS subcategory_name,
       st.description   AS subcategory_description,
       f.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       f.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug AS first_appearance_episode_slug,
       fe.number AS first_appearance_episode_number,
       fe.name AS first_appearance_episode_title,
       f.multiple_users
FROM content_version v
         JOIN devil_fruit_version f ON f.version_id = v.id
         JOIN devil_fruit_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY fp.position)
                                    FILTER (WHERE fp.section = 'DESCRIPTION')   AS description,
                                    string_agg(pt.text, E'\n\n' ORDER BY fp.position)
                                    FILTER (WHERE fp.section = 'ADVANTAGES')    AS advantages,
                                    string_agg(pt.text, E'\n\n' ORDER BY fp.position)
                                    FILTER (WHERE fp.section = 'DISADVANTAGES') AS disadvantages
                             FROM devil_fruit_version_paragraph fp
                                      JOIN devil_fruit_version_paragraph_translation pt ON pt.paragraph_id = fp.id
                             WHERE fp.version_id = v.id
                               AND pt.language_code = t.language_code) p
         JOIN content_version tv ON tv.content_id = f.type_content_id AND tv.status = 'PUBLISHED'
         JOIN devil_fruit_type_version d ON d.version_id = tv.id
         JOIN devil_fruit_type_version_translation dt
              ON dt.version_id = tv.id AND dt.language_code = t.language_code
         LEFT JOIN (devil_fruit_type_version_subcategory s
             JOIN devil_fruit_type_version_subcategory_translation st ON st.subcategory_row_id = s.id)
              ON s.version_id = tv.id AND s.subcategory_id = f.subcategory_id
                  AND st.language_code = t.language_code
         LEFT JOIN published.chapter fa
              ON fa.id = f.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = f.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.devil_fruit_type AS
SELECT v.content_id    AS id,
       d.romaji_slug   AS slug,
       d.romaji,
       t.language_code AS language,
       t.name,
       p.description,
       p.advantages,
       p.disadvantages,
       v.updated_at    AS published_at,
       d.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug         AS first_appearance_chapter_slug,
       fa.number       AS first_appearance_chapter_number,
       fa.name         AS first_appearance_chapter_title,
       d.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug AS first_appearance_episode_slug,
       fe.number AS first_appearance_episode_number,
       fe.name AS first_appearance_episode_title
FROM content_version v
         JOIN devil_fruit_type_version d ON d.version_id = v.id
         JOIN devil_fruit_type_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY tp.position)
                                    FILTER (WHERE tp.section = 'DESCRIPTION')   AS description,
                                    string_agg(pt.text, E'\n\n' ORDER BY tp.position)
                                    FILTER (WHERE tp.section = 'ADVANTAGES')    AS advantages,
                                    string_agg(pt.text, E'\n\n' ORDER BY tp.position)
                                    FILTER (WHERE tp.section = 'DISADVANTAGES') AS disadvantages
                             FROM devil_fruit_type_version_paragraph tp
                                      JOIN devil_fruit_type_version_paragraph_translation pt ON pt.paragraph_id = tp.id
                             WHERE tp.version_id = v.id
                               AND pt.language_code = t.language_code) p
         LEFT JOIN published.chapter fa
              ON fa.id = d.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = d.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

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
       c.image_id,
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

CREATE VIEW published.saga AS
SELECT v.content_id    AS id,
       s.number_slug   AS slug,
       s.number,
       t.language_code AS language,
       t.title         AS name,
       p.description,
       v.updated_at    AS published_at,
       coalesce(cs.item_count, 0) AS chapter_count,
       cs.first_number AS first_chapter_number,
       cs.last_number  AS last_chapter_number,
       cs.first_date   AS first_chapter_date,
       cs.last_date    AS last_chapter_date,
       coalesce(es.item_count, 0) AS episode_count,
       es.first_number AS first_episode_number,
       es.last_number  AS last_episode_number,
       es.first_date   AS first_episode_date,
       es.last_date    AS last_episode_date
FROM content_version v
         JOIN saga_version s ON s.version_id = v.id
         JOIN saga_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY sp.position) AS description
                             FROM saga_version_paragraph sp
                                      JOIN saga_version_paragraph_translation pt ON pt.paragraph_id = sp.id
                             WHERE sp.version_id = v.id
                               AND pt.language_code = t.language_code) p
         LEFT JOIN published.saga_chapter_span cs ON cs.saga_id = v.content_id
         LEFT JOIN published.saga_episode_span es ON es.saga_id = v.content_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.arc AS
SELECT v.content_id     AS id,
       a.number_slug    AS slug,
       a.number,
       t.language_code  AS language,
       t.title          AS name,
       p.description,
       a.saga_content_id AS saga_id,
       sv_s.number_slug AS saga_slug,
       sv_s.number      AS saga_number,
       sv_t.title       AS saga_title,
       v.updated_at     AS published_at,
       coalesce(cs.item_count, 0) AS chapter_count,
       cs.first_number  AS first_chapter_number,
       cs.last_number   AS last_chapter_number,
       cs.first_date    AS first_chapter_date,
       cs.last_date     AS last_chapter_date,
       coalesce(es.item_count, 0) AS episode_count,
       es.first_number  AS first_episode_number,
       es.last_number   AS last_episode_number,
       es.first_date    AS first_episode_date,
       es.last_date     AS last_episode_date
FROM content_version v
         JOIN arc_version a ON a.version_id = v.id
         JOIN arc_version_translation t ON t.version_id = v.id
         CROSS JOIN LATERAL (SELECT string_agg(pt.text, E'\n\n' ORDER BY ap.position) AS description
                             FROM arc_version_paragraph ap
                                      JOIN arc_version_paragraph_translation pt ON pt.paragraph_id = ap.id
                             WHERE ap.version_id = v.id
                               AND pt.language_code = t.language_code) p
         JOIN content_version sv ON sv.content_id = a.saga_content_id AND sv.status = 'PUBLISHED'
         JOIN saga_version sv_s ON sv_s.version_id = sv.id
         JOIN saga_version_translation sv_t ON sv_t.version_id = sv.id AND sv_t.language_code = t.language_code
         LEFT JOIN published.arc_chapter_span cs ON cs.arc_id = v.content_id
         LEFT JOIN published.arc_episode_span es ON es.arc_id = v.content_id
WHERE v.status = 'PUBLISHED';
