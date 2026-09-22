-- A language is more than its code (Step 10 follow-up, user feedback): the ADMIN screen
-- needs a full display name (e.g. "English"), not just the two-letter code, and a language
-- is now required to be exactly two lowercase letters (ISO 639-1 style) - the code column
-- itself stays VARCHAR(8) unchanged (shrinking it buys nothing; the format is enforced in
-- LanguageService, same "no DB-level enforcement of a business rule" stance as everywhere
-- else in this schema).
ALTER TABLE language
    ADD COLUMN name VARCHAR(100);

UPDATE language SET name = 'Italiano' WHERE code = 'it';
UPDATE language SET name = 'English' WHERE code = 'en';

ALTER TABLE language
    ALTER COLUMN name SET NOT NULL;
