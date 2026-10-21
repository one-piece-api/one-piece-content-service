-- The Character in the published interface (implementation plan of the Character, C2, D14).
-- Grants come from the default privileges of V8.

-- One row per character online and language it is written in, the same shape as the
-- Species (V25), plus the values that are the same in every language: Japanese name,
-- status, gender and birthday.
CREATE VIEW published.character AS
SELECT v.content_id     AS id,
       c.romaji_slug    AS slug,
       c.romaji,
       c.japanese_name,
       t.language_code  AS language,
       t.name,
       t.description,
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
         LEFT JOIN published.chapter fa
              ON fa.id = c.first_appearance_chapter_id AND fa.language = t.language_code
         LEFT JOIN published.episode fe
              ON fe.id = c.first_appearance_episode_id AND fe.language = t.language_code
WHERE v.status = 'PUBLISHED';

-- The epithets of the characters online, one row per epithet and language it is written
-- in, in the order of the version. An epithet with no text in a language is left out of it,
-- as a subcategory (V13).
CREATE VIEW published.character_epithet AS
SELECT v.content_id     AS character_id,
       et.language_code AS language,
       e.position,
       et.text
FROM content_version v
         JOIN character_version_epithet e ON e.version_id = v.id
         JOIN character_version_epithet_translation et ON et.epithet_id = e.id
WHERE v.status = 'PUBLISHED';

-- The aliases of the characters online, in order: the same in every language.
CREATE VIEW published.character_alias AS
SELECT v.content_id AS character_id,
       a.position,
       a.alias
FROM content_version v
         JOIN character_version_alias a ON a.version_id = v.id
WHERE v.status = 'PUBLISHED';

-- The species of the characters online, in order, and only those online: the publish rule
-- guarantees it already, the view does not rely on it. Names and slugs are read from
-- published.species, in the reader's language.
CREATE VIEW published.character_species AS
SELECT v.content_id         AS character_id,
       s.position,
       s.species_content_id AS species_id
FROM content_version v
         JOIN character_version_species s ON s.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version sv
              WHERE sv.content_id = s.species_content_id
                AND sv.status = 'PUBLISHED');

-- The portraits of the characters online join the fruits' images, the volumes' covers, the
-- bounties' posters, the affiliations' emblems and the species' pictures, and no other (D13
-- of the public API plan). Same columns, so the view is replaced in place.
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
                AND v.status = 'PUBLISHED')
   OR EXISTS (SELECT 1
              FROM character_version c
                       JOIN content_version v ON v.id = c.version_id
              WHERE c.image_id = i.id
                AND v.status = 'PUBLISHED');
