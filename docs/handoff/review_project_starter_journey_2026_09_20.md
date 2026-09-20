# Independent design review — the project-starting and re-entry journey

Date: 2026-09-20 · Reviewer: an analyser session that did not write these specs · Scope: the **uncommitted
working tree** on `main` — `docs/specs/spec-project-starter-journey.md` (new), the 2026-09-20 revision of
`docs/specs/spec-template-from-analyser.md`, the corresponding `docs/specs/tracker.md` entries, and
`docs/handoff/evidence/spring-authoring-feedback-2026-09-20-reopen/`.

This is a **design** review. It judges whether the proposals are implementable as written and whether the
ownership boundaries hold. It is not implementation acceptance, and it does not approve a release. No spec
was modified, nothing was committed, no branch was switched, no paid compilation was run and the owner's
analyser session was not touched.

Other working-tree changes (the local rehearsal launcher under `tools/`, the Mermaid fix on the Spring
conversations page, and the six earlier feedback evidence packets) are **out of scope**; they were read only
far enough to confirm they do not belong to this journey work.

---

## Overall verdict: **CONDITIONAL**

The owner's intent is captured faithfully and the boundaries are the right ones. Three things are
genuinely well done: the walkthrough-is-a-runbook-not-a-mode terminology (template spec D-1), the refusal to
let the analyser become an application runner (journey §1, tracker Decisions), and a diagnosis that says
plainly what it did **not** establish. The close/reopen packet is honest evidence: it reproduces the symptom,
names the cause, and does not claim a fix.

It is not yet ready for an implementation handoff. Five requirements cannot be built as written because the
mechanism they depend on does not exist or contradicts itself (F4–F8), and the accepted full-catalogue change
has a consequence its acceptance list does not cover (F1–F3). None of these needs a redesign; each needs a
decision and a sentence. The largest single risk is **sequencing**: if the analyser ships the full-catalogue
picker before the playground ships default-on support, the picker gets seven times more entries and thirteen
of fourteen of them land the user in a discovery dialog that selects nothing.

Answers to the eight questions asked are folded into the findings; the short form is: (1) not yet — the empty
directory has no documented first step (F16); (2) mostly, with two duplicate-writer risks (F6, F10); (3) no —
opt-out is not expressible headlessly and legacy semantics are undecided (F4, F5); (4) no for Mongoose stop
and audit export (F6) and for processor identity in non-Mongoose projects (F11); (5) the page is sound in
intent but has no named host and a ready-made one exists (F14); (6) the distinction is clear on disk and
**not** in `context` (F7); (7) the findings do support the proposals, and the untested path is the one most
likely to explain the report (F8, F13); (8) acceptance is close but three checks are unfalsifiable as written.

---

## Findings

Severity: **Major** = blocks a clean implementation handoff; **Moderate** = will cause rework or a wrong
surface if unresolved; **Minor** = tighten before handing off.

### F1 — Major. The full catalogue sends thirteen of fourteen choices into the discovery fallback, and the spec calls that "the same existing flow"

`docs/specs/spec-template-from-analyser.md:92` ("hand straight to the existing new-project path") and `:195`
("opened through the same existing flow").

Verified: exactly one of the fourteen starter specs sets `analyserBundle`
(`web/static/starter-templates/*.starter.json`, all read), and `.analyser/project.fluxtion-settings` is
emitted only by the bundle path (`web/src/lib/starter/bundle.ts:603`, gated at `:110`). In the analyser,
`MainFrame.java:4346` branches on `installed.profile() != null`; without a profile it calls
`createProjectAt` (`:4387`), which runs `NewProjectDiscovery` and an offer dialog (`:4400-4402`) where, by
M35.4, nothing is pre-selected.

Consequence: today the picker lists two entries, one of which carries a profile. After D-1 it lists fourteen,
one of which carries a profile. "The same existing flow" therefore describes the experience of one entry in
fourteen; the other thirteen gain an extra modal, and a user who clicks through it adopts nothing. The revised
acceptance at `:195` says an untagged entry "can be selected … and opened through the same existing flow",
which would pass while describing a materially worse journey.

