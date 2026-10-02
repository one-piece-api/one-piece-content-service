-- "Item" gives way to "content" everywhere: an encyclopedia entry is a content, its versions
-- belong to a content. Renames only - no data is touched.

ALTER TABLE content_item
    RENAME TO content;

ALTER TABLE content_version
    RENAME COLUMN item_id TO content_id;

ALTER TABLE audit_log
    RENAME COLUMN target_item_id TO target_content_id;

ALTER INDEX idx_audit_log_target_item_id
    RENAME TO idx_audit_log_target_content_id;

-- The constraints PostgreSQL named after the old table and column (primary key, foreign
-- key, checks) follow: content_item_pkey -> content_pkey,
-- content_version_item_id_fkey -> content_version_content_id_fkey, and so on. Done by
-- pattern because which constraints carry a name depends on the PostgreSQL version.
DO
$$
    DECLARE
        renamed record;
    BEGIN
        FOR renamed IN
            SELECT conrelid::regclass AS table_name, conname AS old_name
            FROM pg_constraint
            WHERE connamespace = current_schema()::regnamespace
              AND conname LIKE '%item%'
            LOOP
                EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', renamed.table_name, renamed.old_name,
                               replace(replace(renamed.old_name, 'content_item', 'content'), 'item_id', 'content_id'));
            END LOOP;
    END
$$;
