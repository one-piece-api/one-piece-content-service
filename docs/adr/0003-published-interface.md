# ADR-0003: A published interface in SQL for the public API

## Context

`one-piece-public-api` exposes, without authentication, only what is online, always up to
date (`docs/implementation-plan-public-api.md` in `one-piece-api`, D1–D3). It reads this
service's database directly, as the read-only role `public_api_reader`
(ADR-0018 in `onepiece-infrastructure`, an exception to its ADR-0016). Three things are
missing for that:

- a boundary: the tables hold drafts, reviews and users, which must not leak, and their
  shape follows the editorial workflow (ADR-0002), not the API;
- an address readable by people: a content is known only by its UUID;
- a cheap way to know whether anything online changed, for HTTP caching (ETag).

## Decision

All in migration `V8__published_interface.sql`.

- **Schema `published`, views only.** One row per content online (and language), already in
  the API's shape: `devil_fruit_type`, `content_slug`, `language`, `revision`. The views run
  with this service's rights, so the reader gets `USAGE` + `SELECT` on this schema alone,
  never on the tables. `ALTER DEFAULT PRIVILEGES` covers the views of the entities to come.
- **Slug from the romaji**, defined once in SQL (`slug_of`): lowercase, accents and macrons
  dropped, other characters turned into hyphens. Used by:
  - a generated column `devil_fruit_type_version.romaji_slug`, so it can never disagree
    with the romaji;
  - the save and submit validation: a romaji whose slug belongs to another content is
    refused (`422 CONTENT_SLUG_ALREADY_USED`), one with no letter or digit too
    (`422 CONTENT_VALUE_INVALID`);
  - `content_slug`, the history of every slug a content had online, written when a version
    goes online and never deleted. An old slug keeps pointing to its content (the API
    answers `301`) and stays reserved to it. A slug taken by two simultaneous saves is caught
    there: the second publication gets `409`.
- **`published_revision`**: one counter, moved by triggers whenever a version enters or
  leaves `PUBLISHED` or the language catalog changes. Draft edits, claims and reviews do not
  move it. It becomes the API's weak ETag.
- **Role ordering**: the role is created by the infrastructure before this service migrates;
  tests create it through `spring.flyway.init-sqls` (`src/test/resources/config`).
- **The migrations are published alone** (`dev.onepieceapi:one-piece-content-service-migrations`,
  GitHub Packages), versioned by the number of the latest migration. The public API's tests
  build the same schema from them, so a breaking change to a view fails there.

## Alternatives considered

- **API reading the tables, with its own filters**: no new objects here, but every query must
  remember to exclude drafts, and the tables could no longer change without breaking it.
- **Materialized views or a table copied at publication**: faster reads, but they must be
  refreshed, and the data volume does not need it.
- **Slug computed in Java**: one more definition that could drift from the SQL the API
  queries; Postgres `normalize` covers it.
- **Revision moved by the services**: every path that changes what is online (publish,
  restore, retire, supersede, override, future entities) would have to remember it.
- **Version-number ETag per resource**: more precise, but a list would need the maximum of
  every row; one counter is enough for a catalog that changes rarely.

## Consequences

- The views are a contract with another service: changing a column needs the public API's
  release in step. New entities add views, they do not change existing ones.
- Publishing has one more write (`content_slug`) and a romaji change one more check.
- Romaji differing only in accents or punctuation can no longer belong to two contents.
- The migration fails if two contents online already share a slug: such data must be fixed
  before deploying.
- Any change to what is online invalidates every cached response of the API at once.
