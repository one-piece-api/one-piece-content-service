-- Step 10 (docs/implementation-plan-content.md): the language catalog gains ADMIN CRUD, so
-- an audit record for it (create/delete a language) has no item to point at - unlike every
-- other action this service audits so far. Relaxed rather than given a separate audit table:
-- the language catalog is still audited the same way as everything else (action, actor,
-- timestamp, detail), it just has no item id.
ALTER TABLE audit_log
    ALTER COLUMN target_item_id DROP NOT NULL;
