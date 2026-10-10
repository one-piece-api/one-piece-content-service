-- The subcategories of a Devil Fruit Type in the published interface (implementation plan of
-- the subcategories, S9, Step SC3). Grants come from the default privileges of V8.

-- One row per subcategory of a type online and language it is written in, in the order of
-- the type's version (S4). A subcategory with no text in a language is left out of it.
CREATE VIEW published.devil_fruit_type_subcategory AS
SELECT v.content_id    AS type_id,
       s.subcategory_id AS id,
       st.language_code AS language,
       s.position,
       st.name,
       st.description
FROM content_version v
         JOIN devil_fruit_type_version_subcategory s ON s.version_id = v.id
         JOIN devil_fruit_type_version_subcategory_translation st ON st.subcategory_row_id = s.id
WHERE v.status = 'PUBLISHED';

-- The fruit gains the subcategory it names, read from its type's version online. A fruit with
-- none, or whose subcategory has no text in the language, keeps its row with these null:
-- the type is online (S6), but the view does not hide a fruit for a gap in a detail.
-- New columns go last, as CREATE OR REPLACE VIEW requires.
CREATE OR REPLACE VIEW published.devil_fruit AS
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
       v.updated_at     AS published_at,
       s.subcategory_id AS subcategory_id,
       st.name          AS subcategory_name,
       st.description   AS subcategory_description
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
WHERE v.status = 'PUBLISHED';
