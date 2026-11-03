-- The reader axis in the published interface (workflow revision 17, 3.7, 3.8; reveals plan
-- R6, D4): what public-api needs to resolve an item at chapter or episode N with the library
-- one-piece-reader-time - windows, membership periods, paragraphs, gallery, initial knowledge
-- and reveals. Additive only: the compatibility columns of V38, V39 and V41 (`former`, the
-- role and rank of the last period, the joined long texts, the character's `image_id`) stay
-- until public-api stops reading them (plan R7b), then a cleanup migration drops them.
--
-- A point is given as numbers, the only thing the library compares: `<point>_chapter` and
-- `<point>_episode`, each the number of a chapter or episode online. A part whose chapter or
-- episode is not online is null: not seen on that axis, so the filter hides it (3.7.4).
-- The default privileges of V8 grant every view to public_api_reader.

-- The number of every chapter and episode online, one row each, language-free.
CREATE VIEW published.chapter_number AS
SELECT v.content_id AS id,
       c.number
FROM content_version v
         JOIN chapter_version c ON c.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.episode_number AS
SELECT v.content_id AS id,
       e.number
FROM content_version v
         JOIN episode_version e ON e.version_id = v.id
WHERE v.status = 'PUBLISHED';

-- Windows of the character's lists (3.7.2): the existing views gain the points, as last
-- columns, so what public-api reads today is unchanged.
CREATE OR REPLACE VIEW published.character_alias AS
SELECT v.content_id AS character_id,
       a.position,
       a.alias,
       dc.number    AS discovered_chapter,
       de.number    AS discovered_episode,
       ec.number    AS ended_chapter,
       ee.number    AS ended_episode
FROM content_version v
         JOIN character_version_alias a ON a.version_id = v.id
         LEFT JOIN published.chapter_number dc ON dc.id = a.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = a.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = a.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = a.ended_episode_id
WHERE v.status = 'PUBLISHED';

CREATE OR REPLACE VIEW published.character_epithet AS
SELECT v.content_id     AS character_id,
       et.language_code AS language,
       e.position,
       et.text,
       dc.number        AS discovered_chapter,
       de.number        AS discovered_episode,
       ec.number        AS ended_chapter,
       ee.number        AS ended_episode
FROM content_version v
         JOIN character_version_epithet e ON e.version_id = v.id
         JOIN character_version_epithet_translation et ON et.epithet_id = e.id
         LEFT JOIN published.chapter_number dc ON dc.id = e.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = e.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = e.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = e.ended_episode_id
WHERE v.status = 'PUBLISHED';

CREATE OR REPLACE VIEW published.character_species AS
SELECT v.content_id         AS character_id,
       s.position,
       s.species_content_id AS species_id,
       dc.number            AS discovered_chapter,
       de.number            AS discovered_episode,
       ec.number            AS ended_chapter,
       ee.number            AS ended_episode
FROM content_version v
         JOIN character_version_species s ON s.version_id = v.id
         LEFT JOIN published.chapter_number dc ON dc.id = s.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = s.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = s.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = s.ended_episode_id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version sv
              WHERE sv.content_id = s.species_content_id
                AND sv.status = 'PUBLISHED');

CREATE OR REPLACE VIEW published.character_devil_fruit AS
SELECT v.content_id                                                AS character_id,
       d.position,
       d.devil_fruit_content_id                                    AS devil_fruit_id,
       (d.ended_chapter_id IS NOT NULL OR d.ended_episode_id IS NOT NULL) AS former,
       dc.number                                                   AS discovered_chapter,
       de.number                                                   AS discovered_episode,
       ec.number                                                   AS ended_chapter,
       ee.number                                                   AS ended_episode
FROM content_version v
         JOIN character_version_devil_fruit d ON d.version_id = v.id
         LEFT JOIN published.chapter_number dc ON dc.id = d.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = d.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = d.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = d.ended_episode_id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version fv
              WHERE fv.content_id = d.devil_fruit_content_id
                AND fv.status = 'PUBLISHED');

CREATE OR REPLACE VIEW published.character_relative AS
SELECT v.content_id          AS character_id,
       r.position,
       r.relative_content_id AS relative_id,
       r.kind,
       dc.number             AS discovered_chapter,
       de.number             AS discovered_episode,
       ec.number             AS ended_chapter,
       ee.number             AS ended_episode
