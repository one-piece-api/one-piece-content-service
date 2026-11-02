# ADR-0012: Field reveals: initial knowledge and reveals per entity, rules written once

Extends ADR-0011 (reader axis) and ADR-0007 (natural key, slugs).

## Context

Workflow revision 17 (`docs/user-flows/content-editorial-workflow.md` 3.7.1 in `one-piece-api`) and the reveals
plan (`docs/implementation-plan-reveals/`, D3, D7, D9, D13, step R4): some fields are believed one way at the first
appearance and learned otherwise later. The Gomu Gomu no Mi, a Paramecia, is the Hito Hito no Mi, Model: Nika, a
Mythical Zoan (chapter 1044, episode 1071); Ace is alive until Marineford; Sanji is a Vinsmoke. The main fields
must stay the truth of today, and the public API must be able to answer "what does the reader know at N".

## Decision

- **Main fields = current value.** An item adds `initialKnowledge` (what is believed at the first appearance) and
  `reveals`, each with `revealedAt` (a `StoryPoint`), the `fields` it names, a `title` per language and optional
  values. A field named without a value reveals the current one. Revealable fields are grouped: Devil Fruit
  `NAME`/`ROMAJI`/`TYPE` (type + subcategory), Character `IDENTITY` (romaji, Japanese name, names)/`STATUS`,
  Bounty `INACTIVE`. Enums are uppercase on the wire like every other enum.
- **Capability by interface, rules written once.** `Revealing<F, V>` (`domain/readertime`) with a per-entity enum
  `F` (`RevealableField`) and values record `V` (`RevealedValues`: `DevilFruitKnowledge`, ...). On save
  (`422 VALUE_INVALID`) `FieldRevealRules` checks the shape (a point, a field, values only for named fields,
  initial values only for fields some reveal names, romaji/name known from the start when revealed, a key with
  its values); `ReaderAxisRules` checks the points like every other point (ADR-0011): after the first appearance
  (strictly), on its axes, and two reveals of the same field never on the same point of an axis. Submitting asks
  every title and every revealed name in every language (`VERSION_INCOMPLETE`).
- **References follow the current field's rules.** A revealed fruit type is linkable, its subcategory belongs to
  it, it must be online to publish (`TYPE_NOT_ONLINE`/`SUBCATEGORY_NOT_ONLINE`, detail starting with
  `{initialKnowledge: true}` or `{revealIndex: i}`), and it keeps the type from being retired
  (`ONLINE_FRUITS_LINKED`); `RelationDescriptor` `REVEALED_TYPE` reuses the type's messages.
- **Slugs (D13).** Every romaji (current, initial, revealed) is unique across other items and registered in
  `content_slug` at publish/restore; no point is stored with a slug: which slugs are known at N is resolved from
  the online version.
- **Storage** (`V40`): per entity `<x>_version_initial_knowledge` (`@SecondaryTable`, one row per version) with its
  translation table; `<x>_version_reveal` (ordered, point columns as ADR-0011), `_reveal_field`,
  `_reveal_translation` (title and revealed name per language). Romaji slugs are generated columns, indexed.

## Alternatives

- **Store the initial value in the main fields and the truth in the reveals**: every filter, slug and link would
  read the past; the current value is what most readers and all relations need.
- **One reveal per field**: Nika reveals name, romaji and type at once; one title per event reads better.
- **A generic key/value reveal table or a JSON column**: loses foreign keys and typed checks (ADR-0002/0004, D9).
- **Initial knowledge columns on the version table**: most versions know nothing beforehand; a secondary table
  keeps the version row unchanged.
- **A point stored with each slug**: duplicates what the online version already says.

## Consequences

- Contract additions only (new optional fields): the back-office input follows in R8, the
  published views in R6, the reader filter in R7.
- public-api must bump the migrations artifact to `V40` before reading the new tables.
- A new revealable entity adds an enum, a values record and its tables; the rules need no change.