Correction: either sequence the playground's default-on profile work ahead of the picker change, or state in
D-1 that a profile-less template opens through the discovery offer, and add that case to the acceptance list
by name so it is witnessed rather than assumed.

### F2 — Major. The catalogue already discloses which templates ship agent instructions; the picker cannot read the field

`TemplateCatalogue.java:18-19` — `Entry` carries `name, description, file, type, mode, keyNeed, tags` and no
`agentBootstrap`. Verified against `origin/main` of the playground: `agentBootstrap` **has landed** in
`index.json`, and only `analyser-bundle` declares it (`["CLAUDE.md","AGENTS.md"]`). `docs/specs/tracker.md:862`
still records UP-PG-02 as "IN PROGRESS, playground session".

Consequence: the field exists for exactly the situation D-1 creates, and the picker ignores it. After the
change a user chooses among thirteen entries that ship no `CLAUDE.md`, no `AGENTS.md` and no skills, with
nothing on screen saying so — the gap the tracker itself describes at `:859-862`, multiplied.

Correction: D-1 should require the picker to render the disclosure, including its absence, and the tracker
entry should be corrected to landed. This is a small analyser change and it is the difference between a
recommendation and an informed choice.

### F3 — Major. Key need is silent on five AOT templates, one of them a Recommended starting point

`TemplateProjectDialog.java:268-273` adds a key line only when `keyNeed` equals `none`; the comment correctly
forbids deriving it from `mode`. Verified in the catalogue: `keyNeed` is declared on `analyser-bundle` alone,
while `fluxtion-aot`, `fluxtion-spring`, `fluxtion-dag-multi-io`, `mongoose-hosted-fluxtion` and
`fluxtion-spring-mongoose` are `mode: aot` with no `keyNeed`. The last of those is one of the two
`onboarding`-tagged entries.

Consequence: the **Recommended starting points** label will sit on a template about which the picker says
nothing regarding a build key, and D-1 adds four more entries in the same condition. D-2's reasoning (never
infer from `mode`) is right and is exactly why silence is the outcome; the fix belongs in the catalogue or in
the wording, not in inference.

Correction: require `keyNeed` on any entry carrying the recommendation (playground), or have the picker say
"build key: not declared" instead of nothing. D-2 stands either way.

### F4 — Major. Opt-out of default-on support is not expressible on the only headless route a non-browser client can use

`docs/specs/spec-project-starter-journey.md:21-23` requires that the effective choice "be expressible by a
headless client". Verified in `web/src/routes/(marketing)/start/scaffold/+server.ts`: the `?template=` form
passes only `group`, `artifact` and `basePackage` through `withIdentity` (`:45-49`); everything else comes
from the stored template spec. The file's own header (`:10-12`) says the template form exists precisely
because "no client should have to reimplement lz-string" — so the token route, which *could* carry the flag,
is the route the endpoint documents as unusable for these callers.

Consequence: a local LLM following the documented headless path gets analyser support forced on with no way
to decline, which contradicts "explicitly switchable off" for the audience the journey spec is written for.

Correction: name the additional query parameter as part of this work (it is one parameter on an existing
endpoint, the same size as the `?template=` addition), or state deliberately that headless callers cannot
opt out and why.

### F5 — Major. "Enabled by default" and "old links unchanged" cannot both hold for a plain boolean, and the spec defers the mechanism without naming the options

`docs/specs/spec-project-starter-journey.md:23-25` records the requirement and defers the field name and
migration policy. Verified in `web/src/lib/starter/share.ts`: `tryDecompressSpec` (`:213-224`) simply
`safeParse`s; the spec's `version` field (`:194`) is a plain string that nothing branches on; and the
established precedent for later-added booleans is `z.boolean().default(false)` (`:70-71`, `customAuditor` and
`perfMonitor`, both annotated as "added after …").

