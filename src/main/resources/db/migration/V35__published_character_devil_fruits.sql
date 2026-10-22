-- The Devil Fruits of the characters online and the fruits with several users in the
-- published interface (implementation plan of the Character, C10, D12, D14). Grants come
-- from the default privileges of V8.

-- Same view as V19 with multiple_users as the last column, so it is replaced in place.
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

-- One row per fruit of a character online, in order, and only of a fruit online: the
-- publish rule guarantees it already, the view does not rely on it. Names and slugs are
-- read from published.devil_fruit, in the reader's language.
CREATE VIEW published.character_devil_fruit AS
SELECT v.content_id             AS character_id,
       d.position,
       d.devil_fruit_content_id AS devil_fruit_id,
       d.former
FROM content_version v
         JOIN character_version_devil_fruit d ON d.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version fv
              WHERE fv.content_id = d.devil_fruit_content_id
                AND fv.status = 'PUBLISHED');
