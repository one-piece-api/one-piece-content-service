-- The holder of a bounty (implementation plan of the Character, C6, D11): the Character
-- the poster is about. Optional, part of the version, pointing to a content and not to
-- one of its versions; its constant entity type keeps it from pointing to anything but a
-- character, and there is no cascade. Like previous_content_id (V20).
ALTER TABLE bounty_version
    ADD COLUMN holder_content_id  UUID,
    ADD COLUMN holder_entity_type VARCHAR(32) NOT NULL DEFAULT 'CHARACTER'
        CONSTRAINT ck_bounty_version_holder_entity CHECK (holder_entity_type = 'CHARACTER'),
    ADD CONSTRAINT fk_bounty_version_holder FOREIGN KEY (holder_content_id, holder_entity_type)
        REFERENCES content (id, entity_type);

-- The bounties of a character: its count in the list, the retire check, the filter.
CREATE INDEX idx_bounty_version_holder_content_id ON bounty_version (holder_content_id);
