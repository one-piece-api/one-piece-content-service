# ADR-0011: Reader axis: windows on list entries, checked as points

Extends ADR-0008 (first appearance). Supersedes the `former` flag of ADR-0007 (addendum, lists of a version) and
the "one current user" form of the fruit invariant of ADR-0009.

## Context

Workflow revision 17 (`docs/user-flows/content-editorial-workflow.md` 3.7 in `one-piece-api`) and the reveals plan
(`docs/implementation-plan-reveals/`, D4, D8, D9, step R2): what the reader knows of a character changes along the
story. Ace loses the Mera Mera no Mi at Marineford and Sabo eats it at Dressrosa; Usopp leaves the crew and comes
back; Koby changes rank. A stored `former` flag says only how things are *today*, so the public API could not
answer "how is it at chapter N" and two users of a fruit at different times looked like a conflict.

## Decision

- **A window on every list entry.** `StoryWindow(discovered, ended)` (`domain/readertime`), each a `StoryPoint`
  (ADR-0008): where the reader learns the entry starts and stops being true. `discovered` is required to submit
  (`VERSION_INCOMPLETE`, `epithets[0].discovered`), `ended` is optional. Epithets, aliases, species, fruits,
  relatives each have one window; a membership has **periods**, each a window with its own `role` and
  `marineRank`. `former` is derived (an end on any axis), never stored.
- **Capabilities by interface, rules written once.** `Windowed` (an entry), `WindowedItem` (an item: its named
  windows, its period sequences, its points; extends `FirstAppearing`). `ReaderAxisRules` checks any
  `WindowedItem`, the numbers compared by the library `one-piece-reader-time` (`PointRules`, `PeriodRules`):
  - save (`422 VALUE_INVALID`, the part named, e.g. `affiliations[0].periods[1].discovered.episode`): each chapter
    or episode linkable; a part only on an axis of the first appearance; `discovered` not before it; `ended` not
    before `discovered` and only on its axes; periods in order, not overlapping, only the last one open. The
    order is checked on the latest approved numbers, once every part is known;
  - publish / restore (`409`): each part online (`RELATED_NOT_ONLINE`, detail `{field, chapterId,
    chapterNumber}`), then the same order rules on the numbers online (`STORY_POINT_OUT_OF_RANGE`, detail
    `{field, message}`): a chapter may have been renumbered since the save;
  - retire of a chapter or episode refused while an online item uses it as a point (`ONLINE_ITEMS_LINKED`).
- **Wire: fields on the entry**, no `window` level, as the error paths: `{ "alias": "Sogeking", "discovered":
  {chapter, episode}, "ended": ... }`; points in requests are `FirstAppearanceRequest` (ids), in responses
  `FirstAppearanceResponse` (numbered references, `null` for none). Species and aliases become objects.
- **Fruit invariant on windows (ADR-0009).** Without `multipleUsers`, the windows of the online users of a fruit
  must not overlap on either axis (from `discovered` included to `ended` excluded): Ace ending where Sabo starts
  is valid; an empty window (`discovered = ended`) overlaps nothing. Still checked on publish and restore only;
  every fruit of the character is now locked `FOR UPDATE`, in id order, since a former use can overlap too.
- **Current is the open window.** Filters (`affiliation`, `affiliationKind`, `devilFruit`), member and user counts
  read entries with no end on either axis.
- **Published views keep their columns** (`V38`): membership `former` = no open period, `role` and `marine_rank`
  of the last period; fruit `former` = an end on any axis. The views of the windows come with R6.
- **Storage** (`V38`): four point columns per list table (`discovered_{chapter|episode}_id`,
  `ended_{chapter|episode}_id`) with constant entity types and composite FKs as `V17`/`V18`, each indexed for the
  retire check; `character_version_affiliation_period` (+ translation) holds the periods. No data conversion: the
  content database restarts empty (D11).

## Alternatives

- **Keep `former` next to the window**: two sources of truth that can disagree.
- **A `window` object in the JSON**: one more level in every request and error path, unlike revision 17.
- **One window per membership, role and rank outside it**: cannot say Usopp left and came back, nor Koby's ranks.
- **Order rules in content-service**: public-api needs the same comparison for its filter; the library keeps one
  implementation.
- **No overlap check, only "one current user"**: Sabo could be published with a window overlapping Ace's.

## Consequences

- Breaking contract for clients: the user-frontend character editor and the e2e character specs follow in R8
  (plan decision 4); `STORY_POINT_OUT_OF_RANGE` needs IT/EN wording in `block-words.ts`.
- A draft with windows needs its first appearance first: without one, no axis exists and every part is refused
  (3.7.4).
- Every windowed entity added later (R3-R5) implements `WindowedItem` and gets the rules without new code.
