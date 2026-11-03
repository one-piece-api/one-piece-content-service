-- Cleanup of the published interface (reveals plan R7, D6; ADR-0011/0013): public-api reads the
-- reader-axis views of V42 and no longer the compatibility columns. Dropped here: the joined
-- long texts (`description`, `advantages`, `disadvantages`) of the views that have paragraph
-- views, the character's `image_id` (the gallery view carries it), `former` and `marine_rank`
-- of the memberships, `former` of the fruit users, and the view of the last period's role.
-- Deploy order: public-api (reading none of them) before this migration.
-- The views are dropped and created again; the default privileges of V8 grant them again.

DROP VIEW published.character_affiliation_role;
DROP VIEW published.species;
DROP VIEW published.affiliation;
DROP VIEW published.bounty;
DROP VIEW published.devil_fruit;
DROP VIEW published.devil_fruit_type;
DROP VIEW published.character;
DROP VIEW published.saga;
DROP VIEW published.arc;
DROP VIEW published.character_affiliation;
DROP VIEW published.character_devil_fruit;

CREATE VIEW published.species AS
SELECT v.content_id     AS id,
       s.romaji_slug    AS slug,
       s.romaji,
       t.language_code  AS language,
       t.name,
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
         LEFT JOIN published.chapter fa
              ON fa.id = a.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = a.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

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
         LEFT JOIN published.chapter fa
              ON fa.id = d.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = d.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.saga AS
SELECT v.content_id    AS id,
       s.number_slug   AS slug,
       s.number,
       t.language_code AS language,
       t.title         AS name,
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
         LEFT JOIN published.saga_chapter_span cs ON cs.saga_id = v.content_id
         LEFT JOIN published.saga_episode_span es ON es.saga_id = v.content_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.arc AS
SELECT v.content_id     AS id,
       a.number_slug    AS slug,
       a.number,
       t.language_code  AS language,
       t.title          AS name,
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
         JOIN content_version sv ON sv.content_id = a.saga_content_id AND sv.status = 'PUBLISHED'
         JOIN saga_version sv_s ON sv_s.version_id = sv.id
         JOIN saga_version_translation sv_t ON sv_t.version_id = sv.id AND sv_t.language_code = t.language_code
         LEFT JOIN published.arc_chapter_span cs ON cs.arc_id = v.content_id
         LEFT JOIN published.arc_episode_span es ON es.arc_id = v.content_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character AS
SELECT v.content_id     AS id,
       c.romaji_slug    AS slug,
       c.romaji,
       c.japanese_name,
       t.language_code  AS language,
       t.name,
       c.status,
       c.gender,
       c.birthday_month,
       c.birthday_day,
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
         LEFT JOIN published.chapter fa
              ON fa.id = c.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = c.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character_affiliation AS
SELECT v.content_id             AS character_id,
       a.position,
       a.affiliation_content_id AS affiliation_id
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');

CREATE VIEW published.character_devil_fruit AS
SELECT v.content_id                 AS character_id,
       d.position,
       d.devil_fruit_content_id     AS devil_fruit_id,
       dc.number                    AS discovered_chapter,
       de.number                    AS discovered_episode,
       ec.number                    AS ended_chapter,
       ee.number                    AS ended_episode
FROM content_version v
         JOIN character_version_devil_fruit d ON d.version_id = v.id
         LEFT JOIN published.chapter_number dc ON dc.id = d.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = d.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = d.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = d.ended_episode_id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version fv
              WHERE fv.content_id = d.devil_fruit_content_id
                AND fv.status = 'PUBLISHED');
