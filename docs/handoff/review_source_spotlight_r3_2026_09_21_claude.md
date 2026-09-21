# Re-review: source spotlight proposal, third round — 2026-09-21 (reviewer: claude)

**Verdict: ONE BLOCKER, otherwise READY FOR HANDOFF.** The revision answers SR-1 to SR-10 substantively
rather than by assertion. I checked every mechanism it names and each one either exists where it says or
is genuinely absent as it says. The preparation stage has the insertion point it claims, the registry
pattern it cites is real, the viewport hooks are reachable, the pane-opener gap is exactly as described,
and the archive resolver behaves as stated.

The blocker is not in the contract. It is that the proposal is written against a baseline 28 commits old,
and in those 28 commits main released a decision about the very geometry this proposal revises. As
written, the design-geometry section would replace a contract shipped hours ago and describe the
replacement as a compatibility correction.

**One correction to my own review first**, because it is the reason the author had to push back.

---

## Correction: SR-6 was wrong

I wrote that the real-display acceptance "silently skips on headless CI, so it is not a gate", and
recommended treating the display checks as developer-run. **That was wrong, and the response is right.**

`.github/workflows/ci.yml:61-91` already has a `ui-frame` job: it installs Xvfb, runs the frame suites
with `-Djava.awt.headless=false` in both Maven and the test JVM, and then checks each suite's surefire XML
and fails on `skipped != 0` or `tests == 0`. Its own comment names the exact failure mode: *"a quiet skip
is exactly the failure mode being closed"* (`:62-64`).

What I measured — a headless Maven run skipping four tests — is the thing that job exists to prevent, not
evidence about CI. I drew a conclusion about the repository's regression protection without reading
`.github/`, which is the same error I have criticised elsewhere: reasoning about a system instead of
reading it.

The residual requirement is narrower than my finding but real, and the revision states it correctly. The
job hard-codes its classes **twice** — the `-Dtest=` list at `:80` and the `for c in ...` loop at `:86`. A
new suite added to one and not the other runs without being gated, or is gated without running. The
proposal's "Add a `JavaSourceSpotlightFrameTest` suite to both lists" (line 326) is exactly the right
instruction; I would cite the two line numbers so nobody adds it once.

---

## Findings

### T-1 · Blocker · the baseline is 28 commits stale, and the design-geometry section would revert a decision released today

The proposal and the response both state they were checked against local main `9c10de82`. `origin/main`
is `401da35b`, **28 commits ahead**, including the v1.17.0 release.

Commit `c6aeafde`, *"Measure design spotlights after scrolling within their viewport"*, already changed
the geometry this proposal revises. `DesignSourcePanel.lineBounds` on main now ends:

```java
if (at.isEmpty() || !text.getVisibleRect().contains(at)) return Optional.empty();
```

That is **refuse a line not wholly inside the viewport**. The released CHANGELOG says so in as many
words: *"Design spotlights settle scrolling before measuring and refuse lines outside their text
viewport."*

The proposal requires the opposite policy and applies it to design:

- line 176: "Intersect the band with the viewport; never return a rectangle over a toolbar…"
- lines 237-240: "**Design line and bean targets gain these hooks too.** Apply the same vertical clipping
  and logical wrapped-row geometry to their line bands… These are explicit compatibility corrections with
  their own tests, not a claim the hooks already exist."

They are not compatibility corrections. Clipping-and-lighting where main refuses is a behaviour change to
a released feature, and acceptance 5's requirement to "Cover design line/bean bands as well as Java bands"
(line 305) would assert the opposite of what 1.17.0 shipped.

The same commit also added `DesignSourcePanel.revealLine` — a navigation ticket, `validate()`, then
`scroll(line)` — which is "settle the reveal before measuring". The proposal's line 172 ("Reveal and lay
out the chosen pane before measuring") reads as new work and is already solved for design.

**Required corrections.**

1. Re-baseline the proposal on `origin/main` and re-check the cited code. Every file:line in it, mine
   included, was read at `9c10de82`.
2. Decide the design policy explicitly and say which: either **Java clips and design refuses**, as two
   stated policies with the reason, or design moves to clipping — in which case it is a change to 1.17.0
   behaviour, needs its own CHANGELOG line, and the existing refusal tests and their mutation witnesses
   must be re-derived rather than extended.
3. Reuse `revealLine` for the settle step rather than specifying it again, or say why the Java panes need
   a different one.

This is a blocker only because shipping it as written would quietly undo a released decision. The
contract itself is sound.

### T-2 · Should fix · the freshness acceptance misses two things the resolver actually does

The SR-10 mechanism is confirmed. `MavenSourceResolver:32` is
`Map<String, Optional<String>> cache = new ConcurrentHashMap<>()` — keyed by FQN, holding **text only**,
with no archive path. The proposal's claim at lines 122-123 is accurate, and the `*-sources.jar` fallback
exists and is disabled by default (`:19-20`), so making it required in this slice (line 130) is a real
scope addition and correctly flagged as one.

