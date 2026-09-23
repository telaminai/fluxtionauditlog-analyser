# Re-review: source spotlight proposal, fourth round — 2026-09-21 (reviewer: claude)

**Verdict: ONE BLOCKER, otherwise READY FOR HANDOFF.**

**Scope of this review.** I reviewed the uncommitted revision of
[the proposal](../../proposals/source-spotlight.md) and the
[round-three response](response_source_spotlight_r3_2026_09_21.md). I checked their claims against
`origin/main` `401da35b`, reading each file with `git show` from that commit; I did not use the working
checkout, which is at `9c10de82`.

T-1 to T-4 are all answered, and every mechanism the response cites exists where it says. The blocker is
new. It is the same kind of error as T-1: a claim about a route that does not hold once you read the
route. The proposal names one existing Java source lookup, but its freshness contract and acceptance 7
are wired into a different one, and the two disagree on jars, on ambiguity and on authority.

---

## Round-three findings — all closed

| Finding | Status | Where |
|---|---|---|
| **T-1** stale baseline and design geometry | **Closed.** "Java clips; design refuses" is stated as two deliberate policies with reasons (lines 215–222). The released `DesignSourcePanel.lineBounds` containment check (`:95` at `401da35b`) and `revealLine` (`:77`, reached via `SourcePanel.revealDesignLine:503`) are preserved and credited, not re-specified (lines 17–19, 195–198, 273–277). Acceptance 5 now mutates containment into intersection-and-lighting and requires the refusal check to fail (lines 344–346), which protects the released decision. | verified |
| **T-2** misses and discovery | **Closed.** Positive and negative entries are both invalidated (lines 145–146). The once-per-resolver jar discovery is stated as a limit (lines 151–157), and resolver recreation is correctly attributed to `SourceService.configure` (`:31–33` constructs a new `MavenSourceResolver`). | verified |
| **T-3** leftover instance-id clause | **Closed** (line 52). | verified |
| **T-4** `partial` has no design counterpart | **Closed.** Java-line-only, and absent from document and design echoes (lines 204, 219–220, 240–241). | verified |
| CI lists | **Correct.** At `401da35b`, `ci.yml:80` (the `-Dtest=` list) and `:86` (the no-skip loop) both contain `DesignSpotlightFrameTest`. | verified |

Also re-verified at `401da35b`:

- the preparation insertion point: `precheck` at `MainFrame:2478`, then `resolveAll` at `:2482`;
- `designSpotlightRevisions` (`:79`), published after the set succeeds (`:2503`);
- `clearSpotlightHere` (`:2312`);
- the `wentOut` echo (`:2508`) and `designSpotlights.wentOut` in context (`:5941`);
- the stale-caption revision (`:2528`);
- `TopologyPanel.openSourcePane()` (`:1433`) and `openSourceFor` (`:1439`);
- `chooseSourceTarget` (`MainFrame:3108`).

---

## U-1 · Blocker · the proposal specifies one Java lookup and wires freshness into a different one

Main has **two** Java source lookups at `401da35b`, and they are not interchangeable:

| | `source {fqn}` verb | Source viewer and navigation |
|---|---|---|
| Path | `DesignWorkspace.source` → `DesignFiles.fqn` (`:72`) → a file `Path` → `access.read(path)` | `SourceService.read` (`:69–70`) → `SourceRootResolver`, then `MavenSourceResolver` |
| Sources jars | **never consulted** | consulted when enabled |
| Same FQN under two roots | **refuses**: "ambiguous file under authorised roots" (`DesignFiles:50`) | **first root wins** (`SourceRootResolver:58–60`) |
| Cache | none (reads the file each time) | per-FQN hits **and misses** (`MavenSourceResolver:32`) |

The proposal draws on both:

- Step 2 (lines 76–78) says "resolve through the existing **authorised** source lookup" but "preserve
  existing root/**repository precedence**" and "does not claim uniqueness across all configured roots".
  That is `SourceService`'s behaviour, described in `DesignFiles`' vocabulary.
- Lines 142–149 require the sources-jar fallback, then "wire this invalidation into the explicit
  Java-source reread path used by **`source {fqn}`**". That path cannot reach the jar cache, so wiring
  invalidation there does nothing for a jar-sourced document.
- Acceptance 7 (lines 354–355): "Cache a miss, add its entry to an already-known jar, then invoke **the
  real reread route** and require success." The only explicit re-read route today is `source {fqn}`,
  and it answers `class not under an authorised root` for a class that exists only in a jar. As written,
  the test cannot pass by that route, or else it passes by some other route the proposal never names.

**Failure scenario:** configure two roots that both contain `com.x.Foo`, and enable the sources jars.

| Request | Result today |
|---|---|
| `source {fqn: com.x.Foo}` | refuses as ambiguous |
| Navigation and the Java viewer | show root 1's copy |
| A Java spotlight | the proposal does not say which of these it is |

The choice changes the identity and revision echoed as proof of *which document* was lit. That is the
proposal's central promise (lines 79–81, 238).

**Required correction.** Name the lookup and give the reason. I recommend `SourceService`, because the
spotlight must measure what the Java viewer actually renders, and only that path supports jars. Then:

1. State the ambiguity policy explicitly. Either adopt first-root precedence and disclose the chosen
   root in the echo, which lines 77–78 half-say already; or add a multi-root refusal, which would be a
   new behaviour for the viewer.
2. Give acceptance 7 a re-read route that goes through `SourceService`. If that means `source {fqn}`
   starts consulting `SourceService` for Java, it is a change to a released verb's resolution and
   ambiguity behaviour, and it needs its own CHANGELOG line. The alternative is a new explicit re-read
   entry point.
3. Say whether the glance and the spotlight may legitimately disagree about which document an FQN
   names, and if they may, how the person is told.

---

## Low

- **L-1 · The file name is misspelt.** `docs/proposals/sorce-spotlight.md` is staged under that name, and
  the response links to it. Rename it to `source-spotlight.md` before the first commit, while nothing
  outside these untracked files links to it.
- **L-2 · The link targets are not committed.** The proposal links three review/response files, and the
  response links the round-three review; all are untracked in the primary checkout. Committing the
  proposal alone would leave dead links. Commit the set together, from a checkout of current main rather
  than `9c10de82`, as line 14 already requires for implementation.
- **L-3 · Listener-driven design remeasurement is a user-visible change.** Today `installSpotlight`
  registers only `componentResized`, so scrolling the design pane leaves a lit band at its old screen
  position. Lines 273–277 make it go out instead. That is a behaviour change to released design
  spotlights, arguably a fix, so the implementation needs a CHANGELOG line. The text says design
  geometry is unchanged, which is true, but it should not read as "design is unchanged".

---

## What I verified versus what I only read

**Read at `401da35b`, not executed:**

- `DesignSourcePanel` (`revealLine`, `lineBounds`, the navigation ticket) and `SourcePanel`
  (`revealDesignLine`, `JScrollPane`, wrap).
- `MavenSourceResolver` (cache shape, lazy jar list) and `SourceService.configure` / `read`.
- `SourceRootResolver` (first-root precedence), `DesignFiles.fqn` (ambiguity refusal) and
  `DesignWorkspace.source`.
- The `source` schema in `VerbSchemas:315`.
- `MainFrame`: the spotlight executor, registries, `wentOut`, `chooseSourceTarget`.
- `TopologyPanel` openers, and `ci.yml:78–90`.

**Not done:** no build, test or UI run (this is a documentation revision); no edits to the proposal,
the response or the tracker; nothing committed or pushed. This report is left uncommitted beside the
earlier rounds.
