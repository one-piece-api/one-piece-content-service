-- Reader axis, windows (workflow revision 17, 3.7.2, 3.4.7; reveals plan R2, D4, D9, D11):
-- every entry of a character's lists says when the reader learns it starts (discovered,
-- required to submit) and stops (ended, optional) being true, each a point with a chapter
-- and/or an episode part. A membership holds periods, each with its window, role and rank.
-- `former` is derived from the windows, never stored. No data conversion: the content
-- database restarts empty (D11).

-- The periods of a membership, in order (Usopp leaves and comes back; Koby has one per
-- rank). Rewritten in place position by position, as the epithets.
CREATE TABLE character_version_affiliation_period
(
    id            UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES character_version_affiliation (id) ON DELETE CASCADE,
    position      INT  NOT NULL,
    marine_rank   VARCHAR(32)
        CONSTRAINT ck_character_version_affiliation_period_marine_rank CHECK (marine_rank IN
            ('FLEET_ADMIRAL', 'ADMIRAL', 'VICE_ADMIRAL', 'REAR_ADMIRAL', 'COMMODORE', 'CAPTAIN', 'COMMANDER',
             'LIEUTENANT_COMMANDER', 'LIEUTENANT', 'LIEUTENANT_JUNIOR_GRADE', 'ENSIGN', 'WARRANT_OFFICER',
             'MASTER_CHIEF_PETTY_OFFICER', 'CHIEF_PETTY_OFFICER', 'PETTY_OFFICER', 'SEAMAN_FIRST_CLASS',
             'SEAMAN_APPRENTICE', 'SEAMAN_RECRUIT', 'CHORE_BOY')),
    CONSTRAINT uq_character_version_affiliation_period_position UNIQUE (membership_id, position)
);

-- The role of a period per language; a row is absent for a language nothing was written
-- in. The reference to the catalog keeps a language in use from being deleted.
CREATE TABLE character_version_affiliation_period_translation
(
    period_id     UUID         NOT NULL REFERENCES character_version_affiliation_period (id) ON DELETE CASCADE,
    language_code VARCHAR(8)   NOT NULL REFERENCES language (code),
    role          VARCHAR(100) NOT NULL,
    PRIMARY KEY (period_id, language_code)
);

-- The four parts of a window on every list table, each the shape of the first appearance
-- (V17, V18): a composite foreign key to the chapter's or the episode's content, kept to
-- that entity by a constant entity type; no cascade. Each part is indexed for what a
-- chapter's or an episode's retirement looks up: the online items using it as a point.
DO
$$
    DECLARE
        list_table TEXT;
        point      TEXT;
        axis       TEXT;
    BEGIN
        FOREACH list_table IN ARRAY ARRAY ['character_version_epithet', 'character_version_alias',
            'character_version_species', 'character_version_devil_fruit', 'character_version_relative',
            'character_version_affiliation_period']
            LOOP
                FOREACH point IN ARRAY ARRAY ['discovered', 'ended']
                    LOOP
                        FOREACH axis IN ARRAY ARRAY ['chapter', 'episode']
                            LOOP
                                EXECUTE format('ALTER TABLE %1$I
                                    ADD COLUMN %2$s_%3$s_id UUID,
                                    ADD COLUMN %2$s_%3$s_entity_type VARCHAR(32) NOT NULL DEFAULT %4$L
                                        CONSTRAINT %5$I CHECK (%2$s_%3$s_entity_type = %4$L),
                                    ADD CONSTRAINT %6$I FOREIGN KEY (%2$s_%3$s_id, %2$s_%3$s_entity_type)
                                        REFERENCES content (id, entity_type)',
                                               list_table, point, axis, upper(axis),
                                               'ck_' || list_table || '_' || point || '_' || axis,
                                               'fk_' || list_table || '_' || point || '_' || axis);
                                EXECUTE format('CREATE INDEX %1$I ON %2$I (%3$s_%4$s_id) WHERE %3$s_%4$s_id IS NOT NULL',
                                               'idx_' || list_table || '_' || point || '_' || axis,
                                               list_table, point, axis);
                            END LOOP;
                    END LOOP;
            END LOOP;
    END
$$;

-- The published interface keeps its columns (reveals plan R2, decision 3): `former`, the
-- role and the rank are derived as the unfiltered reading of the windows (3.7.2, 3.4.7).
-- A membership is former when none of its periods is open; its role and rank are those of
-- its last period. A fruit is former when its window has an end on any axis. The views of
-- the windows themselves come with R6.
CREATE OR REPLACE VIEW published.character_affiliation AS
SELECT v.content_id              AS character_id,
       a.position,
       a.affiliation_content_id  AS affiliation_id,
       last_period.marine_rank,
       NOT EXISTS (SELECT 1
                   FROM character_version_affiliation_period p
                   WHERE p.membership_id = a.id
                     AND p.ended_chapter_id IS NULL
                     AND p.ended_episode_id IS NULL) AS former
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
         LEFT JOIN LATERAL (SELECT p.marine_rank
                            FROM character_version_affiliation_period p
                            WHERE p.membership_id = a.id
                            ORDER BY p.position DESC
                            LIMIT 1) last_period ON true
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');

CREATE OR REPLACE VIEW published.character_affiliation_role AS
SELECT v.content_id     AS character_id,
       a.position,
       t.language_code  AS language,
       t.role
FROM content_version v
         JOIN character_version_affiliation a ON a.version_id = v.id
         JOIN LATERAL (SELECT p.id
                       FROM character_version_affiliation_period p
                       WHERE p.membership_id = a.id
                       ORDER BY p.position DESC
                       LIMIT 1) last_period ON true
         JOIN character_version_affiliation_period_translation t ON t.period_id = last_period.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version av
              WHERE av.content_id = a.affiliation_content_id
                AND av.status = 'PUBLISHED');

CREATE OR REPLACE VIEW published.character_devil_fruit AS
SELECT v.content_id             AS character_id,
       d.position,
       d.devil_fruit_content_id AS devil_fruit_id,
       (d.ended_chapter_id IS NOT NULL OR d.ended_episode_id IS NOT NULL) AS former
FROM content_version v
         JOIN character_version_devil_fruit d ON d.version_id = v.id
WHERE v.status = 'PUBLISHED'
  AND EXISTS (SELECT 1
              FROM content_version fv
              WHERE fv.content_id = d.devil_fruit_content_id
                AND fv.status = 'PUBLISHED');

-- What moved to the periods, and the stored `former` (D4: derived, never stored).
DROP TABLE character_version_affiliation_translation;

ALTER TABLE character_version_affiliation
    DROP COLUMN marine_rank,
    DROP COLUMN former;

ALTER TABLE character_version_devil_fruit
    DROP COLUMN former;
