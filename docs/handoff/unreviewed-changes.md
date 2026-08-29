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

## ☐ `99c79bf` · publish the analyser-owned canonical m19-skills/1 index

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