Consequence: `default(true)` silently changes what every pre-existing shared link and exported
`.starter.json` generates — the outcome the spec's own sentence forbids. `default(false)` contradicts
"enabled by default" for anything that travels through a link. Acceptance 1 covers only *explicitly* disabled
configurations; a legacy link carries no value at all and is therefore neither.

Correction: decide between a tri-state (absent is distinct from false, with the UI writing the field
explicitly for every new project) and an explicit spec-version bump that migrates old payloads. Add an
acceptance case whose input is a link captured before the change.

### F6 — Major. Mongoose stop, feed and audit-export runbooks are promised for projects whose scripts exist only in the analyserBundle path

`docs/specs/spec-project-starter-journey.md:33-36` requires "Mongoose deployment, start, stop, feed, audit and
evidence-export procedures for a hosted project", and `:47-48` insists the option is "distinct from … the
existing `analyserBundle` example mode".

Verified: `stop-server.sh` and `export-audit.sh` are generated only by `bundle.ts` (`lifecycleScripts`,
`:374-393`), depend on a generated `BundleLifecycle` Java helper and on finding the server "via its registry
entry", and the bundle contract requires mongoose + fluxtion + AOT + `webAdmin`
(`web/src/lib/starter/validate.ts:63-76`). Ordinary Mongoose templates emit `run-server.sh` only
(`mongoose.ts:646`). Thirteen of fourteen catalogue entries do not set `analyserBundle`.

Consequence: delivering the promised runbooks for a plain Mongoose project means generalising the bundle's
lifecycle machinery and its registry dependency — which is a larger change than "distinct from the
analyserBundle mode" suggests — or writing instructions for scripts that are not in the project, which
acceptance 3 (`:119-120`, "instructions match actual scripts") would then fail.

Correction: say explicitly which parts of the bundle become general (the lifecycle helper, the audit capture
configuration, the registry lookup), and name the mongoose-plugins version floor that the registry publisher
imposes. If the answer is that plain Mongoose projects get start and feed guidance only, say that instead.

### F7 — Major. Saved chart definitions are invisible to `context` in exactly the state the landing page must describe

