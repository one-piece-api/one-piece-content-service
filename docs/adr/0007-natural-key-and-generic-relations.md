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
