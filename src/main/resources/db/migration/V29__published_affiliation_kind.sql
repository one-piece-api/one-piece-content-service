-- The kind of an affiliation in the published interface (implementation plan of the
-- Character, C5). Same view as V23 with the kind as the last column, so it is replaced in
-- place; null for a version written before kinds existed.
CREATE OR REPLACE VIEW published.affiliation AS
SELECT v.content_id     AS id,
       a.romaji_slug    AS slug,
       a.romaji,
       t.language_code  AS language,
       t.name,
       t.description,
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
