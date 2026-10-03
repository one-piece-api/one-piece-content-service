-- Whether the actor could act only through content:admin - on someone else's version or
-- claim, or reviewing their own version (2.3, 7). The records written before are not
-- overrides: content:admin was not honoured yet.
ALTER TABLE audit_log
    ADD COLUMN override BOOLEAN NOT NULL DEFAULT FALSE;
