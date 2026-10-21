-- The Species in the published interface (implementation plan of the Species, S2).
-- Grants come from the default privileges of V8.

-- One row per species online and language it is written in, the same shape as the
-- Affiliation (V23): romaji as key and slug, the name and description of the language, the
-- picture, and the first appearance as in V19.
CREATE VIEW published.species AS
SELECT v.content_id     AS id,
       s.romaji_slug    AS slug,
       s.romaji,
       t.language_code  AS language,
       t.name,
       t.description,
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

-- The pictures of the species online join the fruits' images, the volumes' covers, the
-- bounties' posters and the affiliations' emblems, and no other (D13 of the public API
-- plan). Same columns, so the view is replaced in place.
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
                AND v.status = 'PUBLISHED');
