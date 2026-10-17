-- Reader time in the published interface (implementation plan of the timeline, T7, T13,
-- Step TL5; ADR-0003 addendum). Grants come from the default privileges of V8.

-- The chapter of the first appearance, as the reader addresses it: the chapter online in the
-- same language (published.chapter). Required online for a fruit and a type, optional for a
-- subcategory; a chapter with no text in the language leaves these null, the row stays.
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
       fa.name          AS first_appearance_chapter_title
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
       fa.name         AS first_appearance_chapter_title
FROM content_version v
         JOIN devil_fruit_type_version d ON d.version_id = v.id
         JOIN devil_fruit_type_version_translation t ON t.version_id = v.id
         LEFT JOIN published.chapter fa
              ON fa.id = d.first_appearance_chapter_id AND fa.language = t.language_code
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
       fa.name          AS first_appearance_chapter_title
FROM content_version v
         JOIN devil_fruit_type_version_subcategory s ON s.version_id = v.id
         JOIN devil_fruit_type_version_subcategory_translation st ON st.subcategory_row_id = s.id
         LEFT JOIN published.chapter fa
              ON fa.id = s.first_appearance_chapter_id AND fa.language = st.language_code
WHERE v.status = 'PUBLISHED';

-- Each field a reveal of a fruit online names, and whether it is the field's last mention:
-- the last mention reveals the truth (the main fields), an earlier one the value it holds.
-- Decided by position, not by a missing value, so a language left untranslated never shows
-- the truth too early. Outside the published schema: it holds internal version ids.
CREATE VIEW devil_fruit_reveal_mention AS
SELECT r.version_id,
       r.id AS reveal_id,
       rf.field,
       NOT EXISTS (SELECT 1
                   FROM devil_fruit_version_reveal r2
                            JOIN devil_fruit_version_reveal_field rf2 ON rf2.reveal_id = r2.id
                   WHERE r2.version_id = r.version_id
                     AND r2.position > r.position
                     AND rf2.field = rf.field) AS last_mention
FROM content_version v
         JOIN devil_fruit_version_reveal r ON r.version_id = v.id
         JOIN devil_fruit_version_reveal_field rf ON rf.reveal_id = r.id
WHERE v.status = 'PUBLISHED';

-- What the reader knows of a fruit online at its first appearance (the public revealHistory's
-- initial), one row per language of the fruit, only for a fruit with reveals. fields lists
-- the fields some reveal names: only those are part of it, a null among them is unknown.
CREATE VIEW published.devil_fruit_initial_knowledge AS
SELECT k.devil_fruit_id,
       k.language,
       k.fields,
       k.name,
       k.romaji,
       k.type_id,
       ty.slug   AS type_slug,
       ty.romaji AS type_romaji,
       ty.name   AS type_name,
       k.subcategory_id,
       sc.name        AS subcategory_name,
       sc.description AS subcategory_description,
       k.description,
       k.advantages,
       k.disadvantages
FROM (SELECT v.content_id    AS devil_fruit_id,
             t.language_code AS language,
             m.fields,
             CASE WHEN 'NAME' = ANY (m.fields) THEN it.name END                   AS name,
             CASE WHEN 'ROMAJI' = ANY (m.fields) THEN ik.romaji END               AS romaji,
             CASE WHEN 'TYPE' = ANY (m.fields) THEN ik.type_content_id END        AS type_id,
             CASE WHEN 'TYPE' = ANY (m.fields) THEN ik.subcategory_id END         AS subcategory_id,
             CASE WHEN 'DESCRIPTION' = ANY (m.fields) THEN it.description END     AS description,
             CASE WHEN 'ADVANTAGES' = ANY (m.fields) THEN it.advantages END       AS advantages,
             CASE WHEN 'DISADVANTAGES' = ANY (m.fields) THEN it.disadvantages END AS disadvantages
      FROM content_version v
               JOIN devil_fruit_version_translation t ON t.version_id = v.id
               JOIN (SELECT version_id, array_agg(DISTINCT field ORDER BY field) AS fields
                     FROM devil_fruit_reveal_mention
                     GROUP BY version_id) m ON m.version_id = v.id
               LEFT JOIN devil_fruit_version_initial_knowledge ik ON ik.version_id = v.id
               LEFT JOIN devil_fruit_version_initial_knowledge_translation it
                         ON it.version_id = v.id AND it.language_code = t.language_code
      WHERE v.status = 'PUBLISHED') k
         LEFT JOIN published.devil_fruit_type ty ON ty.id = k.type_id AND ty.language = k.language
         LEFT JOIN published.devil_fruit_type_subcategory sc
                   ON sc.type_id = k.type_id AND sc.id = k.subcategory_id AND sc.language = k.language;

