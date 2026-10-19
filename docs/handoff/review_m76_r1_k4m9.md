# Review of M76 r1: the authoring journey (spec-authoring-journey.md)

**Reviewer id:** k4m9. **Date:** 2026-10-17. **Branch reviewed:** `docs/m76-checkpoint-and-completion` at `101dce84`.
**Read against:** the code under `src/main/java/telamin/fluxtion/audit/analyser/`, `tools/replay/ReplayBundle.java`,
`src/graph/java/.../session/graph/SessionProcessorBuilder.java`, M74 r16, M70, the packaging spec, D-I1 to D-I8, M69, M73,
the session processor spec, M12, the dev-loop spec, M75 r2 (`origin/docs/m75-document-renderer`), the analyser's own
format spec (`docs/site/format-spec.md`), and the Fluxtion reference `docs/claude.txt` (fetched 2026-10-17).

## Verdict

**Not ready to build. Revise to r2.** The direction is sound. Criteria as closed data, three verdicts, the person as
the only confirmer, pointer-only drafts and "no Run button" are all right, and most of §2 checks out against the code.
Five problems would still produce wrong results or unsafe behaviour if built as written:

- the checkpoint hold sits in a node that cannot hold its parents' decisions (F1);
- run identity rests on `LogFingerprint`, which M69 already ruled out as an identity basis (F2);
- "same inputs" is claimed from a subsequence rule that cannot show it (F3);
- labels and confirmations are trusted from profiles that arrive from other people (F4);
- every after run made by the existing runner will show as *not decided*, because it writes no stream-end marker (F5).

Several silence and vacuity rules also need settling before A1 starts (F6, F7, F8). The phase sizes for A2, A3 and
A4 are low (F17).

## Findings

### F1 — blocker — §6.3, §11: the hold is placed where Fluxtion ordering cannot honour it

**Evidence.** §11 (lines 673, 693) gives `investigation` the parents `assistantProfile` and `localAgentSession`. It
also makes `investigation` decide "the offer and the hold (§6.3)" and release `ApplyAssistantProfileEffect` "from
the hold". In the code, `AssistantProfileChoice.onProfileChosen` (`session/node/AssistantProfileChoice.java:116-137`)
requests `ApplyAssistantProfileEffect` itself, in the same cycle as the `AssistantProfileChosen` fact. New chat is
decided in `localAgentSession`, and the project switch is held in `sessionBoundary` (M74 §5.3 step 1). A child node
runs after its parents in the cycle. By the time `investigation` sees the fact, the apply, the New chat or the
switch has already been requested, and the child cannot take it back.

There is a second gap. While a profile change is held, the person must be able to Send *"Ask Claude to draft the
rest"* (§6.4) and receive a `propose {checkpoint}` turn. M74 deferred Move steps 3–7 because a turn inside a held
transition was hard (r11-1). §6.3 (1) and (3) reintroduce that same turn-during-hold without specifying it.

**Fix.**
- Put each hold in the node that owns the action: `assistantProfile` for a card, `localAgentSession` for New chat,
  `sessionBoundary` for Move.
- Each owner asks for `ShowCheckpointOfferEffect` and holds its own pending request. `investigation` (downstream)
  reports `CheckpointSettled{request, choice}` as a fact, which the owner consumes to release or drop.
- Specify what Send, Stop and a second profile change do while a hold is open, and which node refuses them.
- Add a T12 witness that posts `AssistantProfileChosen` and asserts that **no** `ApplyAssistantProfileEffect` is in
  that cycle's effect list.

### F2 — major — §6.1, §6.2, §10.1, T17: `LogFingerprint` cannot tell runs apart

**Evidence.** The spec stores record pointers as "index + the run's `LogFingerprint`" (line 315) and identifies runs
by it (lines 320, 569). `LogFingerprint` (`report/LogFingerprint.java:24-25`) is the file name, the record count,
the first and last `logTime`, and free-text provenance. M69 §3.5 says it "stays a mismatch *warning* … It is never a
basis for 'current'."

With a data-driven clock, a before run and an after run of the same scenario have the same base and offsets, and
very often the same count. They then differ only by file name. Re-running into a reused name (delete, then run
again) gives two different logs with the same fingerprint. T17's witness ("refused against another log") would pass
on that collision.

