# ADR-0007: Natural key and generic relations

## Context

The framework of ADR-0004 assumed every entity has a `romaji`: `ContentBody`, `VersionBodyEntity`,
the list's search and sort, uniqueness and the slug (`docs/user-flows/content-editorial-workflow.md`
3.3) were built on it. The relation rules of ADR-0005 were written for one pair (fruit → type).
The story structure (`docs/implementation-plan-story-structure.md` in `one-piece-api`, S2, S5)
adds five entities identified by a `number`, with five relations: copying either part per entity
would multiply fake romaji columns and copies of the same rules.

## Decision

- **Natural key.** Each entity declares what identifies it: `ContentBody.naturalKey()` and
  `VersionBodyEntity.naturalKey()` return a `NaturalKey(field, value)`; the repository names
  the attribute holding it (`VersionBodyRepository.naturalKeyAttribute()`). Search, sort
  (`sort=romaji` reads the natural key), uniqueness, slug (`slug_of` of its text) and
  `SlugAlreadyUsedException` read it. A text compares case-insensitively, a number as it is.
  Devil Fruit and Type declare `romaji`; the slug queries stay per repository (they read the
  entity's table).
- **Generic relations.** A `RelationDescriptor` (name, target, reason codes, name of the
  linked items) describes one relation; `RelationRules` applies the DF-D2 rules to any of
  them: target linkable on save (`422`), target online on publish/restore (`409`), target
  retire refused while online items point to it, shared lock on going online / exclusive on
  retire. `LinkedItems` gives the queries of the entity holding the relation. Requiredness
  stays in the entity's completeness (`missingFields`).
- **Entity rules on top.** Devil Fruit subcategory rules (subcategory plan S5-S7) stay in
  `DevilFruitValidator` / `DevilFruitRules` / `DevilFruitTypeRules`, which call
  `RelationRules` first.
- **Wire unchanged.** The fruit's type keeps `TYPE_NOT_ONLINE` / `ONLINE_FRUITS_LINKED` and
  its detail keys (`typeId`, `typeRomaji`, `fruits[{id, romaji}]`, built from the relation
  name and the natural key field). New relations get generic codes when they arrive (SS3).

## Alternatives

- **Optional `romaji` on every entity**: two notions of identity, fake columns.
- **Hibernate `@NaturalId`**: one value per row, but a version table holds many versions of
  the same content with the same key - wrong semantics.
- **Rules per pair**: five near-copies of the same lock and checks.
- **Descriptor holding repositories as Spring beans**: couples a static description to
  wiring; kept as a value, repositories are passed by the entity's rules.

## Consequences

- A new entity declares its key and its relations instead of rewriting rules.
- No observable change for Devil Fruit / Type: OpenAPI and Bruno identical, existing tests
  green (only the two test fixtures implementing `ContentBody` / `VersionBodyEntity` gained
  `naturalKey()`).
- The natural key attribute is named twice (entity and repository) through the same Lombok
  `Fields` constant.

## Addendum - first number-keyed entities (Saga, Arc; story structure SS3)

- **Sort wire value:** sorting by the natural key uses the key's own field name:
  `sort=romaji` (Devil Fruit / Type), `sort=number` (story entities). Both are values of
  `ContentSortField` and read the same natural key, so either is accepted by every list.
- **References:** a number-keyed target is answered as `{id, number, names}`
  (`NumberedReferenceResponse`), the key under its own name as `romaji` is for a type;
  `names` are the titles. Target lookups and `/linkable` come from `NumberedLinks`.
- **Generic block codes:** new relations use `RELATED_NOT_ONLINE` (detail
  `{<relation>Id, <relation>Number}`) and `ONLINE_ITEMS_LINKED` (detail
  `{count, <items>: [{id, number}]}`); the keys name the relation. Devil Fruit codes unchanged.
- **Title as name:** story translations map `title` onto the embeddable attribute `name`
  (column `title`), so the generic search / sort / uniqueness queries stay unchanged;
  `ContentBody#nameField()` names the field in violations (`translations[en].title`).
- **Number as text:** search and the name-sort fallback convert a non-text key with HQL
  `str()`: a JPA `as(String.class)` emits no SQL cast and PostgreSQL has no `lower(integer)`.

## Addendum - Volume, Chapter, Episode (story structure SS4)

- **Titles may repeat:** `ContentBody#uniqueNames()` (default `true`) lets an entity skip the
  per-language name uniqueness; false for Volume, Chapter and Episode. Saga and Arc keep it.
- **Several relations on one entity:** Chapter declares two `RelationDescriptor`s (arc required,
  volume optional), Episode one (arc optional). Rule classes (`VolumeRules`, `ArcRules`) stay thin
  wiring over `RelationRules`; `ArcRules` also refuses a retire while online chapters or episodes
  link to the arc (`ONLINE_ITEMS_LINKED`, detail `{count, chapters|episodes: [{id, number}]}`).
- **Releases / broadcasts:** a composition of the version (`Release{country, date}`), not a
  content: no lifecycle, one entry per ISO 3166-1 country, validated by `ReleaseRules`.
- **Cover:** `/volumes` also takes multipart (part `cover`, JSON `removeImage`); the profile
  accepts static PNG only, as the image framework does (JPEG/WebP deferred, plan S6 not met).

## Addendum - composite natural keys and entities without a localized name (Bounty, B1)

- **Why:** a bounty is told apart by poster name and amount together, and has no localized name.
- **Composite key:** `NaturalKey(field, value, fields)`. `value` is the key text (`Monkey D. Luffy 3000000000`) held by
  one generated `natural_key` column, so search, sort, uniqueness and slug stay as for any key.
  `fields` (default: `field`) lists the request fields the key is made of: `ContentValidator` reports every
  violation (`NO_SLUG`, `ALREADY_USED`, `SLUG_TAKEN`) once per field. `field` keeps naming the key in block
  details (`previousKey`).
- **No localized name:** `VersionBodyRepository#hasLocalizedNames()` (default `true`). When `false`, the list
  searches only the key text and `sort=name` orders by it, with no translation join.
- **Trade-off:** a boolean travels beside the key attribute through the list query signatures, rather than a new
  type; revisit if a third per-entity list option appears.

## Addendum - filters and sorts of an entity, answers that depend on the caller (Bounty, B2)

- **Own filters:** `ContentFilter.attributes` (name -> value, built with `withAttribute`) carries what only one
  entity filters by; `VersionBodyRepository#attributeConditions(filter, statuses)` turns it into a
  `Specification` (default: none) and `search`/`count` apply it. The bounty repository answers `condition` and
  `validity`; the latter is an `exists` over the bounties whose most recent version among the caller's statuses
  names the row, so an editor and a reader may read different lists.
- **Own sorts:** each list names what it sorts by in an enum implementing `SortField` (`@SortableBy`);
  `VersionBodySorting` looks the key up by field name, so `BountySortField.AMOUNT` exists only on the bounty list.
- **Answers that depend on the caller:** `ContentController#versionResponse(contentId, access, caller)` defaults to
  the entity's plain mapper; `BountyController` overrides it to read the chain and the validity through the
  statuses the caller sees. Chain walks keep the set of bounties met, since drafts are not checked against each
  other and may close a cycle.
- **`ContentValidator#requireValidRelations(contentId, body)`** defaults to the one without the id: a rule that
  must know which content is saved (a bounty is not its own previous) overrides it.
- **Trade-off:** the attributes map is untyped; the entity's request mapper and repository share the names as
  constants (`Bounty.CONDITION_ATTRIBUTE`, `Bounty.VALIDITY_ATTRIBUTE`).

## Addendum - lists of relations inside a version (Character, C1-C12)

- **Why:** a character names many species, affiliations, fruits and relatives, each with its own data (role, rank,
  `former`, kind). A `RelationDescriptor` points one target per body or per part.
- **A list is child tables:** one table per list (`character_version_<list>`, `position INT` for the order), plus a
  translation table when an entry is localized (epithets, roles), published as child views
  (`published.character_<list>`). Not a JSON column: no foreign key, no index, no relation rules.
- **Each entry is a relation of its own:** the descriptor of the list is applied per entry through the list variants
  of `RelationRules` (`notLinkable`, `lockTargetBefore`, `targetNotOnline` on `List<P>`). Violations name the entry
  (`species[1]`, `affiliations[0].marineRank`); block details carry the index and the target
  (`affiliationsIndex`/`affiliationId`/`affiliationRomaji`). Generic codes stay `RELATED_NOT_ONLINE` and
  `ONLINE_ITEMS_LINKED`.
- **Rules re-checked on publish and restore:** what was true on save may have changed (an affiliation no longer
  `NAVY`: `MARINE_RANK_NOT_ALLOWED`); the link, the rank and the invariants between contents are checked then
  (ADR-0009).
- **Derived reads:** the other side of a list (members, fruit users, relatives) is a query over online versions,
  returned as read-only fields (`memberCount`, `users`, `relativesOf`), never a stored list.
- **Trade-off:** many small tables and mappers per list, in exchange for referential integrity and filters by
  entry (`?species=`, `?affiliation=`, `?devilFruit=`, the last two counting current entries only).
