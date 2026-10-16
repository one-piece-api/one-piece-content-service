# ADR-0010: Reveals

> **Renamed 2026-10-16 (T12).** First named "knowledge phases" (`supersededAt`, tables
> `devil_fruit_version_phase*`). A reveal is an **instant** on the reader axis, like an Event on
> the world axis: its chapter `revealedAt` and what the reader believed before it. The period of
> a belief is derived, never stored. Rename only, in code, contract and DB (`V20`).

## Context

The timeline plan (`docs/implementation-plan-timeline.md` in `one-piece-api`, T9, step TL3;
workflow rev. 7, 3.6) adds **revealable attributes**: what the reader believed about a Devil
Fruit before the story revealed better (Gomu Gomu no Mi, a Paramecia, until chapter 1044).
The main fields stay the truth. The world does not change, only the reader's knowledge does, so
these are not events (ADR-0009).

## Decision

- **Backward diffs ("reveals").** A version carries an ordered list of reveals.
  Each reveal is `revealedAt.chapter` plus only the fields revealed there, with the value
  believed before. A null or absent field is not overridden.
  - Resolution per field (`Reveals`, `DevilFruit.asBelieved`): the first reveal after
    chapter N that defines the field, otherwise the truth. A reveal whose
    chapter has no known number is never believed.
  - Type and subcategory are believed **together**. A reveal defining `type` also defines the
    subcategory (absent = none). A reveal defining only `subcategory` takes the type in force
    there: its own, then that of a later reveal, then the truth (`typeInForceAt`).
- **Framework capability.** `Revealing extends FirstAppearing` (domain) and
  `RevealRules` (service) hold the entity-agnostic rules.
  - Rules: at least one field, a chapter required, a chapter after the first appearance and
    after the previous reveal.
  - Two more `RelationDescriptor`s reuse `RelationRules` for linkable on save, online at
    publish/restore, and blocked retire: `REVEAL_CHAPTER` and `REVEAL_TYPE`.
  - Only the Devil Fruit uses it now.
- **Errors.** No new error codes for save: everything is `422 VALUE_INVALID` with `reveals[i]…`
  paths, and lengths give `400`.
  - Order is checked on save against the chapter numbers as last approved, and again at
    publish/restore against the numbers online (`409 REVEALS_OUT_OF_ORDER {reveal}`).
  - `RELATED_NOT_ONLINE` and `SUBCATEGORY_NOT_ONLINE` carry `reveal` when a reveal is at fault.
- **Last description at submission.** The last reveal must describe the fruit in every active
  language. This goes into `missingFields` (`VERSION_INCOMPLETE`, field
  `reveals[last].translations[lang].description`), not into save, so that drafts can be saved
  one language at a time.
- **Types.** A type version cannot drop a subcategory that an online fruit's reveal uses
  (`SUBCATEGORY_IN_USE` with `reveal`). This extends the rule from S7.
- **Comparison.** Reveals are part of normalization and equality (`VERSION_IDENTICAL`). They have
  **no id**: across versions they are matched by their `revealedAt` chapter (frontend, TL4).
- **Storage `V19` (renamed in `V20`).**
  - `devil_fruit_version_reveal` holds `position` (`@OrderColumn`), a chapter FK of constant
    entity type (as in V17), and a type FK.
  - `devil_fruit_version_reveal_translation` holds the reveal translations.
  - A rewrite updates rows in place by position. Position has no unique constraint, because
    Hibernate shifts positions in place.

## Alternatives

- **Forward snapshots** (each reveal repeats every field): duplicated data and noisy diffs.
- **Reveal id**: stable identity across versions, but nothing needs it. Chapter matching is
  enough because the chapters are strictly increasing.
- **Silent reordering by chapter**: it would hide the editor's mistakes, so order is enforced.
- **Last description checked on save**: it would block drafts written one language at a time.
- **Reveals as events**: events change the world, while reveals change only what the reader
  knows.

## Consequences

- Renumbering a chapter is not blocked when it would break an online fruit's order. The next
  publish or restore of that fruit catches it (known limit).
- Retiring a chapter or type and publishing a type each run one more lookup on the reveal
  tables.
- The frontend (TL4) edits and compares reveals and translates `REVEALS_OUT_OF_ORDER`. The public
  `asOfChapter` resolution comes in TL5/TL6, on the shared `GomuGomuReveals` fixture.
- Other entities (Devil Fruit Type, story structure) can adopt `Revealing` additively.

## Addendum: Reveal title (TL3b)

### Context

An editor needs a one-glance label for each reveal ("The fruit is Nika"), and the
timeline reads forward in the UI (plan T10, T11), so a chapter alone says too little.

### Decision

- Each reveal has a **title per language** (max 60), `DevilFruitReveal.titles`. It is about the
  reveal, not a believed value: `definesNothing()` is unchanged, so a title alone does not
  satisfy "at least one field per reveal".
- **Required at submission** for every reveal and every language
  (`VERSION_INCOMPLETE`, field `reveals[i].titles[lang]`), never on save, so drafts can be saved
  piece by piece.
- Storage `V21`: nullable `title` on `devil_fruit_version_reveal_translation`, mapped by a
  dedicated `DevilFruitRevealTranslationEmbeddable` rather than the shared
  `TranslationEmbeddable`, which would give the fruit's own translations a column they never use.
- A language holding only a title counts among the languages of the version; a title takes part
  in equality, so changing only a title makes a new version not identical.
- Over-long titles are `400` (bean validation), like the other lengths.

### Alternatives

- **No title, chapter only**: discarded by the owner (2026-10-16).
- **`title` in the shared `TranslationEmbeddable`**: leaks a reveal-only field into every translation.

### Consequences

- The public `revealHistory` carries the titles in TL5.
- The backend compares versions only for equality: the frontend (TL4) compares reveals field by field and
  includes the title.
