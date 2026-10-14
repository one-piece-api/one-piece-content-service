-- The story structure in the published interface (implementation plan of the story
-- structure, S7, S9, S13, step SS6). Grants come from the default privileges of V8.
--
-- Same shape as the Devil Fruit views (V11): one row per content online and language it is
-- written in, the slug being the number itself (V14, V15), the title read as "name" (what
-- the public API searches and sorts) and the summary of what the content points to, one
-- level up, in the same language. Derived values (S7) are never stored: they are computed
-- here, from the contents online only, by the span views below.

-- The release days of the volumes, chapters and episodes online, one per country (S13): the
-- full list, whatever the country.
CREATE VIEW published.volume_release AS
SELECT v.content_id AS volume_id,
       r.country,
       r.release_date
FROM content_version v
         JOIN volume_version_release r ON r.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.chapter_release AS
SELECT v.content_id AS chapter_id,
       r.country,
       r.release_date
FROM content_version v
         JOIN chapter_version_release r ON r.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.episode_broadcast AS
SELECT v.content_id AS episode_id,
       b.country,
       b.broadcast_date
FROM content_version v
         JOIN episode_version_broadcast b ON b.version_id = v.id
WHERE v.status = 'PUBLISHED';

-- Derived values (S7): the chapters and episodes online of an arc and of a volume - how
-- many, first and last number - and the first and last Japanese ('JP', the original
-- release) day of those that have one. Language-neutral, one row per parent that has at
-- least one.
CREATE VIEW published.arc_chapter_span AS
SELECT c.arc_content_id AS arc_id,
       count(*)::integer AS item_count,
       min(c.number)     AS first_number,
       max(c.number)     AS last_number,
       min(r.release_date) AS first_date,
       max(r.release_date) AS last_date
FROM content_version v
         JOIN chapter_version c ON c.version_id = v.id
         LEFT JOIN chapter_version_release r ON r.version_id = v.id AND r.country = 'JP'
WHERE v.status = 'PUBLISHED'
GROUP BY c.arc_content_id;

CREATE VIEW published.arc_episode_span AS
SELECT e.arc_content_id AS arc_id,
       count(*)::integer AS item_count,
       min(e.number)     AS first_number,
       max(e.number)     AS last_number,
       min(b.broadcast_date) AS first_date,
       max(b.broadcast_date) AS last_date
FROM content_version v
         JOIN episode_version e ON e.version_id = v.id
         LEFT JOIN episode_version_broadcast b ON b.version_id = v.id AND b.country = 'JP'
WHERE v.status = 'PUBLISHED'
  AND e.arc_content_id IS NOT NULL
GROUP BY e.arc_content_id;

CREATE VIEW published.volume_chapter_span AS
SELECT c.volume_content_id AS volume_id,
       count(*)::integer AS item_count,
       min(c.number)     AS first_number,
       max(c.number)     AS last_number,
       min(r.release_date) AS first_date,
       max(r.release_date) AS last_date
FROM content_version v
         JOIN chapter_version c ON c.version_id = v.id
         LEFT JOIN chapter_version_release r ON r.version_id = v.id AND r.country = 'JP'
WHERE v.status = 'PUBLISHED'
  AND c.volume_content_id IS NOT NULL
GROUP BY c.volume_content_id;

-- A saga's span is its arcs' spans put together.
CREATE VIEW published.saga_chapter_span AS
SELECT a.saga_content_id AS saga_id,
       sum(s.item_count)::integer AS item_count,
       min(s.first_number) AS first_number,
       max(s.last_number)  AS last_number,
       min(s.first_date)   AS first_date,
       max(s.last_date)    AS last_date
FROM content_version v
         JOIN arc_version a ON a.version_id = v.id
         JOIN published.arc_chapter_span s ON s.arc_id = v.content_id
WHERE v.status = 'PUBLISHED'
GROUP BY a.saga_content_id;

CREATE VIEW published.saga_episode_span AS
SELECT a.saga_content_id AS saga_id,
       sum(s.item_count)::integer AS item_count,
       min(s.first_number) AS first_number,
       max(s.last_number)  AS last_number,
       min(s.first_date)   AS first_date,
       max(s.last_date)    AS last_date
FROM content_version v
         JOIN arc_version a ON a.version_id = v.id
         JOIN published.arc_episode_span s ON s.arc_id = v.content_id
