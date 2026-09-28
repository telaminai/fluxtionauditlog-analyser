# readable-surfaces step 2 — predictions, before any code (2026-09-28)

Scope: fold `context`'s identity branches 2 and 3 into the published `surfaces.identityBanner` view, so that
`context` projects rather than composes. Branch 1 (observed at this request, with Follow off; it carries
`readsSuspended`) is left alone.

## What reading the code established first

- **Branch 2** reads `snapshot.logIdentity()` / `logIdentityReason()`: the same `OpenLog` fields the
  `IdentityBanner` node builds its view from. Folding it is a pure re-pointing.
- **Branch 3 is not a session fact.** It reads `store.readThroughAssessed()`, a property of the store that the
  session never receives. Folding it by reading the view is impossible until the fact reaches the session (spike
  Finding 2).

## Design

1. `LogOpened` carries `readThroughAssessed`, reported by the adapter from the store it installed, exactly as
   `followable` already is. There is **no defaulting constructor**: the SPI's rule is that a store that does not
   look is not assessed, and a default would answer "assessed" for any caller that forgot.
2. `OpenLog` holds it. The banner's view states a `NOT_ASSESSED` verdict when the session has no verdict and the
   store does not assess. It never warns (`shown=false`), so no surface draws a banner for it: the status bar
   still speaks only of a change.
3. `context` projects `log.identity` from the published view: its verdict lower-cased, with `NOT_ASSESSED` as
   `"not assessed"`, and the view's reason. `context` keeps its own words for the not-assessed sentence, as every
   backend composes its own words. The response shape is unchanged.

## Predictions

1. Regeneration is needed (a new `OpenLog` field, and `LogOpened`'s shape). The brief says `-Pregen` needs the
   network. I predict **it runs offline** here, because `build-helper-maven-plugin` 3.6.0 is in this machine's local
   repository (the same finding as the #58 review, F5).
2. The payload is **byte-identical** for the three identity cases: a session verdict, not assessed, and
   assessed-with-no-verdict (absent). `ContextSectionsTest`'s recorded projection byte counts **do not move**,
   because the response shape does not change.
3. The fixtures' 18 `LogOpened` constructions each change mechanically, with `true` (a file-backed store), and no
   assertion changes.
4. One retained control is orphaned, whichever is anchored on the branch-2/3 lines (if any). The preflight names
   it.
5. At least one new control is **equivalent**, and I will report it as such rather than force a witness. The
   likely one: "branch 2 reads the snapshot" versus "branch 2 reads the view". The two are equal in every cycle,
   because the view is emitted in the same dispatch that moves the verdict.
