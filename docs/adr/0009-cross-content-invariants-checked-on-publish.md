# ADR-0009: Invariants between contents, checked on publish and kept true by locks

## Context

Two rules of the Character plan (`docs/implementation-plan-character/`, D12 and D13 in `one-piece-api`) are about
*several contents at once*, not about one content and its target (ADR-0005):

- **One current user per Devil Fruit:** among online characters, a fruit is named as not `former` by at most one,
  except fruits with `multipleUsers` (SMILE).
- **A pair of characters is linked once:** if Garp names Luffy as `GRANDCHILD`, Luffy does not name Garp.

Like the bounty chain (ADR-0007, B2), each invariant can be broken by two transactions that each see a consistent
state (write skew, ADR-0005). Unlike the chain, they must not reject drafts: Sabo's draft naming the fruit must be
writable while Ace's update, which releases it, is still in review.

## Decision

- **Check at the moment content goes online, not on save.** The rule runs on publish and restore only
  (`RelationRules.GOING_ONLINE`) and is a `blockOf` of the entity's rules, so the same code fills
  `blockedActions` (a button disabled in advance) and refuses the endpoint (`409 CONTENT_VERSION_ACTION_BLOCKED`,
  `DEVIL_FRUIT_IN_USE`, `RELATIVE_ALREADY_LINKED`). Drafts and reviews are never compared with each other.
  The save checks only what is cheap and certain (not yourself, not already named by an open version of the
  other character: 422 `relatives[i]`), as a courtesy; the publish check is the invariant.
- **Lock by the thing the invariant is about, taken in `lockBefore`, in a fixed order.**

  | Invariant | Lock | Why this one |
  |---|---|---|
  | Bounty chain (ADR-0007) | one advisory lock, `bounty-chain` | the chain has no row to lock |
  | Fruit's current user | the fruit's `content` row: `FOR UPDATE` for a current use, `FOR SHARE` for a former one; the fruit itself takes `FOR UPDATE` when it goes online | the fruit is a row; a fruit going online or retired waits for characters naming it |
  | Relatives pair | advisory lock keyed by the pair (`character-relatives:<smaller id>:<larger id>`) | a pair is not a row; the key is the same whichever side asks |

  Several locks are always taken sorted (fruit ids, pair keys), so two publishes sharing items never wait for each
  other in turns (no deadlock). Each rule reads what it needs *after* the lock, with a new query, so under
  `READ COMMITTED` it sees what the other transaction committed. Advisory locks are `pg_advisory_xact_lock`:
  released at commit or rollback, nothing to clean.
- **Exceptions are data of the target, not branches of the rule:** `multipleUsers` on the fruit decides whether
  the check runs; the rule on publishing a *fruit* version (without `multipleUsers`, at most one current user)
  closes the other direction.
- **The inverse is derived, never stored.** A relative is stored once, by one of the two characters; the other
  side is a query for the online characters naming this one (`relativesOf`, kind inverted, label by gender).
  Same for a fruit's users and an affiliation's `memberCount`. A second stored copy would need its own invariant
  ("both sides agree") and a lock to keep it.

## Alternatives

- **Unique index** (`UNIQUE (devil_fruit) WHERE current AND online`): the online state is a property of the
  version's status, spread over two tables; a partial index cannot express it, and it would also block drafts.
- **Serializable isolation** for publish: retries on conflict for every publish of every entity, to protect a few
  rules; framework-wide cost for a local need.
- **A row per pair** (`character_relative_pair`) to lock with `FOR UPDATE`: a table and its upkeep for a lock that
  holds no data. The advisory lock is the lighter standard PostgreSQL tool.
- **Storing both sides of a relative:** reads need no join, but two copies drift, and a draft of one side would
  have to edit the other character's version.
- **Checking on save as well:** refuses Sabo's draft for Ace's still-open version; the fruit would be unwritable
  until the owner publishes first, which is a workflow rule the editors never asked for.

## Consequences

- A publish may fail late (`409`) for something that was fine on save; the block explains it, naming the fruit or
  the character, and the editor sees it beforehand in `blockedActions`.
- Publishes touching the same fruit or pair are serialized: invisible at this scale, but each new invariant of this
  kind adds a lock to take in a documented order.
- Three different lock keys (a constant, a row, a pair text) live in `ContentRepository` and the entity's rules;
  a fourth should be weighed against this table before adding another style.
- Derived answers cost a query per read of the detail, on indexed child tables of online versions.