**Fix.** Identify a run by the content identity the session already computes: the read identity's SHA-256 (M68), the
"run basis" walks use. Keep `LogFingerprint` as the human-facing warning. Record pointers must carry the record
digest, as M69 targets do. Add a witness: two scenario runs with identical times and counts but one changed value
must not share an identity.

### F3 — major — §8.4, §10.2: "paired" over-claims "same inputs"

**Evidence.** `ReplayPairing.observe` (`bundle/ReplayPairing.java:88`) skips log records until it finds a match. It
proves that the replay's records are a **subsequence** of the log. It does not prove that the log has no other
inputs.

So a run whose `Main` feeds extra events, or a scenario plus a hard-coded feed, still "pairs". So does a before log
that holds exported-service calls the replay cannot carry: `ReplayPairing` counts `serviceCalls`, and M70 §3.4
warns about them. The view would still say *"Both runs replay the same 7 recorded inputs (paired)"* and *"Both runs
come from scenario … (paired)"* (lines 580-581).

**Fix.**
- Word the strength honestly: *"the scenario's 4 events all appear, in order, in both runs"*.
- Add a separate count of log records that are neither matched nor graph-raised where the analyser can tell, at
  least records whose event type is a scenario type but is unmatched.
- Make any `serviceCalls > 0` on either run downgrade to **not paired**, with the M70 sentence.
- Add a witness: a run carrying one extra input event must not read as the same inputs.

### F4 — major (security) — §4.2 step 2, §5.6, §6.2, §6.5, §6.7: labels and confirmations are self-asserted by the file

**Evidence.**
- Investigations live in the committed project profile (`investigation.N.*`, line 420), with `proposedBy`,
  `confirmedAt`, per-row labels (YOUR NOTE, MODEL CONCLUSION) and the state IN_USE.
- The teaching example ships its investigation in "its committed project profile" (line 165).
- A colleague's clone or a downloaded archive carries the same project nonce, so §6.5's "from another project" check
  passes.

Nothing stops a downloaded or third-party profile from shipping criteria marked `PERSON`/confirmed, text labelled
**YOUR NOTE**, an IN_USE state, and included rows. C1 would then be armed, and Full Claude Code would read that text
as the person's.

The example author's text is also third-party text, but §6.6's "data, not an instruction" line names only "person
or assistant".

**Fix.**
- Treat authorship and confirmation as **machine-tier** facts recorded where the person confirmed them, as consent
  is (M74 §3 "machine-tier only").
- Any investigation the analyser did not see confirmed on this machine loads as DRAFT, every criterion unconfirmed,
  every non-OBSERVED row excluded, and labelled *"from the project file (author not known)"*. The example case is
  labelled *"from the example (publisher …)"*.
- Extend the `context` caveat to every non-person author.
- Witness: a hand-written profile that claims `proposedBy=PERSON`, `confirmedAt` and IN_USE is shown unconfirmed, and
  C1 is disabled.

### F5 — major — §5.4, §10.2: the existing runner's output can never pass

**Evidence.** `passes` requires `streamEnd: complete` (line 260). `ReplayBundle.run` (`tools/replay/ReplayBundle.java:
175-180`) writes `---` plus each record and **no stream-end marker**, so `StreamEnd` reads UNKNOWN (`parse/StreamEnd.java`).
Every after run made by "paired by recorded inputs" (the strongest strength, §10.2) is therefore *not decided* on
every universal criterion.

Production logs in diagnosis usually carry no marker either. §14's claim that "A1–A4 are useful to the daily
diagnosis workflow on their own" (line 762) therefore mostly yields "✗ before, ? after".

**Fix.**
- A4 adds a stream-end marker (`streamEnd: normal`, `streamEndRecords: N`, per `parse/StreamEndMarker.java`) to
  `ReplayBundle` output, and §8.3 step 5 states the same for `RunScenario`.
- State in §10.4 the expected outcome for a production before log with no marker.
- Add a T3 case on a real `ReplayBundle` output.

### F6 — major — §5.3, §5.4, T2: two incompatible silence rules

