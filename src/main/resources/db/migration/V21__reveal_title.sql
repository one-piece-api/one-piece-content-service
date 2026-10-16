-- The reveal at a glance (T10): a short title per language on each reveal.
-- Nullable: a draft may lack it, the submission asks for it.
ALTER TABLE devil_fruit_version_reveal_translation ADD COLUMN title VARCHAR(60);
