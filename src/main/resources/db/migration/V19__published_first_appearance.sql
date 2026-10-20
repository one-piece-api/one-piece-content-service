-- The first appearance in the published interface (ADR-0008, ADR-0003): a chapter and/or an
-- episode. Grants come from the default privileges of V8.

-- The chapter and the episode of the first appearance, as the reader addresses them: online
-- in the same language (published.chapter, published.episode). One with no text in the
-- language leaves its columns null; the row stays.
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
       st.description   AS subcategory_description,
       f.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       f.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug AS first_appearance_episode_slug,
       fe.number AS first_appearance_episode_number,
       fe.name AS first_appearance_episode_title
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

CREATE OR REPLACE VIEW published.devil_fruit_type AS
SELECT v.content_id    AS id,
       d.romaji_slug   AS slug,
       d.romaji,
       t.language_code AS language,
       t.name,
       t.description,
       t.advantages,
       t.disadvantages,
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

CREATE OR REPLACE VIEW published.devil_fruit_type_subcategory AS
SELECT v.content_id     AS type_id,
       s.subcategory_id AS id,
       st.language_code AS language,
       s.position,
       st.name,
       st.description,
       s.first_appearance_chapter_id AS first_appearance_chapter_id,
       fa.slug          AS first_appearance_chapter_slug,
       fa.number        AS first_appearance_chapter_number,
       fa.name          AS first_appearance_chapter_title,
       s.first_appearance_episode_id AS first_appearance_episode_id,
       fe.slug AS first_appearance_episode_slug,
       fe.number AS first_appearance_episode_number,
       fe.name AS first_appearance_episode_title
FROM content_version v
         JOIN devil_fruit_type_version_subcategory s ON s.version_id = v.id
         JOIN devil_fruit_type_version_subcategory_translation st ON st.subcategory_row_id = s.id
         LEFT JOIN published.chapter fa
              ON fa.id = s.first_appearance_chapter_id AND fa.language = st.language_code
         LEFT JOIN published.episode fe
              ON fe.id = s.first_appearance_episode_id AND fe.language = st.language_code
WHERE v.status = 'PUBLISHED';