FROM content_version v
         JOIN character_version_relative r ON r.version_id = v.id
         LEFT JOIN published.chapter_number dc ON dc.id = r.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = r.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = r.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = r.ended_episode_id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version rv
              WHERE rv.content_id = r.relative_content_id
                AND rv.status = 'PUBLISHED');

-- The periods of each membership (3.4.7), in order, each with its own window, rank and role.
-- `membership_position` is the `position` of published.character_affiliation.
CREATE VIEW published.character_affiliation_period AS
SELECT v.content_id AS character_id,
       a.position   AS membership_position,
       p.position,
       p.marine_rank,
       dc.number    AS discovered_chapter,
       de.number    AS discovered_episode,
       ec.number    AS ended_chapter,
       ee.number    AS ended_episode
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
         JOIN character_version_affiliation_period p ON p.membership_id = a.id
         LEFT JOIN published.chapter_number dc ON dc.id = p.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = p.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = p.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = p.ended_episode_id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');

CREATE VIEW published.character_affiliation_period_role AS
SELECT v.content_id    AS character_id,
       a.position      AS membership_position,
       p.position,
       t.language_code AS language,
       t.role
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
         JOIN character_version_affiliation_period p ON p.membership_id = a.id
         JOIN character_version_affiliation_period_translation t ON t.period_id = p.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');

-- The paragraphs of every paragraphed item (3.7.3), one row per paragraph and language:
-- `section` is DESCRIPTION, ADVANTAGES or DISADVANTAGES, `position` the order within it.
CREATE VIEW published.paragraph AS
SELECT v.content_id AS content_id,
       p.section,
       p.position,
       p.language,
       p.text,
       dc.number    AS discovered_chapter,
       de.number    AS discovered_episode,
       ec.number    AS ended_chapter,
       ee.number    AS ended_episode
FROM (SELECT p.version_id, p.section, p.position, t.language_code AS language, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM character_version_paragraph p
               JOIN character_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM devil_fruit_version_paragraph p
               JOIN devil_fruit_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM devil_fruit_type_version_paragraph p
               JOIN devil_fruit_type_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM bounty_version_paragraph p
               JOIN bounty_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM affiliation_version_paragraph p
               JOIN affiliation_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM species_version_paragraph p
               JOIN species_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM saga_version_paragraph p
               JOIN saga_version_paragraph_translation t ON t.paragraph_id = p.id
      UNION ALL
      SELECT p.version_id, p.section, p.position, t.language_code, t.text,
             p.discovered_chapter_id, p.discovered_episode_id, p.ended_chapter_id, p.ended_episode_id
      FROM arc_version_paragraph p
               JOIN arc_version_paragraph_translation t ON t.paragraph_id = p.id) p
         JOIN content_version v ON v.id = p.version_id
         LEFT JOIN published.chapter_number dc ON dc.id = p.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = p.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = p.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = p.ended_episode_id
WHERE v.status = 'PUBLISHED';

-- The character's gallery (3.5.1), in order; the main portrait at N is derived by the
-- library, never stored. Captions per language apart.
CREATE VIEW published.character_gallery AS
SELECT v.content_id AS character_id,
       g.position,
       g.image_id,
       g.kind,
       dc.number    AS discovered_chapter,
       de.number    AS discovered_episode,
       ec.number    AS ended_chapter,
       ee.number    AS ended_episode
FROM content_version v
         JOIN character_version_image g ON g.version_id = v.id
         LEFT JOIN published.chapter_number dc ON dc.id = g.discovered_chapter_id
         LEFT JOIN published.episode_number de ON de.id = g.discovered_episode_id
         LEFT JOIN published.chapter_number ec ON ec.id = g.ended_chapter_id
         LEFT JOIN published.episode_number ee ON ee.id = g.ended_episode_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character_gallery_caption AS
SELECT v.content_id    AS character_id,
       g.position,
       t.language_code AS language,
       t.caption
FROM content_version v
         JOIN character_version_image g ON g.version_id = v.id
         JOIN character_version_image_translation t ON t.gallery_image_id = g.id
WHERE v.status = 'PUBLISHED';

-- Field reveals (3.7.1; ADR-0012). The main fields of the entity views are the current
-- value. The initial knowledge holds only the fields some reveal names (null otherwise); each
-- reveal lists the fields it names in `fields` and, for each, the value learned - null in
-- every value column of a field revealing the current value. `slug` is the slug of a romaji
-- held (D13): which slugs the reader knows at N is resolved from these rows.
CREATE VIEW published.devil_fruit_initial_knowledge AS
SELECT v.content_id      AS devil_fruit_id,
       k.romaji,
       k.romaji_slug     AS slug,
       k.type_content_id AS type_id,
       k.subcategory_id
