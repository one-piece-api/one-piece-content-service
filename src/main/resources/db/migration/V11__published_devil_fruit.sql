-- The Devil Fruit in the published interface (implementation plan of the Devil Fruit, D11 -
-- D13, Step DF8). Grants come from the default privileges of V8.

-- One row per Devil Fruit online and language it is written in, with the summary of its
-- type in the same language. The type of a fruit online is online too (D2); the inner join
-- keeps a fruit out of a language its type is not written in, should the data ever allow it.
CREATE VIEW published.devil_fruit AS
SELECT v.content_id     AS id,
       f.romaji_slug    AS slug,
       f.romaji,
       t.language_code  AS language,
       t.name,
       t.description,
       t.advantages,
       t.disadvantages,
       f.type_content_id AS type_id,
       d.romaji_slug    AS type_slug,
       d.romaji         AS type_romaji,
       dt.name          AS type_name,
       f.image_id,
       v.updated_at     AS published_at
FROM content_version v
         JOIN devil_fruit_version f ON f.version_id = v.id
         JOIN devil_fruit_version_translation t ON t.version_id = v.id
         JOIN content_version tv ON tv.content_id = f.type_content_id AND tv.status = 'PUBLISHED'
         JOIN devil_fruit_type_version d ON d.version_id = tv.id
         JOIN devil_fruit_type_version_translation dt
              ON dt.version_id = tv.id AND dt.language_code = t.language_code
WHERE v.status = 'PUBLISHED';

-- The images of the versions online, and no other: a draft's image does not exist for the
-- reader (D13).
CREATE VIEW published.image AS
SELECT i.id, i.content_type, i.bytes
FROM image i
WHERE EXISTS (SELECT 1
              FROM devil_fruit_version f
                       JOIN content_version v ON v.id = f.version_id
              WHERE f.image_id = i.id
                AND v.status = 'PUBLISHED');
