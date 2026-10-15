# M73 — Create a project over the action socket: `open {project, create}`

**Status:** SETTLED r3, 2026-10-15 — r1 reviewed independently (verb shape sound; flow, failure handling and consent
redesigned in r2); r2 re-reviewed (ready after eight small fixes, folded into r3). Review findings are cited as [R…].

**Tracker:** M73. **Builds on:** [`completed/spec-portable-context.md`](completed/spec-portable-context.md),
[`spec-session-processor.md`](spec-session-processor.md) (rule 9), the M35.8 `open {project}` contract and the M19.13
*Project ▸ New project…* discovery offer.

## 1. Why

An agent that wants a directory treated as an analyser project today either asks the person to use **Project ▸ New
project…** (a modal it cannot reach) or hand-writes `.analyser/project.fluxtion-settings` by copying another project's
file — which happened on 2026-10-14 for a demo. That bypasses discovery and the profile writer. The human flow already
discovers what is in the directory and adopts only what is chosen (D-AI5). This spec gives an agent that path, not a
second one — and fixes three defects in the existing create path found while specifying (§6).

## 2. Decisions

| # | Decision |
|---|---|
| D-73.1 | **No new verb.** `create` is a parameter of `open`, beside `project`. The verb set stays at 18. |
| D-73.2 | **Preview, then apply.** `create: true` writes nothing and returns the offer with a digest. `create: {…}` creates. |
| D-73.3 | **One adoption function.** The selection-to-config mapping moves from `ui/NewProjectDiscovery.apply` to a `config/ProjectAdoption` that both surfaces call through the one effect performer; it iterates **the offer's order** so profiles are deterministic [R F6]. |
| D-73.4 | **The node decides what may be adopted.** The adapter observes the offer as plain strings and carries it with the selection; `SessionBoundary` computes `selection − offer` and refuses a non-empty difference. No `ui` type crosses into `session/` [R §2]. The performer adopts the node-approved items and never re-discovers. |
| D-73.5 | **Create never overwrites, by construction.** A dedicated writer creates `.analyser/` with `createDirectory` (not `createDirectories`) and writes the profile with `CREATE_NEW` and no link-following. It is separate from `ProjectProfile.save`, which merges and overwrites [R F3, §2]. |
| D-73.6 | **One write.** The selection is folded into the create: snapshot → clear project-scoped → adopt → write once. If the write fails, the snapshot is restored and `activeFile` is unchanged. No debounced adoption save [R F1, F2, §2]. |
| D-73.7 | **Consent is a separate switch, decided by origin.** New setting `assistant.createProjects` (*Settings ▸ Assistant ▸ Allow the assistant to create projects*), **off by default**, **machine tier only**: read and written by `ConfigStore` like `assistant.exports`, never exported or imported by `SettingsShare`, never a project-profile key, never carried by a bundle — a cloned repository or an imported share cannot turn it on (pinned by a test). The adapter carries the raw switch value and the origin as **separate facts**; the node combines them. It gates requests of assistant origin (MCP/REST and the in-app assistant); a **person** using the menu is never gated. `assistant.exports` is not reused: its public meaning is "confined to one directory" [R F4, §3]. |
| D-73.8 | **Refused create still supersedes** (policy, option (b) of [R §2]). A create is an `OpenProjectRequested{kind: CREATE}`: the last deliberate request wins, as for `open {project}` today — a pending log load is superseded whether or not the create is admitted, and a running in-app turn ends only when the create comes from a **competing** origin (the in-app assistant's own create does not end its turn — `AssistantLoop.owns`). Pinned by a test covering both origins. |
| D-73.9 | **Parity with the dialog, minus the graph.** v1 adopts source roots, skill pointers and (OD-73.2, accepted) the `CLAUDE.md` reference guide. **Opening a graph is cut** from the socket create: it is a UI open, never persisted; the agent calls `open {graphml}` next [R §6]. The menu keeps its graph radio, unchanged. |
| D-73.10 | **Unknown keys in `create` are refused**, so later fields (processors, a named profile) are additive [R §4]. `profile` is reserved and refused in v1 ("named profiles are not created over the socket yet"). |

OD-73.1 (workspace-anchor confinement) is **closed: no** — accepted by the owner 2026-10-15, and the reviewer showed the
anchor is project-scoped and cleared on create, so it cannot be a machine-level boundary [R §3].
OD-73.2 (reference guide over the socket) is **closed: yes**, only when selected and offered, never overwriting.

## 3. The contract

### 3.1 Routing

`open` with `create` is checked **first**, before the follow, canvas (`posture`/`record`/`close:handoff`), `analysis`,
`restore` and `bundle` branches [R F6]. `create` requires `project`; combined with any of `follow`, `posture`, `record`,
`close`, `analysis`, `restore`, `bundle` it is **refused whole**. Other parameters (`log`, `graphml`, …) are ignored and
named as M35.8 does; a new `IgnoredParameters.SUBORDINATES` entry for `project` lists `create` so it is never reported
ignored. The app interface gains `createProject(String dir, Object create)` with a refusing default (the
`ProjectVerbTest` legacy-implementor pattern).

### 3.2 Paths

`<dir>` must be **absolute** (a relative path would resolve against the analyser's working directory) and an existing
directory. For **assistant origin only** — a person's menu behaviour is unchanged [R r2-1] — it is also refused when
inside an evidence-bundle working copy (`EvidenceBundle.isWorkingCopy`), inside the exchange directory in force, or
inside an existing project (`ProjectProfile.findNear(dir.getParent())`, the nearest enclosing profile, is named; the
directory itself is covered by `profileExists`), and when `<dir>/.analyser` is not either absent or a real directory
whose real path is under `<dir>`'s real path (a symlinked `.analyser` is refused). A directory holding only named `project.<x>.fluxtion-settings` profiles is not "existing" for
the canonical file, and the echo names them.

### 3.3 Preview — `open {project: "<dir>", create: true}`

Writes nothing; runs **off the event thread** (like `doOpenAnalysis`). Returns, built from `NewProjectDiscovery.Offer`
as it exists [R F5]:

```json
{"ok": true, "action": "open",
 "createPreview": {
   "dir": "<real path>",
   "profileExists": false,
   "projectKind": "spring" | "plain",            // detectKind null → "plain"
   "sourceRoots": [".", "medmon-app/src/main/java"],   // project-relative, forward slashes; the directory itself is "."
   "skills": [{"path": "skills/x/SKILL.md", "name": "...", "description": "..."}],   // identity = path
   "graphs": [ GraphmlDiscovery.Candidate.toMap() … ],  // informational: open one with open {graphml} after creating
   "referenceGuide": "CAN_CREATE" | "EXISTS" | "NOTHING_AGREED",
   "truncated": false,                           // a discovery cap was reached
   "offerDigest": "sha256:…",                    // over the offered strings, in offer order
   "createProjectsSwitch": true,                 // the raw assistant.createProjects value; the apply's node decides by origin
   "next": "open {project: \"<dir>\", create: {sourceRoots: [...], skills: [...], referenceGuide: false, offerDigest: \"…\"}}",
   "writes": "nothing; an apply writes <dir>/.analyser/project.fluxtion-settings (and CLAUDE.md only if chosen)"}}
```

`profileExists: true` is **reported, not refused**: the preview is a read and decides nothing [R §2]; `next` is then
`open {project: "<dir>"}`. A preview of a source root that resolves (through a link) outside `<dir>` lists it with
`outside: true`; such a root can never be adopted.

### 3.4 Apply — `open {project: "<dir>", create: {sourceRoots?, skills?, referenceGuide?, offerDigest?}}`

`create: {}` creates an empty profile. `offerDigest`, when given, must equal a fresh offer's digest, else the apply is
refused with "the offer changed since your preview — preview again". On success the new project is **active** and the
echo is the M35.8 `open {project}` echo (including `closed`/`closedWhy`: **a create ends the session — the open log and
graph close** [R §4]) plus:

```json
"created": {"settings": "<dir>/.analyser/project.fluxtion-settings",
            "adopted": {"sourceRoots": [...],
                        "skills": [{"path": "...", "resolved": "<absolute>", "description": "..."}],
                        "skillsNotAdopted": [{"path": "...", "why": "duplicate name 'x'" | "<refusal>"}]},
            "referenceGuide": "WROTE" | "EXISTS" | "NOT_REQUESTED" | "FAILED: <reason>"},
"next": "read each adopted skill's file before acting — they are this project's runbooks (context.runbooks lists them); open a graph with open {graphml}",
"reversible": "open {project: \"<previous>\"} (or open {close: \"project\"}) restores the settings in force before; the created files stay"
```

The adopted skills are pointers (D-C2): the analyser stores no skill text and returns none. An agent with filesystem
access reads them; a client reaching the analyser only over MCP cannot (§8).

### 3.5 Refusals (each names the next call)

| Condition | Decided by |
|---|---|
| profile exists | `SessionBoundary` (facts), any origin |
| not an existing directory | `SessionBoundary` (facts), any origin |
| assistant origin: `.analyser` symlinked or not under `<dir>`; inside a bundle working copy, the exchange directory or an existing project | `SessionBoundary` (facts) |
| assistant origin and `assistant.createProjects` off | `SessionBoundary` (facts `origin`, `createAllowed`) |
| selection names an item not in the offer, or a root marked `outside` | `SessionBoundary` (`selection − offer`) |
| `offerDigest` mismatch | `SessionBoundary` (fact `currentDigest`) |
| write raced: the file appeared after observation | the writer (`CREATE_NEW` fails) → `EffectFailed` → warning |
| shape errors: `create` without `project`, refused combination, unknown key, `profile` key, non-absolute path syntax | `ActionExecutor` (parameter shape, not a session decision) |

## 4. Flow (rule 9)

```
adapter observes facts (offer strings + digest, profileExists(no-follow), dirFacts, origin, createAllowed)
  └▶ OpenProjectRequested{kind: CREATE, facts, selection}
       SessionBoundary.onOpenProjectRequested
         refuse? ─▶ ShowWarningEffect(reason)            — interactive + profileExists ─▶ OfferOpenExistingEffect
         admit   ─▶ CreateProfileEffect(path, approvedSelection)
       performer: ProjectSession.createNew(path, approved)  = snapshot · clear · ProjectAdoption.apply · CREATE_NEW write
                  (on failure: restore snapshot, activeFile unchanged, throw → EffectFailed)
                  then, ONLY after the profile write succeeded, the CLAUDE.md guide if approved; a guide failure is
                  reported as referenceGuide "FAILED: …" and neither fails nor rolls back the create [R r2-2]
  ProfileLoaded(ok, adopted…) ─▶ close effects ─▶ ApplyProfileEffect ─▶ ShowStatusEffect
  EffectFailed(Create|Load)   ─▶ ShowWarningEffect(reason); inFlight cleared        [R F1]
```

- **Preview** does not enter the session — a read with no state change.
- **Apply ordering** [R r2-6]: the adapter observes the offer and digest **off the EDT**, then moves to the EDT to
  submit (`requestProject` is EDT-bound). The gap this leaves is covered by `CREATE_NEW` and the digest check.
- **Open instead** [R r2-5]: `OfferOpenExistingEffect` shows its question with `invokeLater` and, on *Yes*, submits a new
  `OpenProjectRequested(EXPLICIT_SWITCH)` from outside the dispatch — the `ShowProjectReopenEffect` pattern; never a
  re-entrant submit.
- **The menu, after the change** [R §2, must-fix 7]: (1) choose a directory; (2) the frame observes facts at once; if
  `profileExists`, it submits immediately and the node's refusal is rendered as the existing **"That directory already
  has a project. Open it instead?"** question (`OfferOpenExistingEffect`; *Yes* requests the switch) — so the person is
  not asked to fill the offer first, as today; (3) otherwise the offer dialog; (4) submit with the selection. A person
  is never gated by `assistant.createProjects`, nor by the assistant-only path refusals of §3.2. The graph radio, if
  chosen, opens the graph after the transition exactly as today (unchanged, out of this milestone's routing). Visible
  change for a person: a failed write now says so instead of appearing to succeed, and a profile that appears (or a
  dangling profile link) between choosing the directory and confirming is refused rather than written through.
- Regenerate the session processor (`mvn -Pregen process-classes`) and commit the generated source.

## 5. Every write a create causes [R §3]

1. `<dir>/.analyser/` (one `createDirectory`, only if absent) and `<dir>/.analyser/project.fluxtion-settings` (`CREATE_NEW`).
2. `<dir>/CLAUDE.md` — only if `referenceGuide: true` was selected and offered (`CAN_CREATE`); `ReferenceSet.create`, `CREATE_NEW`.
3. The **outgoing** project's pending changes are flushed to its own profile (as any project switch does).
4. Machine config: `activeProjectPath` and the recent-projects list — **the created project becomes the project
   activated at the next start**.
5. The session-recovery journal entry for the new session.

The FAQ's security section is rewritten to state this, the new switch, and the preview's **read reach** (skills to depth
7, GraphML to depth 12, ≤ 32 MB each, under the chosen directory); `FaqSecurityContractTest` pins it.

## 6. Defects fixed on the way

- **G1 — overwrite is prevented only by a dialog.** Now prevented by construction (D-73.5) and decided in the node.
- **G2 — a failed create reports success and can destroy settings** [R F1]. `SessionBoundary` has no `EffectFailed`
  arm, so the caller sees success; `createProjectAt` then adopts into the already-cleared config and the debounced save
  writes it — over **the previous project's committed profile**, or over the person's **global settings** when no
  project was open. Fixed by D-73.6 and the `EffectFailed` arm (which also covers a thrown `LoadProfileEffect`).
- **G3 — nondeterministic source-root order** in created profiles (`Set.copyOf` iteration). Fixed by D-73.3.

Recorded, not fixed here (follow-up issues): **Save project as** overwrites an existing profile silently and inherits
its nonce [R F3]; `openSampleProject` writes from the frame behind its own check.

## 7. Tests (rule 8 — each with the defect it catches; a wrong-result witness for each starred one)

| Test | Catches |
|---|---|
| ★ performer-level parity: menu-shaped and socket-shaped selections through `ProjectAdoption` give byte-identical profiles (modulo nonce), headless | a second adoption implementation |
| source test: both surfaces submit `OpenProjectRequested{CREATE}`; the qualified calls `project.createNew(`, `ProjectAdoption.apply(` and `NewProjectDiscovery.writeReferenceGuide(` appear in `ui/` only inside `performSessionEffect` (new `OneDispatchModelTest` rule, qualified so it never matches a definition; a witness per pattern) | a frame-side create or adoption |
| ★ G2: failing write with project A active → A's file byte-identical, config equal to snapshot, caller sees an error, inFlight cleared; and with no project → global config unchanged (`FakeSessionAdapter.createThrows`) | G2 |
| ★ race: the profile appears between observation and write → `CREATE_NEW` refuses; A untouched | a writer that overwrites |
| symlinks: `.analyser` symlink, dangling profile symlink, source root linking outside → each refused / `outside` | link escapes |
| deterministic root order across two runs with different `Set` salts (or asserting offer order) | G3 |
| preview writes nothing (tree listing identical) and pre-selects nothing; runs off the EDT | a writing or selecting preview |
| node refusals: exists, not dir, relative, bundle working copy, exchange dir, nested, unoffered, digest mismatch | D-73.4, §3.2 |
| consent: assistant origin + switch off → refused; person origin + switch off → admitted; in-app assistant origin gated | D-73.7 |
| ★ the switch is machine-only: a profile, a settings share and a bundle containing `assistant.createProjects=true` leave it off | consent smuggled in by a repository |
| path refusals are assistant-only: a person creating a module project inside a monorepo project is still admitted | a person's menu behaviour regressing |
| supersede policy: a refused create supersedes a pending log load; a competing-origin create ends a running in-app turn, the in-app assistant's own does not (D-73.8) | silent policy drift |
| routing: `create` with canvas/analysis/follow/restore/bundle refused whole; `create` without `project` refused; unknown key and `profile` refused | contract drift |
| honest echo: duplicate skill names reported in `skillsNotAdopted`; guide `EXISTS` reported | an echo claiming adoption it did not do |
| verb count still 18; `open` schema names `create`, "preview", "never overwrites" | D-73.1 |
| `FaqSecurityContractTest` names the switch and the writes | security text drift |

## 8. Out of scope (follow-ups)

- **Clients without filesystem access cannot load the skills** they adopted: the socket serves pointers, not text
  (D-C2). Serving skill text would loosen a standing decision and is a separate one.
- Processors, exchange directory, environments, analyses, named profiles at creation — each needs discovery first.
- Creating from a template over the socket (network download; separate trust decision).
- The *Save project as* overwrite and nonce inheritance, and `openSampleProject` routing (§6) — filed as a GitHub issue
  when this lands (rule 8: recorded with its check, not merely noted).
- MCP marks `open` destructive, so clients prompt even for a preview; accepted.

## Implementation notes (2026-10-15)

Implemented on `feature/create-project-verb`; acceptance open (tracker M73). Where the code made a detail impossible
or ambiguous, the closest sound option was taken and is recorded here.

- **Where things live.** Facts and selection are plain records in `SessionEvents` (`CreateFacts`, `CreateOffer`,
  `OfferedRoot`, `OfferedSkill`, `CreateSelection`, `CreateRequest`, `CreateApproved`), carried by a new
  `OpenProjectRequested.create` component (null for every other kind). The adapter's observation, preview and
  selection translation are `ui/CreateProjectObservation` (headless, reads only). Adoption is `config/ProjectAdoption`,
  called from `ProjectSession.createNew`; the writer is `ProjectProfile.createNew`.
- **Assistant origin** is a `MACHINE` audience *or* a non-null in-app `assistantOrigin`; only a `PERSON` audience with
  no assistant origin is a person.
- **Digest.** `offerDigest` is sha256 over the *adoptable* offer in offer order — project kind, reference-guide
  outcome, each root (relative, with its `outside` mark) and each skill (path, name, description). GraphML candidates
  are informational and not adoptable, so a new graph does not invalidate a preview.
- **Preview shape.** `sourceRoots` lists inside roots as strings and an outside root as `{path, outside: true}` (the
  spec's "lists it with `outside: true`"). The preview also carries `namedProfiles` (and the apply echo does) when
  `.analyser/` holds named profiles, and a `note`. A preview of a path that is not an existing directory is an error
  from the adapter (the preview never enters the session).
- **Reference guide.** `referenceGuide: true` when the offer is not `CAN_CREATE` is a `selection − offer` refusal.
  `NOTHING_AGREED` at write time is reported as `FAILED: …`.
- **Exchange directory.** "In force" is read as `ExchangeDir.of(config).dir()` whenever it is non-blank, whether or not
  file exchange is enabled — the conservative reading.
- **Menu.** The frame re-observes the filesystem facts at submit but judges the selection against the offer the person
  saw (no second discovery on the event thread). The guide's `EXISTS`/`FAILED` outcome is still shown as the dialog's
  warning, and the graph radio (with its status suffix) is unchanged.
- **Status line.** The performer reports what it adopted in `ProfileLoaded.reason`; for `CREATE` the node composes
  `created project <name> · adopted N source root(s), M skill pointer(s)[, wrote CLAUDE.md]` (rule 9: one composer).
- **`OfferOpenExistingEffect`** is answered with `StatusShown` at once (its *Yes* is a new request, not a later result
  of this one) and sets the frame's `sessionProblem`, so the menu treats it as "not created".
- **Failed write.** Besides restoring the snapshot, `createNew` restores the "no project" defaults it may have just
  taken, and `ProjectProfile.createNew` removes an `.analyser/` it created if the write then fails (it is empty).
- **`ProjectSession.create(Path)`** is kept as a thin empty `createNew`; it now requires the project directory to
  exist (tests that relied on `createDirectories` create it first). `NewProjectDiscovery.apply` is removed;
  `writeReferenceGuide(Offer, Selection)` stays for its tests and a `writeReferenceGuide(Path, kind)` overload serves
  the performer.
- **Dispatch rule.** In `ui/`, `ProjectAdoption.apply(` appears nowhere (it runs inside `ProjectSession.createNew`), so
  `OneDispatchModelTest` pins all three qualified patterns to zero outside `performSessionEffect`, requires
  `project.createNew(` and `NewProjectDiscovery.writeReferenceGuide(` inside it, forbids the old `project.create(`, and
  requires both surfaces to submit `TransitionKind.CREATE`.
- **Regeneration.** `mvn -Pregen process-classes` added the `sessionBoundary.onEffectFailed` dispatch; the hosted
  generator stamped `target generator version 1.0.79` (builder 1.0.76 unchanged).
- **Not done here:** the §6/§8 follow-up GitHub issue (*Save project as* overwrite and nonce, `openSampleProject`) is
  to be filed when this lands; no socket run against the built jar yet.

**Witnesses run (each starred test failed at its named assertion, then passed when restored):** parity — approve in
selection order; G2 — no snapshot restore ("the config equals the snapshot"), and `onEffectFailed` returning early
("the caller sees an error"); race — `CREATE`+`TRUNCATE_EXISTING` for `CREATE_NEW`; switch — `ProjectProfile.load`
reading `assistant.createProjects`; and `OneDispatchModelTest` once per pattern (`project.createNew(`,
`ProjectAdoption.apply(`, `NewProjectDiscovery.writeReferenceGuide(`, `project.create(` in `createProjectAt`).
