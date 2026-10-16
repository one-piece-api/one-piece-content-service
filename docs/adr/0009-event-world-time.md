# ADR-0009: Event, world time and the reveal rule

## Context

The timeline plan (`docs/implementation-plan-timeline.md` in `one-piece-api`, T2-T5, step TL2;
workflow `docs/user-flows/content-editorial-workflow.md` rev. 7, 3.1.8, 3.4.3, 3.6) adds the
**Event**: something that happens in the world, with a time on the world axis and a reveal on
the reader axis (ADR-0008). It is the first entity with no natural key and the first relation
to many contents (its Devil Fruit participants). The framework (ADR-0004, ADR-0007) assumed
both a natural key and single-target relations.

## Decision

- **Natural key optional.** `ContentBody.naturalKey()`, `VersionBodyEntity.naturalKey()` and
  `VersionBodyRepository.naturalKeyAttribute()` return `Optional` (empty by default). Without
  one: no slug, no uniqueness check, search on names only, `sort=romaji|number` falls back to
  the name, items named `{id, names}` in blocks, audit label `null`. The keyed entities are
  unchanged on the wire. Supersedes the "every entity has a natural key" part of ADR-0007.
- **Relations to many.** `RelationDescriptor` is built with `.target(...)` (one) or
  `.targets(...)` (many); `RelationRules` applies each rule per target: `422` names the first
  one at fault (`participants.devilFruits[<id>]`), `409 RELATED_NOT_ONLINE` the first not
  online (`devilFruitId`, `devilFruitRomaji`), shared locks taken in id order. A list filter on
  a to-many relation reads `<relation>ContentIds` with `member of` (`GET /events?devilFruit=`).
- **World time as a tagged union.** `sealed interface EventTime` -> `ChapterTime(chapter)` |
  `WorldDateTime(WorldDate{yearFrom, yearTo, approximate})`. Wire:
  `{"chapter": <id>}` or `{"worldDate": {...}}` in requests (`@OnePart`, `@YearsInOrder`: `400`),
  the same keys with the chapter as a reference in responses. Storage `V18`: per group
  (`occurs_*`, `ends_*`) a chapter FK (constant entity type, as V17) and the world-date columns,
  `CHECK` at most one part. `occursAt` is required for review only; `endsAt` always optional.
- **Order (`EventTimeOrder`, domain service).** Every world date precedes every chapter;
  chapters by number; world dates only when one certainly ends before the other starts (same
  year never out of order). Checked on save and submission (`422 VALUE_INVALID`) with the
  chapter numbers as last approved: `endsAt` not before `occursAt` (so chapter start + world-date
  end is refused), `revealedAt` not before `occursAt`. A world date may not end after
  `content.timeline.story-start-year` (1522).
- **Reveal.** `revealedAt` is a `StoryPoint` (chapter part only, ADR-0008). The effective reveal
  is computed, never stored: `revealedAt ?? occursAt chapter`. Going online without one is
  `409 REVEAL_MISSING`.
- **Reveal rule, both directions** (numbers of the online chapter versions): an event goes online
  only if no participant's first appearance comes after its effective reveal
  (`409 REVEALED_BEFORE_PARTICIPANT`, `{count, fruits}`); a fruit goes online only if no online
  event it takes part in is revealed before its new first appearance
  (`409 EVENTS_REVEALED_BEFORE`, `{count, events: [{id, names}]}`). Event publish holds the fruits
  shared; fruit publish holds itself exclusively, so the two never interleave.
- **Strong references.** A fruit cannot be retired while online events have it as participant; a
  chapter cannot be retired while online events occur, end or are revealed in it
  (`409 ONLINE_ITEMS_LINKED`, `events`).

## Alternatives

- **Slug for events** (written or derived from the title): titles repeat (every Reverie) and
  would need disambiguation; the id is enough for an editorial API.
- **Weak references on retire**: would let online events point to offline content; rejected to
  keep the published data consistent.
- **Two free optional fields per time** instead of a union: ambiguous order and shape.
- **Storing the effective reveal**: duplicated data to keep in sync with `occursAt`.
- **Participant rule only on the event side**: a fruit's new version could move its first
  appearance after an online event's reveal unnoticed.

## Consequences

- Clients translate `REVEAL_MISSING`, `REVEALED_BEFORE_PARTICIPANT`, `EVENTS_REVEALED_BEFORE`
  (frontend, TL4). The public views and `asOfChapter` come in TL5.
- Retiring a fruit and publishing one each run one more lookup on `event_version_devil_fruit`;
  retiring a chapter one more on `event_version`.
- Characters will join as one more participant table and one more `targets(...)` relation,
  additively (`participants.characters`).

## Addendum (2026-10-16, TL4): the list in the order of the world

- **Decision.** The events list sorts by `occursAt` (`EventSortField`), its default: world dates by
  first year, then chapters by the number of their last approved version, then events with no time;
  `desc` reverses the first two, events with no time stay last. `romaji`/`number` are no longer
  accepted for events (no natural key).
- **How.** An entity may declare sorts of its own: `VersionBodyRepository.ownSorting()` (keys +
  default sort, none by default) feeds `VersionBodySorting`; `EventSorting` holds the event's.
- **Alternative rejected.** `occursAt` in the shared `ContentSortField` guarded per entity: every
  entity would accept a sort only one has.