FROM content_version v
         JOIN devil_fruit_version_initial_knowledge k ON k.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.devil_fruit_initial_knowledge_name AS
SELECT v.content_id    AS devil_fruit_id,
       t.language_code AS language,
       t.name
FROM content_version v
         JOIN devil_fruit_version_initial_knowledge_translation t ON t.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.devil_fruit_reveal AS
SELECT v.content_id                                                   AS devil_fruit_id,
       r.position,
       ARRAY(SELECT f.field FROM devil_fruit_version_reveal_field f WHERE f.reveal_id = r.id ORDER BY f.field) AS fields,
       r.romaji,
       r.romaji_slug                                                  AS slug,
       r.type_content_id                                              AS type_id,
       r.subcategory_id,
       rc.number                                                      AS revealed_chapter,
       re.number                                                      AS revealed_episode
FROM content_version v
         JOIN devil_fruit_version_reveal r ON r.version_id = v.id
         LEFT JOIN published.chapter_number rc ON rc.id = r.revealed_chapter_id
         LEFT JOIN published.episode_number re ON re.id = r.revealed_episode_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.devil_fruit_reveal_translation AS
SELECT v.content_id    AS devil_fruit_id,
       r.position,
       t.language_code AS language,
       t.title,
       t.name
FROM content_version v
         JOIN devil_fruit_version_reveal r ON r.version_id = v.id
         JOIN devil_fruit_version_reveal_translation t ON t.reveal_id = r.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character_initial_knowledge AS
SELECT v.content_id  AS character_id,
       k.romaji,
       k.romaji_slug AS slug,
       k.japanese_name,
       k.status
FROM content_version v
         JOIN character_version_initial_knowledge k ON k.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character_initial_knowledge_name AS
SELECT v.content_id    AS character_id,
       t.language_code AS language,
       t.name
FROM content_version v
         JOIN character_version_initial_knowledge_translation t ON t.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character_reveal AS
SELECT v.content_id                                                 AS character_id,
       r.position,
       ARRAY(SELECT f.field FROM character_version_reveal_field f WHERE f.reveal_id = r.id ORDER BY f.field) AS fields,
       r.romaji,
       r.romaji_slug                                                AS slug,
       r.japanese_name,
       r.status,
       rc.number                                                    AS revealed_chapter,
       re.number                                                    AS revealed_episode
FROM content_version v
         JOIN character_version_reveal r ON r.version_id = v.id
         LEFT JOIN published.chapter_number rc ON rc.id = r.revealed_chapter_id
         LEFT JOIN published.episode_number re ON re.id = r.revealed_episode_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.character_reveal_translation AS
SELECT v.content_id    AS character_id,
       r.position,
       t.language_code AS language,
       t.title,
       t.name
FROM content_version v
         JOIN character_version_reveal r ON r.version_id = v.id
         JOIN character_version_reveal_translation t ON t.reveal_id = r.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.bounty_initial_knowledge AS
SELECT v.content_id AS bounty_id,
       k.inactive
FROM content_version v
         JOIN bounty_version_initial_knowledge k ON k.version_id = v.id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.bounty_reveal AS
SELECT v.content_id                                              AS bounty_id,
       r.position,
       ARRAY(SELECT f.field FROM bounty_version_reveal_field f WHERE f.reveal_id = r.id ORDER BY f.field) AS fields,
       r.inactive,
       rc.number                                                 AS revealed_chapter,
       re.number                                                 AS revealed_episode
FROM content_version v
         JOIN bounty_version_reveal r ON r.version_id = v.id
         LEFT JOIN published.chapter_number rc ON rc.id = r.revealed_chapter_id
         LEFT JOIN published.episode_number re ON re.id = r.revealed_episode_id
WHERE v.status = 'PUBLISHED';

CREATE VIEW published.bounty_reveal_translation AS
SELECT v.content_id    AS bounty_id,
       r.position,
       t.language_code AS language,
       t.title
FROM content_version v
         JOIN bounty_version_reveal r ON r.version_id = v.id
         JOIN bounty_version_reveal_translation t ON t.reveal_id = r.id
WHERE v.status = 'PUBLISHED';