WHERE v.status = 'PUBLISHED'
GROUP BY a.saga_content_id;

CREATE VIEW published.saga AS
SELECT v.content_id    AS id,
       s.number_slug   AS slug,
       s.number,
       t.language_code AS language,
       t.title         AS name,
       t.description,
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

-- An arc with the summary of its saga, online too (S5); the inner join keeps an arc out of
-- a language its saga is not written in, should the data ever allow it.
CREATE VIEW published.arc AS
SELECT v.content_id     AS id,
       a.number_slug    AS slug,
       a.number,
       t.language_code  AS language,
       t.title          AS name,
       t.description,
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

-- A volume: no arc and no description. Its chapters' span is derived.
CREATE VIEW published.volume AS
SELECT v.content_id     AS id,
       o.number_slug    AS slug,
       o.number,
       t.language_code  AS language,
       t.title          AS name,
       o.japanese_title,
       o.cover_image_id,
       v.updated_at     AS published_at,
       coalesce(cs.item_count, 0) AS chapter_count,
       cs.first_number  AS first_chapter_number,
       cs.last_number   AS last_chapter_number,
       cs.first_date    AS first_chapter_date,
       cs.last_date     AS last_chapter_date
FROM content_version v
         JOIN volume_version o ON o.version_id = v.id
         JOIN volume_version_translation t ON t.version_id = v.id
         LEFT JOIN published.volume_chapter_span cs ON cs.volume_id = v.content_id
WHERE v.status = 'PUBLISHED';

-- A chapter with the summary of its arc (online, S5) and of its volume, if it has one. A
-- volume with no text in the language leaves the summary null: the chapter stays.
CREATE VIEW published.chapter AS
SELECT v.content_id     AS id,
       c.number_slug    AS slug,
       c.number,
       t.language_code  AS language,
       t.title          AS name,
       t.description,
       c.japanese_title,
       c.page_count,
       c.arc_content_id AS arc_id,
       av_a.number_slug AS arc_slug,
       av_a.number      AS arc_number,
       av_t.title       AS arc_title,
       c.volume_content_id AS volume_id,
       vo.number_slug   AS volume_slug,
       vo.number        AS volume_number,
       vo_t.title       AS volume_title,
       vo.cover_image_id AS volume_cover_image_id,
       v.updated_at     AS published_at
FROM content_version v
         JOIN chapter_version c ON c.version_id = v.id
         JOIN chapter_version_translation t ON t.version_id = v.id
         JOIN content_version av ON av.content_id = c.arc_content_id AND av.status = 'PUBLISHED'
         JOIN arc_version av_a ON av_a.version_id = av.id
         JOIN arc_version_translation av_t ON av_t.version_id = av.id AND av_t.language_code = t.language_code
         LEFT JOIN (content_version vv
             JOIN volume_version vo ON vo.version_id = vv.id
             JOIN volume_version_translation vo_t ON vo_t.version_id = vv.id)
              ON vv.content_id = c.volume_content_id AND vv.status = 'PUBLISHED'
                  AND vo_t.language_code = t.language_code
WHERE v.status = 'PUBLISHED';

-- An episode with the summary of its arc, if it has one (a filler has none).
CREATE VIEW published.episode AS
SELECT v.content_id     AS id,
       e.number_slug    AS slug,
       e.number,
       t.language_code  AS language,
       t.title          AS name,
       t.description,
       e.japanese_title,
       e.arc_content_id AS arc_id,
       av_a.number_slug AS arc_slug,
       av_a.number      AS arc_number,
       av_t.title       AS arc_title,
       v.updated_at     AS published_at
FROM content_version v
         JOIN episode_version e ON e.version_id = v.id
         JOIN episode_version_translation t ON t.version_id = v.id
         LEFT JOIN (content_version av
             JOIN arc_version av_a ON av_a.version_id = av.id
             JOIN arc_version_translation av_t ON av_t.version_id = av.id)
              ON av.content_id = e.arc_content_id AND av.status = 'PUBLISHED'
                  AND av_t.language_code = t.language_code
WHERE v.status = 'PUBLISHED';

-- The covers of the volumes online join the images of the fruits online, and no other (D13).
-- Same columns as before, so the view is replaced in place.
CREATE OR REPLACE VIEW published.image AS
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
                AND v.status = 'PUBLISHED');
