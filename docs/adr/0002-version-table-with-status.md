# ADR-0002: Shared version tables, each version carrying its own status

## Context

Revision 3 of the editorial workflow (`docs/user-flows/content-editorial-workflow.md` in
`one-piece-api`, §4) makes the **version** the unit of work: it is born with its draft,
keeps one number for life, and is published, superseded, retired, archived or restored by
changing status. A content has at most one open version and at most one online version.

The previous model kept two things apart: a mutable working revision per author, and an
immutable snapshot written at publication. That cannot express a version that was approved
but never published, nor "open a new draft from v2".

The workflow is the same for every content entity (Devil Fruit Types first, then Devil
Fruits, Characters, …). What a version says is not: each entity has its own fields.

## Decision

- **Workflow in two tables shared by every entity:**
  - `content` — the content: an id and its `entity_type`;
  - `content_version` — every version of every content, workflow only: number, based-on
    number, author, status, claimant, rejection reason, dates.
- **Content in the tables of its entity, with real, typed columns, keyed by version.** For
  Devil Fruit Type:
  - `devil_fruit_type_version` — one row per version, sharing its id: romaji;
  - `devil_fruit_type_version_translation` — name and description per language,
    referencing the language catalog.

  The next entity adds its own pair (the translation one only if it has translated
  fields); relations between contents get a foreign key to `content`.
- The invariants are constraints, not application checks alone:
  - `UNIQUE (content_id, version_number)`;
  - a partial unique index on `content_id` over the open statuses: one open version per content;
  - a partial unique index on `content_id` where `status = 'PUBLISHED'`: one online version;
  - `CHECK` on the status values, on the entity types and on
    `based_on_number < version_number`.
- "Has it ever been online" is derived from the status (`PUBLISHED`, `RETIRED`,
  `SUPERSEDED`), not stored: those two are only reachable from `PUBLISHED`.
- A version's history is not in these tables: it is read from `audit_log`, which gains
  `target_version_id` (implementation plan, D4). The id, not the number: a number is reused
  once a draft is deleted, and that draft's records must not join the history of the next
  version taking its number.
- Only the workflow schema is shared. Each entity keeps its own API path, service and
  repository, reading its own tables joined to the shared ones: no generic content
  framework.

## Alternatives considered

- **Keep working revision + published snapshot.** Two shapes for the same content, a copy
  at every publication, and no place for an archived or never-published version.
- **One set of tables per entity** (`devil_fruit_type_version`, `character_version`, …).
  The workflow columns and their invariants repeated for each entity, and the cross-entity
  dashboard becomes a `UNION` growing with every entity.
- **Romaji, name and description in the shared tables**, as fields every entity has. One
  join fewer, and the cross-entity dashboard reads names from one place; but the shared
  tables would be shaped on the first entity, with columns left empty by any entity that
  lacks one of them.
- **A key/value table, or a JSON column, for entity-specific attributes.** No migration to
  add an attribute, but schemaless: every value is text, the database can enforce no type,
  no uniqueness and no reference, and filtering or sorting on an attribute needs a pivot.
  Not what a relational database is for.
- **An append-only table for closed versions.** Immutability enforced by the storage, but
  restoring or superseding a version changes its status, so the rows would still be
  updated, or moved between tables on every transition.
- **Invariants checked only in the service.** Two concurrent requests can both pass a
  check-then-write; the partial indexes make the second one fail whatever the code does.
- **A stored `ever_published` flag.** One more column to keep consistent with a status that
  already says it.

## Consequences

- The workflow rules and their constraints exist once; "the online version" is a filter,
  not a pointer to maintain.
- Reading a version means joining the shared row with its entity's row. Only versions of
  an entity have a row in its table, so its queries need no filter by type.
- A new entity adds its type to the `entity_type` check and its content tables, through a
  migration, plus its own check in "is this language still in use".
- A list across entities (the dashboard) takes the name of each row from the table of its
  entity: one join, or one branch of a `UNION`, per entity.
- Uniqueness of romaji and name is within an entity type, not across all content.
- A closed version is immutable by rule, enforced by the service, not by the schema.
- Transitions that move the online version (publish, restore) must update the old one
  before the new one in the same transaction, or the partial index refuses the write.
- A violated invariant surfaces as a constraint error: the service must check first to
  answer a clean `409`, and treats the constraint as the safety net.
- Author and claimant are stored as id, username and e-mail, as they were on the token: this
  service has no user directory to resolve an id against, and neither value can change for
  an account. The audit log gains the actor's username for the same reason.

## Naming

The entry is called a **content**, never an "item": table `content`, column `content_id`,
`audit_log.target_content_id`. The first migration of this model (`V2`) used `content_item`
and `item_id`; `V3` renames them.
