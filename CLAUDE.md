# one-piece-content-service

Editorial workflow (versions, review, publish, retire) over a generic content framework. Setup and endpoints: `README.md`. Product rules: `../docs/user-flows/content-editorial-workflow.md`.

## Commands

```bash
./gradlew check > "$TMP/check.log" 2>&1; echo "exit=$?"   # tests + format + Checkstyle (Docker on)
./gradlew format
./gradlew updateOpenApiSpec && ./scripts/generate-bruno-collection.sh   # after any contract change
./scripts/deploy-local.sh                                              # image -> kind, rollout
```

Port 8082 (`/api/content`); through the cluster: `http://localhost:4180/api/content/...`.

## Map (`dev.onepieceapi.contentservice`)

Layered packages, one sub-package per feature inside each layer:

| Package | Holds |
|---|---|
| `domain/workflow` | The framework's domain: `Version`, `VersionStatus`, transitions (`TransitionPolicy`, `TransitionRule`), `BlockReason`, `ContentBody`, `EntityType` |
| `domain/<feature>` | Body records per entity (`devilfruit`, `devilfruittype`, `story`, `readertime`, ...) |
| `persistence/entity` | `VersionBodyEntity` (`@MappedSuperclass`) + one `<X>VersionEntity` per entity, embeddables |
| `persistence/{repository,mapper,specification,projection}` | `VersionBodyRepository`, entity mappers, `VersionBodySpecifications`/`Sorting` |
| `service/content` | Generic: `ContentService` (whole workflow), `ContentDefinition`, `ContentRules`, `RelationDescriptor`/`RelationRules`, capabilities (`FirstAppearanceRules`) |
| `service/<feature>` | Per entity: `<X>Definition`, `<X>Rules`, `<X>Links`, thin `<X>Service` |
| `service/{validation,exception}` | `ContentValidator` + per-entity validators; `ContentErrorCode`, not-found exceptions |
| `web/controller` | `ContentController` (generic read + workflow) + concrete `<X>Controller` (path, create, edit) |
| `web/{dto,mapper,validation,security}` | Requests/responses, web mappers, `SecuredEndpoint`, `ApiPaths.CONTENT_SECTIONS` |
| `src/main/resources/db/migration` | Flyway `V<n>__<name>.sql`; `published` schema views read by public-api |

## Key ideas (ADRs in `docs/adr/`)

- 0002 one version table with status; 0003 `published` views are the contract with public-api.
- 0004 generic framework = Strategy: the generic service runs with each entity's `ContentDefinition`; per-entity classes only bind types.
- 0005 entity rules are descriptor hooks (`ContentRules`), blocked actions carry a `BlockReason`.
- 0006 images stored in PostgreSQL; 0007 natural key per entity + generic relations.
- 0008 first appearance (chapter, episode or both).
- 0009 invariants between contents: checked on publish, serialized by locks (fruit row, pair advisory lock).

Adding an entity type: skill `new-content-type`. Copy the closest existing entity rather than inventing a new shape.

## Gotchas

- The migrations are also published as `one-piece-content-service-migrations:<latest V number>` (CI on `main`); public-api's tests build the schema from it. After a migration that public-api needs, push here first, then bump that version in public-api's `build.gradle.kts`.
- New `BlockReason` values need IT/EN wording in the frontend (`block-words.ts`).
- Local DB: `kubectl exec -i -n data one-piece-postgresql-0 -- env PGPASSWORD=content-service-db-change-me-locally psql -U content_service -d content_service`.
