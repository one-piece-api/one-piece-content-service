# ADR-0005: Entity rules, relations between contents, and the lock that keeps them true

## Context

The Devil Fruit is the first content that points to another: every fruit belongs to a Devil
Fruit Type (`docs/implementation-plan-devil-fruit.md` in `one-piece-api`, D1-D5). That
brings an invariant the workflow alone cannot hold - **a fruit online always has its type
online** - and rules that belong to one entity, not to the generic workflow of ADR-0004:

- a fruit goes online (publish, restore) only while its type is online;
- a type is not retired while fruits are online with it;
- a fruit may only be linked to a type that passed review at least once.

Two of them read data another transaction may be changing. A publication and a retirement
that run together each see a consistent state - the type online, no fruit online yet - and
both succeed: nothing writes the row the other reads, so the database does not stop them
(write skew).

## Decision

- **The link is an id.** `devil_fruit_version.type_content_id` points to the type's
  *content*, not to one of its versions, through a composite foreign key
  `(type_content_id, type_entity_type) -> content (id, entity_type)` where the second column
  is the constant `'DEVIL_FRUIT_TYPE'`: a fruit can never point to a fruit, and a type with
  fruits cannot be deleted. Nullable, because a draft may not have chosen one; a response
  gives the type as it is today (`{id, romaji, names}`), never a copy kept in the version.
- **Entity rules are a descriptor hook** (`ContentRules`, Strategy): `blockOf(action,
  contentId, version)` answers why an action is refused, reading only; `lockBefore(action,
  contentId, version)` takes the lock the rule relies on. The generic service asks, after the
  transition rules, for every action that changes a status: lock first, then the rule. The
  same `blockOf` fills `blockedActions` in a version's response, so the disabled button and
  the endpoint cannot disagree. An entity with no rules uses `ContentRules.none()`.
- **A refusal is `409 CONTENT_VERSION_ACTION_BLOCKED`**, carrying a closed `reason`
  (`TYPE_NOT_ONLINE`, `ONLINE_FRUITS_LINKED`) and its `detail`; distinct from
  `CONTENT_VERSION_ACTION_CONFLICT`, a version in the wrong state. `content:admin` does not
  lift it: it relaxes who may act, not the data.
- **Pessimistic lock on the type's `content` row**, JPA `PESSIMISTIC_READ` / `WRITE`
  (`FOR SHARE` / `FOR UPDATE`): a fruit going online holds it shared, a type being retired
  holds it exclusively. Fruits of one type go online together; a retirement waits for them
  and a publication waits for a retirement. Each side reads what its rule needs *after* the
  lock, with a new query, so under `READ COMMITTED` it sees what the other committed. The
  `lock_version` of ADR-0002 is optimistic and per version: it does not serialize these.
- **The rule of the fruit's type lives where the data is:** `DevilFruitTypeRules` reads the
  fruit repository (`countOnlineLinkedTo`): a one-way dependency, rules to repository.
- **Choosing a type is validated on every save and again at submission** by
  `DevilFruitValidator` through a hook of `ContentValidator` (`requireValidRelations`): a type
  with at least one approved version. An unknown id, a content that is not a type and a type
  nothing of which was approved answer alike (`422` on `type`).
- **Reading across the link:** the list of fruits takes `?type=<id>` through a relation
  filter of `ContentFilter` (relation `type` is the attribute `typeContentId`); each type row
  carries `devilFruitCount`, equal to the total of that list for the same caller; a type
  detail does *not* embed its fruits - the card asks the list. `GET
  /devil-fruit-types/linkable` (paginated, `content:write`) feeds the editor's choice.
- **"Language in use"** asks every entity's repository (Composite).

## Alternatives considered

- **Hooks `beforePublish` / `beforeRestore` / `beforeRetire` on the descriptor:** to fill
  `blockedActions` the service would need to know which hook answers which action, and each
  new regulated action would widen the interface.
- **`SERIALIZABLE` transactions:** the loser is aborted and retried with no reason to give
  the user.
- **A trigger or `CHECK` in the database:** the rule reads another table, and a trigger
  error cannot say which fruits block.
- **A cascade (retiring a type retires its fruits), or retiring while hiding the type
  publicly:** silent, and the second leaves orphans online.
- **Copying the type's name into the fruit's version:** it would go stale at the first
  rename; the plan chose "relations lead to today's card" (D1).
- **Embedding the fruits in the type's detail:** a second way to ask the same list, with
  its visibility rules applied twice; kept for the public API, whose answers are cached.

## Consequences

- A rule that needs another content is written once, as an entity's `ContentRules`, and is
  shown and enforced in the same place; the workflow stays unaware of fruits and types.
- A retirement of a type waits, for the length of one transaction, for a fruit going online
  with it, and the other way round. A draft saved with a type waits only for the same short
  retirement (the foreign key takes a key-share lock on the type).
- The next entity pointing to another follows the pattern: id column, composite foreign key,
  rules, relation filter.
- Supersedes two statements of ADR-0004: entity rules are no longer "to be added with the
  first entity that needs one", and "language in use" no longer asks the Devil Fruit Type
  repository only.

## Note: subcategories (2026-10-10)

A type's subcategories live in its version and a fruit may name one (`subcategoryId`, no
foreign key: rows belong to versions). The same two rules and the same lock cover it:

- **Fruit online** (publish / restore): its subcategory must exist in the type's *published*
  version, else `SUBCATEGORY_NOT_ONLINE`; checked after the type rule, under the same shared lock.
- **Type version online** (publish / restore): it cannot drop a subcategory used by online
  fruits, else `SUBCATEGORY_IN_USE`. To exclude a racing fruit it takes the type's `content`
  row **exclusively**, as a retirement does; two versions of one type going online now also
  run one after the other.
- `content:admin` lifts neither. The published views carry the subcategories (`V13`).
