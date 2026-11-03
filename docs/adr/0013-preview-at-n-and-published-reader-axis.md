# ADR-0013: Preview at N and the reader axis in the published interface

Extends ADR-0003 (published interface), ADR-0011 (reader axis) and ADR-0012 (field reveals).

## Context

Workflow revision 17 (`docs/user-flows/content-editorial-workflow.md` 3.7, 3.8, 4.3; UF-CNT-23) and the reveals
plan (`docs/implementation-plan-reveals/`, step R6, D1–D5): an editor must see a version, draft included, as a
reader at chapter or episode N would see it; public-api must resolve every item at N from the `published` views.
Both must give the same answer, so both resolve with the library `one-piece-reader-time` on chapter and episode
numbers.

## Decision

- **Endpoint (D1).** `GET /<section>/{id}/versions/{number}/preview?asOfChapter=N|asOfEpisode=N` on every
  reader-axis entity (Devil Fruit, its type, Character, Bounty, Affiliation, Species, Saga, Arc). Exactly one
  parameter, N >= 1 (`@OneReaderAxis` on `PreviewRequest`, else `400 VALIDATION_FAILED`); `content:read`; a version
  the caller cannot see is `404`, as the version read.
- **Envelope (D2).** `{asOf{axis, number}, visible, body, mainPortrait, hiddenEntries[{path, reason, visibleFrom}]}`.
  `body` is the entity's usual version response resolved at N (null when not `visible`): current values replaced by
  what is known at N, entries, periods, paragraphs and gallery filtered and trimmed to N. `mainPortrait` is an
  `ImageResponse` (id + url) like the gallery's. `hiddenEntries` flags only the surprises: target met after N
  (`TARGET_NOT_VISIBLE`, with `visibleFrom`), target never online (`TARGET_NOT_ONLINE`), discovered only on the
  other axis (`NOT_ON_AXIS`); an entry discovered after N is not flagged. No "revealed at N" data (D3).
- **Linked items at N (D5).** A link counts if its target's online version is met at N; its name is the one known at
  N (`namesAt`). Chapter and episode numbers come from the last approved version (`StoryNumbering`). Saga/Arc are
  met at the first chapter/episode online in their span; a subcategory without its own first appearance is met with
  its type.
- **Shape (Strategy, as ADR-0004).** One `ContentPreviewer<T>` per entity resolves the body; `ReaderPreviews` and
  `PreviewContext` hold the rules written once (met, trimmed, entries, paragraphs, portrait, hidden entries).
  `ContentService.preview` loads the visible version and returns `VersionPreview` (preview + the resolved version
  with no actions). `PreviewableContentController` adds the endpoint and maps the body with the existing response
  mapper; Character and Bounty override `previewBody` to resolve their lookups at N (relatives of, chain).
- **Published views (D4, `V42`).** `chapter_number`/`episode_number`; every window, period, paragraph, gallery image
  and reveal point as `<point>_chapter`/`<point>_episode` numbers, null when that chapter or episode is not online;
  new views for membership periods (+ roles), paragraphs of every entity, gallery (+ captions), initial knowledge
  and reveals (+ translations, `fields` as a sorted array). Additive only: existing columns keep their meaning.

## Alternatives

- **Preview computed in the frontend**: the rules would be written twice (TS and Java) and drift from public-api.
- **A separate preview DTO per entity**: duplicates every response; the existing mappers already render a body.
- **Points as content ids in the views**: public-api would need the chapter table to compare; numbers are what the
  library compares.
- **Replace the compatibility columns in V42**: breaks the public-api in production until R7b ships.

## Consequences

- Contract additions only: one endpoint per section, three response records.
- public-api bumps the migrations artifact to `V42` before reading the new views (R7b); a cleanup migration drops
  `former`, the last period's role/rank, the joined long texts and `image_id` after it.
- A new reader-axis entity adds a previewer and extends `PreviewableContentController`.