-- The reveals of a fruit online, in order, one row per reveal and language of the fruit:
-- the chapter, the reveal's title and what the reader learns there, resolved (the truth at
-- a field's last mention, the reveal's own value before). fields lists the fields it names;
-- the other value columns are null. The type is learned with its subcategory.
CREATE VIEW published.devil_fruit_reveal AS
SELECT x.devil_fruit_id,
       x.language,
       x.position,
       x.chapter_id,
       ch.slug   AS chapter_slug,
       ch.number AS chapter_number,
       ch.name   AS chapter_title,
       x.title,
       x.fields,
       x.name,
       x.romaji,
       x.type_id,
       ty.slug   AS type_slug,
       ty.romaji AS type_romaji,
       ty.name   AS type_name,
       x.subcategory_id,
       sc.name        AS subcategory_name,
       sc.description AS subcategory_description,
       x.description,
       x.advantages,
       x.disadvantages
FROM (SELECT v.content_id              AS devil_fruit_id,
             t.language_code           AS language,
             r.position,
             r.revealed_at_chapter_id  AS chapter_id,
             rt.title,
             m.fields,
             CASE WHEN 'NAME' = ANY (m.fields)
                      THEN CASE WHEN 'NAME' = ANY (m.last) THEN t.name ELSE rt.name END END AS name,
             CASE WHEN 'ROMAJI' = ANY (m.fields)
                      THEN CASE WHEN 'ROMAJI' = ANY (m.last) THEN f.romaji ELSE r.romaji END END AS romaji,
             CASE WHEN 'TYPE' = ANY (m.fields)
                      THEN CASE WHEN 'TYPE' = ANY (m.last) THEN f.type_content_id ELSE r.type_content_id END END
                                       AS type_id,
             CASE WHEN 'TYPE' = ANY (m.fields)
                      THEN CASE WHEN 'TYPE' = ANY (m.last) THEN f.subcategory_id ELSE r.subcategory_id END END
                                       AS subcategory_id,
             CASE WHEN 'DESCRIPTION' = ANY (m.fields)
                      THEN CASE WHEN 'DESCRIPTION' = ANY (m.last) THEN t.description ELSE rt.description END END
                                       AS description,
             CASE WHEN 'ADVANTAGES' = ANY (m.fields)
                      THEN CASE WHEN 'ADVANTAGES' = ANY (m.last) THEN t.advantages ELSE rt.advantages END END
                                       AS advantages,
             CASE WHEN 'DISADVANTAGES' = ANY (m.fields)
                      THEN CASE WHEN 'DISADVANTAGES' = ANY (m.last) THEN t.disadvantages ELSE rt.disadvantages END END
                                       AS disadvantages
      FROM content_version v
               JOIN devil_fruit_version f ON f.version_id = v.id
               JOIN devil_fruit_version_translation t ON t.version_id = v.id
               JOIN devil_fruit_version_reveal r ON r.version_id = v.id
               JOIN (SELECT reveal_id,
                            array_agg(field ORDER BY field)                                    AS fields,
                            array_agg(field ORDER BY field) FILTER (WHERE last_mention)        AS last
                     FROM devil_fruit_reveal_mention
                     GROUP BY reveal_id) m ON m.reveal_id = r.id
               LEFT JOIN devil_fruit_version_reveal_translation rt
                         ON rt.reveal_id = r.id AND rt.language_code = t.language_code
      WHERE v.status = 'PUBLISHED') x
         LEFT JOIN published.chapter ch ON ch.id = x.chapter_id AND ch.language = x.language
         LEFT JOIN published.devil_fruit_type ty ON ty.id = x.type_id AND ty.language = x.language
         LEFT JOIN published.devil_fruit_type_subcategory sc
                   ON sc.type_id = x.type_id AND sc.id = x.subcategory_id AND sc.language = x.language;
