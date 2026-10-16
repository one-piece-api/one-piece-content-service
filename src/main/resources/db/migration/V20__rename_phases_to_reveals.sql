-- Knowledge phases become reveals (implementation plan of the timeline, T12; ADR-0010):
-- a reveal is an instant on the reader axis, the chapter it happens in and what the
-- reader believed before it. Rename only, no change of shape or data; V19 stays as
-- applied. Constraints and indexes, named or PostgreSQL defaults, follow the tables.
ALTER TABLE devil_fruit_version_phase RENAME TO devil_fruit_version_reveal;
ALTER TABLE devil_fruit_version_reveal RENAME COLUMN superseded_at_chapter_id TO revealed_at_chapter_id;
ALTER TABLE devil_fruit_version_reveal
    RENAME COLUMN superseded_at_chapter_entity_type TO revealed_at_chapter_entity_type;
ALTER TABLE devil_fruit_version_reveal
    RENAME CONSTRAINT devil_fruit_version_phase_pkey TO devil_fruit_version_reveal_pkey;
ALTER TABLE devil_fruit_version_reveal
    RENAME CONSTRAINT devil_fruit_version_phase_version_id_fkey TO devil_fruit_version_reveal_version_id_fkey;
ALTER TABLE devil_fruit_version_reveal
    RENAME CONSTRAINT ck_devil_fruit_version_phase_chapter_entity TO ck_devil_fruit_version_reveal_chapter_entity;
ALTER TABLE devil_fruit_version_reveal
    RENAME CONSTRAINT ck_devil_fruit_version_phase_type_entity TO ck_devil_fruit_version_reveal_type_entity;
ALTER TABLE devil_fruit_version_reveal
    RENAME CONSTRAINT fk_devil_fruit_version_phase_chapter TO fk_devil_fruit_version_reveal_chapter;
ALTER TABLE devil_fruit_version_reveal
    RENAME CONSTRAINT fk_devil_fruit_version_phase_type TO fk_devil_fruit_version_reveal_type;
ALTER INDEX idx_devil_fruit_version_phase_version RENAME TO idx_devil_fruit_version_reveal_version;
ALTER INDEX idx_devil_fruit_version_phase_chapter RENAME TO idx_devil_fruit_version_reveal_chapter;
ALTER INDEX idx_devil_fruit_version_phase_type RENAME TO idx_devil_fruit_version_reveal_type;

ALTER TABLE devil_fruit_version_phase_translation RENAME TO devil_fruit_version_reveal_translation;
ALTER TABLE devil_fruit_version_reveal_translation RENAME COLUMN phase_id TO reveal_id;
ALTER TABLE devil_fruit_version_reveal_translation
    RENAME CONSTRAINT devil_fruit_version_phase_translation_pkey TO devil_fruit_version_reveal_translation_pkey;
ALTER TABLE devil_fruit_version_reveal_translation
    RENAME CONSTRAINT devil_fruit_version_phase_translation_phase_id_fkey
        TO devil_fruit_version_reveal_translation_reveal_id_fkey;
ALTER TABLE devil_fruit_version_reveal_translation
    RENAME CONSTRAINT devil_fruit_version_phase_translation_language_code_fkey
        TO devil_fruit_version_reveal_translation_language_code_fkey;
