-- The kind of an affiliation (implementation plan of the Character, C5, D10): what sets a
-- Navy apart from a pirate crew, and what lets a membership carry a marine rank only in
-- the Navy. Nullable: versions written before keep no kind, as the advantages of V7; it is
-- required at the next submission.
ALTER TABLE affiliation_version
    ADD COLUMN kind VARCHAR(32)
        CONSTRAINT ck_affiliation_version_kind
            CHECK (kind IN ('PIRATE_CREW', 'NAVY', 'WORLD_GOVERNMENT', 'REVOLUTIONARY_ARMY', 'OTHER'));