Two behaviours of that cache are not covered by acceptance 7 (lines 311-315):

- **Misses are cached too.** The class comment says so: *"Both hits and misses are cached per FQN"*
  (`:25`). A refresh acceptance that only re-reads hits leaves a class invisible for the rest of the
  session after one failed lookup — and under this proposal that is a refusal the assistant cannot clear.
- **The jar list is discovered once per session** — *"a filesystem walk, lazy, then cached"* (`:22`). A
  sources jar that appears mid-session is not seen.

**Required correction.** Say that invalidation clears negative entries as well as positive ones, and
state the once-per-session jar discovery as a limit of this slice rather than leaving it to be discovered
during implementation.

### T-3 · Should fix · the node deferral left a reference behind

Line 40 still reads "Family keywords are case-insensitive; FQNs **and instance ids** retain their
spelling." Instance ids are no longer part of this vocabulary — the table at lines 34-37 has only the two
Java forms, and the deferral at 48-54 is otherwise thorough. One clause to drop.

### T-4 · Low · `partial` has no stated design counterpart

`partial: true` is echoed for a clipped wrapped Java line (lines 178, 205). If T-1 is resolved by giving
design clipping too, design needs the same echo field and acceptance; if design keeps refusing, say that
`partial` is Java-only and why. Either way it should not be left implicit.

---

## Verified as accurate — no action needed

These were the claims most worth checking, and all of them hold.

- **SR-1, the insertion point exists and is clean.** `MainFrame.java:2448` calls `precheck`, `:2452` calls
  `resolveAll`, and **nothing between them touches the view** — the intervening statement only maps
  requests to names. So "a frame-owned stage between the successful pure precheck and the first
  resolveAll call" (lines 87-88) is available exactly as described, and "a failure on the second Java
  request leaves the entire view and lit set unchanged" (line 95) is achievable at that point.
- **SR-2, the pattern cited is the right one.** `designSpotlightRevisions` (`MainFrame.java:79`) is
  populated only after the set succeeds (`:2471-2473`, inside the post-`resolveAll` loop), which matches
  the proposal's "published only after the whole set succeeds and replace/add is applied" (lines 108-109).
  `clearSpotlightHere` (`:2284`) and `spotlight.setOnPressed` (`:2042`) are the dismissal paths the
  proposal wires cleanup into.
- **SR-3, the hooks are reachable.** Each Java pane is a `JScrollPane` (`SourcePanel.java:532`), so a
  `JViewport` exists to listen on. `installSpotlight` (`MainFrame.java:2039-2048`) still registers only
  `componentResized`, so "window resize alone is insufficient" (line 224) remains true.
- **SR-4, the deferral is the right call and honestly framed.** `EventProcessorModel.resolveSimpleType:82`
  does return a same-package guess indistinguishably, and `fieldTypes` (`:29`) is a map that has already
  collapsed conflicts. The proposal does not claim to fix either, and forbids this slice from calling
  `fqnForInstance` (line 50), which is the enforceable version of that promise.
- **SR-5, the gap is exactly as stated.** `TopologyPanel.openSourcePane():1412-1415` returns the pane only
  when showing, `openSourceFor(instanceId):1418-1420` navigates, and `chooseSourceTarget` deliberately
  leaves a closed pane closed. A new navigation-free opener is genuinely needed.
- **SR-7 and SR-9** are answered as deliberate differences with reasons given, which is what I asked for.
- **SR-8**, three separate mutation witnesses (line 303-305), including the omitted vertical intersection
  I said the nearest existing code would pass.

---

## What I verified versus what I only read

**Run:** `git fetch`; confirmed local `9c10de82` is 28 behind `origin/main` `401da35b`; read `c6aeafde`'s
diff and main's current `DesignSourcePanel.lineBounds` and CHANGELOG line.

**Read, not executed:** `.github/workflows/ci.yml` (the `ui-frame` job, its class lists and skip gate);
`MainFrame.java` (the spotlight executor 2428-2472, `installSpotlight`, `relightSpotlight`,
`designSpotlightRevisions`, `clearSpotlightHere`); `SpotlightTarget.java` (`precheck`, `resolveAll`,
`Surface`); `SpotlightOverlay.java` (`Lit`, `remeasure`); `SourcePanel.java` (`JScrollPane`, `Mode`,
`designBounds`); `MavenSourceResolver.java` (cache shape, archive fallback, discovery cost model);
`EventProcessorModel.java`; `TopologyPanel.java`.

**Not done:** no UI driven, no window opened, no implementation attempted, no test suite run this round,
no release or branch work touched, nothing committed. I did not re-run the earlier baseline suites; the
numbers in my previous review stand as measured then, at the stale baseline.

**Unchanged from my previous review and still true:** the proposal remains well scoped, and deferring the
node target — which I raised without recommending — has removed the riskiest resolution path while
leaving the graph-and-code payoff intact. The worked example never needed it.