`MainFrame.java:5820` returns early from `context()` when `filter == null`; `graphs` is put at `:5872-5873`,
below that return, and it is sourced from `graphTabs.graphNames()` — the **live tabs**, not the saved
definitions, which live in `AppConfig.savedGraphs` (`AppConfig.java:82`) and in the profile's `graph.*`
families (confirmed by reading the staged profile's key shapes, read-only).

The packet's own `summary.json` shows the effect: five chart names before close, `[]` after reopening the
project, five again only after the log is reopened. `reports` (`:5751`) and `runbooks` (`:5660`) sit above the
early return, which is why they survive — the asymmetry is specific to charts.

Consequence: acceptance 5 (`docs/specs/spec-project-starter-journey.md:124-126`, "the human and MCP context
expose the same persisted project declarations after close/reopen … saved definitions must survive") cannot
be satisfied by any amount of care in the UI, because the socket has no key that carries them. The
project-aware landing cannot say "five saved charts, waiting for their log" from `context` either.

Correction: add a key that reports **saved definitions** as distinct from **open tabs**, placed above the
`filter == null` return, and honour the standing rule in `docs/ONBOARDING.md:88-96` — name the panel row and
the docs page in the same commit. This finding is the one that should be implemented first, because both the
landing page and acceptance 5 depend on it.

### F8 — Major. An unconditional automatic restore already exists at launch; the proposed offer would be a second, different answer to the same question

Verified: `Main.java:80` opens `frame.config().logFile` when no file argument is given, and
`AppConfig.java:13-15` documents `graphmlFile` as "the topology showing when the app last closed, reopened on
the next start beside the log". Both are user-local and **global**, not project-scoped; `activeProjectPath`
(`:112`) is separate and is read by `ProjectSession.java:68-69`.

Consequence: quit-and-relaunch silently restores a log with no freshness check and no project-identity check,
while close-and-reopen restores nothing. Two routes to the same user intent behave differently, and neither
matches the proposed explicit offer. Worse, because the fields are global, a user who switches to project B
and relaunches is offered project A's log — the cross-project mixing that
`docs/specs/spec-project-starter-journey.md:81-82` explicitly forbids.

The evidence packet did not test this path: `README.md:38-40` says menu clicks and quit/relaunch were not
driven. The owner's report could have come from either route, and the untested one has the more surprising
behaviour.

Correction: the spec must state what happens to the launch restore — retired, gated behind the same offer, or
kept and made project-scoped. Until it does, an implementer will add the offer and leave the old path
running.

### F9 — Moderate. "Restore last session" has no defined storage, and the obvious place is the wrong one

Verified by reading the staged profile's key families (read-only, no modification): `share.version`,
`sourceRoot.*`, `eventProcessorFqn.*`, `selectedEventProcessor`, `focus.*`, `graph.*`, `report.*`,
`runbook.*`, `mavenRepo.*`, `hiddenColumn.*`. There is **no** log, design, GraphML or diagnostics path, and
the generator documents that as deliberate — `bundle.ts:578-583`, "nothing forbidden: no log/export path, no
API key".

Consequence: the proposed offer needs a record of what was open, and the portable profile is a shared artefact
that by contract does not carry one. Storing it there would put one person's session into a file colleagues
receive.

Correction: decide where the record lives (user-local config keyed by project path is the natural answer given
F8's existing fields) and say so in the spec. This is an owner decision, listed below.

### F10 — Moderate. Two repositories write `CLAUDE.md` into the same project

`ReferenceSet.java:42` sets `FILE_NAME = "CLAUDE.md"`, written from `MainFrame.java:4336` when the user ticks
the picker's reference-guide box; `bundle.ts:770-773` writes `CLAUDE.md` and `AGENTS.md` with different,
project-specific content. Collision is avoided only by `ReferenceSet`'s `ALREADY_EXISTS` result, which is
tested (`TemplateReferenceGuideTest.aTemplateThatSHIPSItsOwnKeepsIt`).

Consequence: `docs/specs/spec-project-starter-journey.md:31-33` requires the generator to own "one obvious
bootstrap entry" for every supported project. Once that lands, the analyser's writer is redundant for new
downloads but still the only bootstrap a pre-existing bare project will ever get. Two owners of one filename,
in two repositories, is how instructions drift.

Correction: state which writer owns the file after default-on lands, and keep the existing guard as the
arbiter rather than adding a second rule.

### F11 — Moderate. "Declared processor information" is not available for every project type at download time

`docs/specs/spec-project-starter-journey.md:29-30` requires the portable profile to carry it. Verified: the
only profile emitter takes the processor from `spec.mongoose!.processors[0]` (`bundle.ts:588-594`), and
interpreted, embedded and DSL templates have no generated processor class until a build runs — for
`interpreted` there is none at all. `ProjectProfile` tolerates empty lists and never throws
(`ProjectProfile.java:245`), so this degrades silently into an empty declaration.

Consequence: source navigation and graph pairing key off `eventProcessorFqn` / `selectedEventProcessor`. An
empty declaration is indistinguishable, to a reader, from a project whose processor is simply not built yet —
which cuts against the standing "declared, never inferred" decision and against the spec's own care at
`:37-38` about not advertising files that do not exist.

Correction: extend that same honesty to the processor: require the profile and the landing surface to
distinguish "declared, not yet generated" from "absent".

### F12 — Moderate. The start page is selected by log presence alone; the spec states the requirement but not the decision site

Verified: `MainFrame.java:913-916` — `syncRecordsCard()` shows the start card exactly when `store == null`,
with a comment noting it is deliberately the single call site "so the two can never both be right".
`StartPanel.java` has no notion of a project (760 lines; the only match for "project" is incidental text about
regenerating a processor). This is the direct cause of the reported symptom, and `after.png` shows it: the
left rail still lists the project, its posture and both runbooks while the centre offers "Open the demo log".

The requirement at `docs/specs/spec-project-starter-journey.md:106-110` is right, but it describes an outcome
without naming the rule that produces the wrong one.

Correction: name `syncRecordsCard` and require it to choose among three states (no project; project with no
loaded evidence; records), so the fix is a widened rule rather than a second card competing with the first.

### F13 — Minor. Acceptance 6 asks for witnessed UI paths, and the one path most likely to explain the report is still untested

`README.md:38-40` is explicit that the socket drove the application and that quit/relaunch was not repeated.
Given F8, relaunch behaves differently from reopen. Acceptance 6 (`:127-128`) should name
"quit and relaunch with an active project" as a case rather than leaving it inside a general instruction to
witness the UI.

### F14 — Minor. The authoring page has no named host, and the analyser already has the right one

`docs/specs/spec-project-starter-journey.md:65-71` leaves layout and navigation open, which is reasonable for
a proposal, but the surface question is not cosmetic: the analyser would then have three catalogue renderings
(picker dialog, authoring page, website configurator). `StartPanel` already has four sections
(`:104`, `:117`, `:124`, `:137`, plus the AI-client offer at `:251`) and no "new project" section.

Correction: state whether the page is a new window or a `StartPanel` section. A project-aware start page (F12)
and an authoring section are the same surface; building them together is one change and avoids a third
rendering of the same catalogue.

### F15 — Minor. Acceptance 2 names four starting journeys and the catalogue supplies three

`docs/specs/spec-project-starter-journey.md:117-118` requires applicable runbooks for "worked example,
Mongoose + Spring, bare Fluxtion, and vendor integration". Verified against the catalogue: the first three map
to `analyser-bundle`, `fluxtion-spring-mongoose` / `mongoose-dynamic-spring` and `fluxtion-embedded`. There is
no vendor-integration entry; the closest is `fluxtion-connector`, which is a connector shape, not a project
consuming a third-party component library.

Correction: either map the vendor journey onto an existing entry by name, or record that a new template is in
scope — it is playground work that nothing currently tracks.

### F16 — Minor. The empty directory has no documented first step

Acceptance 4 (`:121-123`) has a fresh LLM start "in an empty directory with catalogue/bootstrap access".
Verified: the playground's own agent-facing guide `web/static/CLAUDE.md` mentions neither the catalogue nor
the scaffold endpoint, and `web/static/fluxtion-golden-path.md:15` names
`starter-templates/index.json` but not `start/scaffold`. So the catalogue is discoverable and the way to
obtain a project from it is not.

Correction: the journey spec already says documentation "must describe the actual template/token API and its
limits" (`:62-63`); name the file that owns it. The playground's `CLAUDE.md` is the one an agent reads first.

---

## Owner decisions still required

1. **Legacy semantics for the support flag.** Absent means on, or absent means off with the UI always writing
   it? (F5) This one blocks the playground schema change and therefore most of the rest.
2. **Headless opt-out.** Add a query parameter to `?template=`, or state that headless callers cannot decline?
   (F4)
3. **Where the last-session record lives**, and whether it is keyed by project. (F9)
4. **What happens to the existing launch restore** at `Main.java:80` — retire, gate, or make project-scoped?
   (F8)
5. **What `onboarding` now obliges.** May an entry carry **Recommended starting points** without declaring
   `keyNeed` and `agentBootstrap`? (F2, F3)
6. **Mongoose lifecycle scope.** Does `BundleLifecycle` (stop, audit export) generalise out of the bundle, with
   the mongoose-plugins floor that implies, or do plain Mongoose projects get start and feed guidance only?
   (F6)
7. **The authoring page's host** — new window or `StartPanel` section. (F14)
8. **Vendor integration** — map onto an existing template or commission a new one. (F15)

---

## What I verified versus what I only read

**Verified by running or by reading the source it describes**

- The headless API description in the journey spec (`:53-58`) is accurate: `?s=` or `?template=`, GET only,
  identity overrides only, no JSON POST — read in `scaffold/+server.ts`.
- The shipped picker selection rule the spec says D-1 supersedes — `TemplateCatalogue.onboarding():96-105`,
  tagged, then Mongoose/hosted, then everything with a note.
- Exactly one of fourteen templates sets `analyserBundle`; the profile, both agent files and the stop/export
  scripts come only from that path.
- `agentBootstrap` is live on the playground's `origin/main` and absent from the analyser's `Entry` record.
- The `context` early return and the position and source of `graphs`, `reports` and `runbooks`.
- The staged profile's persisted key families — read-only, on the copy under `/private/tmp`; the live profile
  was not opened for writing and no analyser session was started.
- The start-page rule (`syncRecordsCard`) and `StartPanel`'s lack of project awareness.
- Launch-time restore at `Main.java:80` and the global `logFile` / `graphmlFile` fields.
- The evidence packet's screenshots: both inspected. `after.png` matches its description — the project, its
  posture and both runbooks survive in the left rail while the centre shows the generic demo page.
- `mvn -o -Dtest=SpecLinksResolveTest,TemplateCatalogueTest,ProjectProfileTest test` → **29 tests, 0 failures,
  0 errors, 0 skipped**. `SpecLinksResolveTest` walks `docs/specs` (`:62`), so the new spec's links are
  covered and resolve.

**Read, not independently reproduced**

- The probe script's own run (`probe.py`) and its `summary.json`; I read the code and the recorded results
  rather than re-running it against a fresh copy. Its reasoning matches what I verified in the source.
- The six earlier feedback packets and the staged-feedback review, read only for context.
- The tracker's claim that a live `fluxtion-spring-mongoose` download contains no runbook or bootstrap file.
  I did not fetch it; the generator source and the catalogue's `agentBootstrap` field independently imply the
  same thing.

**Not run**

- Any UI path. No analyser was started, no menu was clicked, no relaunch was performed. F8 and F13 therefore
  rest on source reading, not on a witnessed run.
- Template generation for all fourteen entries — attempted locally and abandoned rather than modify the
  owner's playground checkout to work around a module-loading issue. The per-template facts above come from
  the starter specs and the generator's gates instead.
- Anything requiring network, publication or a paid key.

---

## Suggested implementation order

The order is chosen so that each step's acceptance can be witnessed when it lands, rather than waiting for the
step after it.

1. **Playground — settle the flag** (F5, F4): field name, legacy semantics, the headless parameter, and a test
   whose input is a link captured before the change. Nothing else can be specified until absent-means-what is
   decided.
2. **Playground — factor the profile, bootstrap and runbook emitters out of the bundle path** (F6, F10, F11):
   one generator, applied per project type; state the processor-not-yet-generated case; declare `keyNeed` and
   `agentBootstrap` on every catalogue entry (F2, F3).
3. **Playground — document the way in** (F16): the catalogue and the scaffold route in the agent-facing guide.
   Cheap, and acceptance 4 is unfalsifiable without it.
4. **Analyser — saved definitions in `context`** (F7), above the `filter == null` return, with its panel row
   and docs page in the same commit. This unblocks both the landing page and acceptance 5.
5. **Analyser — the project-aware landing** (F12), as a widened rule at `syncRecordsCard`, naming closed and
   missing inputs.
6. **Analyser — the restore offer** (F8, F9), project-scoped, and reconciled with the launch-time restore in
   the same change so the product has one answer.
7. **Analyser — the full-catalogue picker** (F1, F2, F3), after step 2, so that most entries now carry a
   profile and the discovery fallback is the exception rather than thirteen cases in fourteen.
8. **Optional — the authoring section** (F14), in the surface step 5 already touched.
9. **Witnessed acceptance** (F13): selection, download, open, close, reopen, **and quit-relaunch with an
   active project**.

Steps 1–3 are playground-owned, 4–8 analyser-owned, and step 2 carries the mongoose-plugins dependency that
decision 6 settles.

---

## Housekeeping

This review file is the only thing this session added to the repository. Nothing was committed or pushed, the
reviewed specs and the tracker are untouched, and the other uncommitted work in the tree was left exactly as
found. Scratch files used for the checks live under this session's scratchpad and are disposable.
