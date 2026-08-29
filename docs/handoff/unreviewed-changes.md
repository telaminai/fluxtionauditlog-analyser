# Un-reviewed changes on `main` — pending review

A running ledger of changes **committed directly to `main` without** the usual brief → report → review
cycle. These are small, ad-hoc fixes made by a session working primarily in **another repo** (a downstream
consumer of the analyser) that hit an analyser-side bug and fixed it in passing, rather than a delegated
work block.

**For the reviewing session:** on your next pull, review each `☐` entry below — read the commit, sanity
the change against the codebase and the repo rules (CLAUDE.md), run `mvn test`, and **verify anything the
entry says was not verified** (Swing UI changes are not unit-tested — build and run the jar). Then tick it
`☑ reviewed <date>` with a one-line verdict, and file any follow-up as a normal review. Fully-reviewed
entries move to `completed/` when this file is next tidied.

Every entry must carry: commit SHA, what & why, files, what was verified, and **what the reviewer must
still check**.

---

## ☐ `b07a74d` · make the released-bundle analyser/MCP acceptance reproducible

**What.** `tools/bench/bundle-client-bench.py` launches the packaged analyser under a disposable,
never-configured Java home; opens a generated bundle project before its exported YAML + declared GraphML;
asserts the profile's two runbooks, skill provenance, record count, pairing and complete coverage; then
launches the packaged stdio bridge and checks current discovery, tools/list and analyser_context against
that same state. The bench README documents its place beside the static ZIP checker.

**Why.** M19 P3's producer run had one remaining analyser-owned gate. An in-process test or a hand-written
REST request would not prove the command an MCP client actually launches, and a one-off script would not
guard the three-repository seam after the milestone. This preserves the exact public-wire check that
accepted the released bundle.

**Files.** `tools/bench/bundle-client-bench.py`, `tools/bench/README.md`; tracker and P3 review in the
following metadata commit.

**Verified.** Python compile and help pass. Against fluxtion-web artefact branch `m19/p3-artifacts` at
`893fbdf`, the bench passes 19/19 using the packaged analyser jar: fresh REST launch, two-call project/log
load, two described runbooks with existing files, canonical@f5efe17 provenance, 23 records, pairing 2/2,
coverage 1.0, modern MCP discovery, 14 advertised tools and analyser_context state parity. Diff check
passes. The desktop launch required running outside the filesystem sandbox; the isolated home and bridge
were removed afterwards.

**What the reviewer must still check.** Read the process cleanup and failure paths, especially early
returns and a bridge timeout. Run the command from `tools/bench/README.md` under Linux/xvfb against the
handed-off ZIP if practical. Challenge the strict coverage-1.0 requirement: it is deliberate for M19's
typed business-event example, but this bench must not be presented as a generic arbitrary-bundle checker.

---

## ☑ reviewed 2026-08-29 (the Mongoose/playground session — the consumer side, and the session that reported the behaviour) · `f5efe17` · make the canonical load-log procedure respect project session boundaries

**Verdict.** Accepted. This came from my P3 run and the correction matches what I actually observed:
`open {project, log, graphml}` returns ok with `ignored: [log, graphml]` and the exact reason "a project
switch is a session boundary, so open the log and graph in a second call, inside the new project". The
new step 2 says precisely that, in the right order, and the wording matches the verb's real behaviour
rather than paraphrasing it. All three checks the entry asks for, run against the SHIPPED bundle:
(1) the "do not use `analyser_context` to find an unopened log" correction is still present — the false
claim is not reintroduced; (2) the shipped skill carries the concrete `./export-audit.sh`,
`logs/audit-fluxtion-spring-mongoose.yaml` and `src/main/resources/.../MarketProcessor.graphml`
substitutions with ZERO `TODO(bundle)` or `/path/to/` markers; (3) the vendored bytes are SHA-256
identical to the published canonical file (`936950b…`). Re-vendored from the live default root:
`canonical@f5efe17e1b234bdb6c55cd8fada27d2bdc8d2bc8`, matrix green on all three legs, and the whole
clean-machine chain re-run on a bundle regenerated at this revision. Worth recording that the drift
guard worked exactly as designed on this re-vendor: the exact-match substitutions still hit, and the
only failure was the VENDORED.md/manifest revision pin — the check whose entire job is to notice that a
re-vendor happened.

