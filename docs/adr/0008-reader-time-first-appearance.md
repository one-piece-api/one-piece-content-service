# ADR-0008: First appearance: a chapter, an episode, or both

## Context

Devil Fruit, Devil Fruit Type and each subcategory record where the reader first meets them
(`docs/user-flows/content-editorial-workflow.md` in `one-piece-api`). The manga and the anime
are separate media with their own numbering: an item may first appear in a chapter, in an
episode (anime-only content), or both, and no chapter-episode mapping exists. A fruit or type
goes online only with one.

## Decision

- **Value, not field.** `StoryPoint(chapterContentId, episodeContentId)` (`domain/readertime`)
  is the point, each part optional; `StoryPoint.NONE` is "none chosen", never `null`, and
  `isMissing()` means no part chosen. Wire: `firstAppearance: {chapter, episode}` (ids) in
  requests, `{chapter: {id, number, names}, episode: {...}}` in responses (`null` for a part
  not chosen, `null` as a whole when none).
- **Capability by interface.** `FirstAppearing` (implemented by fruit, type, subcategory)
  gives `firstAppearanceChapterId()`, `firstAppearanceEpisodeId()`, `lacksFirstAppearance()`;
  `FirstAppearanceRules` writes the rules once: each part is a `RelationDescriptor` (fields
  `firstAppearance.chapter` / `firstAppearance.episode`, names `firstAppearanceChapter` /
  `firstAppearanceEpisode`) followed through `RelationRules` - linkable on save (`422`),
  online on publish / restore (`409 RELATED_NOT_ONLINE`, detail
  `{[subcategoryId,] firstAppearance{Chapter|Episode}Id, ...Number}`), chapter or episode
  retire refused while online items point to it (`409 ONLINE_ITEMS_LINKED`, fruits first,
  then types including their subcategories).
- **Required to submit and to go online** (owner, 2026-10-16: the editor learns it before
  review, not at publish). Either part is enough. A fruit or type without one is incomplete:
  submitting is `422 VERSION_INCOMPLETE` naming `firstAppearance` (`missingFields`); saving
  stays free. `PUBLISH` / `RESTORE` without one stays `409 FIRST_APPEARANCE_MISSING`, checked
  before what the item points to. For a subcategory it stays optional.
- **`RelationDescriptor.field`.** A relation's request field may differ from its block name
  (`firstAppearance.chapter` vs `firstAppearanceChapterId`); defaults to the name, so the
  other relations are unchanged.
- **Storage.** `V17` (chapter) and `V18` (episode): `first_appearance_{chapter|episode}_id`
  with a constant `..._entity_type` (`'CHAPTER'` / `'EPISODE'`) and a composite FK to
  `content(id, entity_type)` on `devil_fruit_version`, `devil_fruit_type_version` and
  `devil_fruit_type_version_subcategory`, plus an index for the retire check.

## Alternatives

- **Plain `UUID` fields on each body**: simplest, but two sibling fields on every entity and
  every rule; the value keeps them in one place.
- **A single "story point" id pointing to either a chapter or an episode**: one column, but
  an item in both media would lose one of them, and the FK could not check the entity type.
- **Required only to go online** (until 2026-10-16): the editor found out only when
  publishing, after a full review round.
- **Separate relation codes (`CHAPTER_NOT_ONLINE`)**: the generic codes of ADR-0007 already
  carry the relation name in the detail keys.

## Consequences

- Clients translate `FIRST_APPEARANCE_MISSING`; `GET /chapters/linkable` and
  `GET /episodes/linkable` list what a first appearance may name (`content:write`).
- Chapter and episode retire each run two lookups (fruits, then types and subcategories),
  each a count plus a preview page.
