# ADR-0008: Reader time: first appearance and the per-medium open door

## Context

The timeline plan (`docs/implementation-plan-timeline.md` in `one-piece-api`, T1, T7; workflow
`docs/user-flows/content-editorial-workflow.md` rev. 7, 3.6, 3.4.3, 3.1.1/3.1.2) adds a reader
axis: where the reader first meets an item, measured per medium (manga chapter, anime episode).
Devil Fruit, Devil Fruit Type and each subcategory get a `firstAppearance`; a fruit or type
goes online only with one. Only the chapter part is built now (step TL1); the episode part is
deferred, and the shape must not change when it arrives.

## Decision

- **Value, not field.** `StoryPoint(chapterContentId)` (`domain/readertime`) is the point;
  `StoryPoint.NONE` is "none chosen", never `null`. Wire: `firstAppearance: {chapter}` in
  requests, `{chapter: {id, number, names}}` in responses (`null` when none).
- **Capability by interface.** `FirstAppearing` (implemented by fruit, type, subcategory)
  gives `firstAppearanceChapterId()` / `lacksFirstAppearance()`; `FirstAppearanceRules` writes
  the rules once: the chapter is a `RelationDescriptor` (field `firstAppearance.chapter`,
  name `firstAppearanceChapter`) followed through `RelationRules` - linkable on save (`422`),
  online on publish / restore (`409 RELATED_NOT_ONLINE`, detail
  `{[subcategoryId,] firstAppearanceChapterId, firstAppearanceChapterNumber}`), chapter retire
  refused while online items point to it (`409 ONLINE_ITEMS_LINKED`, fruits first, then
  types including their subcategories).
- **Required to submit and to go online** (revised 2026-10-16, owner, timeline TL4: the editor
  learns it before review, not at publish). A fruit or type without one is incomplete:
  submitting is `422 VERSION_INCOMPLETE` naming `firstAppearance.chapter` (`missingFields`);
  saving stays free. `PUBLISH` / `RESTORE` without one stays `409 FIRST_APPEARANCE_MISSING`,
  checked before what the item points to - a safety net for versions approved before the
  revision. For a subcategory it stays optional.
- **`RelationDescriptor.field`.** A relation's request field may differ from its block name
  (`firstAppearance.chapter` vs `firstAppearanceChapterId`); defaults to the name, so the
  existing relations are unchanged.
- **Storage.** `V17`: `first_appearance_chapter_id` with a constant
  `first_appearance_chapter_entity_type = 'CHAPTER'` and a composite FK to `content(id,
  entity_type)` on `devil_fruit_version`, `devil_fruit_type_version` and
  `devil_fruit_type_version_subcategory`, plus an index for the retire check.
- **Per-medium open door (episode).** The anime part arrives additively: one more
  `StoryPoint` component (`episodeContentId`), one more column pair and FK, one more JSON key
  `episode`, `isMissing()` becoming "no part chosen". "At least one part to go online" then
  holds unchanged; no chapter-episode mapping is needed. Same pattern as public-api D14.

## Alternatives

- **Plain `UUID firstAppearanceChapterId` on each body**: simplest now, but the episode would
  add a sibling field on every entity and every rule; the value keeps it in one place.
- **Required only to go online** (the first decision, until 2026-10-16): the editor found out
  only when publishing, after a full review round. Nothing is online yet, so making it part of
  completeness blocks no existing item.
- **Separate relation code (`CHAPTER_NOT_ONLINE`)**: the generic codes of ADR-0007 already
  carry the relation name in the detail keys.

## Consequences

- Versions written before `V17` have no first appearance: items already online stay online,
  but a new version or a restore needs one first.
- Clients must translate `FIRST_APPEARANCE_MISSING` (frontend, step TL4); `GET
  /chapters/linkable` lists the chapters a first appearance may name (`content:write`).
- Chapter retire runs two lookups (fruits, then types and subcategories), each a count plus a
  preview page.