**What.** `common/load-audit-log/SKILL.md` now checks whether this project is active and, when needed,
opens the project alone before opening the YAML + GraphML in a second call. The versioned index advances
to the skill-source commit; CanonicalSkillsTest pins the new bytes and the two-call instruction.

**Why.** The live P3 session tried the tempting combined `open {project, log, graphml}` shape. The
analyser correctly treats a project switch as a session boundary and ignores the other parameters, but
the canonical skill did not warn the agent. A generated procedure must not recommend a call that only
partially applies while looking successful.

**Files.** Canonical load-log skill and changelog in `f5efe17`; index, parity test, tracker and handoff in
the following metadata commit.

**Verified.** Focused `CanonicalSkillsTest,ProjectVerbTest,SpecLinksResolveTest`, full Maven suite
1,112/1,112, strict docs and diff check pass on the final index state. The application behavior is
already pinned by ProjectVerbTest; this slice changes instructions, not the verb.

**What the reviewer must still check.** Confirm the two-call wording matches `ProjectVerbTest` and does
not reintroduce the false claim that `analyser_context` can discover an unopened log. Re-vendor from the
live default root in the playground, verify the new skill bytes and revision, and ensure the generated
bundle contains the concrete export/GraphML substitution with no marker.

---

## ☑ reviewed 2026-08-29 (the Mongoose/playground session — the consumer side) · `99c79bf` · publish the analyser-owned canonical m19-skills/1 index

**Verdict.** Accepted, with the strongest evidence available: the consumer now runs against it. The
default CLI (no `--source`) fetches this exact root over the network and records
`canonical@6243a899774d591119559305a137ecf144819efd`, and the fetched bytes were IDENTICAL to the
previously committed snapshot — so the live mechanism retroactively validated the `--declare-canonical`
declaration it replaced, which is now DELETED (F4's playground half). Independently checked: the index
returns 200; both `skills[].path` entries exist below the root; and both files' SHA-256 match analyser
commit `6243a89` byte-for-byte (`4c95500…` and `f2737e2…`). Raw `main` as the build/release root is the
right call — canonical content is analyser-owned, it already matches the required
`<root>/m19-skills/1/index.json` layout, and nothing at runtime ever fetches it (the generator vendors
at build time into a committed snapshot). The load-audit-log + run-mongoose-server subset matches the
accepted no-replay deviation and the not-publishable embedded gate. The refresh rule cannot silently
retain a stale revision because `manifest.json` records the fetched revision and a test pins it against
`VENDORED.md`. Detail in the handoff report §7k/§7m.

**What.** `docs/skills/m19-skills/1/index.json` publishes the accepted Mongoose subset from the public
raw repository root. The skills README names the machine root and refresh rule; CanonicalSkillsTest pins
the contract, two selected tiers/paths, source revision and exact SHA-256 bytes. The changelog records the
new build/release endpoint. Generated projects remain offline snapshots and never fetch it.

**Why.** P2 review F4 proved the playground's proposed default root returned HTTP 404. Canonical content
is analyser-owned, so the stable raw analyser repository is the smallest root that preserves one source
of truth and already matches m19-skills/1's required `<root>/m19-skills/1/index.json` plus below-root
skill paths. replay-a-run is omitted by the accepted no-replay claim; embedded remains not publishable.

**Files.** Canonical index and README; CanonicalSkillsTest; changelog. Tracker/review/handoff disposition
is in the following metadata commit.

**Verified.** Focused canonical tests and full Maven suite 1,111/1,111 pass; strict docs and diff check
pass. After push the exact raw index returned 200, and the playground P2 retriever selected both files
from the live root with revision `6243a899774d591119559305a137ecf144819efd`.

**What the reviewer must still check.** Challenge raw `main` as the stable build/release root and the
decision to publish only load-audit-log + run-mongoose-server. Compare both hashes with analyser commit
`6243a89`; confirm the index paths stay below root and the refresh rule cannot silently retain a stale
revision. Independently run the playground retriever against the root. F4 still requires the playground
to adopt it as CANONICAL_ROOT and delete its manual relabelling flag; F5 then tests the actual zips.

---

_Nine earlier entries were reviewed and archived verbatim on 2026-08-29 in
[`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md), together with
their review detail files._
