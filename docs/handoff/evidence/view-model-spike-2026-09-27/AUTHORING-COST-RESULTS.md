# What authoring a node cost: results (2026-09-27)

Scores [`AUTHORING-COST-PREDICTIONS.md`](AUTHORING-COST-PREDICTIONS.md) (`a29ded5c`, corrected at `1142a282`,
both before any code). The element is the identity banner; the work is in `RESULTS-2.md`.

## Scored

| # | prediction | result |
|---|---|---|
| 1 | The node lands in under 50 lines and is the least of the work | **Half.** `IdentityBanner` is 51 non-comment lines, `IdentityBannerView` 20 — at the limit, not under it. But see 2: it was not the least of the work. |
| 2 | Edge wiring outweighs the node **2:1** | **MISS, and reversed.** Node + view: 71 non-comment lines. Every edge — effect record, driver vocabulary, fake adapter, three Swing backends, builder: **+63 / −8 across 6 files.** The framework's edge cost is *lower* than I predicted, not higher. |
| 3 | I hit at least one generator rule I did not know | **MISS. None.** Regeneration succeeded first time. I had pre-emptively copied `StatusLine`'s `// Not final: node-local state` comment without having met the rule — so the folklore travelled and I never tested whether I would have hit it. That is a weaker result than a hit or a miss: I inoculated myself and cannot say. |
| 4 | When I hit one, the error names the fix | **Not exercised** for the generator. The one compile error was javac's, on the sealed `switch` — and it named file and line exactly, so the *edge* is guarded by exhaustiveness rather than by remembering. |
| 5 | **Absent a silent drop, no need to read the generated processor** | **Hit.** `SessionProcessor.java` was never opened. It was `grep`ed once to confirm the node appeared — checking output, not authoring against it. |
| 6 | Regeneration uneventful, both copies byte-identical | **Hit, with a caveat.** True with the network; `mvn -o -Pregen` **fails offline** — `build-helper-maven-plugin:3.6.0` is not in the local repo. Everything else here builds and tests offline. |

## What actually cost time, none of it predicted

1. **Re-anchoring static regression checks.** Two rule-8 checks asserted the literal text of the call sites this
   element removes. They failed correctly, and re-anchoring them — to the backend registration, plus a new
   assertion that `onSessionSnapshot` contains no `setIdentityNote` at all — was more thought than the node.
2. **A test that was not element-specific.** `StatusLineViewTest` matched `"backends: recorder"`, which any view's
   answer satisfies. The first user of a shared mechanism cannot write a test that distinguishes it from a second
   user who does not exist yet.
3. **One assertion of mine was simply wrong**: I expected one render and got two, because opening a log draws the
   first (clear) view. That is correct behaviour — without it a surface keeps the previous log's banner — and I had
   not thought it through.

**The pattern:** the framework cost roughly nothing. The cost was in the *tests around* the code being moved, and
it will recur for every element migrated. Budget the re-anchor, not the node.

## So: what I now think of Fluxtion, having built in it rather than reviewed it

**The strongest thing I can say is prediction 5.** I wrote a node, wired it into a graph, regenerated 2,000-odd
lines of dispatch, and never opened the output. That is the ergonomic claim I have been making all day on the
evidence that I never opened it while *reviewing* — which was weak evidence, and it now has better.

**The edge is cheaper than I expected and guarded by the compiler.** I predicted the adapter boilerplate would
dominate; it was 63 lines and the one thing I forgot — the new effect's `switch` case — was a compile error naming
the file and line. The sealed hierarchy makes "you added an effect and forgot to handle it" unrepresentable rather
than a runtime surprise. That is the paradigm's argument working at the edge as well as in the middle.

**What it did not do** is help with any of the three things that cost time. Re-anchoring checks, an over-broad
test and a wrong expectation are all *semantic*, and I have said all day that this is the split. Building rather
than reviewing did not move that line; it confirmed where it is.

**The one reservation I would keep.** Finding 3 says a node cannot be added offline. Small, but it is the kind of
friction that decides whether someone routes new state through a node "and regenerates — that is expected, not
avoided" (rule 9) or quietly hand-places a call instead. The paradigm's weakest point is not its runtime or its
ergonomics; it is any moment where the correct path is more inconvenient than the wrong one.

**And one correction to myself, recorded because it was the sharpest thing I got wrong today.** I described
telaminai/fluxtion#34 as "the most dangerous defect class this paradigm has" and said it "attacks the central
claim". The owner corrected me: `@FluxtionIgnore` means the reference is *not* serialised, by design, and a value
set on an ignored field is dropped because that is what the annotation asks for. The generated artefact was
faithful to what I declared; I declared the wrong thing, and then tested the node directly rather than firing
events at a real processor — against a rule I had already written down. The residual ask in #34 is a *diagnostic*
one, and that is all it ever should have been.
