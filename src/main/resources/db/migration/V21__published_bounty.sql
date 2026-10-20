-- The Bounty in the published interface (implementation plan of the Bounty, B3, D1-D5).
-- Grants come from the default privileges of V8.

-- One row per bounty online and language it is written in, the same shape as the other
-- entities (V11, V16): the key text is read as "name" (what the public API searches), its
-- slug is the slug. Validity is derived here, from the bounties online only (D3): a draft
-- or a version in review naming this one as previous does not supersede it. The previous
-- bounty is online too: publish and restore require it, and retire is refused while an
-- online bounty names it (flows 3.4.4). The first appearance as in V19.
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
       t.description,
       v.updated_at     AS published_at,
       b.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       b.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug          AS first_appearance_episode_slug,
       fe.number        AS first_appearance_episode_number,
       fe.name          AS first_appearance_episode_title
FROM content_version v
         JOIN bounty_version b ON b.version_id = v.id
         JOIN bounty_version_translation t ON t.version_id = v.id
         LEFT JOIN published.chapter fa
              ON fa.id = b.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = b.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

-- The posters of the bounties online join the fruits' images and the volumes' covers, and
-- no other (D13 of the public API plan). Same columns, so the view is replaced in place.
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
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM bounty_version b
                       JOIN content_version v ON v.id = b.version_id
              WHERE b.image_id = i.id
                AND v.status = 'PUBLISHED');