**Evidence.**
- §5.3 (line 253) says c2 is *not decided* "if `NodeLogging` cannot show that d can log". Under that rule UNKNOWN
  means not decided, and UNKNOWN is the normal case when source is missing (`topology/NodeLogging.java:21-27`).
- §5.4 `NODE_CANNOT_LOG` (line 271) fires only when it is silent by construction or "the audit level in force hides
  its entries".
- `AuditLevel` (`topology/AuditLevel.java:22-31`) gives only a **lower bound** and says "the log cannot distinguish
  those". The analyser cannot decide "the level hides its entries".
- The plain words are about *entries* ("writes at most 1 audit entry"), which the log decides on its own.

**Fix.** Pick one model and write it down. Recommended:
- A verdict is about entries and is decided from the log.
- `NODE_CANNOT_LOG` applies only when the node is `SILENT_BY_CONSTRUCTION` (any basis), because the criterion is then
  vacuous.
- UNKNOWN capability and any level that could hide entries add an APP caveat beside the verdict (*"silence is not
  proof it did not run"*). They do not change the verdict.
- T2 gets witnesses for SILENT (not decided), UNKNOWN (decided, with the caveat) and CAN_LOG (decided).

### F7 — major — §5.2, §5.4: vacuous greens and the `none` contradiction

**Evidence.**
- `KEY_NEVER_SEEN` covers "a key or node the criterion names". If it also covers event names, `none` can never pass:
  its whole point is an event that never appears. Yet §10.4 (line 611) shows *"No record is a RiskBreach record"*
  passing.
- If it does not cover event names, a misspelled event in `none` passes green.
- `each` with zero A records, `range` whose `match` selects nothing, `entries` with `match` selecting nothing, and
  `none … after {firstOf: S2}` where S2 never occurs all pass vacuously.

**Fix.**
- Check every event name against the graph's handled types (the GraphML or the generated `handleEvent` set), not
  only the log. A name known to neither is `KEY_NEVER_SEEN`.
- Add a reason `NOTHING_MATCHED` for universal kinds whose selector matched no record, shown amber, unless the
  person marks the criterion "may be empty".
- Define `firstOf` absent.
- Add a T-witness: a misspelled `none` event is amber, not green.

### F8 — major — §5.4: the counterexample rule is only right for monotone kinds

**Evidence.** "A counterexample is red on any log" (T3, line 770), with only `count ge` named as the exception
(line 261). On a log not known to be whole, a shortfall is also not decidable for these:
- `count eq` (too few);
- `each … op eq|ge` (the later ack may be in unread records);
- `entries … op eq|ge`, if per-record completeness is ever in doubt.

Only excess (for `le`/`eq`) and a found violation (for `none`/`range`) are certain.

**Fix.** Tabulate, per kind and op, which outcome is monotone in added records. Only those may be red on an
incomplete log. Map all six `StreamEnd.State` values explicitly. `MISSING_RECORDS` and `UNTERMINATED_MARKER` are not
"incomplete" in the same way as `UNKNOWN`. T3 gets one witness per non-monotone case.

### F9 — major — §4.3, §3.1 step 3: "fresh against their inputs (M66)" does not exist for an imperative build

**Evidence.** `DesignSession` and `ProducerResult` (`design/ProducerResult.java:47-75`) track a Spring design input and
an explicitly opened producer result. Its own text says "XML match alone does not establish result freshness". A
`FluxtionGraphBuilder` project such as the diamond writes no producer result. `docs/specs/spec-authoring-modes.md:35`
records that the build identity carrier on the audit log is **unpinned**: only the GraphML carries
`fluxtion.sourceFingerprint`, and a rebuild overwrites that GraphML in place.

So "a runnable application" cannot be decided as written. §6.1's "build identity where the GraphML states one" will
also attribute the after build's fingerprint to the before run if it is read late.

**Fix.**
- Drop "fresh" from the end condition, or define it from facts that exist.
- Have `RunScenario` (and `ReplayBundle --changed-build`) record the GraphML's `sourceFingerprint` and digest into
  the run, either as a header record or a sidecar the analyser checks.
- Snapshot the build identity at `RunAdded`, never later.
- Witness: rebuild after the before run; the before run keeps its original fingerprint.

### F10 — major — §4.2 step 5, §3.3: the *Teach me* change step needs a Fluxtion key or generator

**Evidence.** E17 says only the default build is keyless, because the generated processor is committed.
`docs/claude.txt` says "There is no generate-without-a-generator path — a machine with no key and no local build
engine cannot build any Fluxtion graph." Both suggested changes (line 173: change d's update rule; add a node) need
regeneration. A newcomer without a key stops at step 6 of 7, and nothing in §3.3 or the strip says so.

**Fix.**
- Add a step requirement, "Changing the graph needs a Fluxtion key or local build engine", shown before step 6.
- Offer one change that needs no regeneration, if the example allows one.
- Name the key as a prerequisite in OD-76.7.
- Add it to §3.3's table.

### F11 — major — §8.3, §8.5: the run command cannot be composed as written

**Evidence.**
- §8.3 writes `--cp target/classes:<runtime deps>`, and the analyser "composes the command" (line 525). The analyser
  cannot know `<runtime deps>`, which includes the libraries added in step 2, without running Maven. Running Maven
  is executing the person's build, which the spec forbids (OD-76.5).
- `ReplayBundle` pins `//DEPS com.telamin.fluxtion:fluxtion-runtime:1.1.0` (line 3). A processor generated against
  another runtime puts two runtimes on one classpath.
- `handledTypes` (`ReplayBundle.java:262-271`) returns `getMethods()` overloads keyed by **FQCN**, while scenarios
  name **simple names**. Two handled types sharing a simple name are ambiguous.
- The handled set includes framework types. A scenario could feed `EventLogControlEvent` (changing the audit level
  mid-run, which silences nodes and greens `entries` criteria) or a clock event.
- `ReplayBundle.read` refuses non-records (line 284). §8.3 step 3 widens this to "no-arg constructor and setters",
  which calls user methods. That contradicts "No other method is called" and E8's "reuses both rules".

**Fix.**
- Make the copied command a two-step one the person runs (`mvn -q dependency:build-classpath -Dmdep.outputFile=…`,
  then the runner), or ship the runner as a project-level Maven profile or test. Make this an owner decision.
- State the runtime-version rule: use the project's own runtime, never a pinned one, when `--cp` already has one.
- Refuse an ambiguous simple name by name.
- Exclude `com.telamin.fluxtion.runtime.*` types from the scenario allow-list.
- Keep records-only in v1, as `ReplayBundle` does.
- Load the processor with `loadClass` (no initialisation) and check `DataFlow` before any instance is created, which
  is pinned by T15.

### F12 — major — §6.7, §13: D-I1 and D-I6 are side-stepped rather than kept

**Evidence.**
- D-I1's rationale (`completed/spec-investigation-reports.md:50-58`) is that `flag` is "the ONE place a finding is
  written", so that a record never carries "two accounts of itself that disagree". An investigation's MODEL
  CONCLUSION and YOUR NOTE rows that cite `record 42` are a second account. §13's "kept … the investigation is not a
  report" answers the letter, not the reason.
- §6.7 (line 430) says intent is something "the report spec deliberately does not store (D-I1)". D-I1 says no such
  thing.
- D-I6 makes M12's fix-brief, *including its acceptance section*, a report. M76 moves acceptance out of reports
  without recording that.

**Fix.**
- Either make a YOUR NOTE that cites one record a `flag` (the one write site) and keep only multi-record or
  record-free notes in the investigation, or record a departure from D-I1 as a new owner decision.
- Correct the D-I1 attribution.
- Record the D-I6 change in §13.

### F13 — major — §11: rule 9 gaps (writes, watches and composed commands without effects)

**Evidence.**
- Saving a scenario (§8.2, line 488) writes a file into `scenarios/`, but there is no write effect in §11. That is a
  hand-placed effect.
- "The analyser re-reads it (a fact)" and "When the run's log appears in `runs/`" (line 530) need a watcher adapter
  and facts. Neither is listed.
- `context.journey.runCommand` (line 156) and *Copy build/run command* are composed somewhere. They contain paths,
  so they must be `FactRefs`, and the composer needs one home.
- Nobody emits `RunAdded`: what is the person's act that names a run as before or after?
- `InvestigationChanged` (§10.8, line 663) is not in §11.
- None of the three nodes lists `effectQueue` or `operationGate`, which every effect-requesting node in
  `SessionProcessorBuilder` takes (lines 78-180).
- Follow appends to the open run are not named as an evaluation trigger, and §5.4 says nothing about a run under
  Follow.

**Fix.**
- Add `WriteScenarioEffect → ScenarioWritten{ok, reason}`, `WatchRunsEffect` and `RunFileObserved{runRef, digest}`,
  `ComposeRunCommandEffect` (or a pure function inside the node, with output only in `FactRefs`),
  `RunRoleAssigned{investigationId, role, runRef}` from a named person act, and `InvestigationChanged`.
- List `effectQueue` and `operationGate` as parents.
- Make Follow appends re-evaluate, with the result *not decided* until the log claims whole.
- Extend `OneDispatchModelTest` to the scenario writer and the command composer.

### F14 — minor — §2: corrections to the "What exists" table

- **E11 is backwards.** `KnownKeys` (`config/KnownKeys.java:19-23`) says "a family this list forgets is carried over
  unchanged, never lost". Listing a family makes it **owned and rewritten wholesale**. So §6.7's "added to
  `PROFILE_FAMILIES`, so every writer preserves it" has the hazard inverted. Once listed, `SettingsShare.export` and
  `ConfigStore.save` must both **write** it, or a save deletes every investigation. Add `deletedInvestigation` to
  `CONFIG_FAMILIES` and add a witness: listed but not exported loses the family.
- **E7.** It is a subsequence rule; see F3.
- **E16.** `SourceNavigation.eventHandlerOffset` finds one overload **given** a name (line 64). It cannot list them,
  so the §8.2 editor's picker needs a new enumerator. That enumerator is source-text based and can disagree with the
  runner's reflection list; say which wins.
- **E18 and §9.2 contradict the analyser's own format spec.** `docs/site/format-spec.md` §4 says the built-in reader
  declares `TOTAL` ordering: "A Fluxtion processor's order … **is** dispatch order". The step-through already paints
  "step n / m" from it. Either the tour follows the reader's declared `ordering`, or the format spec is wrong and must
  change. Do not hold two rules.
- **Paths.** Rows say `config/ProjectProfile` and `config/KnownKeys`, but the files are under `analyser/config/`, while
  `bundle/` is at the top level. State the actual paths.

### F15 — minor — §5.3, T6: the teaching set includes a criterion that cannot fail

**Evidence.** `docs/site/log-format.md:29` and `:49` say `nodeLogs` holds "one entry per node that logged this
cycle". c1 ("node d writes at most 1 audit entry", line 249) therefore holds by construction on every built-in-reader
log. T6's witness needs a hand-edited log. Teaching a newcomer a criterion that cannot fail is the wrong first lesson.
§10.3's own "criteria distinguish the change" warning would flag it.

**Fix.** Replace c1 with a criterion the diamond's own change would flip, such as an `each` or a `count` over d's
values. Keep the duplicate-entry case only as a parser robustness fixture.

### F16 — minor — §5.2: comparison semantics are unspecified

**Evidence.**
- Values in records are YAML text. `value: 1` against a logged `1.0` or `"1"` has no stated coercion. `each`'s key
  equality (`ack.orderId` = `placer.orderId`) has none either.
- Whether a `where` or `range` key reads the record's own entry or a carried (LOCF) value is unstated. D-I8 chose
  "STRICTLY … the row's own record".
- `from` and `to` do not say whether they are `eventTime` or `logTime`.
- `event` versus dimension: `aggregate`'s `dimensions` are the callback name for exported-service calls
  (format-spec §5), not the event.

**Fix.**
- Define numeric versus string comparison, with no implicit coercion: a number compares only with a value that
  parses as one, otherwise the record is a mismatch, counted.
- State "own record, no LOCF".
- Name the time field.
- Say how `ExportFunctionAuditEvent` records are selected.

### F17 — major — §14: A2, A3 and A4 are under-sized

**Evidence.** The spec's own yardstick is that one node with a presenter and UI came to about 10k lines (PR #57).

- A2 holds two nodes, a form-based criterion editor with pickers, a new profile family with the full D-I4 share
  checklist (ConfigStore round-trip, snapshot, restore and clear, `sharing-setups.md`), the `context` sections and a
  Project panel row, at about 4.5k.
- A3 holds the hold machinery in three owner nodes (F1), the checkpoint editor, two new `propose` forms (F18) and
  the M74 text retirement, at about 5k.
- A4 holds pairing, comparison, a view, a chip, a report section with its PDF, and a published-tool change, at about
  4.5k.

**Fix.**
- Split A2 into A2a (the nodes, storage, sharing and `context`, headless) and A2b (the editor and panel UI).
- Split A3 into A3a (holds and offer) and A3b (the editor and `propose`).
- Move `ReplayBundle --changed-build` and the stream-end marker (F5) to a small A4a.
- Re-estimate against PR #57's measured ratio rather than by hand.

### F18 — minor — §11 "The agent surface": `propose` cannot simply "gain forms"

**Evidence.** M74 §5.6's schema is `{summary, items[kind ∈ sourceRoot|processor|graphml|runbook|logToOpen]}`, and an
"unknown top-level key" is a whole-call refusal. It allows "at most one proposal … under review", and its
description says "Offer the person a project set-up". `OnboardingProposal` owns the review. M76 sends `criteria` and
`checkpoint` to a different node (`investigation`), and `context.proposals` is item-shaped.

**Fix.**
- Specify a discriminator (`"form": "setup"|"criteria"|"checkpoint"`, with no form meaning setup).
- Specify how many proposals of each form may be open.
- Say which node is the router, and the per-form `context.proposals` shape.
- Update the pinned description.
- Have `cites` name the log generation (M74 drafts bind by generation), so a citation from a replaced log is refused.
- Add a `VerbSchemasTest` witness that a mixed call is refused.

### F19 — minor — §10.7: format 3 does not compose with format 2, and the after run has no graph

**Evidence.** `EvidenceBundle.FORMAT_REPLAY = 2` means "has a replay" (`bundle/EvidenceBundle.java:43-48, 200`). A
"format 3 = has an investigation" leaves a bundle with both undefined. `ReplayBundle.only(members, "graph/")`
refuses more than one `graph/` member (line 214-218). The after run's changed graph therefore cannot travel, and the
recipient's `--verify` cannot decide `NODE_CANNOT_LOG` or `KEY_NEVER_SEEN` for the after run.

**Fix.**
- Use a capability list in the manifest (`"carries": ["replay", "investigation"]`) or define 3 as a superset.
- Add `after/graph/` with its own digest and teach the runner to ignore it.
- State that `--verify` evaluates the after run against the after graph.

### F20 — minor — §10.8: the M75 reuse is not quite M75's model

**Evidence.**
- M75 §3.1 gives each **block** one authority. "The criteria table EVIDENCE with APP verdicts" puts two authorities
  in one `Table`.
- D-I8's row highlight is "an `Expr` condition" over the row's own record, and a verdict is not a record value.
- M75 §3.4 already says a verdict block "is one more block type, added by a revision of this spec".

**Fix.** Take M75's offered route: an `AcceptanceTable` block, APP authority, added by an M75 revision. Alternatively,
split it into an EVIDENCE table plus an APP verdict column block. Do not claim "no new block type".

### F21 — minor — OD-76.7: the recommendation contradicts M74's archive finding

**Evidence.** OD-76.7 recommends that the team "pin it with a `sha256`" (line 816). M74 r13 (spec-claude-code-provider
lines 106-113) found that GitHub's generated `/archive/<commit>.zip` bytes may change, "so for a generated archive the
pinned commit is the identity, and a digest that stops matching is refused". A sha256 on a generated archive would
make S8 break on GitHub's schedule.

**Fix.** Recommend the 40-hex commit pin. Add a digest only for a published release asset, which is itself the
deferred M74 decision.

### F22 — minor — §7, §8.2, D-I5: a new write surface in the person's tree is not named as one

**Evidence.** §7 (line 447) says "nothing is written into the person's sources". §8.2 then saves scenarios into the
project's `scenarios/` folder. D-I5's principle is one argued file-exchange boundary.

**Fix.**
- Name scenario Save as a new, person-only write surface with its guards: inside the project after resolving links,
  never overwrite without confirmation, an effect (F13), and no model path.
- Correct §7.
- Add a test for a symlinked `scenarios/` that escapes the project.

### F23 — minor — vocabulary collision: "journey"

**Evidence.** "Journey" already means a walk with a handoff: `spec-onboard-assistant-journeys.md`, `JOURNEY`
drafts in `AskCatalogue.composeJourney` (line 471), the demo journey, `JourneyCatalogueTest`, and "a configured
journey" start-page entry (M74 §5.8). M76 adds `JourneyState`, `context.journey`, `JourneyStarted` and the "journey
strip".

**Fix.** Rename M76's concept (for example *authoring path* or `authoringJourney` throughout, with
`context.authoring`) so that a `context` reader and a test name cannot confuse the two.

