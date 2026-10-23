-- The relatives of the characters online in the published interface (implementation plan
-- of the Character, C12, D13, D14). Grants come from the default privileges of V8.

-- One row per relative of a character online, in order, and only of a relative online:
-- the publish rule guarantees it already, the view does not rely on it. The kind is the
-- one the character stores ("relative is the kind of this character"); the other side,
-- and the inverse kind, are for the reader to derive. Names, slugs and gender are read
-- from published.character, in the reader's language.
CREATE VIEW published.character_relative AS
SELECT v.content_id            AS character_id,
       r.position,
       r.relative_content_id   AS relative_id,
       r.kind
FROM content_version v
         JOIN character_version_relative r ON r.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version rv
              WHERE rv.content_id = r.relative_content_id
                AND rv.status = 'PUBLISHED');
