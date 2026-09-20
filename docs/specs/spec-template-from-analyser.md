# Template From The Analyser — `File ▸ New project from template…` (Design Spec)

_Status: **REVIEWED AND ACCEPTED 2026-08-30** (M19.5, archived in [completed/tracker.md](completed/tracker.md)); the
*File ▸ New project from template…* entry point is pictured and spotlit in the 1.14.1 docs. Owner: greg.higgins. Analyser
implementation: `9d38cc4`; playground: `994e82a` live. Companion to
[tracker.md](tracker.md) (**M19.5**), [spec-onboarding-example.md](spec-onboarding-example.md) (whose
step 1 this changes) and [spec-agent-brokered-dev-loop.md](spec-agent-brokered-dev-loop.md) §C2 (which
read the live catalogue and settled what it already encodes)._

_Raised by the owner, 2026-08-30, in these words: **"I thought we would be able to choose a template
from the swing app to make it seamless to get started."** That expectation is reasonable and the
product did not meet it. This spec records the implemented boundary and its acceptance evidence._

**Owner revision, 2026-09-20 — accepted, implementation pending:** show the full catalogue and mark
entries tagged `onboarding` as **Recommended starting points**. D-1 below supersedes the shipped
onboarding-only selection rule. The August acceptance evidence covers the old picker, not this revision.

---

## The gap

`spec-onboarding-example.md` step 1 is *"open the playground, pick an example flow, **Download**"*. A
browser round trip is the designed flow, not an oversight. The analyser's existing new-project surface
(`NewProjectDiscovery` / `NewProjectOfferDialog`) **discovers a project already on disk**; nothing
fetches one.

So a first-time reader is asked to change tool three times before seeing a single record: browser →
terminal → analyser. The tutorial is honest about it and it still reads as three tools.

**M19.5 already existed** as *"(defer unless tutorial reads clunky) File ▸ Open example… one-action
helper (import + open + Follow)"*. That entry assumed the bundle was **already downloaded**; it
automated the last hop only. This spec **widens** M19.5 to include acquiring the project, which is the
hop the owner actually meant. The old scope survives inside the new one as its final step.

## A — the shipped pieces (read live, 2026-08-30)

All four pieces are shipped. This section exists so the next reader does not re-invent them,
which §C2 of the dev-loop spec had to correct twice in its own draft.

| Piece | State | Evidence |
|---|---|---|
| A versioned, public, machine-readable catalogue | **shipped** | `/starter-templates/index.json`, top-level `catalogue: 1` (UP-PG-01, landed 2026-08-29), 14 templates |
| **Server-side zip generation over HTTP** | **shipped** | `/start/scaffold?s=<token>` — a Cloudflare function running the same TypeScript generator (`buildStarterZip`), Worker-safe |
| An analyser that opens a bundle's profile with zero setup | **shipped** | the whole M19 path; the bundle ships `.analyser/project.fluxtion-settings` |
| A way for a **Java** client to ask for a named template | **shipped** | `/start/scaffold?template=<id>`; analyser `TemplateClient` |

The second row is the one that changes the size of this job. Generation was assumed to be
browser-only — this repo's own staging script drives *vitest* to obtain a zip, which is evidence of
that assumption rather than of the constraint. It was wrong: `scaffold/+server.ts` already served a
real zip over HTTP. It originally accepted only an lz-string spec token produced by the `/start`
page's "Copy curl" button; B1 added the catalogue id rather than reimplementing lz-string in Java.

That server-side gap was one query parameter; the analyser half was the hardened archive boundary and
the human confirmation flow around it.

## B — the mechanism

### B1 · Playground: teach the existing endpoint a template id — **LANDED 2026-08-30**

**Live in production** at `fluxtion-web` `994e82a`, verified against the deployed site rather than
only in test: a 38,564-byte `audit-analyser-bundle` zip, the `artifact` override rooting the project
at `risk-engine/`, and 404 / 400 / 404 on an unknown id, both-inputs and a path-shaped id. All 14
catalogue entries are fetchable, pinned by a test, so a template cannot be added and be unreachable.
The catalogue additions of D-1 and D-2 landed with it. Contract as shipped is in
[handoff_30_aug_2026_1.txt](../handoff/completed/handoff_30_aug_2026_1.txt).

`GET /start/scaffold?template=<file>[&artifact=&group=&basePackage=]`

Resolves `<file>` against the same `static/starter-templates/` directory the catalogue indexes, applies
the identity overrides if present, runs the existing `validate()`, and returns the existing
`buildStarterZip()` output. No second generator, no new zip path, ~20 lines in a 38-line file.

The identity overrides matter and are not decoration. §C2 established the factoring this must not
break: *"the template is a **shape**, and the project is generated to the user's package and artifact
names"*. A `?template=` with no overrides returns the template's defaults, which is right for
onboarding; the overrides are what stop this from degrading into a fixed zip.

Unknown `template` → **404 naming the catalogue URL**. Both `s` and `template` → 400 (they are
alternative inputs, and silently preferring one is how a caller learns the wrong mental model).

