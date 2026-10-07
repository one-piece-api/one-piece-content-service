# ADR-0004: A generic content framework, one descriptor per entity

## Context

ADR-0002 shared the workflow **schema** between entities but kept, on purpose, one service,
repository and controller per entity: "no generic content framework" until a second entity
existed. The second one, the Devil Fruit, is now planned (`docs/implementation-plan-devil-fruit.md`
in `one-piece-api`, D8), and the mockup already lists the next ones (Characters, Arcs).

About 80 % of `DevilFruitTypeService` was the workflow itself (submit, claim, approve,
publish, retire, restore, new version, audit), identical for every entity; the same for most
of its repository, list specifications, validator, controller and endpoint registry. Copying
it per entity would duplicate every workflow rule.

## Decision

Everything that works the same for every entity is written once and is generic over two
types: `T`, what a version says (a `ContentBody`), and `E`, the entity's version table (a
`VersionBodyEntity`). What differs comes from the entity.

| Layer | Generic, once | Per entity |
|---|---|---|
| Domain | `ContentBody`: romaji, languages, names, normalization, missing fields; `ContentSortField` | the body record, e.g. `DevilFruitType` (its own fields and completeness) |
| Persistence | `VersionBodyEntity` (`@MappedSuperclass`: id shared with `content_version`, romaji, workflow delegation); `VersionBodyRepository` (JPQL written with `#{#entityName}`); `VersionBodySpecifications`, `VersionBodySorting` | the entity table and its translations; the two native queries naming the table (slug check, slug assignment) |
| Service | `ContentService` (the whole workflow); `ContentValidator` (catalog, slug, uniqueness, completeness, no identical resubmission); `VersionBodyTitleSource` | a `ContentDefinition` (descriptor: type, repository, validator, mapping, not-found error) and a thin subclass binding the types, e.g. `DevilFruitTypeService` |
| Web | `ContentController` (read endpoints, workflow actions, new version, delete); `ContentEndpoint` (permissions per endpoint, applied to every section of `ApiPaths.CONTENT_SECTIONS`) | a concrete controller fixing the section path and types, plus create and edit, whose body is the entity's own (JSON for the type, multipart for the fruit) |

- **Pattern:** Strategy — the generic service is run with the entity's descriptor. The thin
  subclasses (service, validator, title source, controller) only bind the type parameters,
  so Spring injects one bean per entity unambiguously and springdoc documents each section
  with its exact schemas.
- **Convention:** each entity keeps its localized fields in a map named `translations`
  whose values have a `name` (`VersionBodyEntity.TRANSLATIONS`): the generic search and sort
  by name read it.
- **Not generic, deliberately:** the typed tables per entity (ADR-0002: no key/value table,
  no JSON column). The framework handles how a content is worked on, not what it is made of.
- Entity-specific workflow rules (e.g. "a fruit goes online only with its type online") will
  be hooks of the descriptor, added with the first entity that needs one.

## Alternatives considered

- **Copy per entity** (ADR-0002 as it was). No refactoring, but every workflow rule and every
  fix written once per entity.
- **A shared workflow service, with read and write services per entity.** Removes the
  duplicated workflow, but leaves list, detail, create and edit duplicated for every entity.
- **One controller for every section, `/{entity}` in the path.** The API contract could no
  longer describe each section's body, and permissions would be read from a variable path.
- **Abstract base classes with hook methods (Template Method) everywhere.** Ties every entity
  to the hierarchy; the descriptor keeps the entity's parts in one object, testable alone.
- **The descriptor as a Spring bean.** Not needed while a descriptor uses only its own
  entity's repository; it becomes one when a rule reads another entity.

## Consequences

- A new entity adds its body record, its tables (migration), its descriptor, its validator,
  its mappers, its concrete controller with create and edit, and its path in
  `ApiPaths.CONTENT_SECTIONS` — permissions and the dashboard follow.
- Reading an endpoint of an entity means knowing the base controller and the descriptor: a
  steeper entry than one class per entity.
- Introduced without any observable change: the Devil Fruit Type's tests pass unchanged and
  its generated OpenAPI contract is identical. The generic parts have their own tests, run
  with an entity that exists only in the tests.
- "Is this language still in use" still asks the Devil Fruit Type repository only; it must
  ask every entity once there are two.

## Updated by ADR-0005

Entity-specific rules are now a hook of the descriptor (`ContentRules`), and "language in use"
asks every entity - see ADR-0005.
