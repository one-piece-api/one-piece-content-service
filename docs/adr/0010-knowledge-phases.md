# ADR-0010: Knowledge phases

## Context

The timeline plan (`docs/implementation-plan-timeline.md` in `one-piece-api`, T9, step TL3;
workflow rev. 7, 3.6) adds **revealable attributes**: what the reader believed about a Devil
Fruit before the story revealed better (Gomu Gomu no Mi, a Paramecia, until chapter 1044).
The main fields stay the truth. The world does not change, only the reader's knowledge does, so
these are not events (ADR-0009).

## Decision

- **Backward diffs ("knowledge phases").** A version carries an ordered list of phases.
  Each phase is `supersededAt.chapter` plus only the fields revealed there, with the value
  believed before. A null or absent field is not overridden.
  - Resolution per field (`KnowledgePhases`, `DevilFruit.asBelieved`): the first phase not
    yet superseded at chapter N that defines the field, otherwise the truth. A phase whose
    chapter has no known number is never believed.
  - Type and subcategory are believed **together**. A phase defining `type` also defines the
    subcategory (absent = none). A phase defining only `subcategory` takes the type in force
    there: its own, then that of a later phase, then the truth (`typeInForceAt`).
- **Framework capability.** `PhaseRevealing extends FirstAppearing` (domain) and
  `KnowledgePhaseRules` (service) hold the entity-agnostic rules.
  - Rules: at least one field, a chapter required, a chapter after the first appearance and
    after the previous phase.
  - Two more `RelationDescriptor`s reuse `RelationRules` for linkable on save, online at
    publish/restore, and blocked retire: `PHASE_CHAPTER` and `PHASE_TYPE`.
  - Only the Devil Fruit uses it now.
- **Errors.** No new error codes for save: everything is `422 VALUE_INVALID` with `phases[i]…`
  paths, and lengths give `400`.
  - Order is checked on save against the chapter numbers as last approved, and again at
    publish/restore against the numbers online (`409 PHASES_OUT_OF_ORDER {phase}`).
  - `RELATED_NOT_ONLINE` and `SUBCATEGORY_NOT_ONLINE` carry `phase` when a phase is at fault.
- **Last description at submission.** The last phase must describe the fruit in every active
  language. This goes into `missingFields` (`VERSION_INCOMPLETE`, field
  `phases[last].translations[lang].description`), not into save, so that drafts can be saved
  one language at a time.
- **Types.** A type version cannot drop a subcategory that an online fruit's phase uses
  (`SUBCATEGORY_IN_USE` with `phase`). This extends the rule from S7.
- **Comparison.** Phases are part of normalization and equality (`VERSION_IDENTICAL`). They have
  **no id**: across versions they are matched by their `supersededAt` chapter (frontend, TL4).
- **Storage `V19`.**
  - `devil_fruit_version_phase` holds `position` (`@OrderColumn`), a chapter FK of constant
    entity type (as in V17), and a type FK.
  - `devil_fruit_version_phase_translation` holds the phase translations.
  - A rewrite updates rows in place by position. Position has no unique constraint, because
    Hibernate shifts positions in place.

## Alternatives

- **Forward snapshots** (each phase repeats every field): duplicated data and noisy diffs.
- **Phase id**: stable identity across versions, but nothing needs it. Chapter matching is
  enough because the chapters are strictly increasing.
- **Silent reordering by chapter**: it would hide the editor's mistakes, so order is enforced.
- **Last description checked on save**: it would block drafts written one language at a time.
- **Phases as events**: events change the world, while phases change only what the reader
  knows.

## Consequences

- Renumbering a chapter is not blocked when it would break an online fruit's order. The next
  publish or restore of that fruit catches it (known limit).
- Retiring a chapter or type and publishing a type each run one more lookup on the phase
  tables.
- The frontend (TL4) edits and compares phases and translates `PHASES_OUT_OF_ORDER`. The public
  `asOfChapter` resolution comes in TL5/TL6, on the shared `GomuGomuPhases` fixture.
- Other entities (Devil Fruit Type, story structure) can adopt `PhaseRevealing` additively.