### F24 — minor — §15: missing tests and witnesses

The following have no row in §15:
- the ordering witness for F1;
- the run-identity collision (F2);
- an extra input event and service calls under pairing (F3);
- a forged profile (F4);
- a real `ReplayBundle` output with the marker (F5);
- a misspelled `none` event and `NOTHING_MATCHED` (F7);
- the per-kind monotonicity of incomplete-log verdicts (F8);
- the shared scenario-grammar fixture between `RunScenario`'s copy and the analyser's parser (§8.3 step 1 promises
  it);
- a framework event refused by the runner (F11);
- `KnownKeys` owned-but-not-written (F14);
- a symlink escape from `scenarios/` (F22);
- the proposal form discriminator (F18).

T10 has no wrong-result witness ("—"). Use a draft composed from a fixture with an unsanitised identifier, which must
become *"investigation 2 of this project"*.

T13 scans "the docs pages" for "done", which matches ordinary English across the site. Scope the scan to the
acceptance resource bundle and the acceptance docs page, and drop "done" or match it only as a verdict label.

### F25 — nit

- §10.4's example mixes ids: c3 there is the diamond's c1, inside a "Duplicate acks" investigation.
- §10.5 bans "verified fix" while the bundle surface says "verified" (`--verify`, the S3 verdict). State that the
  bundle-integrity word is exempt.