### B2 · Analyser: `File ▸ New project from template…`

The download/open flow shipped in `9d38cc4`; step 2 is revised by the owner's 2026-09-20 decision
and remains to be implemented.

1. `GET /starter-templates/index.json`; refuse a `catalogue` integer this build does not know, with a
   message naming the analyser version — the same refusal shape as `x-analyser-min-version`.
2. List **all catalogue entries**, showing `name` + `description`. Mark entries tagged `onboarding`
   as **Recommended starting points** (D-1); the tag must not hide the other entries.
3. Ask for a destination directory and, optionally, artifact/package (defaults pre-filled).
4. `GET /start/scaffold?template=…`, unzip under **D-4's** constraints, then hand straight to the
   existing new-project path — which is where the old M19.5 scope (import + open + Follow) resumes,
   unchanged.
5. Show fixed commands for recognised run/export wrappers, copyable, **without reading command text
   from the bundle or executing it** (D-3).

**Review correction F1 (2026-09-20):** the current flow has two branches: an archive with a profile opens
it; an archive without one reaches a discovery offer whose checkboxes start unselected. These are not
equivalent onboarding experiences. Release the full-catalogue expansion after playground default-on
support supplies the promised profiles/bootstrap. Keep the discovery path explicitly described and
tested for legacy/profile-less downloads. Explicit opt-out must not be silently reversed by discovery.
See [the cross-repo delivery order](spec-project-starter-journey.md#delivery-order-and-catalogue-coverage).

## C — decisions

**D-1 · All templates, with catalogue-owned recommendations — owner decision 2026-09-20.**
`New project from template…` shows every entry in the supported catalogue. The analyser is also a
starting point for authoring, so embedded, DSL and other project types must remain available alongside
hosted examples. There is no hardcoded name list or restriction by project type.

The existing optional `onboarding` tag means **Recommended starting points** in this picker. Render
that recommendation visibly without excluding untagged entries. With no onboarding tags, show all
entries without recommendations; do not fall back to a Mongoose-only subset. The catalogue remains
the single source of template names, descriptions and recommendations. No catalogue schema change is
required, and the number of entries is not pinned to today's fourteen.

This replaces the original D-1: the shipped client selected only onboarding-tagged entries, otherwise
Mongoose/hosted entries, otherwise everything. That historical rule explains the two-entry picker;
it is no longer the intended behaviour. Existing archive, version and network refusals remain intact.
A recommendation alone does not certify that the template contains a tutorial, complete agent
instructions or keyless generation; those capabilities need their own evidence and documentation.

Render the catalogue's existing `agentBootstrap` disclosure: listed entry files, explicitly none for an
empty array, or **not declared** when absent. The field is already on playground main; it is the analyser
reader/display that is missing. Show **build key: not declared** when `keyNeed` is absent, including on
recommended entries. Never infer either capability from recommendation, project type or compilation mode.
Render supported declared key requirements as facts; unknown future values are reported as unrecognised,
not silently treated as keyless. Catalogue declarations describe the default download; if the user opts
out or changes capabilities, show effective configuration rather than retaining a contradicted badge.

**Terminology — a guided walkthrough uses a project.** A template selects the project's code,
configuration, scripts and documentation. A guided walkthrough is an optional procedure in its
runbook: run a supplied scenario, predict an outcome, inspect the evidence, make one explained change
and compare the result. Freeform design starts from the user's own behaviour requirements instead of
following that exercise. Either style can use a hosted, embedded or vendor-integration project.
Thus “guided starter” may name a worked example with a walkthrough, but is not a new execution mode
or an automatic property of an `onboarding` tag. No separate guided-session engine is specified here;
the human/LLM follows the runbook, and the analyser presents design and evidence.

**D-2 · `mode: aot` does not mean "needs a key" — and the catalogue implied it did. LANDED, and it
was a live bug, not a hypothetical.** Reading the code made it worse than this section first stated:
`KEY_BADGE` already carries a **"No key"** variant that **no `mode` can produce**, because the badge
is derived from `mode` alone. So the live gallery was showing *"Key once at build"* on
`analyser-bundle` — a false warning on the one template the tutorial recommends, as the first thing a
new user sees. Fixed by an entry-level `keyNeed` override; every existing entry is unaffected.
§C2 states the rule: *"`interpreted` is keyless…anything else is AOT and needs a subscribed compiler
key."* That was true when written. It is now false for exactly one template — `analyser-bundle`, which
is `mode: aot` **and builds keylessly**, because M19 commits the generated processor and moves the
`fluxtion-maven-plugin` scan behind `-Pgenerate-fluxtion`.

So a template picker that derives a "needs an API key" warning from `mode` would show a **wrong
warning on the one template the tutorial recommends** — the first thing a new user sees. The catalogue
needs to distinguish *builds keylessly* from *regenerates keylessly*; they are different facts and
`mode` currently carries neither cleanly. Also filed as UP-PG-03.

The shipped analyser renders `none` when expressly declared and otherwise says nothing. **Revised
requirement after F3:** render declared supported values and **not declared** for absence, without
consulting `mode`. The generated project's README/bootstrap remains authoritative for regeneration;
a build-key badge must not imply a regeneration-key guarantee.

**D-3 · OWNER DECISION: the analyser does not run the project in M19.5.**
This is the boundary the owner should set deliberately, because the seamless version of this feature
walks straight into it. `Runbooks.java` is explicit: a profile stores runbooks as **pointers, never
commands**, *"so that opening a colleague's project with an agent attached does not execute text
written by whoever sent the file."* The analyser today executes only S3 fetches and MCP client
launches — both user-initiated, neither derived from file content.

Downloading an archive from the network and then running a script out of it is a different act from
anything the product does now. M19.5 therefore **shows fixed analyser-owned commands, lets the user
copy them, and does not run them.** The reader still uses a terminal for build/run/export; the
three-tool hop becomes two.

If the owner wants the run step too, it should be its own slice with its own review, and the rule
should be that the analyser runs a **fixed, known command from a template it fetched itself** — never
a string read out of a downloaded file.

**D-4 · The archive boundary.** The analyser now, for the first time, unzips a network-fetched
archive that legitimately contains executable content (`mvnw`, `run-server.sh`, `.cmd` wrappers). Non-
negotiable, and the acceptance bench must attack each one:

- HTTPS only; the host is pinned to the configured playground origin, not taken from the catalogue
- **zip-slip**: every entry resolved and required to stay under the destination; `..` and absolute
  entries refused with the entry named
- refuse to write into a non-empty destination — never merge, never overwrite
- cap entry count, per-entry size and total expansion (a zip bomb is a plausible bad day)
- the executable bit is granted **only** to the fixed root lifecycle allowlist (`mvnw`, run, export,
  stop and key-check shell wrappers) — never taken from the archive's own mode bits
- the whole extraction is atomic: stage in a sibling temp directory, move into place on success, and
  leave nothing behind on failure

**D-5 · Offline and failure.** A first-run analyser on a disconnected machine must degrade to a clear
"the template catalogue is unreachable" with the manual path (the tutorial URL), not an empty list and
not a stack trace. Network and extraction never block the EDT and expose modeless progress + cancel.

## D — non-goals

- Editing or authoring templates from the analyser. It reads and explains; authoring is the
  playground's and the IDE's job.
- Caching the catalogue between runs (fetch per open; it is one small file).
- Replacing the playground's Download button, or the tutorial's browser path — both remain, and the
  tutorial keeps working exactly as written for anyone who prefers it.

## E — acceptance

**2026-09-20 revision, still to verify:**

- A catalogue with fourteen entries and two onboarding tags presents all fourteen; only the two
  tagged entries carry the recommendation. Test the general rule as well, without a fixed size limit.
- A catalogue with no onboarding tags presents every entry, including embedded and DSL choices;
  no implicit Mongoose restriction remains.
- An untagged entry can be selected, downloaded with its declared identity defaults and opened through
  the appropriate flow. Witness both a profile-bearing download and a legacy/profile-less discovery
  offer with nothing preselected; say which occurred. Explicit opt-out is not silently undone.
- Verify `agentBootstrap` present/empty/absent and `keyNeed` declared/absent/unrecognised on recommended
  and ordinary entries. Check advertised entry files against the generated default ZIP. The reader
  handles unknown additive catalogue fields without treating them as capabilities.
- Witness the picker on screen: recommendation labels and descriptions are clear, every entry is
  reachable, and the existing version/network/archive refusals still apply. No build or run is executed.

The following records the original shipped acceptance:

A bench in `tools/bench/`, run headlessly in CI, that: reads the live catalogue; refuses a bumped
`catalogue` integer; fetches a named template from the real endpoint; asserts the zip generates a
project whose profile opens with `project.active` and its runbooks resolved; and **fails on each D-4
attack** (slip entry, absolute entry, oversized expansion, populated destination, an archive claiming
an executable bit on a file outside the allowlist).

Implemented by `tools/bench/template-bench.py`. The local hostile leg is part of the Maven suite. The
optional live leg passed 6/6 against production on 2026-08-30: catalogue selection, real scaffold zip,
profile presence, `ProjectSession` open and resolved runbooks. The last hostile case is the point of the
bench: an archive-marked `evil.sh` remains non-executable.

## F — cost, honestly

Playground half: small — one parameter on an existing endpoint, plus the two catalogue additions of
D-1/D-2, which are additive under `catalogue: 1`. Analyser half: a dialog, an HTTP client, and a
**hardened** unzip, which is most of the work and all of the risk. The D-4 list is not padding; it is
the reason this is not an afternoon.

It does not remove the terminal from onboarding (D-3). It removes the required browser round trip
and manual extraction. Catalogue recommendations help the user choose while keeping every template
available. The analyser is one starting point; website and direct LLM downloads remain valid routes.
