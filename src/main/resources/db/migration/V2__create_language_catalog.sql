-- The supported-language catalog (docs/user-flows/authentication-and-user-management.md
-- §3.2). ADMIN-manageable from Step 10 onward; for now these are the only two rows, seeded
-- here rather than exposed through any write endpoint yet. Presence in this table is what
-- "active" means - there is no separate soft-delete flag, since removing a language is a
-- literal DELETE once Step 10 adds that capability.
CREATE TABLE language
(
    code VARCHAR(8) PRIMARY KEY
);

INSERT INTO language (code)
VALUES ('it'),
       ('en');