- §6.3 (1) covers profile cards only. A posture change through Settings ▸ Assistant switches bypasses the offer, so
  either include it or say why not.
- The C1 draft names `revision {rev}`, but `Draft` binds only a log identity and generation. Say what `context` returns
  when the investigation has been revised between arming Start and Send.
- §10.2's "paired by recorded inputs" needs the before run captured into a bundle first, because `ReplayBundle` takes
  `--bundle`. Say so, or add `--replay <file>`.

## Owner decisions to add or change

- **New:** where authorship and confirmation are recorded, and how a profile-borne investigation loads (F4).
- **New:** how the run classpath is obtained without the analyser running Maven (F11).
- **New:** whether a departure from D-I1 is accepted for investigation notes, or notes that cite one record become
  flags (F12).
- **New:** the vacuous-match policy (F7).
- **Change OD-76.7:** use the commit pin, not a sha256 (F21), and name the Fluxtion key as a prerequisite of the change
  step (F10).
- **OD-76.14** as worded ("Yes, by the person") is fine, but "the same session that wrote the code" cannot be told
  from facts today. Say that the confirmation fact records the posture and session id in force, so that a later
  reviewer can see it.

## What checked out

- E1, E4 and E5: `AssistantProfileChoice`, `OnboardingProposal`, `ProposeShape`, `FactRefs` and `FactFieldsTest`
  exist and say what the spec says.
- E6: `ReplayCompare` excepts exactly `endTime` and `thread`.
- E8: `ReplayBundle`'s allow-list is built from `handleEvent` by reflection, with a data-driven clock.
- E10's report types: `ReportSpec.Kind` has no CRITERIA yet.
- E11's `PROJECT_SCOPED` (eleven categories) and `BundleProfile.CATEGORIES`.
- E12's `Expr` and `windowFunctions()`.
- E13's aggregate filter fields.
- E15's S1–S6 and the identifier pattern `[A-Za-z0-9_.$:-]{1,100}` (`assistant/AskPointers.java:23`).
- E18's reading of `docs/claude.txt`, but see F14 on the format spec.
- §13's standing decisions (D-AI4, no server control, the analyser runs no processor, pointer-only drafts) are
  respected by the design as written.
