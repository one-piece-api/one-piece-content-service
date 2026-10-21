-- The holder of a bounty in the published interface (implementation plan of the
-- Character, C6). Same view as V21 with the holder as the last column, so it is replaced
-- in place. The holder is online too: publish and restore require it, and retire is
-- refused while an online bounty names it, so it is null only for a bounty without one.
CREATE OR REPLACE VIEW published.bounty AS
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
       fe.name          AS first_appearance_episode_title,
       b.holder_content_id AS holder_id
FROM content_version v
         JOIN bounty_version b ON b.version_id = v.id
         JOIN bounty_version_translation t ON t.version_id = v.id
         LEFT JOIN published.chapter fa
              ON fa.id = b.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = b.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';
