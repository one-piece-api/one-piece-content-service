-- The memberships of the characters online in the published interface (implementation plan
-- of the Character, C8, D10, D14). Grants come from the default privileges of V8.

-- One row per membership of a character online, in order, and only in an affiliation
-- online: the publish rule guarantees it already, the view does not rely on it. Names,
-- slugs and kinds are read from published.affiliation, in the reader's language.
CREATE VIEW published.character_affiliation AS
SELECT v.content_id              AS character_id,
       a.position,
       a.affiliation_content_id  AS affiliation_id,
       a.marine_rank,
       a.former
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');

-- The role of each of those memberships, one row per language it is written in; a
-- membership without a role has none, as an epithet without a text (V27).
CREATE VIEW published.character_affiliation_role AS
SELECT v.content_id     AS character_id,
       a.position,
       t.language_code  AS language,
       t.role
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
         JOIN character_version_affiliation_translation t ON t.membership_id = a.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');
