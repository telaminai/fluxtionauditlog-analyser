# Fluxtion Audit Log Analyser — Work Tracker

Companion to **[spec.md](spec.md)**. Status keys: ☐ todo · ◧ in‑progress · ☑ done · ⊘ dropped.

Legend for each item: **[id] status — title** · _acceptance_.

---

## Spring authoring edit loop — session intake 2026-09-25

**PROPOSED; no fixes accepted or shipped by this intake.** A guided website-template session
reported twenty-four friction points, chiefly on editing an existing graph. Read the
[review and evidence limits](../handoff/review_spring_authoring_feedback_2026_09_25.md) and
[fix specification](spec-spring-authoring-edit-loop.md). This is not another client battery
or a G14 pass. The running project remains untouched.

- ☐ **Java snapshot freshness** — spec §C, feedback 3/20: ordinary same-FQN navigation must
  revalidate both the Source tab and Topology's embedded pane, replacing the service model
  from the same snapshot off the EDT, with stale/deleted/cancelled reads disclosed. Reuse the
  source-spotlight resolver; local Maven source lookup already exists.
- ☐ **Recovery profile identity** — spec §E, feedback 5: first reproduce capture under P at X,
  delete/recreate a profile at X, then activate it. Current real-path keys cannot distinguish
  replacement profiles. Bind identity, disclose capturedAt/capturing identity, withhold or
  qualify mismatch; retain pending-I/O and cross-project controls. Participant history remains
  unverified; this is proposed regression work, not a reproduced or closed finding.
- ☐ **Template installer authoring executables** — spec §G, feedback 8: add setup/validate/generate
  to TemplateArchive's fixed executable list, retaining arbitrary-mode refusal. Removing
  generate.sh must fail a real install assertion. Direct-browser ZIP modes are a separate
  producer check; D6 approves documenting Windows absence, with implementation deferred.
- ☐ **Design-first project and admin-console offer** — [edit-loop §I1–I2](spec-spring-authoring-edit-loop.md#i-design-first-market-data-tour-and-existing-vendor-jars):
  add the emitted Spring design directory to template roots. Validate all template profile
  source roots inside the installed project in TemplateArchive; preserve explicitly granted
  external roots in user-authored profiles. Verify topology/source with no log, and under
  approved D5 offer a verified reachable process with disclosed inferred
  project association; withhold ambiguous or stale offers. No automatic control or token disclosure.
- ☐ **Project input grants, approved policy; implementation open** — spec §E, feedback 11:
  retain M68.5's canonical root diagnostics work. D3 approves offering explicit role-scoped
  grants at project opening, showing what becomes readable; opening alone authorises neither
  the project root nor target directory.
- ☐ **Context section projection** — spec §H, feedback 17: opt-in projection, compatible full
  default, same-state equality, and basis/qualification carried with every selected verdict.
- ☐ **Upstream handoff** — new asks and existing ownership are routed through
  [upstream asks](../proposals/upstream-asks.md#spring-authoring-edit-loop--2026-09-25-intake).
  First: UP-FLX-21's compile pipeline and the mapper's fabricated values. Then explicit rename,
  preflight, matching contract and direct-browser archive/documentation checks. Installer
  permissions and recovery identity belong here, not upstream. No producer implementation
  belongs in the analyser. D1–D8 were approved by the owner on 2026-09-26 and are recorded in
  the spec. Its eight startable slices retain their acceptance, and published/provider checks
  still cannot be closed by branch fixtures. Policy approval advances no implementation mark.

The existing **MA-2/MA-5/OD-5**, **Staged feedback — next chart capabilities** and
**Staged feedback — authoring route and hosted audit boundary** entries retain their statuses.
The spec adds acceptance to them; it does not create second completion claims.

## Project / Sources / Audit log menus — remaining follow-ups

Implementation and guard follow-ups shipped in **1.20.0** (PRs #15 and #17);
[verification and historical counts](completed/tracker.md)
are in the completed tracker. The release is not a claim that these optional issues are resolved:

- ☐ **Display stability:** the second-menu spotlight flake was not reproduced; the trial focus retry
  did not demonstrate improvement and was removed. Keep the zero-skip display gate.
- ☐ **Optional menu guard refinements:** computed Java menu strings and the prose-boundary heuristic
  remain limited; see the [guard report](../handoff/report_menu_guard_followups_2026_09_24.md).
- ☐ **D1 / D2 owner decisions:** CSV placement under Audit log and whether to add a legacy File
  spotlight alias. Current shipped behaviour and the saved-step compatibility consequence are
  documented; this release does not choose a different policy.
- ☐ **D3 identity:** GitHub web merges bypass the local email pin. In addition to the earlier report,
  `7254c29d` and `2a15f0ba` have non-personal metadata. Local email is personal at this release;
  history is not rewritten. The owner still needs to settle the web-merge identity setting.
- ☐ **CI policy:** whether the new full mutation gate is required by branch protection is an owner
  setting; the job already runs on every PR and push to main.

## Mongoose audit format — [proposal](../proposals/mongoose-audit-format/README.md), revision 9 (2026-09-21)

Remove the export step between a Mongoose run and the analyser. Owner decisions taken 2026-09-21; the
proposal carries the seven decisions, the repository order and the acceptance. Release 1 is text and
touches no `fluxtion` code; release 2 is one runtime release carrying binary, the renderer and two live
defects. **The analyser half of release 1 shipped in 1.18.0** (AF-1, AF-2, AF-2a, AF-3, and the seven
review rounds behind them, now in [the completed tracker](completed/tracker.md)). Everything below is
still to do.

- **[AF-8] ☐ — OWNER DECISION NEEDED: does a record scalar support a trailing `#` comment?**
  _Found 2026-09-21 while checking the vendor-integration pages for the same defect class. Not from the
  stream-end branch; it is in **released, published** `format-spec.md` §2._ **§2's normative example is
  annotated with trailing `#` comments on almost every line, and the reference parser does not treat `#`
  as a comment on a value line.** Measured on a file written in exactly the shape that example teaches:

  ```
  kind       = OK
  logTime    = null                  <- every record falls off the timeline
  event      = [ExportFunctionAuditEvent      # event class or trigger type]
  groupingId = [null                     # optional correlation id]
  ```

  Loaded as a file, `minLogTime` and `maxLogTime` are both **null** and every record reports `OK`. The
  time filter, the graph axis, coverage windows and the time-order check all read that one field, so the
  failure is total and completely silent, and the thing teaching the shape is the specification's own
  example. §2's field table never says whether a trailing comment is allowed.

  **Scope, measured rather than assumed.** No shipped artefact is affected: not `sample.yml`, not the
  demo asset, not any conformance fixture. The exposure is an adapter author copying the example, which
  is the audience §2 exists for.

  **An inconsistency I introduced.** §1a's recognition rule (stream-end branch) says an unquoted `#`
  begins a comment, because the marker recogniser strips them. So the format now states one rule for
  `streamEnd` values and has different, undeclared behaviour for every other scalar in the same record.
  I made an existing inconsistency normative without noticing it was one.

  **Why this is an owner decision and not a fix.** The two answers are different products:
  (a) comments ARE supported — `RecordParser` changes, and so does how every released reader reads every
  value, for every producer that has ever written a `#` inside one; or
  (b) comments are NOT supported — §2's example is de-annotated, the field table says so, and §1a's
  comment rule for markers is removed as the odd one out.
  **Not started, and nothing done on either path.** Whichever is chosen, the guard is the same and it
  already exists in miniature: extend `PublishedSpecExamplesTest` to parse **every** published
  `eventLogRecord` example, not only the marker ones. That test is what would have caught this, scoped
  one notch too narrowly.
- **[AF-9] ☐ — no real Mongoose export is classified as an exported call (§5).** _Found by the round-four
  reviewer while checking `c21`'s stated limit; **pre-existing, and true of the released reader too.**_
  A real export writes `eventToString` for an `ExportFunctionAuditEvent` as TWO lines — `@Override` then
  the Java signature — with the continuation at column 0. `RecordParser` reads a scalar to end of line,
  so it keeps `@Override` and drops the signature. §5 classifies a record as an exported call by
  recognising a method signature in that field, so **all 14 such records in `c21` have `callback` and
  `declaringType` null**, and their dimension falls back to the event class rather than the method name.
  Confirmed against released 1.16.0 as well, so it is not from this branch. The consequence is that the
  callback dimension, which §5 exists to provide, is empty for every real Mongoose export. Pinned by
  `c21` so a fix is visible; not fixed here because it changes how the parser folds continuation lines,
  and that is the same question AF-8 asks about what a scalar line may contain. **AF-8 is filed on
  `fix/follow-stale-partial-record`; these two should be decided together.**

- **[AF-10] ☐ — a reader plugin can apply §1a rule 1 but cannot report that it did.** _Round six S-4._
  A plugin yields items one at a time, so applying the termination rule is its job: `frameForPlugin`
  does it, and the reference reader and the conformance pass-through reader both use it. What the SPI
  has no way to express is **why** an item was withheld, so the plugin path reports `unknown` where the
  built-in reader reports `unterminated_marker`. The safety property holds exactly — neither path ever
  claims completeness the other does not, and `bothPathsAgree` asserts that — but the plugin path is
  less precise, and a person reading a plugin-sourced log is not told their producer must terminate its
  marker. Closing it means a way for a reader to hand back a container fact, which is a change to the
  SPI and belongs in its own slice.
  **Round seven accepted this disposition and added a condition: the SPI needs that route before any
  third-party TEXT plugin ships.** Until then every such plugin reports `unknown` for a file the built-in
  reader can explain, and its users are never told their producer must terminate its marker. Text files
  do not reach the plugin path in production today, which is the only reason this can wait.

- **[AF-3a] ⊘ — WITHDRAWN: main fixed it through TA-6, and my fix was wrong.** _Round six L-2: this branch
  still listed it open._ `HeapLogStore.appendFrom` on main refuses to append when the snapshot includes an
  EOF record and reloads as a live read, so an indexed slot is never rewritten and M65 D-F0 holds by
  construction. My attempt rewrote the slot in place: a walker holding a `readView()` across the append
  threw `StringIndexOutOfBoundsException`, a half-written marker left a phantom row for ever, and the
  index was observably not monotonic. The attempt is reverted on `fix/follow-stale-partial-record`, which
  must not be merged; its own entry records the detail.
- **[MA-0] ☐ — the analyser reports an empty log as a finding** · _THIS repository; the smallest item and
  the only one here._ Every empty shape returns from `ProducerDiagnostics` before any check, so an empty
  log raises nothing, marked or unmarked. A **finding**, never a seventh state; keyed on zero records only
  (the quiet-level case is already caught by `ONLY_CONTROL_EVENTS`). Acceptance and the six-case table are
  in the spec, adopted from review's run on the published 1.19.0 jar.
- **[MA-1] ☐ — a processor that cannot audit says so** · _rescoped twice._ The silent population is **any
  processor with no `EventLogManager`** — including **AOT processors built without audit**, the
  low-latency profile — not just `customHandler`. Detect by capability (`getAuditorById("eventLogger")`);
  refuse at `start`, not at boot, so a server with one unaudited processor in `autoStart` still boots.
  The level endpoint lives in `svc-admin-web`, not core, and must return 409/422.
- **[MA-5] ☐ — capture must fan out, and restore on stop** · _live silent-discard path in core._
  `ChronicleAuditCaptureService`'s comment promises to compose in front of the existing listener and
  restore it; the code sets `previousListener = null` and, on stop, installs a no-op. `DataFlow` has no
  getter, so the mechanism is to pass Mongoose's own listener into `attach`/`start`.
  **Recurring intake 2026-09-25:** feedback 13 reports silent stdout on the bundle's older pins;
  [edit-loop §H](spec-spring-authoring-edit-loop.md#h-existing-auditchart-work-and-a-smaller-context-response)
  requires acceptance against the actually released and consumed producer. This intake does not
  re-test or close the separate in-progress MA implementation.
- **[MA-6] ☐ — a document without `eventLogRecord:` is named** · _split out at round 4 to resolve a
  contradiction: D-MA0b keeps MA-0 at zero records only, so this check lives here._ Reader half in this
  repo; **writer half in MA-2 — the writer WRITES the record, COUNTS it, and WITHHOLDS the marker**, so
  the file reads `unknown` plus the reader finding. (The earlier "refuses to count or mark" had three
  readings and two broke an invariant: write-but-don't-count reads `more_than_declared`, and
  don't-write-don't-count hides a produced record under `complete`.) **This unblocks MA-2 without waiting
  on AFMT-3.**
- **[MA-7] ☐ — framing injection: a payload forges a marker** · **GATES MA-2**, and the most serious
  finding in this spec. An event `toString()` carrying a line that **trims to** `---` — space, tab or CR,
  matching the framers' own predicate, **not** an exact match — plus marker lines breaks the framing
  BEFORE §1a recognition runs, so the allow-list cannot defend it. Reproduced twice
  independently: 3 real records read as 4 or 5, a forged marker recognised, and `missing_records`
  reported on a file that lost nothing. **Live on the SHIPPED 1.0.44 exporter** — `YamlContainerWriter`
  writes record text as-is. **TWO fixes, both decided:** the writer **ESCAPES** (never refuses — refusing
  changes the count or hides a record, so only escaping satisfies V1), **and a producer-side escape is
  MANDATORY for anything through the shipped 1.0.44 exporter**, filed as
  [mongoose-plugins#39](https://github.com/telaminai/mongoose-plugins/issues/39) since the writer-side
  fix cannot reach bytes already shipped. Payloads come from event `toString()`, routinely
  user-controlled. Not previously recorded anywhere.
- **[MA-8] ☐ — coverage qualifies a node whose level was changed per node** · _THIS repository; an MA-0
  sibling._ A node at `WARN` runs but reads as never logged, and the control record naming its
  `sourceId` and level is in the log. Measured: `complete`, 6 of 6, no findings, zero entries for a node
  that ran three times. Coverage must say why it is silent instead of listing it as uncovered.
- **[AFMT-3] — a live runtime defect, no longer a gate on MA-2** (MA-6 is the defence). Per-node `NONE`
  corrupts the next record, reproduced on today's bundle (`riskCheck`/`rootNode`); a marked file holding
  one reads `complete` with no finding. **Cause NOT established.** **The tracker repro is stale** — it
  targets `volumeTotal`, absent from today's bundle, so re-running it wrongly looks clean.
- **[MA-2] ☐ — the text writer and the marker** · text half needs **MA-5, MA-6 and MA-7**; **Chronicle
  half** waits on **OD-5**. **The MA-5 dependency was found by running:** capture REPLACES the configured
  listener, and OD-4 makes the text writer that listener, so with the bundle's shipped capture on the
  writer got 4 startup records, none of the 8 business events, and the file read `complete, 4 of 4`. Lifecycle ANSWERED from a booted spike: marker after `server.stop()` returns, a new file
  per start, no roll, counts records RECEIVED, flush a user-configurable property defaulting to
  per-record in the developer bundle. Measured: writing the marker *before* the processors stopped lost
  39 and 20 records while the file still read `complete`. `backend` is
  read by nothing today (`getBackend()` has no caller), so `backend: text` is silently ignored — the
  switch OD-4 depends on does nothing. The text writer owns its own separators; the marker's lifecycle
  (when, which thread, restart, roll, retention) is five decisions; `svc-admin-web` must become
  backend-aware or refuse by name.
- **[MA-4] ☐ — the developer journey** · **GATED on MA-2's text half**. **OD-4 DECIDED: the developer
  text backend is the server's CONFIGURED LISTENER** (`bootServer(config, listener)`), not the capture
  service — no `backend` switch, no `svc-admin-web` change. **It DEPENDS ON MA-5**: run with the
  bundle's shipped capture on, the text writer got 4 startup records and none of the 8 business events,
  and the file read `complete, 4 of 4` — a V3/V4 violation in MA-2's own configuration, found by running
  the two together for the first time. Cost stated: it
  is invisible to `audit.start`/`stop`, `liveSinks` and the admin file list, which is acceptable for a
  journey whose point is opening the file. Since OD-4 makes text the developer default
  and text *is* MA-2. Mongoose audit-logs by default; the gap is persistence in a form the analyser can
  open.
- **[MA-3] ☐ → moved to [mongoose-plugins#38](https://github.com/telaminai/mongoose-plugins/issues/38).**
- **[OD-5] ☐ — OWNER DECISION: does the Chronicle backend get a marker?** Four options in the spec;
  **(c) an export-time marker is REJECTED BY NAME** — it always reads `complete`, the manufactured marker
  the skill forbids. Blocks only MA-2's **Chronicle half**; the text writer the developer journey depends on can proceed. An export-time marker is
  **rejected by name** — it would always read `complete`, the manufactured marker the skill forbids.
  Running the text writer as a second destination needs no Chronicle change once MA-5 lands. OD-2 (refuse) and OD-4 (text
  for developers) are taken; **OD-1 and OD-3 are moot**.
- **[MA-R] ◧ — spec REWRITTEN at round 3 and out for re-review**, three reviews answered, all committed in
  `docs/handoff/`. Implementation is mine.
- **[AF-4] ☐ — mongoose writes the text file** · _not this repository._ **SUPERSEDED as the place this
  work is specified: see [MA-0…MA-5] above and
  [`spec-mongoose-audit-production.md`](spec-mongoose-audit-production.md).** Item 2 shipped in
  `mongoose-plugins` 1.0.44; item 1 is now MA-2, and the spec adds the prerequisite AF-4 did not know
  about. Kept here for its history.
  Two changes, and the second is
  in a different class from the first, which round six had to point out to me.
  1. **The marker writer.** `asCharSequence()` per record, then the marker. Config validation refuses
     unknown values by name. Per-node entry parity, not a record count.
  2. **THE EXPORTER MUST TERMINATE ITS LAST DOCUMENT.** `WebAdminService.handleAuditExport` writes
     `if (!first) w.write("\n---\n"); w.write(yaml);` and **nothing after the last document**. The
     separator belongs to the export formatter, not to whatever writes the marker, so a marker writer
     cannot satisfy §1a rule 1 on its own: under today's exporter its marker is always the last,
     unterminated document, and the analyser reports `unterminated_marker` and ignores the claim.
     **The fix is one line: write `\n---\n` after the last document as well as between them.** A
     trailing separator has always been legal under §1 and released 1.17.0 reads it. Filed upstream as
     **UP-MON-01**.
  **The old acceptance was impossible and is withdrawn.** It said "byte-identical to a known-good export
  modulo the marker". §1a rule 1 makes that unachievable, because the known-good export has no trailing
  separator. The acceptance is now: byte-identical modulo the marker AND the final separator.
- **[AF-5] ☑ — RELEASED 2026-09-23 in `mongoose-plugins` 1.0.44.** · _not this repository; owner decision
  3. Create the tailer and call `toEnd()` on the reading thread. Repairs live tailing for every existing
  Chronicle deployment and waits for nothing here. A second defect behind it discards unflushed reads, so
  the test needs a sub-50 ms burst._
  **Implemented 2026-09-21** in `telaminai/mongoose-plugins`, branch `fix/audit-tail-thread-safety`,
  commit `419f8d4`, pushed and **not yet reviewed or merged**. Both defects were in
  `WebAdminService.java` as diagnosed: the tailer was created with `toEnd()` on the Jetty connect thread
  and read on a scheduled executor, so every tick threw `ThreadingIllegalStateException` and the catch
  logged it at debug — which is why the socket completed a real upgrade, reported healthy, and delivered
  nothing. Behind it, each tick read into a LOCAL list and discarded it unless the flush condition was
  met, losing records the tailer had already advanced past. Tailer now created lazily on first tick; the
  batch lives in the per-socket state and clears only after a successful send. `AuditTailThreadingTest`,
  3 tests; the first reproduces Chronicle's cross-thread refusal against a bare queue, so the lazy
  creation is load-bearing rather than stylistic. Module suite 100 pass, 0 fail.
  **That gate is MET and the work is released.** Merged to `mongoose-plugins` `main` as `beadf70` after
  five independent review rounds, released as **1.0.44** (tag `v1.0.44`, 59 artifacts published,
  `svc-admin-web-1.0.44.jar`). The acceptance needed a client and none shipped, so the test brought its
  own: the JDK's `HttpClient` speaks WebSocket, so it cost a test class rather than a dependency.
  Delivered equals exported, by record id, against a booted `MongooseServer` with its own audit sink.

  **Two further defects were found after the entry above was written, both by driving the running thing
  rather than reasoning about it.** The lazy tailer described above put `toEnd()` up to one poll interval
  AFTER the client connected; fixing that moved it to connect, which review then measured and rejected —
  `onOpen` fires at the HTTP 101, so the window spans the whole connect handler. Records written straight
  after `onOpen` were lost in **19 of 20 rounds**, the window a **median 8.0 ms, max 17.1 ms**. It is
  closed by positioning the tail in `wsBeforeUpgrade`, before the 101 is sent: **0 of 20**, first record
  delivered 0µs after `onOpen`, at rest and under load average 21–27.

  **Still owed, filed so it outlives the branch:** the `MAX_PENDING` ceiling has no live-server test —
  [mongoose-plugins#38](https://github.com/telaminai/mongoose-plugins/issues/38). A JDK client that stops
  calling `request()` applies flow control in its listener, not on the wire, so the server's sends keep
  succeeding; reaching the ceiling live needs a client that stops reading at TCP level.
- **[AF-6] ☐ — the coupled analyser documents** · _the Mongoose skill states Mongoose does not write
  analyser-readable YAML directly, which this makes false; its pin and the playground re-vendor follow.
  Also `spec-tool-agreement.md` D12/D13, `spec-onboarding-example.md:62-72`, `spec-guided-start.md:66`,
  `spec-follow-refreshes-graphs.md:37`, `docs/experience/current/skills/read-audit-log`,
  `tutorial-playground.md` §3, and `TemplateArchive` installing `export-audit`._ Last in release 1: it
  describes shipped behaviour.
  **Still blocked after 1.18.0 AND after `mongoose-plugins` 1.0.44, deliberately.** The analyser half is
  released and the exporter now terminates its last document, but the Mongoose skill's statement that
  Mongoose does not write analyser-readable text directly is still TRUE — nothing writes a stream-end
  marker, so exports still read `unknown`. Editing these documents now would make them wrong rather than
  right. Blocked on AF-4 shipping.
  **Note for whoever is aligning the playground:** the re-vendor named in this item is part of AF-6 and
  should NOT be done ahead of AF-4, for the same reason. Aligning the playground's *versions* is
  independent and safe; changing what it *says about Mongoose writing text* is not.
- **[AF-7] ☐ — release 2, blocked** · _one `fluxtion-runtime` release: pluggable record selection (owner
  decision 6), the record-swap staleness, the per-node `NONE` corruption (owner decision 2, confirmed to
  ride this release), and the renderer move. **Gate:** the corruption's cause is undiagnosed, and diagnosis
  is a prerequisite for the release rather than work inside it — a release carrying an undiagnosed
  corruption cannot say whether it fixed it._

## Beta — [proposal](../proposals/beta-testing/README.md), sixth draft (2026-09-21)

Rewritten after the public release: the battery is retired, acquisition is measured, the journal is replaced by
observed tool events, and the fifth draft's slices A–D become a short blocker list (proposal §7).

- ➜ **BETA-7 ☑ archived 2026-09-21** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-21* (the A3 two-half
  report instrument and the skill write-up section that teaches its shape).
- **[BETA-6] ◧ — sixth draft written; one review round before any approach** · _owner decisions in §15._
- **[BETA-B1] ◧ — A2 needs a generation key, and the customer key journey is untested** · _A2 is a graph change;
  the bundle README and the SG-1 report both put regeneration behind a key, and both release reports say customer
  credential acquisition is not established. Resolve before A2: test the key journey, or provision revocable keys,
  or redesign A2._ **2026-09-21, virgin-session runs (sealed predictions, verified independently, n=1 each):** with no
  key, the subject stops honestly with no edits (r1); whether it also says where to get one varied between an
  unchanged control and a one-line treatment, so that is run-to-run variance, not a fix. With a key present, the
  full journey passed: one new node (r2b, 71 s) and the beta's A2 wording, two related nodes (A2, 126 s, 11/11
  predictions). **Still open:** customer key *acquisition*. Owner, 2026-09-21: free registration is enough for now,
  and simple key registration in the analyser is a separate future item. Evidence:
  `.local-evidence/coldstart-v2-2026-09-20/{key-journey-*,a2-*}` (git-ignored).
- **[BETA-B2] ☑ — new-node stubs must audit before the beta** · _feedback 6 / issue 3; otherwise A2 measures the
  product, not the tester (§11 carve-out)._ **Owned by the starter (compiler + playground), not the analyser — done as
  part of the [Spring-side work block](#spring-side-work-block--assembled-2026-09-23-to-be-done-as-one-piece-of-work).** Implementation intake 2026-09-23:
  **Policy settled; reviewed and published in starter 1.0.74 / deployed playground 5d6a38a.** Owner chose INFO callback
  facts: event name/current filter, trigger fired and lifecycle phase, including unfinished nodes;
  retain existing superclasses using runtime logger injection. No inferred business-state dump.
  Both emitters are covered by real audit-manager tests. Historical BETA-B2 stage gate: 84 builder + 51 starter;
  playground: 551 tests; no failures or skips. Five Java and three browser mutations reject missing
  facts/disabled protection, with green baselines and restored green. Canonical policy is compiler
  Spec 3 B4; original playground commit `ec2b1f8`. Independent review and publication now hold; authors
  still supply the values behind their decisions. `CallbackAuditTest` covers fresh and added-member
  execution facts; browser `callback-audit.test.ts` covers the fresh generator. G14 is a separate
  public acceptance gate, and only that run may consume the owner-authorised key.
- **[BETA-B3] ☐ — template decision, with a dry run of A1–A2 by someone other than the author** · _recommendation:
  standalone Spring, the only template with a verified local authoring route._ **The original recommendation rested on SG-2
  being open; hosted authoring files now ship too.** The dry run itself stays with the beta.
- **[BETA-B4] ☐ — jar A (convention mismatch), jar B and the spec-derived check** · _implemented and independently source-reviewed; publication/integration remain open._ **Build these in
  the same collection as M67.1's catalogue jars — same artefact type, same repository, one decision (Spring-side block ▸ 4).**
  G18 access resolved: [source branch](https://github.com/telaminai/fluxtion-vendor-jars/tree/feat/spring-side-work-block) at `3a89391`; clean clone and placeholder-source controls pass. G's re-review `e559c424` now closes source/oracle review; binaries are not published.
- **[BETA-8] ◧ — the journey on Haiku (2026-09-21)** · _A2 and A3 hold on a model two generations smaller._
  A2 ×3: full success 3/3 (92–123 s; two runs edited shipped data, one undisclosed). A3 without routing: fix
  3/3, but 0/6 runs (with or without the `Skill` tool) loaded `point-at-the-fault`, 0/3 kept deliberate
  before-fix evidence, and needless regenerations spent the paid generator. With the bundle's routing line
  plus the fixer block in the skill (v2, re-pinned and re-vendored): 3/3 loaded the skill, kept evidence,
  fixed byte-identically, no regeneration; 2/3 still made an unrequested watcher change, which the next
  wording should target. A shorter v3 that dropped the "read an edge-triggered flag" rule regressed to 1/3
  correct and was not released. **Instrument gap:** the two-half reader question is scoped to the report's
  stated fault, so a confident report of the wrong fault passed it — A3 scoring must also check the fix
  removes the planted defect. n=3 per arm. Evidence: `.local-evidence/coldstart-v2-2026-09-20/haiku-series-2026-09-21-SUMMARY.md`.
---

## Product discovery — the notebook for event-driven applications (2026-09-22)

From a single session review (22 Sept): one session built a library-free WebSocket market-data feed, deployed it across
three repositories, drove it over the admin REST and proved a subscribe/unsubscribe toggle from a pinned plot. The framing
that came out of it is the clearest category anchor this product has found — **cells, kernel, canvas**. The builder DAG and
node Java are the cells, Turing-complete authoring, compiled. The deterministic processor running in the server is the
kernel. The analyser is the rendered canvas, drivable by a person or an assistant. The tagline says why it matters; this
says what it is, to an audience that has never heard of deterministic dispatch.

**The comparison is winnable on the incumbent's own ground.** A Python kernel's state is hidden and order-dependent, and
reproducibility is the criticism notebooks attract most. Here the kernel is deterministic and the canvas renders the
kernel's own record, so the same events through the same classes reproduce exactly. The session's sharper line is worth
keeping verbatim: telemetry is evidence **about** a system; the audit log is evidence **from** it.

**The risk is in the word.** Notebook reads as exploratory, disposable and not-for-production, and the claim here is the
opposite — what you explore with IS the production artefact. Lead with what a notebook cannot do, and never let the
analyser be described as a scratchpad.

**Three gaps stand between the framing and the fact.** None needs a new engine; each assembles parts that exist.

- [ND-1] ☐ **Iteration — the cycle is a redeploy, not a cell run.** A notebook's defining move is change, run, see, change
  again, without restarting the kernel. The owner's development-mode proposal (2026-09-21) is exactly that: the runbook
  starts the process under JVMTI, the assistant edits a body, redefines the class, fires data through, and the before and
  after logs are compared on the canvas. **Judge it as a category move, not a developer convenience** — without it the
  notebook claim is aspiration. The boundary is the ownership rule that already exists, bodies hot-swap and declarations
  regenerate, which happens to be exactly what the JVM permits. Development mode only: a native image is closed-world.
  One log per code version, rather than one log spanning a swap, keeps the comparison honest without needing the log
  writer's cooperation, and gives the clean re-initialised second run for free. **No analyser change — runbook and skill
  only:** `source` already re-reads on every call (M66), and `open {follow: true}` shipped in 1.17.0.
- [ND-2] ☐ **The document — every component exists, and there is no wrapper.** A notebook is one file you hand someone.
  Today the project profile carries pointers and policy, saved chart definitions carry the views, investigation reports
  carry the narrative and its typed sections, and the audit log carries the run — yet nobody can hand a colleague one
  artefact that opens to the same view, the same evidence and the same story. Scope before building: what travels by
  pointer and what by value (M38.1's rule is pointers, never contents), and what a recipient sees when an input has moved
  or is missing.
- [ND-3] ☐ **Re-execute the document — "run all cells" is nearly assembled.** M33.7 stores a report's call so it re-issues
  exactly; saved chart definitions are declared before input (1.16.0); replay exists as a skill. Pointing those three at a
  new log gives *re-run this document against this run*, which is what makes the artefact live rather than a screenshot.
  M66's relationship states say what to show when the new run does not match what the document was written against.

**Sequencing.** ND-1 first, because it is the cheapest and it is what earns the claim. ND-2 and ND-3 are one design
conversation, not two. None of this displaces the beta blockers or the evidence-correctness work — for this framing above
all others, an instrument that lies is worse than a slow loop.

**Two analyser defects found in the same session, filed here because that is where they were seen.**

- [WS-1] ☐ **Follow shows stale content after the log file is replaced.** A restart rewrites the audit log as a new inode
  and live-tail silently held the old content until the operator noticed through `context`. Same class as DX-02: the
  instrument reporting confidently and wrongly, rather than failing. The detection already exists — `context` computes the
  file identity and flags changed-on-disk inputs — so only the action is missing: reopen, or raise a banner that says the
  file underneath was replaced. Prioritise with DX-02/DX-03.
- [WS-2] ☐ **A non-canonical profile resolves the project root as the settings directory.** The root came out as the
  `.analyser/` directory rather than the repository, so every runbook and skill pointer resolved `exists: false`. That
  breaks the mechanism the whole authoring story depends on. Resolve the root from the settings file's parent's parent, or
  record it explicitly in the settings.

_Status of the source: self-authored by the session that did the work, confirming rather than falsifying, and n=1 — its own
neutrality section says exactly that. A strong internal data point, not outside evidence; the falsification attempts it
proposes (unsubscribe mid-burst, reconnect mid-unsubscribe) would be worth more than another successful run._
**Before any of that review is published or quoted, the kernel line must lose the strategy class it names — the prefix is
one of rule 1's four sweep terms.**

## Spring-side work block — assembled 2026-09-23, to be done as ONE piece of work

**Release execution — 1.0.74:** owner approved publication. Compiler/starter/BOM 1.0.74
is published; the owner reports matching cloud deployment. Playground `5d6a38a` is
on main and deployed, with public artifact/comment parity and 563 tests/zero skips.
Public provisioning CI 35929392911 passes both Spring templates and the keyless
bundle (build/run, five independently checked rows, export, stop). Its first standalone
attempt served 1.0.73 and refused; the separately retained retry serves 1.0.74 and passes.
**G14 is still open:** the first sandbox-invalid attempt is preserved. The fresh retry
finished after 869 seconds with no substantive coaching. It generated with client/server
1.0.74 and captured two scenario runs, but could not read the analyser endpoint; no chart
or evidence-linked report was produced. It also edited its ownership baseline to resolve
a conflict. Scenario correctness and that recovery require independent assessment; this
is not an acceptance pass. [Release checks, failures and limits](../handoff/release_spring_side_1_0_74_2026_09_23.md).

**2026-09-24 connection recovery:** supervised released analyser stayed reachable
for the 181-second client (91 healthy checks); all 68 project files unchanged.
Independent getter checks passed all seven states twice. Chart/report were created;
exports required an operator configuration correction/replay. G14 stays open: the
original log lacks separators, coverage falsely calls a declared sink absent, and
the exported chart/topology need correctness follow-up. See the
[recovery evidence and disputed client claims](../handoff/evidence/spring-g14-recovery-2026-09-24/README.md).
No additional generation or key use. Existing evidence-correctness work remains P1.

**Review-response checkpoint 2026-09-23:** `fix/spring-side-review-response` carries
compiler `a37ff17b` (docs intake; tested head `af7352bf`, implementation `a156010b`) and playground `454a313`; shared catalogue
source branch `feat/spring-side-work-block` is available at `3a89391`.
Reviewer G independently closed G17–G20 at `e559c424`, including the catalogue source/oracle
review. G21 remains an accepted P4 playground gate follow-up: missing test jars must
refuse a full-evidence gate or prominently report incomplete evidence; both supplied
must execute the callback probe with no skips, with a guard-disabled negative control. H1 and
M1/M2/M4 and Java H1 are independently confirmed; M3's original protections hold but
R1–R3 now have an explicit input allow-list and three seen-red regressions. The browser
addendum confirms H1/M5/M6 on the prior head. Its N1 is now guarded in both emitters
with the real tokenizer and a guard-removal witness each; N2 narrows the unregistered
node claim. The owner-supplied re-review now independently closes R1–R3/N1/N2.
Compiler and playground are ready for owner approval. G separately corroborates gates
and current tokenizer identity at `c8594b5f`, without claiming those closures. Runtime
value escaping remains upstream at mongoose-plugins#39. See the existing response for
the exact allow-list, its limitations, predictions, commits and regression witnesses.
[Response and exact gate commands](../handoff/response_spring_side_reviews_2026_09_23.md).
The original implementation handoff is historical; its filtered counts are superseded.
**New accepted, non-blocking follow-ups:** P1 (Low, starter: unresolved/external POM
roots currently throw; conservatively retain types when coverage is unknown), NF1
(Low, both generators: blank/edge-space filter representation), G22 (P3 in G's report,
generator/runtime: make value transformation visible) and G23 (P3, test-gate owner:
verify the oracle against the declared supported analyser version). Canonical owners
and red-control acceptance remain in compiler `design/spring-authoring/TRACKER.md`
under “Independent re-review intake — required corrections hold”; this is an index,
not a duplicate implementation queue. None of these follow-ups is implemented here.

**Still open after publication:**
G14 on the real public download, catalogue public binary resolution/integration and D-X9's
previously owner-built provenance. Low review follow-ups and runtime metadata escaping
remain open. No analyser application code changed. G14 uses an isolated released analyser
as the evidence canvas; it does not run the application.

<!-- branch-archive-evidence -->
**G19 scope of the five generated-project checks and round trips:** these checks run
branch tool **1.0.74-SNAPSHOT** while the emitted POM and authoring record pin
published **1.0.73**, which contains none of this work. They are branch evidence,
not evidence of what a user receives. G14 remains open for a carrying public release.
<!-- end-branch-archive-evidence -->

**Both reviews addressed without overriding either verdict.** Final Java command:
`JAVA_HOME=/path/to/jdk-21 bash tools/spring-authoring/run-local-gates.sh` runs the entire
starter module and dependencies: **264 builder (one existing packaged-jar-only skip),
80 starter (zero skips), zero failures/errors**; coverage guard and jar check pass.
Playground `pnpm test` with both `FLUXTION_STARTER_TEST_JAR` and
`FLUXTION_RUNTIME_TEST_JAR` as in the response: **563 passed, zero skipped**; build passes,
type check retains four existing errors/six warnings. The earlier 559-plus-one-skip run
omitted the runtime jar; its callback probe now runs. All five generated-project script
paths pass, with the G19 scope below the checkpoint applying to every one. Hosted exports
include wrong-expectation controls and clean stop; the pristine keyless bundle also passes.

Owner's instruction, 2026-09-23: complete the Spring-side blockers together rather than as separate queue items,
because they share owners, repositories and artefacts. **This section is an index, not a second home** — each item's
canonical entry stays where it is linked below, and status changes there. Hand this section to the implementing
session as its scope.

Three repositories move together: the compiler (starter and reconciler), the playground (browser generator,
templates, download) and, for evidence only, the analyser. Every defect this programme has produced has lived at
those seams, so the order below groups by what a single change set can close.

**1. What a new node contains — the piece that blocks the beta.**
- **BETA-B2** (▸ *Beta*): a newly added node must audit its own state, or the A2 task measures the product rather
  than the tester. **This is starter work, not analyser work:** the browser generator decides a fresh class and the
  reconciler decides a member added to an existing one, and today the reconciler writes stub bodies with no state
  logging. The `add-a-node` skill has already been reworded to ask authors to log the state behind a decision;
  a wording change does not fix a generated stub. Settle the new-owned-node policy at the same time — absence of
  values is not absence of execution.

**2. The reconciler follow-ups from pass 4** (compiler; canonical text in `design/spring-authoring/` on
`feat/spring-authoring-a1`, findings G12, G5, F5/G13/F10 and G7).
- **G12** withdrawing an explicit reference binding demands a field the class does not have, because the undeclared
  path names the field after the bean while the declared path resolves by type. Refusal is transactional, so nothing
  is written, but generation cannot proceed.
- **G5** value and service type shells are written outside the ownership record and the report, never removed when a
  declaration is withdrawn, and the classpath is not checked before one is created.
- **F5 / G13 / F10** one formatting item: added members unindented and fully qualified, updated annotations
  unindenting their neighbours, blank lines accumulating, and a line-feed inserted into files that use carriage returns.
- **G7** the declaration verifier stops at the first unmet declaration and names no bean in its element. For a jar the
  integrator cannot read this message is the whole conversation, and **M67 beat 4 improves when it lands**.

**3. The download must carry its own tooling** (playground).
- **SG-2 — SPLIT 2026-09-24. Provisioning closed; hosted generation/run still open.** On 2026-09-24 this was
  reconciled as fully closed, and **that was wrong** — corrected the same day after round 4 inspected the CI log
  rather than the claim copied from it.
  - ☑ **Closed: acquisition, setup and validation on the actual hosted archive.** Playground **1.0.74** deployed at
    `5d6a38a`; public CI 35929392911 — setup/validate shipped, 97 classpath entries, 36 originals unchanged. The
    keyless bundle separately builds, runs, exports five independently checked records and stops cleanly.
  - ☐ **Still open: changed-graph generation and run on the hosted ZIP**, which the written requirement includes.
    **Verified here, not taken on trust:** that CI run has three jobs — `spring-provisioning (fluxtion-spring)`,
    `spring-provisioning (fluxtion-spring-mongoose)` and `customer-download`. There is no hosted generation job,
    the mongoose provisioning result reports `generationAttempted: false`, and the build/run/export/stop evidence
    comes from the `analyser-bundle` template, which is the keyless regression check. Hosted generation/run remains
    the earlier **branch** trial, and a later provisioning pass does not retroactively change that trial's artefact
    identity. This clause needs its own acceptance naming the **hosted** template; G14 remaining open does not
    establish that its standalone extended-design trial will exercise the hosted archive.
  - **Consequence for BETA-B3, restated honestly:** hosted Spring is now a genuine candidate, because its
    acquisition, setup and validation are verified on the real archive, so the recommendation may be reconsidered
    on merit rather than by elimination. That is **not** evidence that the hosted guided journey has been
    verified. **BETA-B3's template decision and its dry run stay open.**
  - Evidence in [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-24*.
- **F9** extended declarations in interpreted mode pin the carrying release with no setup files and nothing naming the
  coordinate. **F11** a user version override splits the tool from the dependency set.

**4. The jars — build one collection, not two.**
- **BETA-B4** needs jar A, a component whose vendor built it honestly and got the logic wrong, so that no integrity
  mechanism can catch it and only an independent calculation finds it, plus jar B and the spec-derived check.
  **M67.1** needs a public catalogue with three jars of its own. Different purposes, same artefact type and the same
  repository. Decide the shared home once (M67's D-X7 proposes a new vendor-jar repository) and build both sets there.

**5. Close the release evidence** (all three).
- **G14**: the real published-download run generated and ran, but its canvas step was interrupted; the supervised recovery and remaining evidence defects are recorded in the release checkpoint above. Now that the tool is published,
  preserve that public setup/generation evidence and resolve the named remaining findings before another acceptance attempt.
  The public two-node browser preview was already owner-witnessed; this recovery did not repeat it.

**Order inside the block, because the fan-out is uneven.** Do not work it top to bottom. Two items unblock other
people and two unblock nobody, and a session handed a list will naturally start with the concrete reproducible
defects rather than the policy work.

1. **The new-node stub policy (BETA-B2).** Nothing in the beta can be judged until it lands, because the central task
   measures the tester rather than the product without it.
2. **SG-2, the hosted download — part done.** Acquisition, setup and validation are closed on the real hosted
   archive; **changed-graph generation and run on that archive are not.** The template choice can now be argued on
   merit rather than by elimination, which was the point of this item, but the guided path is not yet proven true
   for the hosted route end to end. What remains is a single acceptance naming the hosted template.
3. **The jars**, built once for both BETA-B4 and M67.1.
4. **The reconciler follow-ups** (G12, G5, F5/G13/F10) and **G7**. Real, and they unblock nobody, so they come last.
5. **G14**, the acceptance run from a real download, once the rest is in.

**One decision is the owner's and must be settled before the work starts, not invented during it: what does a newly
generated node log?** Too much costs allocation and dispatch time in a runtime that sells zero allocation, and fills
the record with noise; too little leaves the gap the observed trial found, where a node had nothing to say about its
own decision. The decision belongs in the compiler's Spec 3 B-series alongside the other generated-member rules, and
the implementing session records it there rather than here.

**Not in this block:** the analyser's own instrument defects (DX-02 to DX-05, WS-1, WS-2). They are analyser work and
they gate the beta's invite-the-attack step for a different reason — a tester must not be invited to find a hole we
already know about. Run them alongside rather than after: they are small, they belong to different owners, and they
are what makes the instrument argue against itself in front of a stranger.

---

## Release tooling — restart checker path aliases

- ☐ Canonicalise the restart checker's compared file paths on macOS. The default temporary root can
  report `/var/...` while `Path.resolve()` yields `/private/var/...`, causing a false CLI-isolation
  failure. The 1.19.0 check passed unchanged using the supported canonical `--output` root. This is a
  checker portability follow-up, not a product restore failure. Acceptance: an alias-path input and its
  canonical equivalent compare equal, while genuinely different files still fail. See the
  [release record](../handoff/completed/release_java_source_spotlight_1_19_0.md).

---

## M68 · Evidence integrity — [spec-evidence-integrity.md](spec-evidence-integrity.md) (proposed 2026-09-24)

The open DX/WS findings, given one governing rule and one owner instead of six loose items: **the instrument never
says more than it established.** Raised to a milestone because on 2026-09-24 a held-out client was told a declared
node was absent from a graph that declares it, reported that faithfully, and the result now holds G14 open. The
same packet shows a chart reporting no data where the series response yields a point, and a requested illustration
missing from an export that reported success.

**Independent review, 2026-09-26** (`0bb01fa8`): R1–R8 and the remaining implementable gaps were implemented on
`feat/m44-single-state-session` and are tracked under **M44.4r**. They were accepted by the two re-reviews, and
merged to main with PR #25 on 2026-09-26. **The M68 items below stay open (◧):** merging put their code on main;
it closed none of them. Q4 (partial delivery), Q5 and each slice's own stated gaps remain as written.

Sibling of the tool-agreement spec, which governs agreement *between* tools. Distinct from the Mongoose audit
format proposal, which governs how a producer writes and delivers its file; the two meet only at D-E9, the framing
verdict, which is owned jointly.

- [M68.1] ◧ **The coverage, pairing and scope verdicts — and it completes M45.4.** **Start here:**
  [`handoff_m68_1_coverage_pairing_scope.md`](../handoff/handoff_m68_1_coverage_pairing_scope.md) is the cold-start brief.
  Declared authorship read before
  any package heuristic (D-E10), membership against every declared node vertex, the authored eligible population
  kept for the ratio, and the three states — membership, ratio, retention — never derived from one another. The
  honest figures for the packet's graph are 3 declared and 3 covered, not the 2 the response reported. Also: a
  zero-denominator population still has established membership; with no logged ids no pairing is established and
  no downstream verdict may claim the graph describes the log, asserted on the coverage/context/report path rather
  than the pure comparison; and every verdict discloses its observation scope, because the pairing on open samples
  500 records while coverage scans the whole log. Fixture is the committed graph plus a labelled constructed log,
  so this slice is **not** gated on the packet's missing audit logs. **This was authorised on 2026-09-01 as M45.4
  and lost in an archive sweep** — see that entry, restored above. Was DX-02.
  **IMPLEMENTED 2026-09-24 on branch `feat/m68-1-coverage-pairing-scope` — not merged, not independently
  reviewed, so ◧ and not ☑.** Declared authorship read first (`Scaffolding.classify`, node-scoped, every answer
  carrying DECLARED/INFERRED); membership against every declared node; no ratio for an empty population; the
  pairing carries its scope as data plus `evidenced` / `everyObservedIdDeclared` / `sampled`; the policy no longer
  lets a kept-but-unjudged or partial pairing reach FULL; provenance wording removed from coverage, pairing and
  `Match.describe`. **Evidence:** 17 new tests (`EvidenceIntegrityCoverageTest`, `CoveragePolicyEvidenceTest`,
  `EntryPointAuthorshipTest`, plus one renderer test); four existing wording assertions converted into guards that
  now *forbid* the build conclusion; full suite 1,895 / 0 / 0 / 62 skips. **Five mutations each turn the suite red
  alone** (ignore declared authorship: 4 tests; authored-only membership: 3; membership derived from the ratio: 2;
  retention reaching the claim: 1; the PDF dropping a table's notes: 1). **End to end:** `tools/verify-m68-1-coverage.py` drives the built jar through
  `open` / `coverage` / `context` against the committed packet graph; it passes on the branch and **fails 17 checks
  on a jar built from `main`**, reproducing the client's exact warning text — the seen-red run. **Also fixed here, found by
  driving a real export:** the exported PDF dropped coverage's warning while the on-screen Reports tab showed it; the
  page now prints each table's notes as the tab does.
  **Not asserted, and said so:** the pairing note on screen, which the panel's width cuts off at the default
  window size (the authored-view count *was* checked by screenshot: four nodes on `main`, five here); audit
  readiness through the panel's hide control.
  **Re-review round, 2026-09-24:** the implementation review (`review/m68-1-coverage-pairing-scope-2026-09-24`,
  `5c6f865d`) found five required changes — a display test the branch broke and CI never ran, acceptance 3 met in
  only one of three open orders, the build conclusion alive on five surfaces, the brief's third mutation
  unguarded, and an untested merged tree. All answered on the branch with `main` merged in; see the report's
  addendum and `docs/handoff/evidence/m68-1-rereview-2026-09-24/`. Final gates: headless 1,927 / 0 / 0 / 62, frame
  63 / 0 / 1 skip, 21 of 21 mutations RED at their named test.
  **Round 3, 2026-09-24:** the re-review (`review/m68-1-rereview-2026-09-24`, `6a7042e7`; not independent, its author
  wrote the first review) found three more — the whole-log qualification outliving its log under Follow, a
  narrower comparison erasing a wider one, and holes in the wording guard. All three answered, plus the same
  staleness one and two levels down (the published pairing, the session's copy). Final gates: headless
  1,933 / 0 / 0 / 64, frame 65 / 0 / 1 skip, 33 of 33 mutations RED with a `<failure>` at their named test and green
  again after every restore.
  **Round 4, 2026-09-24:** the round 3 review (`review/m68-1-round3-2026-09-24`, `4769d93a`, same author as both
  earlier reviews) found narrower versions of the same class — two filtered comparisons erasing each other, a
  filtered comparison outliving its filter, a stale comparison's fields still claiming the whole log, formatting
  that hid the phrase from the guard, and a fourth sampling loop. All answered; the normalised guard also caught a
  real survivor in the published topology guide. Final gates: headless 1,937 / 0 / 0 / 65, frame 66 / 0 / 0 / 1
  skipped, 48 of 48 mutations RED with a `<failure>` at their named test and 49 of 49 green again. **Awaiting
  review; still ◧.**
  **Found while testing, belongs to M68.4:** `open {log, graphml}` with a graph that declares only half the logged
  ids reports success and the graph is then no longer loaded — a combined request that silently drops part of
  itself. Reproduced end to end, and **pre-existing**: identical on a jar built from `main`, and only the combined
  request drops the graph — opened separately, the graph is kept and announced. Not fixed here.
- [M68.2] ◧ **Report and chart rendering** — every requested section renders or says why not; a chart claiming no
  data is contradicted by a successful series response over the same inputs; acceptance is by inspecting the
  artefact, not by exit status. **Built 2026-09-24 on `feat/m44-single-state-session`; awaiting review.** Read from
  the G14 packet's own PDF first. Its "Trend" was the whole chart TAB painted at about 190 px, with a plot sliver
  saying "No data under the current fi…", and its requested topology section left nothing on the page. Three defects,
  three fixes: charts render off-screen at page size (`ChartPanel.toImage(w,h)`, `GraphPanel.renderForReport`); a
  starved plot says it has no room, not that there is no data; and the renderer prints NOT RENDERED with its reason
  for any CHART/TOPOLOGY section with no picture. The topology gap text had never reached a page. Verifier scenario 13
  fails on a `df0b24a5` jar and passes on the fix. **Open:** an off-screen render of a named FOCUS, so the section is
  drawn and not only explained; the D-E8 contradiction test in its general form (chart versus `series` over the same
  inputs); and SERIES sections, still a stated gap.
- [M68.3] ◧ **The framing diagnostic — correcting a shipped false verdict, not adding a diagnostic.** **Analyser half
  built 2026-09-24 on `feat/m44-single-state-session`, awaiting review, on CONSTRUCTED logs** (the original is not
  in the public packet; the diagnostic judges structure, so each acceptance-10 case was built exactly, and the real
  starter sample is the cross-check). The legal quoted-key file is no longer accused: the old jar says "record 1
  alone contains 2 records run together" word for word (verifier scenario 16). Collapses are suspected with span and
  candidate lines; beyond the bound is "not assessed"; the pending frame is scanned under Follow. **Open:** the
  producer half, which stays filed against the starter and the audit format. The producer
  diagnostic already exists, counts raw occurrences of the record-header key inside a frame, and was reproduced in
  round 3 reporting "record 1 alone contains 2 records run together" for a **legal one-record file** whose quoted
  value contains that key as literal text. That verdict reaches `context.producer` and the human surface today.
  Replace the substring count with a physical-line predicate excluding quoted scalars, publish the inspected span
  and candidate locations, phrase it as suspicion, report beyond-bound frames as not assessed, and supply the
  pending frame under follow without accepting the pending record. Format 1 §1 and §1a already settle the legal
  single-record file, the ordinary unterminated tail and the pending record. Needs the original logs. Producer half
  filed against **the starter** (the writer actually at fault) as well as the audit format work.
- [M68.4] ◧ **Whole-or-refused requests, and the declared parameter** — was DX-03 and DX-04.
  **Completed 2026-09-24 on `feat/m44-single-state-session`, awaiting review.** The D-E3 disposition table is written
  in the spec: every audited row is brought under the rule or listed as an exception with its reason and its pinning
  test. Acceptance 4: a rolled set, or a formatted log, opened with a graph keeps both (DX-03; verifier scenario 15).
  Acceptance 5: `topology {recordIndex}` establishes the record or refuses naming what is missing (DX-04; scenario
  14). A refused call leaves the spotlight lit. **Decisions for review:** the spotlight ordering reverses M64's
  recorded rationale; `flag` refuses rather than clamps; a rename with other fields is refused.
  **The combined open, fixed 2026-09-24 on `feat/m44-single-state-session`** (built on M44.4's session model; not
  reviewed). `LogArrival` treats as residue only a graph that was open when the log was REQUESTED. An `OPENED` graph
  opened since, even the same graph again, is intent for that log, so it is kept and the mismatch announced (M35.3;
  M44.3b's "the last deliberate request wins"). Reader-supplied graphs are judged as before. `CombinedOpenTest`
  covers four cases and three witnesses. The verifier's new scenario 11 FAILS on a `8893cb08` jar (graph gone
  after the combined open settles) and passes on the fix. **Changed a reviewed test's premise:**
  `AsyncOpenInterleavingFrameTest.b1_…` relied on the arrival closing a graph a person re-opened mid-load. Its point,
  that a socket arrival's warning is not a dialog, is kept and still asserted.
  **Reproduced input from M68.1 (2026-09-24):** `open {log, graphml}` with a graph declaring only half the logged
  ids replies `ok`, and the graph is then no longer loaded; opened separately, the graph is kept and announced.
  **The drop lands after the reply** (M68.1 review O6): `coverage` answered with the graph still present
  immediately after the combined open settled, and two seconds later it was gone. So an acceptance that checks the
  first echo, or even the first settled context, passes on a request that will still lose part of itself —
  test on the final state after the log-arrival rule has run.
- [M68.5] ◧ **Identity change under follow, and the project root** — was WS-1 and WS-2. **Built 2026-09-24 on
  `feat/m44-single-state-session`; awaiting review, so ◧ not ☑.** All three parts below are in, with their stated
  limits. Acceptance 8's diagnostic: `Runbooks.resolution` names the root tried, and the resolved path, for the
  runbook and vocabulary pointers, including the two cases that used to show no warning (no project root, and a
  path out of the root). Environment log directories and report destinations are not covered.
  **Heap-store Follow identity built 2026-09-24 on `feat/m44-single-state-session`** (not reviewed).
  `FollowIdentity.classify` covers every acceptance-7 case, with one departure recorded under D-E6 (full-byte
  comparison makes a touched identical file UNCHANGED). The heap store decides before indexing; a replacement
  reopens as a new generation and `context.log.identity` says why. The verifier's scenario 12 FAILS on a
  `f2e25e80` jar (the same-length rewrite ignored) and passes on the fix. **The next request, both stores, built the
  same day:** the dispatcher observes the file before any record-reading verb. It refuses while the opened file
  changed in place under the mapped store (which reads through its channel), and labels superseded-but-retained
  content (the heap store's text; the mapped store's open channel after a replace). `context` and window focus
  observe as well. **Limits, stated:** an in-place rewrite that restores size, time and key is invisible to a
  metadata check; the human table is announced, not suspended.
  Related narrow correction: [PR #7](https://github.com/telaminai/fluxtionauditlog-analyser/pull/7),
  `fix/named-profile-project-root` at `c3523506`, fixes relative-path anchoring for named profiles.
  Reviewed: 52 configuration tests pass; restoring the canonical-only lookup fails the named-profile
  regression, then restoring the source returns all 52 to green. Full headless gate: 1,877 tests,
  zero failures/errors, 62 skips; strict docs pass. See the
  [investigation](../investigations/profile-project-root-resolution.md). Pending merge/release;
  this does not close M68.5's freshness or unresolved-pointer diagnostic acceptance.
- [M68.6] ◧ **The naming grammar** — was DX-05. **Q2 answered by the owner 2026-09-24: refuse at creation.** Built the
  same day on `feat/m44-single-state-session`, awaiting review. A chart name that spotlight cannot address (`:`,
  `"`, or exactly `note`/`series`) is refused at the `graph` verb's create and rename and at the UI rename. A saved
  one stays reachable: by the verb as saved, and by spotlight quoted (`graph:"a:b":note:2`). `context.graphAddresses`
  gives every address. Recorded under D-E5.

Three owner questions in the spec: whether a genuine mismatch blocks or annotates, the compatibility choice for
unaddressable names, and whether the inspect-the-artefact rule becomes a standing release gate. All three now
carry a recommendation from both reviews; Q1 is close to settled by the tool-agreement spec's existing policy.

**Spec is at v3**, after three review rounds on 2026-09-24. **None of them was independent, and calling them
independent was wrong:** round 1 (`review/m68-evidence-integrity-2026-09-24`, corrected `2f321705`) also prepared
the client evidence the spec cites; round 2 (`review/m68-evidence-integrity-response-G`, `7b8d9f51`) was reviewer G,
who wrote the spec, and added D-E10; round 3 (`review/m68-v2-2026-09-24`, `d1e58bc9`) was round 1's reviewer again,
and narrowed a conformance claim it had itself endorsed. Their value is that each found errors the previous missed,
not that they agree. v1 held a contradiction in D-E1 and an error in acceptance 9; v2 introduced a new conflation
in acceptance 2 and over-read the tool-agreement spec; all are fixed. Round 3 also **reproduced two false verdicts
in shipped code**, so part of this milestone corrects what the product says today rather than adding anything.

## Tool agreement — [spec-tool-agreement.md](spec-tool-agreement.md) (proposed 2026-09-21)

### Released — 2026-09-21

PR #4 landed on main by fast-forward at `d1bb7a1a` (no squash or rebase, so the `41b77650` pin stays an
ancestor); CI 35619600124 and the docs deploy pass. **v1.17.0** released from it (Release run
35620337212; changelog stamp `fdc9c042`; jar, versioned jar, SHA256SUMS). Playground re-vendored onto
fluxtion-web main as `89366ff`, carrying `d917a7a` forward with provenance `local@41b77650` (git archive of
`d1bb7a1a`; the unauthenticated GitHub API was rate-limited); 544 pass / 5 skip, build passes. A fresh
public `analyser-bundle` download serves it: `run-mongoose-server` differs from the vendored bytes only by
the three bundle substitutions, with no `TODO(bundle)` left. TA-5b/5c stay open.

### Re-review intake — 2026-09-21

[Independent re-review](../handoff/rereview_tool_agreement_2026_09_21_opus.md) at `51df9ebd`:
mergeable after N1's mechanical gate correction. F1–F6 closed (F1/F2/F3 with follow-ups);
F7 withdrawn. **Frozen N1 prediction before edit:** an exact-path `-whitespace` attribute makes
merge-base-to-HEAD diff checking pass without changing one evidence byte; removing it restores
seven failures. Ordinary source files must retain whitespace checking.
**Frozen N3 prediction:** the already-present length assertion at playground `d917a7a` rejects a
validly shaped mirror provenance longer than 300 characters; restore the manifest and it passes.

- ☐ **N2:** Follow's snapshot-to-live reload clears flags/selection/filters. Add preservation by
  verified record identity or notice at the point of use; the FAQ alone is insufficient. Owner:
  analyser Follow UI. Acceptance: flag an ordinary export, start Follow and either retain the
  verified flag or warn before it is cleared; exercise both affected and unaffected paths.
- ☐ **F3 integration gate:** old recipe is valid only at end-marker `02fa62b3`. Regenerate against
  the actual chosen head before combining branches; at `955b90ed`, preserve `statusText` and
  member-nested `streamEndFacts` plus later framing corrections, then rerun combined gates.
  Owner: branch integrator. Do not apply the old “take ours” recipe to newer heads.

N1 prediction held: exact-path attribute passes the branch-range gate, removal restores seven
failures, ordinary-file whitespace remains rejected, evidence hash unchanged. N3 prediction held:
reviewed test already has the 300-character assertion; 381 characters fails it and restored input
passes. [Response and witnesses](../handoff/response_tool_agreement_2026_09_21.md).
The re-review's N3 omission claim is not supported by the reviewed source. No playground edit.
Mechanical-response gates: Maven 1,767/0/0/49; strict docs, branch-range diff and sweep pass.

### Review response — implementation and verification

[Response to F1–F7](../handoff/response_tool_agreement_2026_09_21.md),
[re-review brief](../handoff/brief_rereview_tool_agreement_2026_09_21.md), draft PR 4.
F1 fixed with export-layout regression and 25→24 mutation; ordinary EOF records retained, live
Follow stays pending-only. F3 agrees with corrected end-marker branch `02fa62b3`; disposable combined
clean gate 1,806/0/0/49, resolution patch preserved. Main `ea865d2d` integrated, all skill bytes pinned
at `41b77650` by `b6633048`; playground re-vendor `d917a7a` pushed, undeployed (543 pass / 5 skip; build passes). D12 corrected;
D13 explicitly not reproduced later. F5 CI Linux/Xvfb 50/50, zero skips (run 35610006347).
F6 exact mutation anchors added; F7's 49 headless skips were already recorded, now cross-linked.
Response clean gate 1,767/0/0/49, Mac display 50/0/0/0. Counts remain analyser 1 / upstream 8.
29 affected demo/conversation captures refreshed. Await independent re-review; no release claimed.

### Review response — frozen predictions before fixes

Review `cac590a3` (F1–F7), feature reviewed at `fcaf14ad`.
- **F1/F3 prediction:** ordinary heap, mapped and rolled opens will expose every record in a
  separator-between-records export, including its final EOF record. EOF without a separator is
  disclosed, never treated as proof of damage or completion. Explicit Follow starts a fresh live
  read when the ordinary snapshot included an EOF record; only the live read withholds its tail.
  A quiet poll and partial separator cannot publish it; its completed separator publishes it once.
  Reinstating strict ordinary-open framing must fail the export regression. Latest end-marker
  branch `02fa62b3` has already withdrawn STOPPED_MID_WRITE; integration must preserve UNKNOWN.
- **F2 prediction:** incorporating main's skill bytes plus the Mongoose correction, then pinning
  to a commit containing all three, passes canonical hash tests. Re-vendoring must use that same pin.
- **F4 prediction:** corrected D12 cause and qualified, unreproduced D13 remain identical in the
  canonical skill and runbook, and parity/hash checks pass. No endpoint retest is claimed.
- **F5:** seek a CI display run with zero skips; local focus success alone does not close it.
- **F6/F7:** add exact mutation file/method anchors. The original final report already states
  49 headless skips in both its Full-gate correction and Final handoff; retain and link that evidence.


**Initial implementation pass (superseded by review response above):** `feat/tool-agreement`, source head `7df316c8`.
[Author report](../handoff/report_tool_agreement_2026_09_21.md) and
[review brief](../handoff/brief_review_tool_agreement_2026_09_21.md). Analyser **13 → 1 open** (D20
producer-blocked); upstream **8 → 8 open**. TA-5b/5c remain open. Final clean gate 1,766 tests
(49 display skips); separate display gate 50/50 with no skips. Packaged spotlight, tools smoke
and strict docs pass. All 33 affected main/conversation/Spring captures refreshed and inspected.
Concurrent main integration and independent review remain outstanding; not merged or released.

Source: an uncoached session's report, [copied as evidence](../handoff/evidence/unguided-session-2026-09-21/session-report.md)
with graphml fixtures. The runtime matched every prediction. The baseline records D1–D21, with D3 split into
D3a (build) and D3b (detection). Two counts, reported separately: **analyser 1 open** (13 at baseline; D20 producer-blocked), **upstream 8 open**;
D7 is reclassified as capability disclosure. Direction check: re-count each release. **Revised 2026-09-21
after review:** the first version reversed the two fixture fingerprints (source `4ecd6133…`, stale
`f6ae6f84…`), and wrongly called feedback 23, 25 and 34 untracked.

- ➜ **TA-1/2/3/4/5a/6/7/8/U ☑ archived 2026-09-21** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-21*;
  all nine shipped in 1.17.0. The four below stay.
- **[TA-5b] ☐ — implement and vendor the chosen route (owner named in TA-5a)** · _open until shipped in a starter._
- **[TA-5c] ☐ — one spot-check session, only after TA-5a and TA-5b ship** · _fresh agent reaches a followed
  log without jar disassembly or a hand-written follower; scored from tool events. The spec's only session._
- **[TA-9] ☐ — analyser half of "Authoritative dispatch metadata (34)"** · _blocked on producer metadata;
  the analyser never infers the hierarchy. `desk-quote-supertype.graphml` is the before case: hierarchy shown as
  unknown, not off-path. With a relationship-carrying fixture: union route drawn; unrelated class adds none;
  same simple name in different packages not conflated; missing metadata stays unknown._
- **[TA-B] ☐ — category B of the 2026-09-19/20 feedback counted in the baseline (D14–D20)** · _built under their
  existing items below ("evidence correctness first", "Feedback 38/39 and recurring 6", "Chart feedback
  41–43", "Topology feedback 37"); proposed: raise "evidence correctness first" from P1 to P0 alongside
  TA-1–TA-4, since it is the same slice B gate._
  **Owner decision, 2026-09-21:** keep evidence correctness first at P1 for this pass; do not reorder it.
---

## Mongoose audit format — [proposal](../proposals/mongoose-audit-format/README.md) (2026-09-21)

Remove the export step: Mongoose writes a file the analyser opens directly. Cross-repo; nothing is built.

- **[AFMT-1] ◧ — proposal reviewed at revision 4 (`952a9555`) and again at revision 5 (`473b8db5`); now at revision 6 (`23560093`)** ·
  _the direction (option B) survives; the review's findings are addressed or explicitly declined._ Findings,
  OBSERVED on the shipped bundle's processor and a live server (evidence:
  `.local-evidence/coldstart-v2-2026-09-20/audit-format-review-2026-09-21/`):
  1. A live swap to binary is **not** equivalent to starting binary. Both `DataFlow` swap orders throw; the one
     order that runs silently drops trace entries of nodes that do not log for themselves (`riskCheck` 15 → 0)
     and leaves an unresolved id. `EventLogManager.updateLogRecord()` rebuilds only log-source loggers. So binary
     needs a runtime fix or a build-time choice. Sink-only swap (text) works today.
  2. The repository list omits this repo (`run-mongoose-server` says Mongoose does not write analyser-readable
     YAML; re-pin + re-vendor) and the runtime (per 1), and the web admin's audit views are Chronicle-bound.
  3. Recommend text first: its content is `asCharSequence()`, already produced and read on every hosted run.
  4. `/ws/audit-tail` root cause (svc-admin-web 1.0.43): `ThreadingIllegalStateException` on every tick at
     `WebAdminService.java:1022`, swallowed at DEBUG; plus unsent batches are discarded per tick. The route,
     upgrade and unknown-processor error all work. The tail reads the Chronicle capture, so it is not
     independent of the format change.
  5. Acceptance must compare per-node entries, not record counts; `chronicle` + `binary` must be refused.
  **Second review, revision 5 (`473b8db5`), 2026-09-21** — text-first confirmed (export is byte-identical to the
  Chronicle excerpts joined by `---`), but: the framing requirement omits the `---` separator (records merge into one,
  silently); an end marker is invisible to the analyser without a format-spec change and analyser code; any record
  swap, not only a format swap, drops traced-only nodes; no shipped client opens `/ws/audit-tail`; twelve true items
  were lost across revisions 2–5 and not restored in revision 6. Evidence: `…/audit-format-review-2026-09-21/rev5/`.
- **[AFMT-2] ☐ — file the `/ws/audit-tail` defect upstream (mongoose-plugins)** · _tailer created and read on one
  thread; tick failures reported, not swallowed; unsent records carried to the next tick; delivered count equals
  exported count for the same window._
- **[AFMT-3] ☐ — a per-node log level of NONE corrupts the whole text audit record (public runtime, live today)** ·
  _after `EventLogControlEvent(sourceId, null, NONE)`, the next record keeps its values but loses its header, keys and
  newlines, for every node. Observed on the bundle's processor (fluxtion-runtime 1.0.16); global levels and per-node
  DEBUG are unaffected. Cause not diagnosed. Repro: `…/audit-format-review-2026-09-21/rev5/LevelTest3.java`._


## Spring authoring documentation — 2026-09-19

- ➜ **Two ☑ items archived 2026-09-21** ▸ *Tidy 2026-09-21*: the guides themselves and the Mermaid syntax correction.
- ☐ **Independent review of the two pages** — version correction is implemented; diagrams and overall claims
  still need the owner-chosen review. The next analyser release carries the reviewed pages.
## Spring authoring observed acceptance — 2026-09-19

- ➜ **Seventeen ☑ items archived 2026-09-21** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-21*: the
  journey implementation review and re-review, the stopped R3 battery, four reviewed implementation slices, the staged-feedback
  intake and its four review rounds, topology feedback 37, the report-export/four-use-case/vendor/session-audit reviews, the
  close/reopen diagnosis and the rehearsal launcher. The trial record stays, because DX-02–DX-05 hang off it.
- ◧ **Starter journey implementation started — 2026-09-20** — owner authorized commits, pushes and
  isolated branches through the full reviewed workflow. Preserve existing checkouts/staging. Planned
  branch in each owning repo: `feat/project-starter-journey`; analyser from current main, playground and
  compiler from their reviewed Spring-authoring heads with upstream integration checked explicitly.
  Track acceptance and exact heads in [implementation handoff](../handoff/handoff_project_starter_implementation_2026_09_20.md).
  Public release gates remain distinct from local development verification.
- ☐ **Participant follow-up — imitation and held-out isolation** — template examples teach by copying;
  check the generated harness matches its declared host/feed shape and record the file imitated. Treat
  the exposed EOD exercise as regression only; fresh acceptance uses a clean download and an access manifest
  excluding prior solutions/retrospectives. Dependency identity 30/33 and audit scaffolding 6 stay open
  producer behaviour work; comment-contract hashing does not close either. Journey/onboarding specs now
  name these constraints; they were added after the final review and are not independently accepted evidence.
- ☐ **Chart feedback 41–43 intake** — [ninth addendum](../handoff/review_staged_spring_feedback_2026_09_19.md)
  preserves the updated participant file. 41: pin/filter scope must explain an empty restored plot;
  42: independently reproduced windowing contaminating the left axis with right-axis values, plus MCP
  series removal/replace semantics (UI removal already exists); 43: colon-name targeting incompatibility
  reproduced in the parser. Pin-clear null/schema mismatch is included. No product fix claimed; exact
  staged UI interaction remains unverified. Restore acceptance inherits 41; wider chart fixes stay separate.
  **D18 frozen prediction:** window fitting must partition left/right series exactly as whole-data fitting
  does. Constructed 10–20 versus 1,000,000–1,250,000 ranges stay separate through pin, filter, refresh and
  saved-definition restore; an empty side falls back to 0–1 without borrowing the other's scale.
  Guides and markers do not contaminate either range. Reverting window partitioning fails the regression.
  Counts before: analyser 7, upstream 8 (D19 verification in progress).
  **D18 result:** held. `ChartAxisWindowTest` passes for separate scales, empty sides, pin/filter,
  refresh, guides/markers and saved-definition restore. Removing the window partition fails the
  independent left-range assertion. D18 closes; other 41–43 requests remain open.
  **D17 frozen prediction:** restoring a saved pin against disjoint new log times preserves the pin
  but states why the plot is empty, with pin bounds and separate event/text filters visible below the
  chart and in context. Pending extraction is distinguished from completed emptiness. Clearing the pin
  restores data when filters allow it. A mutation removing the disjoint-window reason fails the test.
  Constructed regression through actual saved-definition restore; counts before: analyser 5, upstream 8.
  **D17 result:** held. `GraphWindowScopeTest` restores the saved definition against disjoint new
  data, checks the separate filters, clearing the pin and pending/failed extraction. Removing the
  disjoint-window check fails its named assertion. Scope appears below the chart, in context/echo
  and PDF captions. D17 closes: analyser 4, upstream 8 remain open.
- ◧ **Cold-start measurement proposal preserved/reviewed** — [instrument intake](../handoff/evidence/coldstart-proposal-2026-09-20/REVIEW.md)
  keeps all four operator/subject files verbatim and records independent scorer probes. Adopt pre-action
  attribution, imitation and pristine-baseline recording, but treat static hits as leads and journal fields
  as testimony. Before acceptance: validate journal/baseline, enforce task isolation, choose an unexposed
  held-out task and report matched or explicitly unmatched comparisons. No trial completed; hardened scorer
  and manual rubric remain acceptance-harness work, never analyser execution logic.

- ◧ **Starter journey design review F1–F16 (2026-09-20)** — [review](../handoff/review_project_starter_journey_2026_09_20.md)
  is CONDITIONAL; [author response](../handoff/response_project_starter_journey_2026_09_20.md) records corrections
  and source limits. **Owner decisions:** absent `analyserSupport` means on even in old links; explicit false
  stays off. Both quit/relaunch and project reopen use the same explicit restore offer.
  [Re-review](../handoff/rereview_project_starter_journey_2026_09_20.md) accepts the F-series dispositions
  and leaves G1–G3. Those are now corrected in spec text: absent query means no override; one emitter per
  output path with a duplicate assertion; Project panel states, StartPanel landing offers actions.
  [Second re-review](../handoff/rereview2_project_starter_journey_2026_09_20.md) closes F/G and leaves
  H1–H3 in the learning section. Author corrections now require choice-neutral comments and changed-mode
  acceptance, a version-neutral fallback exception with unverified-compatibility disclosure, and explicit
  recurrence/held-out thresholds for adopting or removing guidance. Independent confirmation of H and
  implementation remain open. [Consolidated review](../handoff/review_all_specs_2026_09_20.md) approves the
  template spec subject to sequencing and adds H4–H6 for the journey. Current author corrections cover
  canonical comment ownership with direct parity, independent report assertions/mutations separate from
  non-regression, and hosted ordering/reset/replay/capture contracts.
  [Latest re-review](../handoff/rereview_all_specs_2026_09_20.md) closes H1–H6. Author corrections for J1–J3
  now specify the public starter artifact/resource/version/digest mechanism, stamp Mongoose snapshot
  observations separately from resolved project defaults, and clarify late-subscriber cache behaviour.
  [Final re-review](../handoff/rereview_final_project_starter_journey_2026_09_20.md) closes J1–J3 and marks
  the combined handoff READY. Its K1 endpoint and K2 digest wording corrections are now incorporated:
  Repsy is authoritative; resource digest checks content parity, artifact digest checks immutable releases.
  Slices 1–4 can start; comment publication verification requires a released starter containing the resource.
  Shared metadata, recipe version floors and other named implementation prerequisites remain open.
  That review accepted the design only; branch implementation is recorded in the slices below. No publication is claimed.
- ◧ **Additional starter-journey review scope — learning routes** — participant feedback now informs
  the journey spec's “Learning at the point of use” and acceptance 10–12: routed bootstrap/task references,
  decision-point stub comments, actionable diagnostics, version compatibility and a measured held-out
  journey. F/G/H/J are independently closed; final review READY with K1/K2 wording now corrected. Preserve generated-body
  ownership/hash compatibility and the analyser's pointers-only boundary. No guide verb or new runtime
  default is approved. Compiler/starter and playground own emitted guidance; agent/harness owns execution.
- ◧ **Cold-start v2 preview rehearsal — 2026-09-20** — owner selected the branch preview because
  the public Spring download lacks the v2 entry/profile/runbooks. Operator predictions are frozen in
  [the journal](../handoff/evidence/coldstart-v2-2026-09-20/OPERATOR-JOURNAL.md) before subject execution.
  [Operator report](../handoff/report_coldstart_v2_preview_2026_09_20.md) and
  [six-trial evidence](../handoff/evidence/coldstart-v2-2026-09-20/README.md): **T1 matrix complete;
  full T1–T6 battery incomplete; v2 acceptance NOT ESTABLISHED.** Five provider refusals; one normal
  client completion on older dependencies; another legacy application emitted output/tests before its
  refusal. One subject acquired a v2 ZIP, but none verified the v2 workflow. T2–T4/T6 not run; T5 ineligible.
  Direct OS probes deny source/staged-answer contents; pristine ZIP and transcripts are retained.
  CS-1–CS-5 record client/instrument, parser, fingerprint and target-migration findings. Scorer unchanged;
  raw output and manual corrections are separate. Durable unedited local archive is git-ignored.
  **Follow-up operator preflight:** the unchanged preview Audit analyser bundle builds with an empty
  Maven cache and no key, processes five input rows with matching audit values, exports and stops cleanly.
  [Report and corpus re-score](../handoff/report_supported_v2_preflight_2026_09_20.md).
  **PPF-1 CLOSED by author verification (playground `a4b4dde`):** runbooks select an emitted launcher;
  no recognised launcher means a file inventory and no prescribed run command. Standalone POM comments
  now name `run.sh`; script-disabled hosted README agrees. Final tests seen red with the old emitter;
  520/520 tests and production build pass, known type-check errors unchanged. Corrected download's
  commands were executed successfully before a fresh client received its own pristine copy.
  All six preserved trials re-scored with `main@18b47a7` (now pushed); 11 corpus
  witness checks pass and five go red against the old scorer. T-MAIN's new file-backed-driver candidate
  still needs manual classification. Raw originals and baseline are retained; no new sessions ran.
  **One client smoke PASS with recorded friction:** fresh claude-sonnet-5 completed in 92.421 s, no
  provider refusal/intervention, all five values match, clean stop, all 43 original files unchanged.
  Tool-event times replace self-reported times in this scoped smoke; three source pointers observed.
  Denied scratch/ps commands and a wrong registry lookup remain visible; the client's final “no failures”
  wording was too broad. [Fix and smoke handoff](../handoff/report_runbook_fix_client_smoke_2026_09_20.md).
  No battery ran. **Next:** freeze the revised instrument for that battery and provision the generation
  prerequisites for authoring tasks. Scaffold-time capability refusal is a proposal, not implemented:
  the website cannot infer a local key/provider. Ordinary build/run is not regeneration acceptance.
  Neither preflight nor rehearsal closes publication or public empty-directory acquisition acceptance.
- ◧ **Battery readiness R2 — 2026-09-20** — one changed graph regenerated through the published
  customer generation route using the existing external credential, then built, ran, exported and
  stopped. New `volumeTotal` appears in generated Java/GraphML and all five cumulative values match
  predictions written before the single generation attempt. This is operator customer-route evidence,
  not a keyless, private-provider, reconciliation or fresh-client authoring claim. Playground `5b67224`
  adds the two-host README guard (521 tests); all fourteen default templates emit recognised launchers.
  Existing staging port 5174 serves older `affdd85`, not the corrected journey preview.
  [Readiness report](../handoff/report_battery_readiness_2026_09_20.md) and
  [sealed R2 protocol](../handoff/evidence/battery-readiness-2026-09-20/PROTOCOL.md) distinguish first
  refusal from terminal runtime, observable timing from declared attribution, and longer compatibility
  from battery acceptance. Three longer positive clients passed (248/286/460 s monotonic), 54 rows independently checked;
  a fourth correctly declined the no-launcher fixture. No refusals/interventions. R2-3 has an
  UTC/monotonic gap subsequently explained by host idle sleep in R3; R2-2 omitted two source pointers. No general reliability or routing
  improvement claim. PPF-2 (hosted POM naming an omitted launcher) was found in the negative trial
  and fixed afterwards in playground `3bdcfbd` with a seen-red assertion and 521 green tests; no replacement client run.
  Public acquisition, generation capability provisioning
  for beta, publication, held-out task correctness and full T1–T6 acceptance remain open.
- ◧ **Starter verification tiers — owner-approved replacement for the battery** —
  [decision, implementation and gates](../handoff/report_starter_verification_tiers_2026_09_20.md).
  Playground implementation `4b41631`; analyser static checks accompany the report.
  Static corpus/command checks every commit; unattended empty-cache customer-download preflight on
  production publication; one acquisition-only spot-check when routing paths change, not per release.
  A defect is not closed without its cheap regression check. **Public acquisition measured
  2026-09-21:** a fresh client reached independently checked sample output on the third attempt (the first two
  stopped on client authentication, not the product) — [release report](../handoff/report_release_journey_2026_09_21.md).
  Limits recorded there: a longer discovery route than documented, the wrapper bootstrap failing in the
  client sandbox, and 84 of 87 tool batches without a source pointer. Publication is done (starter 1.0.72,
  then 1.0.73), and production CI runs the unattended customer-bundle and standalone Spring preflights.
- ◧ **Choice-neutral comments — local implementation verified** — the starter jar owns the canonical
  resource; playground `47b9952` vendors artifact/resource provenance and consumes it for fresh Spring node
  comments. Java reads the packaged resource. Direct emitted-text parity and a browser-wording mutation
  prove hashes alone cannot establish comment truth. DATA→TRIGGER→DATA preserves code/comment, compiles
  and repeats without changes. Java gates 84 + 47; playground 504/504 and build pass (four known SplitPane
  type errors remain). [Evidence](../handoff/evidence/project-starter-comment-contract-2026-09-20/README.md).
  **Publication open:** authoritative Repsy request returned 404; provenance says local-development and
  the public CI parity gate must remain failing until release/public re-vendoring. Selected diagnostics,
  full reference-version checks and held-out routing acceptance remain open; comments do not close
  vendor identity, audit scaffolding or imitation-channel defects.
- ☐ **Journey delivery dependencies** — playground support/default/API override → factored profiles and
  capability-specific runbooks → documented agent entry. Analyser saved-graph context → project landing →
  user-local per-project restore, replacing global implicit startup opens → full catalogue with bootstrap/key
  disclosures. Recovery through `7eceff5` and catalogue through `9244b0e` are implemented and gated on the branch. Ship picker
  expansion after default-on downloads carry profiles. Vendor entry coordinates
  with M67; the optional authoring section shares StartPanel and the existing picker controller.
- ◧ **Default-on analyser support in generated projects (owner, 2026-09-20)** —
  [starter journey spec](spec-project-starter-journey.md): website option, headless configuration parity,
  portable profile and project-specific bootstrap/runbooks, explicitly switchable off. Mongoose includes
  applicable deploy/start/stop/feed/audit guidance; ordinary project instructions survive opting out.
  Keep this separate from the special `analyserBundle` mode and from runtime/audit choices. Playground
  owns generation; analyser only reads the profile and renders evidence. Implemented on playground
  `feat/project-starter-journey` at `0e7bd58`: schema migration, website flag, headless override, unique-path
  generation and all-catalogue bootstrap/profile checks. Playground `0b5660a` fixes runbook identifiers,
  routes bundles to the same project entry and retains task guidance with vendored skills disabled.
  501/501 producer tests pass with the local starter jar; build passes (four known SplitPane type errors
  remain). [Actual profile import](../handoff/evidence/project-starter-profile-import-2026-09-20/README.md):
  all 14 scaffold-handler ZIPs load without rejected declarations; every runbook/source pointer resolves.
  A changed name reproduces the prior rejection. Runtime runbook validation, browser witness,
  independent review and deployment remain open; do not mark this released.
- ◧ **Author-new-project entry** — the journey branch adds a StartPanel authoring entry using the same
  catalogue/download controller as the File menu, with whole-catalogue explanations. No duplicate
  generator/configurator. A richer separate page and a plain-JSON headless request remain proposals;
  the existing documented headless template/token route is the implementation contract.
- ◧ **Project-aware landing and explicit resume** — implemented on the journey branch through `7eceff5`:
  saved declarations, explicit Restore/Dismiss, actual completion and content checks. Five real-frame
  cases and inspected UI/context captures cover the adapter; 37 display tests and clean package with
  1,707 tests pass. Independent review, OS-process restart and complete downloaded-project acceptance
  remain open. [Journey spec](spec-project-starter-journey.md); [evidence](../handoff/evidence/project-session-recovery-2026-09-20/README.md).
- ☐ **Journey starts before a project exists (owner clarification, 2026-09-20)** — provide one catalogue
  reached from the website, a local LLM or the analyser's existing template picker. The LLM can download
  a project without the analyser; it follows project runbooks to set up/build/run, then connects the
  analyser as the shared design/evidence canvas. Requested entry choices: guided starter, Mongoose +
  Spring authoring, bare Fluxtion, vendor + Fluxtion. Each needs its own runbook, with one short bootstrap
  entry and shared references rather than divergent copies. Keep tutorial guidance, freeform design and
  held-out evaluation explicit; guidance can apply to multiple project types. Clarify vendor consumer
  versus library-author tasks in that runbook. Verify from an empty directory with no source-repo access,
  including template choice, prerequisites, setup, first result, analyser connection and teardown.
  **Observed today:** the live catalogue and headless `start/scaffold?template=fluxtion-spring-mongoose`
  return a 22-entry ZIP with README, but no dedicated RUNBOOK/authoring-docs/bootstrap files. Download
  availability does not establish delivery of the branch's authoring workflow or publication readiness.
  No app execution moves into the analyser; no template/API implementation is claimed by this intake.
- ◧ **Show the full template catalogue (owner accepted, 2026-09-20)** —
  [template-picker spec D-1](spec-template-from-analyser.md#c--decisions) now replaces the onboarding-only
  rule: list every catalogue entry and mark onboarding-tagged entries as **Recommended starting points**.
  No tags means all entries without recommendations, not a Mongoose-only fallback. Implemented on the
  journey branch: full list, declared build/regeneration requirements and absent/empty/populated bootstrap
  disclosure. Mixed/no-tag parser tests and a real Swing picker over all 14 producer-branch entries pass.
  All 14 scaffold-handler downloads now pass the real profile importer (including untagged entries).
  Interactive acquisition/discovery acceptance, independent review and producer-first deployment remain open.
  A guided walkthrough is a runbook procedure, not a separate runtime or a guarantee from the tag.
- ☐ **Topology callout placement (extension of feedback 14)** — participant reports six captions covering non-target
  nodes. Inspected the existing six-target screenshot; geometry currently scores lit cut-outs/other captions,
  not all visible nodes. Feed occupied node bounds to placement and specify crowded-viewport fallback;
  verify zoom/pan/resize and pixels. Same family as 14, but a topology/spotlight implementation scope.
- ☐ **Feedback 36 — named focus lifecycle over MCP** — save/recall exists; deletion has a UI but no MCP
  parameter, and rename is missing. Add explicit operations through existing topology scope with collision,
  persistence and dependent-reference semantics. Do not recommend editing an active profile: close can flush
  dirty memory over it. Empty focus must not ambiguously mean deletion.
- ◧ **Feedback 38/39 and recurring 6** — design/diagnostics recovery and explicit omissions (38) are
  implemented through `7eceff5`, with real-frame close/reopen and new-frame relaunch witnesses; actual OS-process
  restart remains unverified. Spotlight echo/paint after settled layout (39) now closes under D15
  with `DesignSpotlightFrameTest` and its viewport mutation. Compiler/starter owner: repeat EndOfDayReporter
  audit-scaffolding case/new-owned-node policy now shipped under BETA-B2 in starter 1.0.74, with
  `CallbackAuditTest` and browser callback controls. Absence of values is not absence of execution.
- ☐ **Feature request 40 (intake alias P1) — saved focus captions** — assess explicit ordered commentary associated with a focus,
  preserving transient spotlight defaults. Attribute text; bind structural claims to model/code identity where
  available and observational steps to original run/record or existing report references. Current report
  fingerprints are coarse, not exact identity. Keep unresolved targets visible and define rename/delete and
  partial-recall behaviour. This needs design review before changing the pinned never-saved spotlight rule;
  it is not a second evidence/report engine. Correctness and focus-management work take priority.
  **Participant clarification:** the gap is optional ordered captions on an already-persistent focus;
  caption-free focuses behave unchanged. The actual six-step example mixes structural explanation with
  four run-specific observations. Preserve written-against context and qualify mismatched/unknown runs;
  do not narrow the use case to structural-only captions or introduce another focus-save route.
  Latest participant write-up is preserved verbatim. Priority is not established across models: record
  frequency in independent tasks and test whether a returning/new reader can resume the explanation
  without retyping, while correctly identifying stale observations. Existing saved graphs/reports remain
  other durable shareable state; the proposal extends focuses rather than introducing persistence generally.
- ☐ **Staged feedback — authoring route and hosted audit boundary** — playground/compiler/docs owners:
  short entry point, discoverable existing headless download, CSV-driven example, server-owned audit
  listener, accurate feed/reset/completion instructions, and consistent auditing for new owned classes (6).
  A fresh client should follow generated docs without source bundling or invented timing. Existing classes
  must not be re-parented; trace-only evidence remains distinct from missing execution.
  **First measured witness, 2026-09-21:** both public acquisition attempts that reached the website probed
  guessed routes and scraped its JavaScript; the passing one used the catalogue JSON and the site's encoder, not
  the documented `template=analyser-bundle` shortcut ([release report](../handoff/report_release_journey_2026_09_21.md)).
  That is this item's "short entry point" ask, observed. New-owned-class auditing (6) was re-observed on public
  1.0.73 ([issue 3](https://github.com/telaminai/fluxtionauditlog-analyser/issues/3)).
  **Additional 2026-09-25 intake:** [edit-loop §G](spec-spring-authoring-edit-loop.md#g-make-the-downloaded-project-teach-the-supported-path)
  adds executable archive checks, accurate help, immutable matching contract, non-drifting hosting
  instructions, authoring patterns and a meaningful keyless starter test. Mid-cycle feedback 21–24
  adds mapper composition with executable JSONL examples, consistent configuration-specific replay
  guidance, build-time AOT versus loader compile/interpreter comparison, and versioned plugin docs.
  Item 19 now explicitly uses the already-declared hosted test harness. These are proposed, not verified fixes.
- ☐ **Staged feedback — next chart capabilities** — record-order x-axis (17) before same-record entity
  grouping (11), with record identity, filtering, selection, marker/note/export consistency and cardinality
  limits. The full-speed hosted fixture must be useful without pacing the application or logging a new
  key per entity. Formula documentation exists; improve discovery (15) rather than introduce another parser.
  **Recurrence:** feedback 12 in the [2026-09-25 edit-loop intake](spec-spring-authoring-edit-loop.md)
  reports pacing input to avoid overlapping timestamps. §H adds a same-timestamp regression; status unchanged.
- ☐ **Staged feedback — presentation and maintenance** — reserved annotation/legend space (14/21),
  explicit note anchoring/remapping (18), optional held tails (13), compiler formatting/output (7/8,
  overlapping F5/G13/F10), stable runtime control text (16), and documented nodeTypes scope (10).
  Cross-run note mapping must never silently equate unrelated record indices. Changeset targets (5)
  remain deferred pending a previous-text/revision contract. Full dispositions and acceptance are in the review.
- ☐ **Report integrity — first correctness slice** — 24: every requested section must render or carry
  an in-place reason and export warning. Cover resolved-but-unassembled topology, not only missing
  references. A full topology export must render the named saved focus without changing the user's
  current view. The series-section limitation (26b) also needs an honest export warning.
- ☐ **Reports as evidence, not automatically defects** — before optional chart polish: neutral finding
  language and an explicit persisted category at the flag write site (25); typed same-record text
  predicates (26a); real series-section assembly using existing computations (26b); bounded derived
  tables over selected records (26c); complete exported provenance and wrapped titles (27a).
  Preserve the narrative/evidence distinction, old saved findings, row provenance and explicit caps.
  **TA-3 prediction frozen before edits, 2026-09-21:** constructed confirmation flags will render
  Observation / Assessment in the table tooltip, topology callout, report panel and both PDF routes;
  existing fault findings retain their labels. The kind will survive explicit session save/restore only
  against the verified same log. Disabling neutral-label selection will fail the regression; mismatched
  log recovery must not restore flags. Before: analyser 10 open; upstream 8 open.
  **TA-3 result:** held. `FindingPresentationTest`, confirmation cases in `FindingReportTest` and
  `ReportRendererTest`, and `SessionRecoveryFrameTest.confirmationFlagSurvivesExplicitRecoveryOnlyAgainstTheSameLog`
  verify neutral labels, old defaults, invalid-category refusal and real project recovery. Removing
  confirmation label selection fails three tests. Restored headless and full recovery display suites pass.
  [Evidence](../handoff/report_tool_agreement_2026_09_21.md#ta-3--completed). D4 closed; analyser 9 open,
  upstream 8 open. This closes the category/labels slice only; the other requirements of this item stay open.
- ☐ **Additional desk-session intake (22/23/28)** — compiler owner: reproduce signal-stub propagation
  hazard before choosing a default (return-false everywhere is also wrong), document callback boolean
  values with actionable errors. Analyser: consider an explicit restore-last-session offer with identity
  and freshness checks; retain the project session boundary. These are participant-reported intake,
  not re-run compiler mutants or a witnessed project reset. Issue 27b requires the exact band/key/scope.
- ☐ **Validation-pack discovery experiment and result contract** — after evidence/report correctness:
  one project entry point over bootstrap docs for Fluxtion, the analyser, audit logs, Mongoose and
  Mongoose plugins, then task-specific runbooks, spec, feeds, oracle/checker, mutations and results;
  cold-test discovery without the transcript. Agent/harness executes; analyser consumes evidence. Review typed
  result intake with input/build/output identities, scope, completion, freshness and per-rule anchors;
  PASS/FAIL/UNKNOWN/NOT-RUN must remain distinct. No analyser command runner or new verb approved.
- ☐ **EOD reporting validation follow-up (participant assessment, 2026-09-20)** — unchanged desk outputs
  do not validate report arithmetic. Freeze predictions, derive report expectations independently from the
  feed, compare audit summaries and rendered output, and kill isolated mark/multiplier/threshold/grouping/
  order-count mutations. Cover both triggers and boundary/empty cases; keep desk non-regression separate.
  Project runbook/harness executes; analyser presents results. No new run claimed. [Assessment and limits](../handoff/review_staged_spring_feedback_2026_09_19.md#eighth-addendum--participant-system-assessment-and-eod-qualification).
- ☐ **Two-run comparison experiment** — specify alignment, normalization, event/value/occurrence scope
  and missing/extra/damaged input handling before product implementation. Reuse existing comparison
  parts only within their contracts. Fixing control-event identity alone is insufficient. Feed recovery
  needs explicit capture/mapping and prior state; a `toString` audit record cannot promise reproduction.
  Saved analyses are a starting point for later walkthroughs, distinct from M67; human question anchors
  and posture-suggested defaults need task evidence and must not reinterpret saved findings.

- ☐ **Dependency-provided classes stay foreign (29), P1** — starter/compiler: distinguish missing project
  source from dependency-provided types before generating shells. Missing/stale classpath information must
  not silently authorize shadowing. Test listed and referenced vendor beans without paid compilation.
- ☐ **Dependency identity and classpath lifecycle (30/33)** — producers record effective artifact byte
  identities and phase-specific classpath precedence; scripts refresh or verify dependency inputs after
  setup. Analyser consumes provenance. Hashes do not establish certification or link an unrelated log to a
  binary. Review this contract before implementation; dependency provenance P1, cached-path reliability P2.
- ☐ **Authoritative dispatch metadata (34), P1** — compiler/exporter supplies known concrete dispatch
  relationships and declared handler types with explicit semantics; analyser displays the facts and their
  limits. Never infer compiler dispatch by executing application classes or inventing edges from observations.
  **TA-9 before-case prediction (frozen):** with committed `desk-quote-supertype.graphml`, a constructed
  MarketPrice record logging priceBook leaves acmeQuoteFeed unknown, not off-path. Context, topology
  status/legend and coverage/report notes disclose the missing hierarchy. Explicit complete invocation
  traces still prove absence. Restoring off-path classification makes the fixture test fail.
  Counts before: analyser 7, upstream 8; D20 stays open pending producer metadata.
  **Before case completed:** prediction held; `DispatchHierarchyTest` uses the committed graph and
  constructed record, plus the complete-trace negative control. Restoring off-path makes its vendor
  assertion fail. Full suite and strict docs pass. Counts stay 7/8; producer-dependent union remains blocked.
- ☐ **Vendor authoring diagnostics and guidance (31/32/35), P2** — earlier constructibility diagnostics,
  stable instance naming and bootstrap discovery of the existing vendor guide. Test supported construction
  routes and multiple component instances; document state bridging separately from shared event interfaces.
  VI-1 published the supplier checklist for feedback 35 on 2026-09-21; starter bootstrap discovery
  remains pending. This does not close the diagnostics/naming work (31/32).
- ☐ **Bounded session diagnostic snapshot** — review explicit local export with immutable records/counters,
  capture boundary, model/version identity and request/response correlation. Preserve project boundaries;
  restore remains an explicit freshness-checked offer (28). Geometry still needs screenshots. This supports
  current correctness work without delaying it; no new MCP verb, automatic upload or application runner is
  approved by this intake. Test overflow, sink/export failure, stale completions and unchanged live state.

- **Reply-review intake (owner supplied, 2026-09-19):** both reply reviews accept the disposition;
  implementations remain READY WITH FOLLOW-UPS. Compiler review branches
  `review/spring-pass4-reply-G` (`4227a856a`) and `review/spring-pass4-reply-claude-g`
  (`c4085e31`, `fa4d6ab1`) have not been integrated by this analyser task. Record the F8 attribution
  correction: G pass 4 read the wording; F witnessed the path. **G15:** merge current compiler develop
  before the publication build and rerun gates. **G16:** rate F11 P3 when publication makes the split-version
  download installable. **G17:** G12 reaches reference fields whose name differs from the bean id,
  including the extended-XML download; pin the regression to that template. These are review intake,
  not fixes or independently reverified closures. Publication, witnessed preview and G14 remain open.

- ☑ **Fresh-session trial performed and independently assessed.** Predictions were frozen first; no inherited
  conversation or observer coaching. The session produced two real matching runs and a chart/report with existing
  analyser features. **Not a clean authoring pass:** it repaired a utility-generated `privatefinal` declaration.
  That upstream fix and playground G9 are now accepted by both pass-4 reviewers (`dbcfacc4`, `7a051224`):
  both upstream branches are READY WITH FOLLOW-UPS. The G review also confirms DX-02–05 and their proposed order.
  [Results, prediction scores, evidence and exact reproductions](../handoff/report_spring_authoring_acceptance_2026_09_19.md).
- ☐ **DX-02 — compare graph membership against the complete graph.** A declared framework sink was reported
  absent because authored coverage IDs were used as membership. Reproduced independently. Keep the correct
  coverage denominator; use all graph IDs for pairing/mismatch; retain real mismatch warnings across UI, actions
  and reports. First correctness priority arising from this trial, separate from M66/M67.
- ☐ **DX-03 — rolled-log + graph open must honour or refuse the full request.** Current early return silently
  drops graph/processor; observer reproduced it. Acceptance includes load completion and honest pairing/echo.
- ☐ **DX-04 — first topology record selection.** With loaded records and an unbound cursor, the manifest's
  `recordIndex` parameter does nothing. Observer reproduced; selecting via goto works. Bind or refuse clearly.
- ☐ **DX-05 — graph names and spotlight addressability.** Colon-named charts are created but cannot be lit by
  name. Observer reproduced. Resolve compatibility for saved names explicitly before changing the grammar.

This trial predates the M66 merge and does not test M66 or M67. It supports prioritizing existing-surface correctness
before adding new rendering primitives for this workflow. Browser preview and artifact publication remain
unverified. The local-fixture fresh-client trial is complete, with failures preserved rather than called a clean
pass. **G14 remains a release gate:** after artifact publication, repeat a fresh session from a real download
through `setup.sh`, without a pre-provisioned tool/classpath. Upstream review acceptance does not close that gate.

## Archived obligations restored to the live order — 2026-09-24

**Found by asking the systemic version of the M45.4 question**, which is the check that would have caught the
original loss: not *is M45.4 back*, but *what else left the live order while unfinished*. Round 4 searched both
trackers and found three clauses sitting inside entries marked DONE or COMPLETE with no live home. The historical
entries stay exactly as written — the record is not edited — and these lines are the live dispositions they lacked.
None is a dependency of M68.1.

- [OBL-1] ☐ **Background file load has no progress or cancel surface** — inside M7.3, under a heading reading
  *M7 DONE*, where background load is marked ◧ and progress-percent and cancel are deferred. The ordinary file-open
  path still uses background work with no file-load progress or cancel. Template-download cancellation is a
  different operation and does not cover it. Predates this tidy; it was never newly lost.
- [OBL-2] ☐ **Autoscale-Y rescans every point on a slider drag** — inside *Refinements round 11*, marked DONE,
  where the O(points) drag scan is explicitly recorded as *Not built*. Still true:
  `ChartPanel.setViewWindow` loops the series on each window change. It was deliberately low priority, which is
  not the same as delivered.
- [OBL-3] ☐ **First-key-seen iteration-order acceptance** — inside M55.2, marked COMPLETE, where the acceptance is
  explicitly *Still open* until a downstream construct exposes iteration order. Cross-repository verification debt
  with no closure evidence and no recorded transfer. Round 4 did not inspect the current implementation, so this
  says the tracker lacks the evidence, **not** that the behaviour is absent.

**Distinguish genuinely unfinished work from obsolete wording.** This is not a mandate to reopen every historical
partial label; it is a mandate to give each surviving obligation a live home or an explicit withdrawal.

## Shipped — archived

**Tidied 2026-09-24, after the M68 v2 review (rule 7).** Two fully-ticked blocks moved verbatim to
[`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-24*: the **Spring getting-started guide** round
(three ☑ items — the illustrated guided path, SG-1 released in starter 1.0.73, SG-2 shipped in playground 1.0.74)
and the **tool-agreement documentation capture refresh**. Their residual references — G14 and G19 — are recorded in
many other live places, which was checked before moving rather than assumed. **SG-2 was reconciled in the same
pass**: it was ☑ here and ☐ in the Spring-side work block, and the tick won on evidence.

**The rule this sweep now follows, and the reason it is written down.** *An item marked ◧ never leaves the live
tracker, whatever ☑ marks appear inside its text.* M45.4 was swept on 2026-09-03 because a ☑ on one clause made a
◧ item read as finished. Its consumption was never built, it fell out of the delivery order, and three weeks later
a held-out client was told a declared node was absent from a graph that declares it — the exact failure the
archived entry already named. M45.4 is restored above and is now delivered as M68.1.

**Tidied 2026-09-21, after 1.16.0 and 1.17.0 (rule 7).** Thirty-one ☑ items and two completed sections moved verbatim to
[`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-21*: the whole **M66 · Design render** milestone (shipped in
1.16.0; its spec moved with it to [`completed/spec-design-render.md`](completed/spec-design-render.md)); the **Release
execution** round; the nine tool-agreement items released in 1.17.0 (TA-1/2/3/4/5a/6/7/8/U — TA-5b/5c/9/B stay); BETA-7; the
two ticked Spring-authoring documentation items (the page review stays open); and seventeen ticked intake, review and
implementation items from the Spring-authoring acceptance section. The delivery order's 2026-09-17 and 2026-09-19 refreshes
are archived there too, under *Delivery-order history*. **Left ticked on purpose:** the fresh-session trial record, because
DX-02–DX-05 hang off it; and the earlier standing exceptions listed in the 2026-09-19 note below.

**Tidied 2026-09-19, after 1.15.0 (rule 7).** Five ☑ items moved verbatim to
[`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-19*: M46.5/.6/.7/.10 (the analyser-side closure of the
authoring-toolchain programme, shipped in 1.14.0 and held on the built jar by `tools/verify-m46-agent-api.py`) and M48.7
(the canvas handoff, 1.14.0), and — reconciled the same day on the owner's word — M47.2/.3/.4, which had shipped as M19.5 on
2026-08-30 while the M47 section still read as a proposal. The 2026-09-18 tidy had already archived M64.10–.12, M65.6 and M46.11 (▸ *Tidy 2026-09-18*).
Left ticked on purpose, because they are the trail of something still open: UC-LANDED and the UC5 idiom-audit lines
(context for idioms 2a/2b), N1 inside the golden-fixture item, M50's per-item SHIPPED lines (cross-repo, reconciled against
the release tags), and the M19.1 sub-gates. **Landed on main since 1.15.0 and NOT reviewed:** the Spring-authoring guide
pages (`6f8568e8`) — see the section above and *Suggested delivery order* item 1. **M66 · Design render** was merged on main under this note and **shipped in 1.16.0**; the milestone and its spec
are archived ▸ *Tidy 2026-09-21*.

**Tidied 2026-09-17, after 1.14.0/1.14.1 (rule 7).** Twenty ☑ items and one ⊘ moved verbatim to
[`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-17*: M64.1–.9 (M64.10/.11 stay), M65.0–.4 with its
implementation and review record (M65.5/.6 stay), M44.3/.3a/.3b (the open slices stay), and delivery-order items 1–3
and 6. Six handoff files moved to `docs/handoff/completed/` (the M65 implementation handoff, its two impl reviews and
the response; the 2026-09-17 ledger review and its brief) and the two reviewed 2026-09-17 ledger entries to
`completed/unreviewed-changes-2026-09.md`. The person-at-the-screen checks stay open; the playground re-vendor was done the same day
(▸ *Suggested delivery order*).

**Tidied 2026-09-17.** The finish-first round closed READY on its third independent pass (`d78a0144`; M44.3 + M44.3a, N1 and the clamp fixtures, the skill rewording, the note-rule fix). The eleven reviewed ledger entries of 2026-09-16/17 and their seventeen review, response and probe files moved to `docs/handoff/completed/` (ledger: `completed/unreviewed-changes-2026-09.md`). `main` carries the 1.13.2 candidate (CHANGELOG ▸ Unreleased, eight entries). Open decisions: M44.3b (close/reset during a pending open), M64 (spotlight, spec'd).

**Tidied 2026-09-16 (rule 7).** Eight ☑ items moved verbatim to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-16*: the cross-transport schema contract, five M57 items (M57.1/.4 stay), M50.13 and its superseded original. Three closed handoffs moved to `docs/handoff/completed/` (round-11 response, the `perf/w10` branch review, the M52 hot-path report) and the three reviewed 2026-09-03/-15 ledger entries to `completed/unreviewed-changes-2026-09.md`. The 1.13.1 review cycle closed the same day: **READY WITH FOLLOW-UPS after four independent passes** (ledger + `docs/handoff/completed/review_analyser_1.13.1_*`), follow-ups done, **M44.3a** the one deferred item.

**Tidied 2026-08-30 (rule 7).** Fourteen shipped M19 slices, plus the closed M18 and withdrawn M41, moved
to [`completed/tracker.md`](completed/tracker.md) verbatim. This file went 949 → ~720 lines. Relative links
in the moved blocks were rewritten for their new depth — `SpecLinksResolveTest` caught four that a move
silently breaks, which is what it is for.

**The rule, stated once so two claims cannot both be made** (review F3): rule 7 says a finished item is
ticked **☑ here**, and only a fully-shipped milestone or round MOVES. So this file legitimately holds ☑
items — they are work completed since the last tidy, and they leave at the next one. It is **not** true
that this file contains open work only, and an earlier version of this note said so while ticking two
items in the same commit.

Fully-delivered milestones and refinement rounds live in **[completed/tracker.md](completed/tracker.md)**:
M0 setup · M1 parser & index · M2 table + detail · M3 filters & summary · M4 source · M5 LLM ·
M6 graphing · M7 large-file mode · M8 polish/help · M9 UX pass · M10 assistant actions ·
M13.1–13.4 MCP bridge · M14 graph artifacts · M15 settings export/import · M16 release &
distribution · **M17 docs site** · M20 project profiles · M21 topology + step-through (core +
intra-record cursor) · M22 usability (36 of 41) · M23 explaining-what-you-found + charts ·
M24 coverage · M25 drift fixes · M26 agent-efficiency verbs · M27 focus as a filter context +
named focuses · M28 conditionals + rolling windows + guides/bands · M29 external series (core) ·
M30 rolled log sets · M31 log-source plugins (core) · M32 marker series · M33 investigation reports
(core + .7 report table sources) · M34.0–.3 source adapters (SPI, degradation, format spec + conformance suite) · M35 log +
graph lifecycle (all eleven) + §E provenance · **M36 start page (.1–.5) · M37 Project panel · M38 portable
context (.1–.7) · M40 audit readiness (.1/.2a/.2b/.3) · M42 Connect an AI client · M43 the AI menu (+ M38.8) · M52 shipped portion + M60–M63 bench · M48.1–.4/.11** _(archived 2026-09-15)_ · refinement rounds
2–13 · assistant-vocabulary follow-ups. _(Polish H1, 2026-08-25: verified every section left in this
file has open items; the archaeology the 2026-08-17 brief asked for had been done by the per-merge
tidies.)_

---

## Hardening — test-only, ongoing (no user-visible change)

**Related production fix, independently reviewed and accepted 2026-09-15:** the round-11 response records
parsed key spans and shared evidence formatting. Review accepted R11-1/2/3 as closed classes and required one
correction (F1, legacy values were being quoted in the evidence views), fixed in the same commit. See
[response](../handoff/completed/handoff_analyser_round11_response_2026-09-15.txt) and the ledger.

- ➜ **Cross-transport schema contract — ☑ archived 2026-09-16** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-16*.
- ◧ **Formula golden fixtures** — a hand-derived expected-series corpus for the Expr engine, grown
  **without code** (`graph/FormulaGoldenTest` + `src/test/resources/formula-golden/*.golden`). First
  tranche: LOCF/STRICT/two-arg-if/NaN + rolling `mean` fill-before-speak / `lag` / `delta`. The rule
  (derive from intended semantics, **never snapshot output**) and the TODO taxonomy — `rate()`
  span-normalisation first, the c3094ea bug class — are in
  **[spec-formula-golden-fixtures.md](spec-formula-golden-fixtures.md)**.
  Review endorsed (all 7 re-derived correct — `completed/review_formula_golden_fixtures.txt`). **G1 and G2
  closed** (58879d7): an empty `EXPECT` now requires a declared `expectEmpty: true`, and every
  fixture runs through BOTH engine arms — `SeriesExtractor.extractExpr` *and* `SeriesScan` — with
  agreement asserted. Closing G2 immediately caught the `series` verb answering from stale carries
  (the 1.5.0 headline fix): the corpus's first scalp, on the day the cross-path check landed.
  ☑ **N1 closed 2026-09-16** (a doubled metadata line is rejected; pinned by `aDoubledMetadataLineIsRejected`)
  and the clamp fixtures added: `09-clamp-is-elementwise-min-max` and
  `10-min-of-two-constants-is-a-value-not-a-window` pin the M28 guarantee (min/max elementwise; `rollingMin`/
  `rollingMax` are the windows). The taxonomy TODO rows in the spec remain the next tranche.

---

### M50 · working directories and review briefs — for the reviewing LLM

**START AT** [`docs/handoff/report_m50_INDEX.txt`](../handoff/report_m50_INDEX.txt) — four briefs, one
per repo, each stating what was verified, what was **not**, and what to attack.

**Work is in git worktrees, not in the primary checkouts.** The primary checkouts were left on their
existing branches and are undisturbed (`fluxtion-core` on `feature/java_8_compatability`,
`fluxtion-compiler` on `experiment/determination-placement`).

| worktree | branch | base | brief |
|---|---|---|---|
| `~/IdeaProjects/telamin/worktrees/core-w2w3w8` | `perf/w2-w3-w8-runtime-internals` | `origin/main` @ `4b38aeb` | [core](../handoff/report_m50_core_w2w3w8.txt) |
| `~/IdeaProjects/telamin/worktrees/compiler-w12` | `perf/w12-auditor-switch` | `origin/main` @ `9a16035` | [compiler](../handoff/report_m50_compiler_w12.txt) |
| `~/IdeaProjects/telamin/worktrees/analyser-w10` | `perf/w10-conformance-bench` | **local** `main` @ `c0851b5` | [analyser](../handoff/report_m50_analyser_w9w10.txt) |
| `~/IdeaProjects/telamin/worktrees/mavenplugin-w14` | `spec/w14-manifest-optimisation-metadata` | `origin/main` @ `d635950` | [plugin](../handoff/report_m50_mavenplugin_w14.txt) |

**Gates, run in full:** core `fluxtion-runtime` **98/98** · compiler `fluxtion-generator-core` **21/21**,
`fluxtion-builder` **230/230**, `fluxtion-integration-tests` **3518/3520** (the 2 are
`RuntimeMetaBoundaryGateTest`, **environmental** — it finds a sibling repo at `~/IdeaProjects/fluxtion`
but no `fluxtion-runtime` in a layout it knows, because the runtime is at
`~/IdeaProjects/telamin/fluxtion/fluxtion-core/`) · analyser bench **11/11**.

**The two pre-split goldens were updated deliberately** (W12 changes generated source on purpose). The
`.java.txt` goldens moved; `.behaviour.txt` and `.dto.txt` are **byte-identical**, which is the evidence
that the behaviour did not. An earlier report of "2 baseline failures" was wrong — there were 4, two of
them these goldens, caused by W12.

**Version lines, verified — do not assume:** `fluxtion-core` `origin/main` is **1.0.15-SNAPSHOT**, the
line producing the `fluxtion-runtime` **1.0.14** that round 58 measured. The primary checkout's
`feature/java_8_compatability` is **0.9.33-SNAPSHOT**, a different line. `fluxtion-compiler` pins
`fluxtion.base.version=1.0.14`.

**The analyser's local `main` is 33 commits AHEAD of `origin/main` and unpushed** — round 58, the
performance spec and this tracker section are all in that history. A reviewer cloning from GitHub sees
none of it; compare the analyser branch against **local** `main`.

**`git fetch` was NOT run** for core or compiler; bases are the local remote-tracking refs as of
2026-09-01, recorded by commit so the work can be rebased. It **was** run for the maven plugin, and
doing so changed the answer to which repo that is.

**Item → worktree:** W2/W3 → `core-w2w3w8` (W8 deferred, reason in the brief) · W12 → `compiler-w12` ·
W9/W10 → `analyser-w10` · W14 → `mavenplugin-w14` (spec only).
Land W10 first: until the harness exists, no performance claim on the other branches is reproducible —
and note the brief's admission that **the bench has never been run against a real processor**.

---

## M52 · Binary audit encoding + the reader that makes it usable — ◑ PART SHIPPED 2026-09-09, cross-repo

Specs: **[spec-binary-audit-encoding.md](spec-binary-audit-encoding.md)** (core + mongoose) and
**[spec-binary-audit-reader.md](spec-binary-audit-reader.md)** (a new `fluxtion-audit-reader` module in
the compiler repo). Evidence: `docs/experience/runs/round-63/NOTES.md` §6–§8, §33–§36.

The measurement that motivated this said **94% of JIT cost and 98% of native was building a text audit
record**. That is fixed, and then some: the audited path now runs at **42.6 ns JIT / 41.1 native,
23.5 M events/sec** on a 30-node graph where *every* node logs 11.75 values per event, zero allocation.
The two toolchains are level, which they had never been.

Shipped: **sixteen items archived 2026-09-15** to [`completed/tracker.md`](completed/tracker.md) per rule 7 — the `logTime` fix, M52.2, M52.3 (record half), the format specification and corpus, the reader, the hot-path fix, M52.7 docs, binary tracing, guards-as-semantics, the capability-flag refusal, the C++ annotation index, the windowing fix, the C++ DSL emitter + audit oracle, DSL performance measured, the control/test indexes, and the `RuntimeMetaBoundaryGateTest` guard.

### M57 · The audited path — ◑ measured and largely fixed 2026-09-10
- ➜ **Five ☑ items (the audit-cost claim, re-entrant waves, tick→ns by multiply, M57.2, M57.3) archived 2026-09-16** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-16*; M57.1 and M57.4 stay open here.

- ☐ **M57.1 the last ~8 ns is a clock read**, in all three arms. `CachedClockStrategy` exists for the
  several-graphs-per-turn case; nothing else is available without changing what a timestamp means.
- ☐ **M57.4 `fluxtion-generator-http` shades the runtime, and the shaded copy is stale.** Found by
  `NativeImageSmokeTest` (M57.3, archived): that jar carries its own `com.telamin.fluxtion.runtime.*`, including a `Clock` predating
  `shareReading`. It is a DIRECT dependency of `fluxtion-integration-tests` while the runtime arrives
  transitively, so the shaded copy won the classpath and generated source compiled against a stale
  runtime — failing on a method that exists.
  - Symptom fixed: `fluxtion-runtime` is now declared first in that module.
  - **The question left open is whether that jar should shade the runtime at all.** Anything depending
    on both gets whichever the classpath happens to order first, and the failure mode is a compile
    error against a method that exists — or worse, silently running an old implementation. *Owner call.*
  - **Re-checked 2026-09-10** and the shaded copy is CURRENT: its `Clock`, `EventLogger` and
    `LogRecord` are byte-identical to the `core-baseline` build. That is not the same as the problem
    being gone — it is identical only because core was installed before http was built, and the
    staleness returns silently the moment that order is skipped. The owner call stands; what changed
    is that "is it stale today" is now a question with a mechanical answer rather than a guess.

- ➜ **M60, M61, M62, M63 (the bench milestones) — SHIPPED, ARCHIVED 2026-09-15** to [`completed/tracker.md`](completed/tracker.md) per rule 7 (moved verbatim). M57 stays here: M57.1 and M57.4 are open.

### M52 · still open

Open, in dependency order:

- ➜ **M52.1, M52.3 (the sink half) and M52.4 (module withdrawn) — SHIPPED, ARCHIVED 2026-09-15** to [`completed/tracker.md`](completed/tracker.md) per rule 7 (moved verbatim).
- ☐ **M52.6** mongoose: `ValueOut.text(cs)` → `bytes(...)` (**2.20×** measured, byte-identical queue
  file) and drop the per-record `Instant.now()` (3% of time, **100% of the allocation**).


**M52.6 is independent of everything else** and is the cheapest win on the list.

**Owner decision needed** (spec-binary-audit-reader §11): does the analyser's binary reader and the CLI
share a cursor/dictionary library — a fourth artifact nobody has budgeted for — or does each carry its
own decoder? Now sharper than when it was written, because the CLI's decoder exists and is in
`fluxtion-runtime`, so "share it" today means "the analyser depends on the runtime jar".

## M51 · The native-ready starter template — ☐ SPEC DRAFTED, cross-repo

Spec: **[spec-native-ready-template.md](spec-native-ready-template.md)**. Raised by the owner
2026-09-07: the playground template the analyser downloads should be *the best one for native use*.

Read live: the catalogue entry named **"Fluxtion AOT (native-ready)"** carries `compileMode: "aot"` and
`auditLogging: true` and **none of the eight settings that decide whether a native image reaches
1.6 ns/event**. A user who picks it, builds native and measures gets 5.5–29 ns — 3.5× to 18× off — and
nothing tells them. The name is a promise the artifact does not keep.

The template's real job is not to hand over a fast binary. Round 60 established that the **PGO profile
decides the mode** and the compiler reproduces it (four rebuilds from a landing profile: 1.60/1.66/1.68/1.67;
three from a missing one: 5.71/5.63/5.61) — what varies is *collection*. So the template ships **the loop
that finds a good profile and the place to keep it**, plus a benchmark that fails when a build misses,
because a missing build is 3.5× slower and invisible.

**[M51.1] ☐ UP-PG-05 — the honest catalogue field** · _`native: "ready"|"capable"|"none"` on each entry,
and relabel `fluxtion-aot.starter.json`, which is `capable` today. One field and one name; can land alone._

**[M51.2] ☐ UP-PG-04 — the `native` block in the starter schema** · _generator emits the two Maven
profiles (`native-maven-plugin` `compile-no-fork`), the shaped builder, void-trigger nodes, the loop-shaped
`Main`, `tools/collect-pgo.sh`, `src/pgo/` and its staleness README, and `Bench.java`. Absent block ⇒
today's behaviour, so no existing template changes shape._

**[M51.3] ☐ `template-bench.py --native`** · _E1–E5 static and run by default; E6–E8 need a GraalVM and are
opt-in. **E8 gates on the measurement**, never on the profile merely being present._

**Decided in the spec, not deferred:** the default shape stays **`audited`** (`performanceProfile(AUDITED)`
+ `addAuditedEventLog(INFO)`, ~5.2 ns, keeps the log without the 208 bytes/event) with `fastest`
(`LOWEST_LATENCY`, ~1.6 ns, no audit log) opt-in. A starter that emits nothing to analyse would fail the
pathway the catalogue exists to serve.

---

## M50 · Compiler & runtime optimisation — ◧ PERFORMANCE SPINE SHIPPED (runtime 1.0.15, compiler 1.0.67); determinism spine not started

Spec: **[spec-generated-dispatch-performance.md](spec-generated-dispatch-performance.md)** — Part IV §19
is the single work list. Evidence: **[round-58](../experience/runs/round-58/NOTES.md)**, ~700 measured
runs across 9 runtimes, disassembly, every wrong answer preserved. Cross-repo: **UP-FLX-49**.

All items **additive**; W4/W5/W7 opt-in, defaulting to current behaviour.

**Reconciled against the release tags 2026-09-16:** W1, W2, W3, W4, W12 and W15 are in `fluxtion-runtime` 1.0.15 and
`fluxtion-compiler` 1.0.67 (code carries `M50/W…` markers; compiler 1.0.68, cut 2026-09-16, adds no M50 item). The
spec's header block records the same table. Left: W5–W7, W11, W13, W14 (never started), W8 (deferred), W9 (filed
upstream, UP-FLX-50), W10 (◧). This section had said "branch not started" since 2026-09-06.

**[M50.1] ☑ SHIPPED compiler 1.0.67 · W12 — auditor-name switch, stop reflecting** · _`getNodeById`/`getAuditorById`/`newInstance`
reflection-free; the `reflect-config.json` round 58 needed is no longer required; graph introspection
works under native-image without user config._

**[M50.2] ☑ SHIPPED runtime 1.0.15 + compiler 1.0.67 · W1 — guarded callback drain** · _−18% native, −2% JIT; no flag, no semantic change; audit
record stream unchanged._

**[M50.3] ◧ W2/W3 SHIPPED runtime 1.0.15; W8 deferred (third-order, per the brief) · W2/W3/W8 — runtime internals** · _`ArrayDeque` + dropped empty-path store; `BooleanSupplier`
removes per-callback boxing; concrete `ClockStrategy`. No API change._

**[M50.4] ☑ SHIPPED compiler 1.0.67 + runtime 1.0.15 · W4 — `noReentrancy` flag** · _−26% native; build fails naming the offending node when a
re-entrant use is detected; runtime guard throws; default off._

**[M50.5] ☐ W5 — ambient-read scan + service boundary check** · _build fails on wall clock, randomness,
IO or mutable static reachable from a trigger, and on un-capturable service boundaries. Works on vendor
bytecode._

**[M50.6] ☐ W11 — generate service registration dispatch** · _no runtime reflection, no native-image JSON
for registration; notification order declared and stable rather than `getDeclaredMethods()` order._

**[M50.7] ☐ W13a/b/c — generated service auditors** · _exported invocations recorded in event-stream
position; consumed-service **returns** captured and replayed; build fails naming non-recordable
signatures._

**[M50.8] ☐ W6 — compiler-derived replay capture set + determinism report** · _ablation pair passes:
removing an output-reaching capture diverges, removing a non-reaching one does not._

**[M50.9] ☐ W7 — static service binding** · _**gated**: measure exported service-call cost and image-heap
contribution first. Necessary but not sufficient for replay — leaves return-value non-determinism._

**[M50.10] ◧ W9/W10 — docs + conformance bench** · _both on `perf/w10-conformance-bench`; W9 filed as UP-FLX-50_ · · _the performance configuration documented as a coherent
choice; `tools/bench` harness fixes compilation shape, interleaves arms in one binary, asserts output
equivalence before timing, and fails on a suspiciously clean zero._

**[M50.13] ☑ DECIDED and DONE 2026-09-07 — archived 2026-09-16** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-16* (with the superseded original question). `LOWEST_LATENCY` also sets `setSupportBufferAndTrigger(false)` and `setSupportSubscriptions(false)`; measured worth: nothing — kept because the generated code is smaller.

**[M50.14] ☐ The end of source-level tuning, and what follows from it** _(2026-09-07)_ · _Four shapes of
one graph, each a real build with identical output: shipped, W15, post-W11, and guards-removed. **On a
landed native build the generated arm sits 0.12–0.14 ns above hand-rolled and nothing moves it** — not
auditor calls, not the service registry, not the guards. With an accurate profile and the directive the
processor is scalar-replaced whole and there is nothing left for a source change to remove. **Every
remaining M50 item should therefore be justified by correctness, determinism or generated-code clarity,
not by a promised nanosecond.** W11's own entry has already been rewritten on that basis._

**[M50.12] ☑ SHIPPED runtime 1.0.15 + compiler 1.0.67 · W15 — an auditor can decline the event path** · implemented 2026-09-07 ·
_Owner's design: **"add another default method … default is Boolean return true. NodeNameAuditor
overrides and only returns false. The generated code then is even more optimal."** `Auditor` gains
`default boolean auditEventReceipt() { return true; }`; the generator emits `eventReceived` and
`processingComplete` call sites only for auditors that return true, exactly as `auditInvocations()`
already gates `nodeInvoked`. **The two defaults point opposite ways on purpose** — one opts in, one
opts out — because each preserves the behaviour an auditor had before its flag existed._

_**Exactly one runtime auditor changes.** `Clock` implements `eventReceived`, `EventLogManager`
implements both; each keeps the default and its call sites. `NodeNameAuditor` implements neither —
it works in `nodeRegistered` and inherits pure no-ops — so it declines. Measured on the kit's
generated processor: four `auditEvent(typedEvent);` call sites gone and three bodies emptied, with
the `nodeNameLookup` field retained so `getNodeById` and `lookupInstanceName` still work. **That
separation is the point:** keeping node-name lookup used to force the auditor onto the event path._

_**Honest about the size of it: there is no measurable throughput in it, on either runtime.** JIT
5.1096 → 5.0976, inside noise. Native + PGO, a landed build: 1.6867, inside the 1.54–1.68 spread of
the shipped variant's own landed builds, with the hand-rolled control at 1.5645 in the same build so
the machine state was comparable. Round 59 had already measured that empty auditor calls are free at
runtime. **The case is not throughput** — it is that the calls are gone from the generated source
rather than left for a compiler to remove, and that wanting node-name lookup no longer puts an auditor
on the event path._

_Suite: compiler 3520 run, 2 failures — the two pre-existing `RuntimeMetaBoundaryGateTest` environment
failures, down from 4. **Both `.behaviour.txt` goldens byte-identical**, which is the evidence that
matters: source shape changed, behaviour did not. Core: 6 new unit tests green._

_Branches: core `perf/w4-baseline-config` (Auditor, NodeNameAuditor, SourceField, Field), compiler
`perf/w1-w4-baseline-shape` (AuditorDto, DTO builder, SimpleEventProcessorModel, JavaSourceGenerator).
Two `.dto.txt` goldens updated after verifying byte-identity apart from the new field._

_**`ServiceRegistryNode` is NOT opted out, and should not be** — owner, 2026-09-07: *"eventually when
service registration becomes statically generated in the event processor the service registry will not
be an auditor."* Confirmed against the source: it implements `Auditor` solely because `nodeRegistered`
is its hook for the reflection-heavy `@ServiceRegistered` scan. **W11 removes the reason**, so it
leaves the auditor set entirely, taking its 6 allocated objects with it. **Measured, that buys
nothing** — the post-W11 shape emulated on the kit reads 6.4032 against 6.4776 unprofiled (where the
cliff lives), 1.6900 landed, and an identical image size, because §14.1 already established that
removing any ONE of the seven framework fields saves nothing. **W11's case is reflection removal,
native-image config removal and determinism, not throughput.** W15 is the interim
measure for `NodeNameAuditor`, which has no such exit. Spec §16.1a, which also names the one job W11
does not yet cover: `nodeRegistered` also pushes `DataFlowContextListener.currentContext(...)`, and
that needs a generated home before the hook can go._

_**The hazard is documented on the flag** — a subclass overriding `eventReceived` while inheriting a
`false` loses its callbacks silently, so overriding a callback means overriding the flag._

**[M50.11] ☐ W14 — manifest optimisation metadata** · SPEC COMPLETE 2026-09-06 ·
_[spec-manifest-optimisation-metadata.md](spec-manifest-optimisation-metadata.md). The facts W4/W5/W11/W13c
would otherwise rediscover by scanning vendor bytecode are computed once by the component's own build and
published in its manifest. **R2 — absence is not a claim:** every attribute is three-valued and unknown
POISONS the graph-level fold, so a build cannot become permissive by adding an unanalysed dependency.
Manifest carries short verdicts (72-byte wrap makes it a poor list carrier); a sidecar carries the evidence.
Normative derivation rule per attribute, a verification algorithm, and a 12-fixture conformance suite of
which `unresolvable-call`, `manifest-lies` and `no-metadata` are the three that matter._

_**Reading the annotation source reversed both of the first draft's answers.** `@ExportService` is
`@Target(TYPE_USE)` — it annotates `implements @ExportService Foo`, not the interface — so a `deterministic`
attribute there would be asserted by each implementor, the wrong binding point twice over. **One new
annotation type is required, `@ServiceContract` on the interface**, defaulting to `deterministic=false` so
silence is pessimism. Conversely the proposed `ambient` attribute on `@OnTrigger` is **withdrawn**: the
framework `Clock` is deterministic under replay and identifiable by receiver type, so approved-vs-unapproved
is derivable and an attribute would be a second authority. Net: one new annotation type, zero new attributes
on existing ones — the opposite of the first answer on both counts._

_**W14a0 — the `fluxtion:catalogue` goal does not exist.** The plugin's three mojos all generate a
processor; it writes no manifest entries at all. W14 and [spec-component-catalogue.md](spec-component-catalogue.md)
therefore share one piece of new machinery and must agree on it (spec §2, §10.1). Analysis belongs in
`fluxtion-builder` so the same code serves the producing build and the consumer's verification pass; the mojo
is an adapter. Worktree `~/IdeaProjects/telamin/worktrees/mavenplugin-w14`, branch
`spec/w14-manifest-optimisation-metadata`, on `origin/main` @ `d635950` — repo CONFIRMED
`telaminai/dataflow-mavenplugin`, now `com.telamin.fluxtion:fluxtion-maven-plugin:1.3.1-SNAPSHOT`.
Sequence AFTER W4/W5/W11/W13: this is the optimisation of the optimisation, not a prerequisite._

**Blocking question before branching:** does a generated service auditor move
`fluxtion.sourceFingerprint`? If yes, W13 is a graph change, fails gate 11.5, and needs its own release.

---
## M64 · Spotlight — the tutor points at the thing on screen — ◧ .1–.12 SHIPPED (.10–.12 in 1.15.0, 2026-09-18; archived); .13 open
_Report: `docs/handoff/completed/report_m64_spotlight.txt`. **One of the spec's assumptions was wrong and is corrected in it:**
the `screenshot` verb paints the content pane, which the glass pane is not part of, so the tutor's own verification
shot showed NO spotlight (the display test measured `696 → 696` outside the cut-out). The verb now composites a live
spotlight. It is the **fifteenth verb** — nothing else points (`goto`/`topology` select, `screenshot` records);
it was the sixteenth for a day, until M48.7's `handoff` was folded into `open` (Decisions). Held on the built jar by `tools/verify-m64-spotlight.py` (47 checks: every
family, fresh start, a filtered-out row revealed, each view-changing verb). **Not done:** the held-out re-run of the
guided-start prompt by a context-free client — it needs a person and a fresh client._

Spec: **[spec-spotlight.md](spec-spotlight.md)**. Owner question 2026-09-16: a callout that points at the item the
guided-start runbook wants to highlight. Decisions: glass-pane overlay first, a separate window only when a beat
needs to point outside the frame (D-SP1); a fixed, small target vocabulary grown per beat (D-SP2); pure target
resolution, dumb overlay that clears on any view-changing verb (D-SP3); transient by construction, in `context`
only while showing (D-SP4); one line per beat in the skill, no other verb changes (D-SP5). About a day plus half
for the display test and skill edit.

- [M64.13] ☐ **Menu-target follow-ups from the M64.11 review** (F4 a menu already open by hand is heavyweight and refused
  with a false reason — close, request lightweight, reopen; F6 the two FlatLaf client properties stay on a lit popup —
  restore them when it closes; F7 match a typed `...` to the item's `…`; and keep a display case for Escape over a lit
  menu, which the reviewer witnessed with real focus). Non-blocking; `docs/handoff/completed/review_m64_10_11.txt`.

## M65 · Follow refreshes open graphs — ◧ SHIPPED in 1.14.0 (archived); .5 open (.6 checked, archived)

Spec: **[spec-follow-refreshes-graphs.md](spec-follow-refreshes-graphs.md)**; review
[review_spec_m65_2026-09-17.md](../handoff/completed/review_spec_m65_2026-09-17.md) (CONDITIONAL — diagnosis and fix accepted,
C1/C2 required; folded in, plus C3 from its "did not check" list); pass 2
[review_spec_m65_pass2_2026-09-17.md](../handoff/completed/review_spec_m65_pass2_2026-09-17.md) (CONDITIONAL — every new code
claim verified; C4 the slider echo already re-windows an unpinned chart on each growing tick, so D-F7's hold needs a
`lastFrom`/`lastTo` guard in `onFilterChanged`; C5 D-F7's "width unchanged" slide contradicts acceptance 1's "window
extended" — one extend/slide/hold rule; F1 `reason` merges DEFINITION-wins across debounce and `dirty`). Owner question 2026-09-17: *"what is the lowest
overhead way of forcing the graph redraw? should we add something to the plot verb?"* Observed with the
audit-analyser-bundle: follow appends reach the table, slider, status bar and `series` verb, and a graph created
afterwards — but a pre-existing graph keeps its cached points through zoom, Fit, a time-range change and an
identical `graph` re-send, and recomputes only on a *changed* definition. Cause: `pollFollow` hand-notifies six
consumers and `graphTabs` is not one; the graph's incidental filter echo is classified "time only" and served from
cache; `addKeys` dedups, `setMarkers`/`setBands` do not. Decisions: the store is publishable while it grows —
`file` swapped first + volatile, and a read view capturing size+file under the index lock, because the index arrays
are read unsynchronised and grown by reallocation (D-F0); the follow poll tells the graphs through the existing
structural debounce (D-F1); pinned graphs re-extract but keep their window (D-F2); slider/zoom/Fit stay cache-only
per M6 (D-F3); `graph` re-sends are idempotent, `refresh: true` is the one forced re-extract, echo says
`refreshed: "scheduled"` (D-F4); full re-extract first, incremental only on measured need — noting the poll already
re-reads and re-frames the whole file per tick (D-F5); one extraction in flight, dirty flag (D-F6); an unpinned
view moves only to reveal a point that would otherwise be hidden — hold if already visible, else extend if it covered the whole range, else slide if pressed to the live edge, else hold; a definition change
still resets, and coalesced requests merge DEFINITION-wins (D-F7); the slider echo is inert for an unchanged window
via `lastFrom`/`lastTo` in `onFilterChanged`, so the hook is the only mover of an unpinned view on a data tick
(D-F8). Pass 3 [review_spec_m65_pass3_2026-09-17.md](../handoff/completed/review_spec_m65_pass3_2026-09-17.md):
READY WITH FOLLOW-UPS — F1 (required, implementer applies) the read view reads `size` + arrays under the index lock and
`file` *after* it, and serves `record(row)` itself; F2 empty-before lands as reset; F3 D-F8 also silences identical
programmatic ranges (say so); F4 the seam is `core.Background.run`'s three-argument shape. **Known stale under follow, out of scope here: reports and coverage.** About two days.

- [M65.5] ☐ **D-F5 measurement**: extraction wall time per follow tick on the demo log and a ~100k-record log, CI
  machine class; trigger for M65.4 is >~50 ms per extraction or D-F6's `dirty` set in steady state (impl review F5).

## M67 · The extension tour — extend a running application with a jar you already built — ☐ SPEC'D 2026-09-19 (owner-directed); **UNBLOCKED 2026-09-21 — both gates met: the starter tool released in 1.0.73 and M66 shipped in 1.16.0** (owner: "we will finish spring authoring and xml rendering for a solid base to build from")

Spec: **[spec-extension-tour.md](spec-extension-tour.md)**. Owner, 2026-09-19: *"as soon as developers realise they can
extend an application with jars they have built before, use LLM generated spring XML and validate with audit logs …
it is like FP but for whole application construction. Now we have spotlight working we can literally step a developer
through an interactive demo. We would need a basic catalogue of 'vendor' jars that is like the playground libs."*
The claim: the compiler is the boundary, the audit log is the proof, the LLM's working set is XML + intent +
diagnostics + log and never the vendor's source. The tour performs that claim on the developer's own screen, six
beats in the guided-start form (point, one sentence, the screenshot that proves it), driven by their own assistant.
Decisions: a public, versioned jar catalogue on the playground-libs pattern, read by the LLM and the pom, **no
analyser surface** in the first four slices (D-X1); the tour runs in a downloaded template project because two beats
need a build (D-X2); no new verb, one new skill `extend-with-a-jar` in the spring tier (D-X4); evidence is a verify
script + a held-out record + a generated docs page, never a screencast (D-X5). The declaration beat lights the bean
using M66, now on main; the refusal beat lights the bean from the diagnostic only once the compiler's B0 check names it
(Spring-branch review G7). **Owner decisions, 2026-09-19:** a new `fluxtion-vendor-jars`
repository (D-X7); a dedicated *Extend an application* template entry (D-X8); limit check + notifier sink + feed
adapter, one of them a jar the owner built before (D-X9); **the tour waited for M66** (met: shipped in 1.16.0) and publishes with the declaration
lit (D-X10). Proposed, undecided: D-X11 the wizard — `spotlight {wait: true}` with Next · Stop (no Back — owner) that returns the
call the LLM is waiting on; would be M64.14, the tour its first user.

- [M67.1] ☐ **The catalogue** — repository, `catalogue.json`, three jars in placeholder packages with their source (cross-repo).
  **Carries the beta's jars too (BETA-B4): jar A honestly wrong, jar B, and the spec-derived check. One collection, not two.**
  Owner clarification 2026-09-23: feed adapter selected for D-X9, but no existing jar/source
  is available. A new CSV adapter is authorised and will be labelled new example code;
  using a previously owner-built component remains open, not claimed by that example.
  **Source implemented locally at `b05f6e2` (branch `feat/spring-side-work-block`):** limit,
  notifier, new CSV adapter, shared API, risk A/B and an independent Decimal oracle. A has
  5/6 mismatches; B has 0/6 (the zero-exposure row matches both). Limit/notifier/feed and
  zero-multiplier controls are seen red, with green baseline/restoration. Repeated builds
  match bytes; unknown catalogue version and altered jars are refused. These are direct
  callback/packaging checks, not generated-dispatch or receipt-integrity evidence.
  D-X7's remote was unavailable at intake; G18 now provides the [source repository](https://github.com/telaminai/fluxtion-vendor-jars/tree/feat/spring-side-work-block) at `3a89391`. Independently source-reviewed by G at `e559c424`; binary publication/integration remain open.
  Public resolution, owner-built provenance and the tour remain open. [Evidence](../handoff/evidence/spring-side-2026-09-23/jars/summary.json).
  **Owner extension 2026-09-25:** [edit-loop §I](spec-spring-authoring-edit-loop.md#i-design-first-market-data-tour-and-existing-vendor-jars)
  specifies shared market-event compatibility, a real valueMapper, sources packaging and
  a staged market-data tour. Source head `3a89391` remains the inspected baseline; no binary
  publication or generated/hosted integration is claimed. Preserve risk A as the labelled
  incorrect control. The design-first/admin-console journey is specified alongside it.
- [M67.2] ☐ **The tour's project** — an onboarding template that ships running, with the catalogue's repository in its pom (playground).
- [M67.3] ☐ **The skill, the index move, `tools/verify-m67-extension-tour.py`** (analyser).
- [M67.4] ☐ **The held-out record and the generated docs page** (analyser; owner's key, local only).
- [M67.5] ☐ **Beat 3 lights the declaration** — after M66; **the tour publishes with this slice (D-X10)**.
- [M67.6] ☐ **Beat 4 lights the bean from the diagnostic** — after the compiler's refusal carries it.

## M13 · MCP transport — ◧ M13.1–13.4 SHIPPED (archived; M13.5 open)
_M13.1–13.4 (endpoint file, bridge, tools/call forward, docs) shipped 2026-08-15,
reviewed and merged — full record in **[completed/tracker.md](completed/tracker.md)**.
Design: **[spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md)** (stays live for
M13.5). Review decisions: hand-rolled JSON-RPC kept over an SDK; `structuredContent` parked
with M13.5._
- [M13.5] ☐ _(later)_ **Resources/prompts** (`analyser://log|selection|node-types|source`) and/or **option B**
  in-app Streamable-HTTP MCP server.

## M12 · Diagnose → fix → prove flywheel — ☐ ACTIVE DESIGN
_Design: **[spec-closed-loop.md](spec-closed-loop.md)** Part A (the handoff mechanics) and
**[completed/spec-assistant-actions.md](completed/spec-assistant-actions.md)** §13 (the original
diagnose → fix → prove framing). The edit loop lives in the dev env (Claude Code / Codex + CI),
**not** the analyser — the analyser's role is the **briefing and verification instrument**._
- [M12.1] ☐ **`export_finding` action** — emit the **fix-brief** (structure in spec-assistant-actions
  §13.1): diagnosis, evidence (records + byte anchors + file-access seeding), resolved source targets
  (`instanceId → file:line`, EP FQN, roots), replay reference, task, and acceptance (replay-diff: only
  the targeted records change). Built on `PromptBuilder`. **Precondition:** journal ↔ audit-log pairing
  (the analyser loads the output log, not the input journal).
- _Decision (2026-08-16): **the analyser prompt distils Fluxtion semantics; the fix brief carries the
  authoring rules**. The assistant reads logs, so it gets the reading-relevant subset (propagation/dirty,
  audit regimes, wiring-by-constructor) plus a fetch-on-demand pointer — not `claude.txt` inline, which is
  ~30KB of authoring guidance per request and invites answering the wrong question. **M12.4's brief is
  where the full authoring rules belong**, since that agent edits code; M19.1's bundle already plans the
  same via its `CLAUDE.md` bootstrap._
- [M12.4] ☐ **"Fix with agent…" handoff launcher** _(spec-closed-loop §A)_ — writes the brief to
  `<sourceRoot>/.analyser/fix-brief-<ts>.md` **plus `.analyser/.gitignore` (`fix-brief-*` — scoped so
  M20's committed project profile in the same dir isn't ignored) so briefs can never be committed**; v1 copies a ready-to-paste launch command (presets for Claude Code / Codex; template
  placeholders `{brief}` `{sourceRoot}` `{logPath}` — **no token in the command**: the agent reads the
  rotating endpoint+token from M13.1's `~/.fluxtion-analyser/rest-endpoint`). Every brief embeds the
  **git-hygiene contract** (branch, evidence-linked PR, never merge autonomously, prove by replay
  test) — the brief *instructs*; enforcement is the user's branch protection + PR review. _Accept:
  paste one command → agent opens holding the brief, works on a branch, PRs with evidence cited._
- [M12.2] ☐ **`export_test_fixture`** — record range → regression test oracle: a journaled slice driven
  through the processor / one node asserting its `nodeLogs`. Production incidents → real-sequence
  **red tests** the fixing agent turns green.
- [M12.3] ☐ **`DiffBuilder` additive-vs-value classification** — report new `nodeLogs` keys separately
  from changed values, so an instrumentation-only change shows as **pure-additive** (a verifiable property).
- _Out of scope (dev-env / Telamin): the LLM fix itself; replay-diff + unit tests as **mandatory CI
  gates**; **AOT regeneration** when an edit adds a handler/node; guardrail **propose → prove → human
  approves, never autonomous merge** (review the diff of **behaviour**, not just code)._

## Upstream template content — three drafts, ready to be taken

_`docs/proposals/upstream-content/`. Drafted FOR the static authoring resources
(`claude.txt`, the playground `CLAUDE.md`, the golden path), not for this repo. Each carries a
retrieval-dated evidence table, because those are live documents that can change under a claim._

- [UC-LANDED] ☑ **MERGED AND LIVE 2026-09-01 — [fluxtion-web PR #1](https://github.com/telaminai/fluxtion-web/pull/1)**,
  merged to `main` as `a4a124f` and deployed by Cloudflare (`5bf137a4`). **Verified on the live URLs**, not
  just in the repo: `/CLAUDE.md` 20,453 → 25,059 bytes and `fluxtion-golden-path.md` 13,179 → 15,167, both
  carrying `NoTriggerReference`, the finality rule and the green-build section. That distinction mattered —
  the first check after merge showed the OLD text, because the repo and the site are separated by an
  external deploy with its own timing. **The cascade is now real**: every project referencing the agreed set
  gets this without being regenerated. Content went to the two resources that are in the AGREED SET — `/CLAUDE.md` (the
  orientation) and `fluxtion-golden-path.md` — **not** to `claude.txt`, which `reference-set.json` records
  as deliberately EXCLUDED ("redundant, not wrong"). Writing there would have been writing where no
  generated project points. Branched from `main`, not the in-flight `feature/spring-author-extended`.
  **Selected by D-AX1's discriminator — silent failures only:** the plain-reference-is-a-trigger rule
  (`@NoTriggerReference`), one-`@OnTrigger`-not-many with the dirty-flag overwrite, audit wiring before
  `init()`, and the bean-style→constructor workflow. **Plus one correction the run forced:** the triage
  said constructor mapping is triggered by RENDERABILITY and that primitives are safe — a `final int`
  failed FLX-1009 in the run, so the rule is FINALITY, and the old text sends an author looking elsewhere.
  **Deliberately NOT written (D-AX3 shrink, first time with evidence):** the `@FluxtionIgnore` repair
  itself. FLX-1009 named the field and its import unaided, three times, one-step repair each — prose that
  pre-empts a good error message is dead weight. Evidence:
  [`ASSESSMENT-diagnostics-2026-09-01.md`](../experience/runs/ASSESSMENT-diagnostics-2026-09-01.md).
  ☐ **Still to place:** the phase/hook ordering table (filed separately as
  [fluxtion#29](https://github.com/telaminai/fluxtion/issues/29), different destination), and UC1's
  `EventLogSource` contract detail — partly carried now by FLX-1008's own `suggestedFix`.
- [UC1] ☐ **`audit-authoring.md`** — how a node participates in the audit log. The three conditions, the
  `EventLogSource` contract, the `addEventAudit` overloads.
- [UC2] ☐ **`audit-runtime.md`** — getting the log OUT. Six measured wirings, the `System.out` default
  sink, `logLevel()` after `init()` being a silent no-op, and the fact that the audit "setters" are
  dispatches. Evidence **re-verified unchanged 2026-09-01**.
- [UC4] ☐ **`idioms-and-canonical-form.md`** — NEW 2026-09-01, owner-directed, and the one the other three
  imply. They add FACTS; this maps **the shape you are trying to build** to **the construct that builds
  it**. The thesis is measured: over one real twelve-node graph, *every* mistake worth recording was a
  case where the structure was defensible and the framework had a better construct — none was a
  misunderstanding of dispatch, and none would have been caught by a diagnostic. Each was found by being
  corrected by someone who knew the idiom.
  **Two undocumented facts decide half of it**, confirmed by retrieval: `reverse topological`,
  `@AfterTrigger`, `processReentrantEvent` and `processAsNewEventCycle` are at **zero occurrences in all
  three sources**, while `@OnTrigger` appears 21 times in `claude.txt`. The annotation reference is
  strong; the two-phase execution model and re-dispatch are absent.
  Six idioms: derive with one `@OnTrigger` rather than N handlers (with the generated OR-of-dirty guard
  as evidence); state from events, services for query or action, plus the *state-snapshot-pretending-to-
  be-an-event* smell; side effects belong in the after-event phase, and when to go outside instead;
  re-dispatch and its two routes; the three threading options; and field wiring.
- [UC5] ◧ **Idioms audited against the app that produced them — 2026-09-01.** A document that says "do X",
  written by someone doing Y, is not evidence. Audited, with results recorded rather than tidied:
  ☑ **Idiom 1 applied AND corrected by the audit.** Six nodes still carry several handlers, and **five
  are right** — a gate validating ten different correlation ids, an outcome recorder whose seven handlers
  each contribute a *different* effect name, state nodes whose events are transitions rather than
  recomputations. Collapsing on handler COUNT would have destroyed the data they carry. The doc now
  states the real test — one derivation from several inputs, versus each event contributing different
  data — with the counter-case table. **The idiom was over-applicable as written, and only building
  against it showed that.**
  ☐ **Idiom 2a not applied, and it is our own named smell.** `LogObserved(boolean open, …)` and
  `GraphObserved` are state snapshots pretending to be events — the exact shape the doc warns about,
  still in the app. Sequenced, not ignored: the honest events (`LogOpened`/`LogClosed`) come from the
  open path, which [M44.3](completed/spec-async-session-driver.md) unblocks.
  ☐ **Idiom 2b not applied, and actionable now.** The source resolver is hand-threaded as a
  `Function<String, Optional<String>>` across **8 call sites** — already service-shaped, and the doc's
  own live candidate.
  ☑ **Idiom 3 PROBED 2026-09-01, and the probe beat both of my arguments.** Added an `@AfterEvent` method
  to `EffectQueue`, regenerated, and read the emitted `afterEvent()` block. It runs
  `effectQueue.probeAfterEvent()` **first**, then `clock.processingComplete()`, then
  **`eventLogger.processingComplete()` — which is where the cycle's audit record is published** — then
  resets the dirty flags. So **`@AfterEvent` runs BEFORE the audit record exists.** For a processor whose
  log is the product that ordering is the wrong way round: the irreversible act would happen before the
  evidence of deciding it, and an effect that threw could cost the decision record too. The external
  drain runs after `onEvent` returns, so it guarantees **decided, recorded, then acted** — a sharper and
  more specific reason than either "the result must re-enter" or "opening is async", both of which are
  general rather than about this product. Recorded in the spec and in the idioms draft.
  ☑ **Idiom 2b applied, and it corrected the doc a third time.** `SourceResolver` is a named interface
  across the eight call sites — but auditing first showed the doc's "live service candidate" was **not
  one**: nothing in the graph resolves source, so it wanted an interface, not a service registration. The
  doc now carries the test that would have caught it — *if no node queries it you want an interface; if a
  node queries it you want a service*.
- [UC3] ☐ **`node-field-wiring-and-workflow.md`** — NEW 2026-09-01. Two halves of one gap.
  **The rule:** *final* is the trigger for constructor mapping, and the word appears **nowhere** in any
  of the three sources — nor do `non-final`, JavaBean setter-wiring, or `@ConstructorArg`. The canon
  covers how to STOP a field being mapped and never what decides that it is. Route 3 — a non-final field
  is setter-wired and never constructor-mapped — is the one three measured agents found by accident and
  none could explain.
  **The workflow:** develop bean-style, harden to constructors when the shape settles, treat the
  migration as one deliberate break. Measured — four constructor-shape breaks while the node set churned,
  none after it stabilised — and **no diagnostic can carry it**, because it is advice about the order to
  work in rather than a failure to report.
  _Also records what is already RIGHT and must not be touched: `claude.txt` states the exclusion remedy
  with its FQN and says the field initialiser still runs, which this repo measured independently before
  finding it already documented._

## M45 · Consuming the GraphML vocabulary — ◧ .1/.2/.3/.5 SHIPPED 2026-08-31

**Shipped detail archived to [`completed/tracker.md`](completed/tracker.md) on 2026-09-03.** Open slices only below.

- [M45.4] ◧ **RESTORED TO THE LIVE ORDER 2026-09-24 — consume `fluxtion.framework`; now delivered as M68.1,
  implemented on branch `feat/m68-1-coverage-pairing-scope`, unmerged.**
  It was swept into the completed tracker with the shipped detail on 2026-09-03 and **should not have been**: the
  entry carries a ☑ on the sentence recording that *our half* was verified and unparked on 2026-09-01 against the
  real session graph at released 1.0.65, while the item as a whole is ◧ and the consumption was never built. A ☑ on
  one clause made the whole item read as finished, so it left the delivery order and nobody saw it for three weeks.
  **What it cost:** on 2026-09-24 a held-out client was told a declared node was absent from a graph that declares
  it, because the package-prefix heuristic overrode the declared fact — the exact failure mode the archived entry
  already names. See [`spec-evidence-integrity.md`](spec-evidence-integrity.md) ▸ M45.4 reconciliation, and D-E10.
  The archived entry stays where it is and remains the authority and measurement basis; this line is the live
  pointer to it. **Lesson for future sweeps:** an item marked ◧ never leaves the live tracker, whatever ☑ marks
  appear inside its text.

- [M45] ☐ **The vocabulary answers as DATA what we answer by HEURISTIC** — framework-generated nodes,
- [M45.1c] ☐ **Original slice text, for the record — prove reachability, and measure the ceiling.** Install the branch, point `-Pregen` at it
- [M45.2b] ☐ **Original slice text — read the vocabulary, change no behaviour.** `metaVersion` (1.x reader accepts every 1.y;
- [M45.3a] ☐ **Original slice text — the audit trio is a DUO** — `auditCapable`/`auditCapableVia` are emitted, `eventAudit` is
- [M45.5a] ☐ **Original slice text — parallel edges and dispatch rank.** Checked, not assumed: `ProcessorTopology.of` does no
- [M45] ☐ **Backwards compatibility, assessed — and the risk is not in the GraphML.** At `OFF` the only
  our parser against before/after at `dd36bc5` and found adjacency and node facts identical. ☐ **That

## M44 · Session transitions as a Fluxtion processor — ◧ SLICE 1 SHIPPED 2026-08-31 · M44.3/.3a/.3b SHIPPED in 1.14.0 (archived) · M44.4 single-state model IN PROGRESS

**Shipped detail archived to [`completed/tracker.md`](completed/tracker.md) on 2026-09-03.** Open slices only below.
*(Restored 2026-09-24: the three entries below had been cut to their first lines by an earlier edit. The full text
of the originals is in `057a069a`.)*

- [M44] ◧ **Spec: [`spec-session-processor.md`](spec-session-processor.md).** Session transitions are the first
  analyser decisions moved into a Fluxtion processor. Slice 1 and M44.3 shipped. ☐ **Residue:** of the five
  dialog-only entrances (`ADOPT_FOR_OPEN_LOG`, `CREATE`, `FORK`, `STARTUP_ACTIVATION`, import-as-project), each
  call site's kind is verified by reading, not by running (§11).
- [M44.2x] ☐ **Superseded by M44.4a.** The original next-slice list was `IgnoredParameters` and the F3 split,
  both shipped, and then moving log and graph OPENING, which deletes `LogObserved`/`GraphObserved`. That last part
  is now M44.4a.
- [M44.3] ☑ **Licence decision: keep as is** (owner, 2026-09-24; spec D-S1.2 → D-S13.6).
- [M44.4] ◧ **The single-state model: the processor owns every session verdict, and surfaces read one
  snapshot** (spec §13, PROPOSED 2026-09-24; implementation started at the owner's direction, spec under review).
  Motivated by M68.1, whose four review rounds were all synchronisation defects between copies of one pairing
  verdict. Branch `feat/m44-single-state-session`.
  - [M44.4a] ☑ Facts (`GraphOpened`, `GraphCleared`, `LogCleared`, `LogAppended`) with a log-generation gate, and
    `post(fact)`. Deleted the observation funnel (`2fbfee04`).
  - [M44.4b] ☑ `SessionSnapshot` and its listener; Follow reports appends; coverage reads the snapshot. Deleted the
    frame's scorer, the append republish and the `invokeAndWait`.
  - [M44.4c] ☑ `ViewFilterChanged`, `MembershipCompared` and the `PairingQualifier` node. "Pending" comes from the
    gate, on the snapshot. Deleted the frame's pairing and qualification fields; a static test forbids them.
    ~~Closed the scan-during-open race.~~ *Not alone — the independent review's R1 reopened it; see M44.4r.*
  - [M44.4] ☐ **Open acceptance** (spec §13): the remaining `PairingDuringLoadFrameTest` journeys re-expressed as
    headless event sequences; §13's four predictions scored; the reviewers' pass on §13.
  - [M44.4d] ☑ Folded into M44.4b: re-scopes are held in a separate ring and tracing stays on. The DEBUG half was
    not built (spec §13).
  - [M44.4r] ◧ **The independent review's findings (review `0bb01fa8` on `review/m44-4-m68-2026-09-26`, subject
    `14a72acc`), and the gaps closed before one review.** **Implemented 2026-09-26; independent acceptance NOT given**
    — a green suite is not closure. Evidence: sets 12–13 in `docs/handoff/evidence/m44-4-single-state-2026-09-24/`.
    - ☑ impl · R1 coverage inputs captured together on the EDT, scan off it on a filter copy and bound (`762e9853`)
    - ☑ impl · R2 the exported coverage table obeys the session's claim (`56463d99`)
    - ☑ impl · R3 rolled sets report member freshness; the SPI boundary stated (`b8afbe53`)
    - ☑ impl · R4 the published snapshot's qualifications are read-only (`096d93e9`)
    - ☑ impl · R5 `saveFocusAs` precondition in the whole-request check (`b4894e6f`)
    - ☑ impl · R6/R7 framing past a leading BOM; an oversized pending frame says NOT assessed (`1e2c60a9`)
    - ☑ impl · R8 no published address that does not parse; scope and exceptions stated (`c23769ad`)
    - ☑ impl · O1 ordered snapshot delivery; O2 revision meaning stated and pinned (`933f62bd`)
    - ☑ impl · set 13: the table banner, focus and series sections drawn, env/destination pointers, nested keys, the
      chart/series point-count agreement check (`f53da616`…`d7f4966a`)
    - ☑ **re-reviewed** (`93046a48`, `review/m44-4-m68-rereview-2026-09-26`): R1–R4, R6–R8, O1 and set 13 accepted by
      that review; **two findings required**:
      - ☑ impl · N1 a refused `saveFocusAs` judged on the request's real transitions, run on a trial copy through the
        apply's own routine (`f0ad708d`)
      - ☑ impl · N2 a report's series section draws exactly its stored call, through the verb's `parseCall`
        (`bfc1235a`); witnesses `e2d910b0`
      - ☑ **focused re-review** of N1 and the two N2 counterexamples — see
        [the review and authorised correction](../handoff/rereview2_m44_4_m68_2026_09_26.md).
        N1 accepted; the original N2 cases pass. F1 found a new literal-key-to-formula regression in N2;
        the owner authorised the reviewer to fix it on the review branch. Acceptance evidence and final gate
        results are recorded in that report; integration remains separate.
      - ☑ impl (**reviewer-authored**, owner-authorised) · F1 a report `key` stays a literal `GraphKey` through
        `SeriesScan.parseKeyCall`, with the shared scope/resolution parsing (`ed97f363`). **Checked by the
        implementer, not independently reviewed:** `expr` unchanged, the value-sensitive test and its control
        `review-n2-report-literal-key` re-run on the final tree.
    - **Status at merge (2026-09-26):**
      - **Implemented:** everything above.
      - **Accepted:** R1–R4, R6–R8, O1 and set 13 (`93046a48`); N1 and the original N2 cases (`04174894`).
      - **Not independently accepted:** F1's correction, which its reviewer wrote.
      - **Merged to main via PR #25:** yes.
      - **Still open:**
        - [M44.4] §13's open acceptance (below);
        - the owner decisions Q4 (partial delivery), Q5 (saved names with `"`) and O2 (graph-content identity),
          none decided by this merge;
        - the optional follow-ups: the empty series picture's "widen the filter" advice (review O1) and the lone-point
          dot;
        - the Mongoose integration, scheduled after this merge.

## M19 · Onboarding example — playground download → running Mongoose → analyser — ◧ IN PROGRESS
_Design: **[spec-onboarding-example.md](spec-onboarding-example.md)**. The playground's Download button
ships a runnable Mongoose example with Chronicle audit capture pre-enabled and one YAML export command
targeting a predictable project-relative path,
bundled source, and a **project profile at `.analyser/project.fluxtion-settings`** (M20's canonical path —
the bundle *is* a project profile) — so onboarding becomes: download → run → jbang the analyser →
project auto-loads (M20; **File ▸ Import** until it lands) → Follow a live log with click-to-source and Explain working.
Target: under 10 minutes on a fresh machine with only a JDK. The bundle's README links back to the
analyser (reverse funnel)._
- [M19] ➜ **SECOND ARCHIVE PASS 2026-08-30** — five more completed slices moved to
  [`completed/tracker.md`](completed/tracker.md): **M19.21**, **M19.20**, **M19.14a**, **M19.14**, **M19.5**. Two contradictions were
  removed on the way: M19.5 carried both an ACCEPTED and an AWAITING-REVIEW entry, and M19.21 carried
  both a SHIPPED one and a "brief written, not pushed yet" one that had been true for two hours. Both
  are the drift an accumulating tracker produces, and both were found by counting duplicate ids rather
  than by reading.

- [M19] ➜ **SHIPPED SLICES ARCHIVED 2026-08-30** — fourteen completed slices moved to
  [`completed/tracker.md`](completed/tracker.md) per rule 7. What remains here is open work **plus any
  slice finished since that tidy** (☑, awaiting the next one) — see the note at the top of this file.
  Archived: **M19.2** (SettingsShare relative roots), **M19.4** (cross-links), **M19.6–.9**
  (the loop bench, agent-driven fresh start, bench green in CI, headless launch args), **M19.10**
  (canonical skills), **M19.11** (onboarding bench), **M19.12/.12a** (key management and licence
  placement), **M19.13** (day two), **M19.16–.18** (review amendments, bundle contract v2 then v3).
  Nothing about them changed; they are findable there with their commits and evidence.

- [M19] ➜ **REVISED 2026-08-29 · independently reviewed — ACCEPT WITH AMENDMENTS** in
  [`review_m19_onboarding_and_trust.txt`](../handoff/completed/review_m19_onboarding_and_trust.txt). Owner-directed:
  one download should produce a project where an LLM already knows Fluxtion, is connected to the analyser
  over MCP, and is told by the analyser which skills run/stop/read the local app. The spec pre-dated M38,
  M42 and M43, so five additions: **R1** the bundle ships `.claude/skills/*/SKILL.md`; **R2** the shipped
  profile REGISTERS them as `runbook.N.*` (and states why that does not violate D-AI5 — a bundle author
  declaring their own runbooks is the author declaring, not the analyser inferring); **R3** MCP
  pre-wiring, and step 6's division-of-labour paragraph is now WRONG and rewritten (M42 made it one agent
  that both edits and drives; the surviving principle is that the ANALYSER edits no code); **R4** the
  licence key is the first wall after first success and the seeded CLAUDE.md must pre-empt it; **R5** open
  the analyser on the GRAPH before the first run, so M40.1 has something true to say at minute two.
- [M19] ➜ **REVISION RETURN IS SUPERSEDED by M19.16/.17/.18 and the owner's start signal.** The third review
  originally left F1/F4/F5/F8 open. F1 and F4 are now closed by signed `m19-bundle/3` + `m19-skills/1`
  at `b0fdb86`; F5/F8 are a bounded deferral because the embedded tier is explicitly NOT PUBLISHABLE and
  outside the Mongoose bundle. Embedded graduation still needs a key-holder run through the listener and
  analyser; it does not block the selected Mongoose tier.
- [M19.15] ☐ **The seeding prompt for step 2** _(owner, 2026-08-29; spec has it verbatim)_. Step 2 is only
  a measurement if the prompt does not contaminate it: leading the witness produces agreement, manufactured
  hostility produces theatre, instructing the task tests the prompt instead of the docs, and revealing it is
  a test makes the model evaluate rather than use. **The risk that is easy to miss is not failure — it is
  SUCCESS BY COMPENSATION**: a capable model fills a gap from training data or by reading generated source,
  finishes the task, and the gap is invisible. So the prompt's job is to make compensation VISIBLE, not to
  prevent it. Measurement is mostly external — the git history, the code and the audit log are evidence; the
  model's account is testimony (D-T3 applied to assessing the product).
- [M19.23] ◧ **UP-PG-02 producer LANDED; analyser disclosure OPEN** _(corrected 2026-09-20)_ —
  plan: [`plan_playground_agent_bootstrap.txt`](../handoff/completed/plan_playground_agent_bootstrap.txt).
  `agentBootstrap` exists on playground `origin/main` and in the live catalogue inspected on September 20.
  The journey branch now consumes/renders present, explicitly empty and absent declarations separately;
  recommendations do not imply readiness. This analyser change is not yet reviewed or released.
  **Evidence the ask did not have:** all fourteen templates were generated — `analyser-bundle` ships
  `CLAUDE.md` + `AGENTS.md`, the other **thirteen ship neither**. So the field carries real information.
  **The gap that matters more than the field:** the `onboarding` subset the picker lists is TWO
  templates and only ONE ships agent instructions, so a user choosing `fluxtion-spring-mongoose` from
  inside the analyser gets a project with no CLAUDE.md, no AGENTS.md and no skills — chosen from a list
  whose purpose is onboarding. The field DISCLOSES that; it does not fix it. **2026-09-20 owner decision:**
  spec-template-from-analyser D-1 now requires all entries, with `onboarding` a recommendation rather
  than an exclusion rule or a guarantee of agent readiness; that picker change is implemented on the
  journey branch and awaits its gate/review. Default-on producer support is implemented at playground
  `0e7bd58` but not deployed. Producer field delivery is complete; re-run generated ZIP/metadata
  consistency checks when default-on support extends bootstrap output to the other project types.
- [M19.22] ◐ **The generated processor's header claims CONFIDENTIALITY — filed as
  [fluxtion#24](https://github.com/telaminai/fluxtion/issues/24)** _(found 2026-08-30 against the live
  bundle)_ — [`spec-onboarding-example.md` ▸ D-B6](spec-onboarding-example.md). Every generated processor
  carries *"This file is confidential and only available to authorized individuals"* plus all-rights-
  reserved, **into the user's own repository**, in a starter that exists to be built on. The live bundle's
  copy additionally names a personal address on a vendor domain (one of rule 1's four terms; the analyser's
  demo copy does not, so that line is version-dependent or stripped somewhere).
  **THE ANALYSER IS AFFECTED TOO** — `src/main/resources/demo/com/acme/demo/generated/DemoQuoteProcessor.java`
  carries the same confidentiality notice and ships **inside the analyser jar**. Two independently generated
  processors, same header, so it is the generator's template.
  **Deliberately NOT hand-patched here.** The demo processor is a generated artefact reproduced by
  `examples/fixture-generator/`; editing the shipped copy would make it an unfaithful example and would
  drift from what the generator emits — which is precisely the "every check ran on a repaired copy" failure
  the playground just spent a day on. It is fixed when #24 lands and the fixture is regenerated. Recorded
  here so the exposure is visible rather than forgotten.
  **UPDATE 2026-08-30, verified against production independently:** the playground scrubbed the bundle and
  the live zip is now clean on both the four-term sweep and on confidentiality/rights language, with a
  provenance-only header that says what generated the file and how to regenerate it. **The scope of #24 is
  unchanged.** The playground can only scrub the one artefact it commits; the generator still emits the old
  header to anyone who regenerates — and **our own canonical `add-a-node` skill hands users exactly that
  command**, so the analyser routes people into it. The starter has stopped being a carrier; the generator
  has not. Exposure window on the live bundle was roughly two hours (their measurement, not ours).
  **UPDATE 2026-08-30 (comment on #24):** the playground has scrubbed its bundle, verified here against
  production — so **the analyser jar is now the remaining public carrier**, and the generator keeps
  stamping every user who regenerates. Still deliberately not hand-patched; it clears when #24 lands and
  the fixture is regenerated. **No second issue was filed:** #24 already carries the analyser demo as
  evidence item 2, and a duplicate would dilute the one that exists rather than add to it.

- [M19.19] ◧ **Guided start — an install prompt, and an LLM tutor that drives the UI** _(owner idea,
  2026-08-30; **and the experiment's baseline**, D-G8)_. **Skill and docs page shipped; first real drive
  done 2026-08-30** — [`runs/guided-start-01/run.md`](../experience/runs/guided-start-01/run.md). All
  three beats work against a running `--rest` analyser, and the drive found two defects in the skill:
  **beat 2 reported ZERO on the traced demo log I had chosen** (the distinctive beat, showing nothing —
  it now uses the untraced log where one node is uncovered, and reads the analyser's own
  "never logged, not never ran" note aloud, which is a stronger demo than the number); and `flag` takes
  `recordIndexes[]`, so the sketch would have made an agent guess wrong in front of the audience. Also:
  no verb lists graphable keys, the demo set cannot be installed by the agent (a second human pause), and
  a returning analyser restores its previous session. **DONE 2026-09-17:** the held-out run — a context-free client (`tools/heldout-client.py`) given the skill text
  lit every beat before it spoke ([record](../handoff/completed/heldout_m64_2026-09-17.md)); a person watching is still the last word. — [`spec-guided-start.md`](spec-guided-start.md). Zero to a running analyser showing
  capabilities, driven by a prompt an LLM executes. **Verified: the tutor needs NO new verbs** — `open`,
  `filter`, `topology`, `goto`, `graph` and `flag` already drive the UI, and `context` + `screenshot` tell
  the agent what the user can actually see. The load-bearing rule is D-G2, from D-T3: **the tutor points,
  the screen proves** — it may not state a figure the user cannot see, which makes the tutorial a live
  demonstration of the thesis rather than a chatbot describing software. Setup is shell, not analyser
  surface, and the whole path is **keyless** (a bundle ships its generated processor). One real gap: MCP
  registration is an in-app flow — v1 asks the human to do it rather than adding a headless path. Also
  D-G5: this is the best held-out task the experience loop has, because its outcome is objective.
- [M19.1] ◧ **Released bundle produced; implementation accepted, refreshed final evidence artefact remains** — **full Maven project** (O1 resolved: user edits
  it in their IDE with their own LLM) with audit enabled + generated/EP source + settings file +
  **`CLAUDE.md` agent bootstrap** (the layered prompt stack in spec §Contract — thin example-specific
  layer, snapshot of the canon at generation time, canonical-reference line) + admin REST on + README
  with run command and analyser link; tracked in the playground repo, contract recorded in the spec.
  _**The authoring path is not a gap — use its front door.** It is already layered and maintained:
  [`/build-with-ai`](https://fluxtion-playground.dev/build-with-ai) →
  [`CLAUDE.md`](https://fluxtion-playground.dev/CLAUDE.md) (orientation) → `spring-authoring/skill.md`
  (how to run the design conversation) → `contract.md` (the exact `FluxtionSpringConfig` XML to emit) →
  `example.md` (a worked run) → the **project starter generates the build** — the pom is generated
  output, not something an author writes. Design work: `fluxtion-compiler/design/spring-authoring`.
  The bundle's job is to **reference and snapshot** that canon plus what only it knows (log path, admin
  port, the analyser's endpoint file), never to author a rival prompt. Add `skill.md`/`contract.md` to
  the snapshot set for the XML-defined example (spec O2), since those are what make the design-level
  edit in tutorial part 4 possible._
  - **Dependency gate ☑ released and consumed as mongoose-plugins 1.0.41:** local implementation/bench work used
    `svc-admin-web:1.0.39-SNAPSHOT` from Mongoose Plugins `6e7a2cc`. On 2026-08-29 the final
    `svc-admin-web:1.0.39` and `mongoose-test-support:1.0.39` POMs both resolved publicly from the Repsy
    repository generated bundles already declare. Public 1.0.40 added the `startComplete` registry refresh;
    public 1.0.41 added its strengthened behavioural test and the regenerated schema golden. The playground
    now pins 1.0.41; a generated bundle publishes its processor immediately without a dashboard poll and its
    declared GraphML endpoint returns 200. Version 1.0.42 only removes release-plugin scratch from source
    control (the relevant runtime source is identical), so it is not an M19 consumption gate.
  - **P0 ☑ accepted at reviewed head `73565fc`:** fluxtion-web
    `feature/m19-p0-keyless-bundle` moves scan/second-compile behind
    `-Pgenerate-fluxtion`, adds deterministic registry identity plus export/stop scripts, and keeps the
    classic shape additive. `d43552e` closes disabled capture, export-script `eval` and forced serverName;
    `73565fc` closes the Builder-inaccurate preflight, validates bundle invariants, forces both identity
    fields, passes the stop registry path as argv, and refuses a reused/mismatched PID. The live Bash
    fixture keeps hostile registry values as data. Details and exact disposition:
    [`review_m19_p0_fixes_and_p1.txt`](../handoff/completed/review_m19_p0_fixes_and_p1.txt).
  - **Branch-level key-advice follow-up ☑ closed at `3acaf9b`:** ordinary Fluxtion README/run-script
    output now names only the Builder's file/-D sources and a non-bundle fixture pins both files.
  - **P1 ☑ accepted at generator head `8f20016`:** `m19-bundle/3` emits the real zero-based profile ABI;
    one AnalyserBundleModel supplies scripts/README/profile/skills/guides; the acceptance fixture is the
    Spring-XML template with design XML + maintained authoring canon; minimum-version refusal is present.
    Independent gates: 27/27 focused, 376/376 full and production build pass. Review:
    [`review_m19_p1_response_and_download_zip.txt`](../handoff/completed/review_m19_p1_response_and_download_zip.txt).
  - **Download seam ☑ closed at `266132a`:** the actual `buildMavenZip` preserves root CLAUDE/AGENTS,
    Maven wrappers and lifecycle scripts; `mvnw` plus the scripts retain executable modes. The focused
    packaging test passes and the exact Spring Download zip passes analyser bundle-bench 49/49. Stale v2
    implementation comments are gone. Evidence is in the cross-repo report §7g.
  - **Contract-version declaration for v3 — no profile key:** the authoritative marker is the exact
    `Bundle contract: **m19-bundle/3**` line in required root `CLAUDE.md`; required `AGENTS.md` is its
    byte-for-byte mirror. P3 parses that marker, rejects unknown versions and checks the mirror. The
    profile comment is informational. This selects a checker route already emitted by P1 and does not
    change the v2 inventory or profile schema.
  - **P2 ☑ accepted at `4eabc1c`:** source parsing/refusals, bounded
    index/set, distinct outcomes, sanitised provenance and project-input refusal are sound; current
    canonical skill bytes matched analyser `6243a89` at acceptance; the post-P3 instruction correction
    is published at `f5efe17` for the final re-vendor. The empty eager content registry now lets a written
    `none` snapshot build; strict leading frontmatter/exact versions close both false passes; required
    Mongoose skill-set and duplicate-name gates close incomplete `ok` results. Independent gates: 44/44
    focused at `050c0ab`; 395/395 full at `5f01cab`. **F4 ☑:** the public raw analyser root serves the
    versioned index; the playground now selects it as CANONICAL_ROOT, has removed `--declare-canonical`,
    and the independently-run default CLI emits canonical@6243a89 with byte-identical content.
    **F5 ☑:** the matrix now fails closed, asserts exact leg identities, and independently passes
    canonical 49/49, none 35/35 and local 49/49 through build → actual zip → checker. A five-test real
    loopback-TLS fixture covers successful mirror retrieval/provenance, redirects and distinct HTTP/
    transport outcomes; mirror/local reconverge before the shared non-none snapshot/build path.
    Independent gates: 74/74 focused, 401/401 full. **Low follow-up ☑ closed at `2ad5289`:** the fixture
    supplies its generated certificate as the private client's `ca` with verification enabled. Review
    and dispositions:
    [`review_m19_p2_skills_retrieval.txt`](../handoff/completed/review_m19_p2_skills_retrieval.txt).
  - **P3 ◧ implementation accepted; refreshed shared evidence artefact remains:** `tools/bench/bundle-bench.py` checks an
    unzipped project or download zip against `m19-bundle/3`, including the real zero-based profile ABI,
    guide mirror/version, committed processor source, declared/discoverable GraphML, exact shipped
    runbooks + frontmatter/provenance/minimum version, executable lifecycle scripts, safe inventory and
    placeholder refusal. Nine deterministic Python fixtures run in CI, including rejection of v2's
    one-based/singular profile plus canonical, `none` and clean HTTPS-mirror provenance. The real
    canonical Spring Download zip now passes 49/49 static checks. A real SNAPSHOT-based run at playground
    `4eabc1c` proved empty-HOME keyless package, real processor registry publication, 18 audit records and
    declared-path YAML export. It found that generated MongooseMain lacked a shutdown hook; the generator
    now calls server.stop() from one, and the live rerun removed the registry entry cleanly.
    Local gates: 9/9 Python fixtures, 1,112/1,112
    Java tests, strict docs and the existing packaged stub/analyser/MCP loop 23/23.
    The final artefact is reproducible on fluxtion-web `m19/p3-artifacts` @ `893fbdf`; its ZIP SHA-256 is
    `a5fba6c3d07cae710b825131403b1fa8d350fc6e6a284c5d95b03e94f29c9ba6`. Public 1.0.39 keyless build,
    run, five typed PriceEvent cycles, 23-record export and clean ordinary-home stop are producer-proven.
    This session independently passed the ZIP 49/49 and its fresh analyser/MCP leg 19/19: active project,
    two described/existing runbooks, canonical@f5efe17 provenance, pairing 2/2, coverage 1.0, 14 tools and
    analyser_context returning the same state.
    **Lifecycle response accepted at deployed fluxtion-web `c15ed9f`:** `280898e` restores exact Java/JAR/
    start-time stop identity, observes exit plus registry removal, passes the registry override through all
    three commands, and moves runtime claims into a committed real-bundle bench reported 11/11. Public
    mongoose-plugins 1.0.41 refreshes the entry at `startComplete`; its test observes the exact processor,
    group and GraphML route, and a generated-bundle run fetched that route without a dashboard poll. This
    session's current Download ZIP passes 49/49 and pins 1.0.41. **The shared `m19/p3-artifacts` branch is
    still `893fbdf` / 1.0.39**, so refresh it with the current ZIP, generated source/GraphML/YAML/hashes before
    the final current-version 19/19 rerun and P3 completion. Disposition:
    [`review_m19_p3_lifecycle_final.txt`](../handoff/completed/review_m19_p3_lifecycle_final.txt).
- [M19.1a] ◧ **Mongoose starter conformance bench (validation only; not a bundle shipment)** — the
  downloaded `mongoose-hosted-fluxtion` starter now has a reviewable contract snapshot in
  [`mongoose-bootstrap-artefacts/`](mongoose-bootstrap-artefacts/), with its source project retaining
  ownership. It tests this M19.1 contract **and** the accepted agent-brokered dev-loop where they meet:
  M19's bundle-owned YAML export at `./logs/audit-<name>.yaml`, profile and source evidence stay required; the
  registry/export/GraphML leg is **VAL-12**, exercised by `tools/bench/loop-bench.py` only when Mongoose
  supplies UP-MNG-01 and the export surface. The current starter supplies neither, so it makes no
  brokered-loop or distribution claim. Review resolution:
  [`report_mongoose_bootstrap_review_resolution.txt`](../handoff/completed/report_mongoose_bootstrap_review_resolution.txt).
  V0 is documentation-complete except for owner decision D-02; V1 application work has not started.
  The project-local `.claude/skills/mongoose-local/SKILL.md` is discoverable-but-not-auto-added and is
  not the graduated shared skill (V5). Its local tracker carries the four explicit review follow-ups:
  A1 real-server bench, A2 registry-first discovery, A3 UP-MNG-02 disposition, and A4 manual skill
  adoption; none is complete merely because the documentation exists.
- [M19.3] ◧ **Tutorial page corrected against the shipped bundle; screenshot set in progress.**
  `docs/site/tutorial-playground.md` now uses the real Audit analyser bundle name and concrete paths,
  opens project/GraphML/log separately, distinguishes a fixed export from a followable log, teaches
  Explain/copy-prompt/MCP, and refuses to promise a two-run diff that is not built. Four generated
  screenshots now show the real bundle's project, log, PriceEvent cycle and source navigation; three
  existing isolated-DEMO figures cover graphing, the AI menu and MCP setup. The remaining spec captures
  are the live playground Download, terminal lifecycle, an actual Explain answer and IDE edit; no connected
  browser was available in this session, so those were not fabricated. **RESOLVED 2026-08-30:** the
  producer gap that blocked the graph step is fixed — the released bundle now logs `price` and `volume` as
  numeric keys, the tutorial tells readers to chart one from their own run, and the four screenshots were
  re-shot against the real bundle. The DEMO chart is kept only because five cycles make an illegible plot,
  and the page says so rather than implying the demo is the bundle. Remaining: the three neutral captures
  that need a connected browser (live Download, terminal lifecycle, an Explain answer).
## M21 · Topology view + step-through — ◧ CORE SHIPPED (archived; 21.7–21.9 open)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M21.7] ☐ _(later)_ server-sourced GraphML via `GET /api/processors/{group}/{name}/graphml` (needs M18.1).
- [M21.11] ☐ **Consume the declared trace flag** _(owner ask 2026-08-17; needs UP-FLX-11 upstream)_ —
- [M21.9] ☐ **Use `ProcessorDescriptor` instead of inferring** _(found 2026-08-16 reading a generated
- [M21.8] ☐ _(later)_ **node → flag** — "flag every record where node X fired" needs an `instanceId`

## M22 · Topology view usability — ◧ 36 of 41 SHIPPED (archived; 5 open)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M22.3] ☐ **Export the view as PNG** — reuses the offscreen render already used to verify the canvas;
- [M22.6] ☐ **Alternative layouts** — the largest item. `LayeredLayout` is Sugiyama; candidates are
- [M22.11] ☐ **Re-dispatch (`processReentrantEvent`) — show the cause.** A node can raise an event on its
- [M22.20] ☐ **A DataFlow `.push()` target renders as an orphan.** *Measured 2026-08-17 against a probe

## M29 · External series — ◧ SHIPPED 2026-08-18 (archived; M29.5 optional embed open)
_M29.1–.4 shipped, reviewed and merged — full record in **[completed/tracker.md](completed/tracker.md)**.
Design: **[completed/spec-external-series.md](completed/spec-external-series.md)**._
- [M29.5] ☐ *(optional, owner decides)* **`embed: true`** — carry small series inside the saved graph
  for fully-portable sharing (D-F5's alternative).

## M31 · Log-source plugins — ◧ SHIPPED 2026-08-18 (archived; example reader is cross-repo)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M31.5] ☐ **NOT YET** _(owner, 2026-08-27)_ · **Separate `analyser-reader-spi` artifact** — needs a multi-module

## M33 · Investigation reports — ◧ CORE SHIPPED 2026-08-20 (archived; M33.5 gated)

**Detail lives in [`completed/tracker.md`](completed/tracker.md).** Open slices only below.

- [M33.5] ☐ **Fold M12.1's fix-brief onto the model** (D-I6) — after the closed-loop precondition
- [M33.6] ☐ **YES — build it** _(owner, 2026-08-27; support are non-agent users and the CSV source is

## M36 · Start page — follow-up (.1–.5 SHIPPED 2026-08-25; the milestone is in completed/tracker.md, design **[completed/spec-start-page.md](completed/spec-start-page.md)**)
### Rule 1 — owner decisions (raised M36, sharpened by the polish round; the two resolved ones are archived with M36)
- ⚠ **ANSWERED 2026-09-01: YES, it still does — measured on a build against the deployed 1.0.65 backend.**
  Every generated processor opens with:
      Copyright: © 2025.  Gregory Higgins <…> - All Rights Reserved
      This source code is protected under international copyright law…
      This file is confidential and only available to authorized individuals…
  Stamped onto the USER'S generated code — an artefact derived from their graph, which upstream's own
  positioning calls the deliverable. Two problems, and neither is the analyser's to fix: it asserts
  all-rights-reserved and CONFIDENTIALITY over a file the user generated and ships, and the year reads
  2025. Raised with the owner as a licensing decision rather than changed unilaterally.
  **Originally:** an upstream ask, not an analyser one.

## M43 · The AI menu — follow-up (COMPLETE 2026-08-28; the milestone is in completed/tracker.md, design **[completed/spec-ai-menu.md](completed/spec-ai-menu.md)**)
- ☐ **Owner question: the menu's name** — shipped as `AI` (proposed over *AI assistant*, since "assistant" names the
  in-app panel and the docs nav settled on *Working with AI*). Rename is a one-line change if the owner prefers otherwise.
- ☐ **D-AI9 wording addendum (owner's call, from the `c4d1db3` review)** — the light's reclaim is a POLICY: when the
  window owning the endpoint closes, the survivor re-publishes and an AI client mid-session silently reaches the
  survivor's log, where a person sees the light change. Both reviewers judge it the right policy (a dead endpoint hides
  the same change behind a hard failure); the spec should name the residual, not only the choice. One line in
  `completed/spec-ai-menu.md` ▸ D-AI9; no code.

## Framing · The trust structure — "AI you do not have to trust" — ☐ PROPOSED 2026-08-29
_Owner-directed. Spec **[spec-trust-structure.md](spec-trust-structure.md)**. Not a milestone: it creates
little new work and instead CONSTRAINS existing work. Read it before any change that loosens what the
analyser is willing to assert._
- The position: regulated buyers are blocked on agentic AI because nothing an agent produces can be
  independently checked. The answer is not to explain the model — it is to make the model's output
  checkable against a record the model did not write.
- **D-T1** do not say *explainable AI*: it is a term of art meaning model interpretability, we do not do
  that, and a buyer who hears it will correctly conclude we do not fit. Say *verifiable* / *independently
  recorded* — which asks them to believe nothing about the model.
- **D-T3** the distinction that carries the position: an agent's account is TESTIMONY, the audit log is
  EVIDENCE **about execution** — produced by running, not by narration. Why the record must be on by
  default: a log enabled after an incident is not evidence of the incident.
  **BOUNDED (review F5, 2026-08-29):** it is *not* tamper-evident, *not* authenticated, and *not*
  independent of whoever wrote the logging calls — the analyser parses a hand-written log, and an agent
  that authors the project writes the `auditLog` calls. Origin rests on declared provenance and on
  trusting the runtime. Never claim more than that.
- **D-T4** every refusal in the analyser is now load-bearing rather than tasteful. A change that makes it
  assert more than the record supports is **a change to the market position**, and reviewers should treat
  it as one.
- **D-T6 ☐ OPEN, and the most valuable thing to learn:** what is the forcing function for the first
  serious prospect, and does it have a date? Regulated industries tolerate pain for years; availability of
  a better answer is not what moves them. If the answer is "eventually", runway changes, not direction.
- Evidence is measured and none of it was produced for this document — including a simulated regulatory
  return that was FALSE (*"7 of 7 foreseen"*, actually 0) and was refuted only by the record.
- ☐ **Trust-boundary amendment (review F5)** — a supplied audit record is evidence about the recorded
  execution only within a declared, trusted runtime/deployment boundary. The analyser does not establish
  the record's origin, completeness, semantic correctness or freedom from author influence; narrow the
  spec and buyer-facing wording before treating "independently recorded" as a market claim.

## M39 · Baselines — "is this normal here?" — ☐ SPEC'D 2026-08-27 (owner decision 4; spec **[spec-baselines.md](spec-baselines.md)**)
- [M39] ☐ **Baselines** — ☑ **SPEC'D 2026-08-27**, `spec-baselines.md`. "Is this normal here?" — the
  question support cannot answer about a system they did not build, and the one a deterministic record
  uniquely can. Five decisions, the load-bearing two: **D-N1** a baseline is a NAMED REFERENCE RUN, never
  an abstract "normal" (an abstract normal is unfalsifiable authority — nobody can check it, and when it
  disagrees with reality there is no way to tell which is wrong); **D-N3** a comparison prints TWO
  measurements and no verdict, because a scoring tool becomes a tool people ignore after its first false
  alarm. Keyed per environment (M38.3), offered never automatic (M35–M37 spent three milestones removing
  things that fire at load), and it carries no log data. Slices M39.1–.5; four open questions for the
  owner, the first being where a baseline lives.

## M40 · Audit readiness — follow-up (.1/.2a/.2b/.3 COMPLETE 2026-08-27; the milestone is in completed/tracker.md; post-merge review `docs/handoff/completed/review_main_m40_2b_3.txt`)
- [M40.2c] ☐ **Follow the supertype chain** _(optional)_ — a node extending a project-local base that itself extends
  `EventLogNode` currently lands in UNKNOWN and stays counted. Correct but conservative; resolving one more hop needs
  the file's imports (`EventProcessorModel.resolveSimpleType`).

## M34 · Source adapters — ◧ **.0–.3 MERGED to main 2026-08-25** (format spec + conformance suite published); .4/.5 open
_Design: **[spec-source-adapters.md](spec-source-adapters.md)**. Owner ask: make the app general
purpose by identifying the Fluxtion-specific elements and making them plugins — then write adapters
that transform LangGraph/Temporal runs into the audit-log format and get the whole toolset for free.
The model is not Fluxtion-shaped: *an ordered sequence of cycles, each triggered by an event, each
recording which components ran in what order and what each logged, with a static graph alongside*.
M31 made CONTAINERS pluggable; M34 makes the **engine** pluggable._

_**The asymmetry is the finding, and it is a first-class decision.** The audit log generalises cleanly;
the topology does not. Some engines can hand over a **declared** graph (LangGraph); others only what
was **observed** (Temporal has no static workflow structure — but has native replay, which fits
replay-diff better than Fluxtion does). **Coverage is "declared minus observed"** — with no declared
set there is nothing to subtract from, so the feature that found the POC's 54 dead nodes cannot exist
on such a source. D-A1: adapters declare what they can supply and the core degrades LOUDLY per
capability; inferring a declaration from observed history is rejected because it always reports 100%.
D-A2: a graph is DECLARED or INFERRED and the view says which. D-A5 is the real test — GraphML moves
out of the core and becomes what the Fluxtion adapter uses, because an SPI its own built-in cannot use
is decoration. D-A6: publish the format openly and hold the NAME; the defensibility was never the
schema — it is the reference tool and the disciplines in it._

_**Review amendments (v2):** the spec generalised the record and the graph but **not the ORDER** —
and `nodeLogs` order IS dispatch order in Fluxtion, consumed as meaning by step-through, route
escalation and the M21 classification. LangGraph super-steps, Temporal activities and OTel spans
are concurrent, so an adapter would have to INVENT a total order with nothing on screen marking it
as invented. **D-A1a** adds `ordering: TOTAL | PARTIAL` plus a per-cycle concurrency marker, and
consumers qualify loudly — UP-FLX-11's lesson one level up. **D-A3** gains the attribution rule (a
value appears under a component only if that component produced or changed it — a LangGraph state
channel is SHARED, and broadcasting it would make series into cross-component duplicates that
still "work"). **D-A6**'s fixtures pin SEMANTICS not layout. Graph provenance moved onto the
returned `SourceGraph` because availability is per SOURCE, not per adapter._
- ➜ **M34.0–.3 (the LangGraph spike, the RunAdapter SPI, capability degradation, the format specification + conformance fixtures) — SHIPPED, ARCHIVED 2026-09-15** to [`completed/tracker.md`](completed/tracker.md) per rule 7 (moved verbatim).
- [M34.5] ☐ **Per-cycle concurrency marker — specified in D-A1a, absent from Format 1** _(surfaced by
  writing the spec page, 2026-08-25)_ — a mostly-sequential engine cannot be honest about the cycles that
  were concurrent without declaring the whole source PARTIAL. The M34.0 spike smuggled one through a
  `nodeLogs` item and it resolved as a data series with a mangled value (report §3). Needs a real field,
  a parser change, and the badge/step logic honouring it per record. The spec page says "not in Format 1"
  until it lands; the traced-regime marker (UP-FLX-11) is the other gap it names, and is upstream.
- [M34.4] ☐ **First foreign adapter, out of tree — LangGraph**, the throwaway translator of M34.0
  rebuilt against the SPI: same engine, now a supported source rather than a hand-fed file.
- _**Sequencing.** M34.0 is the gate and nothing else starts until it reports. It and M34.4 were two
  descriptions of one idea at different costs — the earlier draft named M34.4 as "the experiment that
  decides whether the rest is worth building", which is M34.0's job now that the spike is a slice of
  its own. M34.4 is no longer an experiment: by then the question is answered and the work is
  conformance. If M34.0 says a foreign run cannot be made legible by today's tool, no SPI fixes that
  and M34.1–.4 do not begin._

## M11 · Research → monitoring promotion (Grafana) — ☐ FUTURE (vision)
_Design: **[spec-assistant-actions.md](completed/spec-assistant-actions.md) §12**. Two complementary systems: the
analyser answers **unknown, one‑off** questions (forensic, source‑linked, LLM‑assisted); Grafana answers
**known, continuous** questions (dashboards, alerting). The workflow is a **promotion pipeline** — research
a series in the analyser until it's diagnostic, then promote it to production monitoring._
- [M11.1] ☐ **`export_promotion`** (analyser authoring action / File export) — emit a **neutral
  promotion manifest** from the named saved graphs; the named `GraphSpec` is the contract, and A10.8
  built the naming/persistence it depends on. _Renamed and rescoped 2026-08-20 by the decision below:
  the analyser emits the manifest, **an agent renders the Grafana JSON**._
  Manifest contents, all exactly reproducible from the `GraphSpec`: the **series** (keys/formula,
  resolve policy, label), the **allowlist** (the precise `instanceId.key` set the tap must publish),
  **thresholds** from the graph's guides, the pinned **window**, the **rationale** (explanation +
  notes — why this is worth watching), and **provenance** (log fingerprint + analyser version, reusing
  M33's D-I3a identity data).
- _**Decision (2026-08-20) — the analyser emits a manifest; the agent renders the dashboard.** M11.1
  as originally written had the analyser learning Grafana's dashboard schema, which contradicts the
  rule M29 and M31 both settled: **the analyser never learns a foreign format — the agent adapts it.**
  If it is wrong to teach the tool FIX on the way in, it is wrong to teach it Grafana on the way out,
  and a versioned foreign schema is a permanent maintenance tax on a hermetic core.
  The two artefacts have opposite requirements, so they split along the derived/declared seam this
  codebase already uses everywhere (M33 D-I7 rows-derived/presentation-declared; M28.6
  condition-persists/intervals-are-data): the **allowlist must be deterministic and analyser-generated**
  because M11.2's tap consumes it and the bounded-cardinality guarantee only holds if it is *derived*;
  the **dashboard JSON is presentation over a foreign schema** and is agent work.
  What makes it safe is that the manifest is a **checkable contract**: every metric the generated
  dashboard references must appear in the allowlist, and every promoted series must appear as a panel —
  a mechanical round-trip. Fidelity ("the chart I validated is the chart that alerts") is preserved by
  the series definition travelling verbatim rather than being re-derived.
  Consequences: multi-target for free (Grafana, Datadog, Perses) with no schema version matrix; the
  agent contributes what the analyser cannot know — dashboard conventions, folder structure, alert
  routing; and M11.1 becomes a serialisation of state already held rather than a foreign-format
  generator. **M11.2 is unaffected** — it consumes the allowlist either way.
  **Validate before speccing the verb:** have an agent build one real Grafana dashboard from a
  hand-written manifest first. If it has to ask questions the manifest cannot answer, the manifest is
  wrong — the same spike-before-SPI logic as M34.0, for the cost of one dashboard._
- [M11.2] ☐ **Telamin‑side tap plugin** (`serverplugin-metrics` / `-grafana`, *not* an analyser feature) —
  a `LogRecordListener` metrics sink alongside the file sink, publishing selected `instanceId.key` as
  typed time‑series (Prometheus / Influx / Kafka). Route B (tap at source), **not** Loki/LogQL re‑parsing
  of raw `toString()`s (Route A rejected — re‑fights the parser battle, loses NaN/boolean/last‑occurrence
  semantics). Cardinality bounded because the graph is static.
- [M11.3] ☐ **Closed loop** — Grafana alert → open the analyser on that log+window → LLM forensics → root
  cause → maybe promote a new series. **Boundary:** the analyser stays a deep‑dive tool; it does **not**
  become a live dashboard (real‑time viz is Grafana's job — don't duplicate where there's no moat).

---

## Suggested delivery order

_Refreshed 2026-09-21; preamble updated 2026-09-23 for two further releases._ **Shipped since that refresh:**
**1.18.0** (2026-09-23 — audit format 1.1 §1a, a log can say whether it is whole and an unclaimed one reads as
`unknown`) and **1.19.0** (the same day — revision-bound Java source spotlights). Neither closes an item below, so the
sequence is unchanged: 1.18.0 delivered the completeness half of the denominator claim, while the membership half
remains open as DX-02. Shipped since the 2026-09-19 refresh: **1.16.0** (2026-09-20 — M66 design
render; the project-starter journey with explicit session recovery, project landing and the full template catalogue) and
**1.17.0** (2026-09-21 — the tool-agreement block TA-1…TA-8: pairing over every declared node, disagreeing graph copies
announced, confirmation findings, window-edge stability, pending EOF records, Follow through `open {follow}`, and the
chart/topology/report fixes). Both tags are on main with their release objects. Every item of the 2026-09-19 sequence is
shipped, archived or rewritten below: its item 2 (M66) shipped and is archived ▸ *Tidy 2026-09-21*, and its item 3 (M67)
is **no longer queued** — the gate the owner set was the Spring-authoring release and M66's merge, and both are met.

**The upstream picture changed on 2026-09-21, and it supersedes the 2026-09-19 line that read NOT READY.** The starter
tool is published in **1.0.73**; the public standalone setup/validate and changed-graph generation/run pass; both pass-4
reviewers judged the compiler and playground branches READY WITH FOLLOW-UPS. That closes the two gates those reviews held
open — a version pin naming a release that did not contain the module, and an acceptance-13 run that had only ever happened
on a provisioned local fixture. **Still open upstream:** SG-2's hosted **generation/run** clause only. The authoring files themselves shipped in
playground 1.0.74 at `5d6a38a`, with public CI 35929392911 verifying acquisition, setup and validation on the real
hosted archive, so this line's earlier NOT READY was stale for two revisions. It was then briefly recorded as fully
closed on 2026-09-24, which over-read the same evidence: that run attempted no generation.

1. ☐ **The beta blockers** — the active push (▸ *Beta*, sixth draft). **Read with the *Spring-side work block* first:
   B1 is already answered by the released starter, B2 is starter work rather than analyser work, B3's recommendation is no longer
   forced by elimination, since SG-2's provisioning half closed on 2026-09-24, though its hosted generation/run
   half is still open, and B4's jars are M67.1's jars. Items 1, 4 and 5 below share owners and artefacts, so they are
   fewer efforts than the numbering implies.** BETA-B2 new-node stubs must audit before A2 measures
   the product rather than the tester; BETA-B3 the template decision plus a dry run of A1–A2 by someone other than the
   author; BETA-B4 the shared jars and spec-derived check, now implemented and source-reviewed (G18 closed; binary publication/integration remain open). BETA-B1's key journey passed with a
   key present and the owner has settled acquisition for now; BETA-8's Haiku series says the routing line plus the fixer
   block is what makes the fault workflow reproducible, and names the A3 scoring gap to close.
2. ☐ **Finish the tool agreement** — TA-5b implement and vendor the chosen follow route, then TA-5c its one spot-check
   session; TA-9 the analyser half of authoritative dispatch metadata, still blocked on the producer; TA-B category B of the
   September feedback.
3. ☐ **Evidence correctness before any new surface** — now specified as **M68** (`spec-evidence-integrity.md`), and
   no longer optional: it is what a held-out client was told wrongly on 2026-09-24 and what holds G14 open. Formerly — DX-02 the false graph/log mismatch first, then DX-03/04/05, and the
   report-integrity slice (every requested section renders or says why). This is the order the observed trial argued for and
   two reviewers endorsed.
4. ◧ **SG-2 — provisioning closed, hosted generation/run open.** The hosted download carries the authoring record
   and local scripts without breaking the keyless bundle: playground 1.0.74 at `5d6a38a`, public CI 35929392911,
   97 classpath entries, 36 originals unchanged. **What is not closed** is changed-graph generation and run on that
   hosted archive: the cited run has two provisioning jobs and a customer-download job, no generation job, and
   reports `generationAttempted: false`. Needs one acceptance naming the hosted template. This item was briefly
   marked ☑ on 2026-09-24 on the strength of the provisioning evidence; round 4 read the CI log and corrected it.
5. ☐ **M67 the extension tour** (`spec-extension-tour.md`) — **unblocked**: extend a running application with a jar you
   already built, LLM-written Spring XML, the compiler's refusal and the audit log as proof, six spotlit beats. The owner's
   four calls are made. M67.1 the vendor-jar catalogue and M67.2 the template are cross-repo and can start; M67.3/.4 the
   skill, verify script and held-out record follow; beat 4 upgrades when the compiler's refusal names the bean.
6. **M39 baselines** — spec'd since 2026-08-27 with four open owner questions (first: where a baseline lives). The next
   model-level feature; the mixed-version hazard it depends on is built (M38.7, D-C10).
7. **The Mongoose audit format proposal** (`docs/proposals/mongoose-audit-format/`) — binary first, with the format treated
   as a deployment property rather than a build input; owner decisions still open.
8. **The Mongoose bootstrap artefacts** (`docs/specs/mongoose-bootstrap-artefacts/`, reviewed with §10a A1–A4 written in) —
   anchor to `spec-agent-brokered-dev-loop.md` and back its gates with `tools/bench/loop-bench.py`.
9. **M34.4/.5** (first foreign adapter; per-cycle concurrency marker — needs the owner to name the field).
10. **M19.1a** (Mongoose starter conformance bench: D-02 then the first typed slice; no bundle claim before its native audit
    and conditional VAL-12 evidence), **M19.3/.4** (tutorial, publish-gated on the playground Download), **M19.8** (bench in CI).
11. **The small schedulable remnants**, any time: **M64.13** (menu follow-ups), **M65.5** (the D-F5 extraction measurement),
    the person-at-the-screen leftovers (*File ▸ Close log* as a physical click during a slow first load; *AI ▸ Place
    mode-selector record…*), **M48.17** (the canvas-thesis brief — owner call), the golden-fixture taxonomy tranche,
    **M40.2c**, **M20.5**, **M29.5**, **M13.5**, **M21.7–.9**, the **M22** five, **M33.5** (gated), **M33.6**, the M36 rule-1
    upstream ask, and the independent review of the two Spring guide pages (diagrams at desktop and phone width;
    expected-versus-observed labelling — the version wording is already corrected).
12. **Cross-repo** — the §H gate is MET; **UP-MNG-01…04, UP-PG-01…02, UP-RDR-01** in [upstream-asks.md](../proposals/upstream-asks.md)
    §5–§7 are drafted and still unfiled; **UP-MNG-03** has its analyser-side counterpart in M38.3.
13. **M12** (diagnose → fix → prove) stays active design; **M11** stays vision until a real Grafana consumer appears.
14. **Not analyser-session work** — cross-repo or gated, listed so nobody picks them up here: **M50**'s determinism spine
    (compiler), **M52.6** (mongoose), **M57.4** (generator-http shading), **M51** (starter template), and the **M19** tutorial
    (publish-gated on the playground Download).

_Earlier refreshes (2026-09-17 and 2026-09-19) and their release detail are archived in
[`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-21 ▸ Delivery-order history*._

## M46 · Authoring-toolchain repair — ◧ SPEC'D 2026-09-01; the analyser-side closure (A1–A5 + the `open {analysis}` regression) SHIPPED in 1.14.0 and archived; U1–U8, X1–X4, H1–H2 open (upstream / docs / harness)

Spec: [`spec-authoring-toolchain-repair.md`](spec-authoring-toolchain-repair.md). Evidence:
`docs/experience/runs/round-07…10` — 23 fresh-context runs, two model tiers, predictions committed first.

**The framing that makes this one programme rather than a list:** the framework's core claims held
perfectly — **M5 and M6 were never violated by any agent in any run**, and 22 of 23 runs produced a
correct graph. **Every item below is a communication failure, not a correctness failure.**

- ➜ **M46.5/.6/.7/.10 — SHIPPED in 1.14.0, ARCHIVED 2026-09-19** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-19*
  (A1 first-open pairing verified fixed by M44.3; A2 both routes held; A3–A5 the echo's node counts, `openedBy`, the unbound
  step cursor; the `open {analysis}` regression). `tools/verify-m46-agent-api.py` holds them on the built jar (17 checks) and
  is on the pre-release checklist. M46.11 (the capture script's exit status) was archived 2026-09-18.
- [M46.1a] ◑ **U1 REFINED by outside evidence — the message DOES reach the console; the `suggestedFix`
  does not** _(round 62, 2026-09-07)_ — four diagnostics hit while building a benchmark graph, all four
  correct, all four naming the offending fields, two of them naming the likely cause
  (*"the fields [a, b, out] look like node-local state rather than references to other nodes"*,
  *"these fields share a type, so the binding is ambiguous: [b, a]"*). Each took about two minutes to
  act on and none required reading Fluxtion source to **understand**.

  **But the remedy required reading the source, and the remedy was already written.**
  `BuilderDiagnostics` carries a `suggestedFix` for FLX-1001 saying *"annotate the parameters with
  `@AssignToField` when two share a type… a field not explicitly opted into constructor mapping can be
  made NON-FINAL and wired through its JavaBean setter"* — exactly what was needed. `@AssignToField`
  was found by grepping the runtime instead. **The registry holds `rule`, `why`, `suggestedFix` and
  `documentationUrl`; the console received `message` alone.**

  Scorecard from four independent encounters: correctness 4/4, offending element named 4/4, likely
  cause named 2/4, **remedy named 0/4 — though it is written for all of them**. So U1 is narrower and
  cheaper than recorded: this is not "diagnostics do not reach the console", it is **"the fix text
  exists, is good, and is not printed"**. Printing it is the whole remaining job.

- [M46.1] ☐ **U1 · Structured diagnostics never reach the console.** `code`/`rule`/`suggestedFix` go to
  the sidecar; the default path prints a raw `DiagnosticException` in 60–80 lines of stack trace. Round 08
  measured the good version only because it passed `-Dfluxtion.diagnostics.sidecar=true`. **Highest-value
  item in the programme** — the diagnostics work, they just are not where authors read. Upstream.
- [M46.2] ☐ **U2 · `target/classes` lags the generated source by one build.** A run straight after a graph
  change executes the PREVIOUS graph and writes an audit log describing a version that no longer exists.
  Found independently by two Opus agents. Upstream (plugin warning); docs half **DONE** in `142b1e1`.
- [M46.3] ☐ **U3 · `scan` silently no-ops with no builder.** A project containing zero Fluxtion code builds
  green — which let an agent ship plain Java and report all six requirements met. Upstream.
- [M46.4] ☐ **U5–U8 · bootstrap deadlock, audit "setters" that dispatch, lifecycle records, `addEventAudit`
  naming.** Four agents hit the bootstrap; four hit the log pollution. Upstream + docs.
- [M46.8] ☐ **X1–X4 · doc gaps** — how to write the audit log to a file, the `addEventAudit` three-arg
  overload, and the fact that every skill describes a project that does not exist in a fresh template
  (reported by every agent in every round).
- [M46.9] ☐ **H1–H2 · harness.** Parallel runs shared one analyser and one `user.home` — one agent
  `pkill`ed another's JVM mid-sequence. And round 09's own specification was self-contradictory (M3 vs M4),
  found by all four Opus agents, who all invented the same repair. Isolate runs; check a behaviours spec
  for internal consistency before using it as an oracle.

**Deliberately not in scope:** making the compiler catch design errors. Four rounds produced no idiom
error a diagnostic could have caught, and the one real design defect was caught by reading the generated
source.

## M47 — start from a template — ◧ .2/.3/.4 SHIPPED (as M19.5, accepted 2026-08-30; archived 2026-09-19); .1 narrowed to a playground check; the round-17 question open

**Reconciled 2026-09-19 (owner: "some of it already works, I have used it").** This section was written on 2026-09-01
as a proposal and never reconciled with what M19.5 had already shipped on 2026-08-30 — *File ▸ New project from template…*
(`MainFrame`, `TemplateProjectDialog`, `TemplateClient`; spec [`spec-template-from-analyser.md`](spec-template-from-analyser.md);
review `docs/handoff/completed/review_m19_5_template_picker.txt`; user guide ▸ *Projects ▸ Start from a playground template*;
spotlit as `menu:File:New project from template…` since 1.14.1). It lists the playground catalogue's onboarding subset
with each entry's description, takes artifact/group/base-package, downloads the scaffold under the archive-boundary rules,
and makes the generated project's profile the active project. That is .2, .3 and .4 — the templates are the playground's
(14 in the versioned catalogue), not a directory in this repo.

**Owner revision 2026-09-20, implemented on the journey branch:** D-1 requires the full catalogue, with
onboarding entries marked **Recommended starting points** and declared prerequisites shown. The subset
above describes released behaviour. Branch display verification uses all 14 producer entries; review,
untagged end-to-end download acceptance and producer-first deployment remain open. See the live full-catalogue item above.

**The idea.** The analyser offers a set of Fluxtion project templates. A user picks one; that is what
their AI client starts from, rather than an empty directory plus prose.

**Why it belongs here rather than upstream.** A template that already runs produces an audit log on
first execution — which is the one thing the analyser needs and cannot supply for itself. Today a new
user has an analyser and nothing to open.

**The measured case.** Round 16 (`docs/experience/runs/round-16/`) established that four blockers
dominate authoring cost, and that all four are absent from the published reference: the bootstrap trap
(`Main` imports the generated class, so compilation breaks generation), `com.fluxtion` vs
`com.telamin.fluxtion`, parents-are-fields (FLX-1001), and how to run a Maven build. A working
template removes all four **structurally** — there is no sentence to skim past. Two independent agents
and one session author all hit the bootstrap trap *after reading a warning about it*, which is the
strongest available argument that prose is the wrong instrument for this class.

- ➜ **M47.2/.3/.4 — SHIPPED as M19.5, ARCHIVED 2026-09-19** to [`completed/tracker.md`](completed/tracker.md) ▸ *Tidy 2026-09-19*
  (the picker over the live catalogue, scaffold to a chosen directory, the profile becomes the active project, more than one
  template). What .2 proposed as "scaffold" shipped as a download from the playground's `/start/scaffold`, so the templates
  have one source of truth and the analyser copies no names.
- [M47.1] ☐ **Narrowed: do the catalogue's ONBOARDING templates remove the four round-16 blockers structurally?** The
  proposal's `docs/experience/current/template/` is a local artefact the shipped path never uses; the question it stood for
  survives — the bootstrap trap, `com.fluxtion` vs `com.telamin.fluxtion`, parents-are-fields (FLX-1001) and how to run a
  Maven build must be ABSENT from what *New project from template…* downloads, not warned about. Check each onboarding
  entry once; anything missing is a playground item (UP-PG), not ours.

**Open question, not yet answered:** whether a template plus a short pointer beats the full published
doc set, or whether it is additive. That is what round 17 measures.


## M48 · Authoring modes — the catalogue resolver, the mode selector, the scorer — ◧ .1–.4/.11 SHIPPED 2026-09-03, .7 SHIPPED in 1.14.0 (all archived); the rest open

**Canonical architecture:** [`spec-authoring-modes.md`](spec-authoring-modes.md) ▸ *THE TARGET
ARCHITECTURE* — eight stages, each marked MEASURED / IMPLEMENTED / PROPOSED / HYPOTHESIS, with the
`declare → resolve → compile → run → inspect → correct` loop's joints marked pinned or unpinned.
**Stages 5 and 7 are substantially shipped, and stage 6 is PARTLY shipped** — descriptor and GraphML
fingerprinting are test-pinned; only the audit-log header carrier is missing. **Stages 1 and 2 are the
open work**, and the eight stages describe COMPOSITION only: component authoring (modes 2/3) is not
decomposed by them and is not measured.

Specs: **[`spec-authoring-modes.md`](spec-authoring-modes.md)** (the taxonomy, the owner's eleven items,
the delivery order), **[`spec-authoring-mode-selector.md`](spec-authoring-mode-selector.md)** (the end
state, the handoff contract, the analyser's role),
**[`spec-builder-component-resolution.md`](spec-builder-component-resolution.md)** (the builder-owned
resolver, typed Spring document and production acceptance gates),
**[`spec-authoring-session-walkthrough.md`](spec-authoring-session-walkthrough.md)** (four sessions, real
output). Evidence: `docs/experience/runs/round-5[3-7]/`.

**The finding that reorganises the programme.** The bean-file half of component integration is a
**constraint solve, not a model task**. `tools/bean-resolver.py` reproduces the measured-optimal
selection *and* wiring from jar manifests alone, builds green, and produces **byte-identical alerts** to
the reference — at **zero token cost** against the measured optimum's 1.98M weighted / 51 turns. Where
the declared surface cannot decide, it reports the ambiguity and refuses to guess.

**So four modes, and which one you are in is derived, not chosen:**

| mode | who writes what | model needed | status |
|---|---|---|---|
| 0 / 0+ | nobody; a resolver emits the bean file | **no** | **built, verified** |
| 1 | selects components, writes beans | selection only | measured (r55, r57) |
| 2 | describes beans, then writes the nodes | yes | **unmeasured** — playground's `spring-authoring/*` is the baseline |
| 3 | writes a Java builder and the nodes | yes | **unmeasured** — `CLAUDE.md` + golden path is the baseline |

- ➜ **M48.1–.4 and M48.11 (resolver, memoisable selection, mode selector, shared scorer, full chain) — SHIPPED, ARCHIVED 2026-09-15** to [`completed/tracker.md`](completed/tracker.md) per rule 7 (moved verbatim).
- ➜ **M48.7 (the canvas handoff — `open {posture | record}`, `context.handoff`, the Project-panel row) — SHIPPED in 1.14.0, ARCHIVED 2026-09-19** ▸ *Tidy 2026-09-19*; `tools/verify-m48-handoff.py` holds it (18 checks).
- [M48.17] ☐ **The shared-evidence-canvas THESIS has had no independent read.** `spec-shared-evidence-canvas.md` says
  *PROPOSED FOR INDEPENDENT REVIEW, 2026-09-04* and its brief (`docs/handoff/brief_review_shared_evidence_canvas.txt`) has sat
  live since that day. M48.7 applied the canvas's write rules and WAS reviewed; the thesis itself was not, and no tracker
  line owned it until now. Owner call: run the brief, or fold the thesis into `spec-authoring-modes.md` and retire it.
- [M48.5] ☐ **the mode-1 selection asset** — small; how to read a `Fluxtion-Description`, that absence of
      a promise rules a candidate out, how an answer becomes a profile line
- [M48.6] ☐ **`generate-sources` rebind** — measured: in modes 0/1 nothing the author writes is a
      generator input, so a plain `mvn compile` works and the whole ordering workaround
      (`generated.dependents`, the `default-compile` exclusion, the second compiler execution) can go.
      **The analyser ships the template, so this is ours.**
- [M48.8] ☐ **cache accounting in the experiment harness** — Haiku 4.5 silently uncaches below 4,096
      tokens, so any prefix-size comparison without `cache_read_input_tokens` is meaningless (P3a)
- [M48.12] ☐ **audit-log header fingerprint carrier** — narrowed twice. The contract is
      **`fluxtion.sourceFingerprint`**, emitted into the GraphML and generated descriptor and pinned by
      `GraphVocabularyTest` and `DescriptorFingerprintTest`. **Compiler → descriptor is already
      pinned.** What is unpinned is **generated model → audit-log header**: the log header carries no
      model identity. `report.LogFingerprint` answers a *different* question — which log a report was
      authored against — and is not a substitute. **Stage 6.**
      *(Recorded twice wrong: first "no fingerprinting exists" — I grepped `src/` and it is a GraphML
      fact; then "it ships as `descriptorFingerprint`" — that is a private test-helper name.)*
- [M48.13] ☐ **compiler-generated component manifests** — today's are hand-authored. Until the build
      emits them, the resolver result is conditional on a convention nobody's toolchain enforces.
      **Stage 2.** Implementation belongs in the existing `fluxtion-builder` jar and is exposed by the
      Maven plugin. Spec: [`spec-component-catalogue.md`](spec-component-catalogue.md).
- [M48.14] ☐ **resolver + Spring document productisation** — the Python prototype now has **24 smoke
      checks wired into CI**, including reviewed cycle, identity and machine-readable-path regressions;
      that does not substitute for the production test matrix. Port one typed resolution authority to
      the existing `fluxtion-builder` jar, add its own JDK parser/canonical writer for the supported
      Spring subset with **no Spring transitive dependency**, and make the starter a conformance-tested
      consumer. Builder main remains Java 8 compatible; the Java parser is a desktop build/API surface,
      with a CheerpJ load-and-normal-compile smoke gate because its classes share the browser-loaded jar.
      `Fluxtion-Consumes` remains parsed and unused in solving. Spec:
      [`spec-builder-component-resolution.md`](spec-builder-component-resolution.md).
- [M48.15] ☐ **one-command experiment reproduction** — round 57 lacks its jar workspace; M49 lacks a
      run script, pinned dependency provenance and raw output for several tables.
- [M48.16] ☐ **measure goal → formal requirements** — **the largest evidence gap.** Every round in
      this programme was handed the figure list. Stage 1, and a hypothesis until measured.
- [M48.9] ☐ **modes 2/3 — spec, asset, and ABLATION.** The unmeasured half, and where the Haiku ceiling
      actually is. **Every ablation this project has run was over *assembly* guidance**; no authoring
      instruction has ever been ablated.
- [M48.10] ☐ **the dev harness loop** (owner's item 4). The analyser contributes exactly two things — a
      queryable graph and a queryable log. Every judgement in the loop is the LLM's or the human's.

**Relationship to M46/M47.** M46 is *toolchain repair* — communication failures found by measuring
authoring. M47 is *start from a template*. M48 is upstream of both: it says which mode an author is in,
and for modes 0/0+ there is nothing to author at all.

**Upstream filed from this work:** **UP-FLX-45** (the wall clock is 55% of dispatch by default; replay
mode already avoids it — low priority, the author's default is defensible) and **UP-FLX-46**, lodged as
[telaminai/fluxtion#31](https://github.com/telaminai/fluxtion/issues/31) — two classes sharing a simple
name in different packages emit uncompilable code with no diagnostic; a component-market blocker with a
12-line reproduction.

## Decisions (resolved)

- **The LLM operates the application toolchain through documentation and runbooks; the analyser
  renders evidence** _(owner, 2026-09-19, staged-feedback review)_. Bootstrap the LLM's understanding
  of Fluxtion, the analyser, audit logs, Mongoose and Mongoose plugins, then point it to project/task
  runbooks. Application processing, generation, hosting, replay and domain validation execute in their
  owning tools/application/test harness, driven externally by the LLM or developer. The analyser must
  not acquire application execution or orchestration logic, interpret runbooks as commands, or become
  another implementation of domain behaviour. It retains log queries, reductions, scoped comparisons,
  provenance/freshness checks and shared presentation of externally produced results. A validation
  pack is portable documentation, test assets and evidence, not an analyser-run workflow. This narrows
  the fourth feedback addendum's invariant/validation proposals; it authorizes no new execution verb.
- **1.15.0 shipped 2026-09-18** with M64.10/.11/.12 and the two review rounds' fixes; no patch releases between _(owner)_.
- **1.14.0 shipped 2026-09-17 with the whole M46-closure block, M65 and M64.8/.9; no 1.13.3** _(owner)_.
- **This block ships as 1.14.0, together — NO 1.13.3, NO cherry-pick** _(owner, 2026-09-17, after the re-review)_.
  The `open {analysis}` regression released in 1.13.0–1.13.2 (M46.10) is therefore **fixed in 1.14.0, not patched**:
  the second reader recommended cutting 1.13.3 from the fix commit first (A4); the owner declined, and A4 is closed
  as decided, not open. One consequence, stated: the commit that fixes it also REMOVES the `nodes` reply key
  (M46 A3), so that removal is a 1.14.0 change and is announced as one — deliberate, no alias.
- **The merge of `fix/m46-agent-api-closure` does NOT wait for M64.9; the 1.14.0 RELEASE does** _(owner, same day)_.
  M64.8 (the runbook that points) and M64.9 (`coverage` → `topology:verdict`) are ONE skill edit and ONE re-pin,
  on main, after the merge and before that release — so the first release of `spotlight` teaches the intended
  target name and the skills index moves once.
- **The built-jar harnesses are on the pre-release checklist** _(owner, same day)_:
  `tools/capture-conversations.py` and the three `tools/verify-*.py`, in `docs/admin/release-process.md` §4.0, with
  the reason they are there (four reviews, three releases, one path nobody drove). `.mcp.json` is git-ignored.

- **REVERSED the same day — `handoff` is NOT a verb; the shared canvas is written through `open`** _(owner,
  2026-09-17, after the second-reader review's A6)_. `open {posture}`, `open {record}` and
  `open {close: "handoff"}`, mirroring `open {close: "project"}`; reading stays `context.handoff`. **Why the
  earlier decision (below) was wrong:** it argued the canvas write is "not a lifecycle act", so it did not belong
  on `open`. But `open` does not mean "a lifecycle act" — it means **put this in force**, which is what setting a
  posture or placing the selector's record does, and it already carries the one idiom for taking something out of
  force. The verb-ness of M48.7 was two lines of wiring; everything of substance (`CanvasHandoff`'s typed,
  attributed, fail-closed state, `context.handoff`, the Project-panel row) is unchanged. Unreleased, so this was
  the cheapest it would ever be; after a release it would have been a compatibility decision. The surface is
  FIFTEEN: the fourteen, plus `spotlight` (M64) — the one concept no existing verb named. **(True when written; the
  surface became SIXTEEN in 1.16.0, when M66 added `source`. This decision is about `handoff` and stands as recorded.)** One consequence is
  stated rather than hidden: `open`'s other forms name what they ignored, but a canvas write is REFUSED when
  combined with anything else, because shared state may not be half applied.
  _Superseded, kept for the record:_ ~~`handoff` is the fifteenth verb (owner, 2026-09-17: "keep the 15th verb,
  handoff is fine") — the canvas's posture and record are not a lifecycle act, and `context` must stay read-only
  because M42's loopback probe depends on it.~~ The `context`-stays-read-only half still holds. The rule the four
  guard tests state also still holds, and is the rule this reversal applies: **the surface does not grow for a
  concept an existing verb already names**; the NEXT verb still has to clear that bar.
- **A close supersedes a pending open of the same kind** _(owner, 2026-09-17; M44.3b)_. Close log, close all and
  Reset take the id of an outstanding log open, so its late result is refused; closing the graph alone does not,
  and a close with nothing outstanding changes nothing in the gate. It is D-A3 — the last deliberate request
  wins — applied to one more request, not a new rule.
- **Component resolution and Spring manipulation live in the single existing `fluxtion-builder` jar**
  _(owner, 2026-09-03)_. The builder owns catalogue generation, the typed resolution result and a small
  parser/canonical writer for the supported Fluxtion Spring authoring subset. It adds no transitive
  Spring dependency. The Maven plugin is an invocation surface, not a second implementation; the
  starter remains the browser editor and consumes the shared document contract and conformance corpus.
  Builder main remains Java 8 compatible; the JDK parser is not a browser surface, and a CheerpJ smoke
  test guards against breaking the jar that the playground loads. `fluxtion-runtime` is untouched.
  Canonical production spec:
  [`spec-builder-component-resolution.md`](spec-builder-component-resolution.md).

- **The commercial model, and where the analyser sits in it** _(owner, 2026-09-01)_. Recorded because it
  bears on **D-S1.2**, the open licence choice that blocks a release, and because the reasoning would
  otherwise be lost in conversation. **Owner's model:** the compiler is priced (hosted, or **hosted
  on-prem for enterprises**, which removes generation friction while keeping enforcement); the analyser is
  **per seat per month**; **redistribution/deployment licences are required for the generated event
  processor and for Mongoose**; and auditors are a pricing dimension.
  - **The deciding argument is redistribution, and it settles the shape.** *"One generation deployed in a
    million drones."* Generation is a one-time act; the artefact replicates without limit. Pricing only
    generation sells a million-unit fleet for the price of one build, so value capture has to sit at
    deployment. That is enforced by contract, procurement and audit rights rather than by code — the
    generated processor is committed Java in the customer's tree, with no wire to gate — which is a normal
    enterprise model but a **different competence** from the rest of the stack, and should be resourced
    deliberately rather than discovered at the first renewal.
  - **Consequence the owner's own model implies: the compiler gate is then a tax on the funnel.** If the
    revenue is at deployment, the drone customer pays regardless of what generation cost, while a public
    repo or a thirty-node experiment never deploys anything and so collects nothing — the gate only
    suppresses the reading and trying that feed the deployments. The narrow ask that survives is
    **generation free for public repositories**, on evidence grounds rather than price sensitivity: an
    open Fluxtion project is the only place a prospect can read a real graph, its annotations and its
    generated dispatch before spending. **This repository is the case in point** — anyone can clone,
    build, test and fix the analyser, and nobody without a key can add a node to the one part that
    demonstrates the thesis.
  - **Withdrawn after owner pushback: "free at entry is required".** That was reasoning from the
    open-source-tooling era. Developers now pay per seat and per token for tooling as a matter of course,
    so entry friction is a far weaker objection than it was, and on-prem hosting removes it for the buyer
    who matters. The public-repo carve-out above is argued from *evidence access*, not from price.
  - **The one part still argued against: charging per auditor.** Two reasons, the first specific to a
    known framework constraint. **(1)** Invocation tracing is fixed at generation time (UP-FLX-37 /
    `fluxtion#25`) — so a customer who wants more audit detail mid-incident must regenerate and redeploy.
    Metering that means metering the thing they most need at the moment they can least obtain it, and the
    incident where the product could not help is the story that travels. **(2)** Auditors are how the
    audit log exists, and the audit log is what creates demand for analyser seats — the razor, not the
    blade. Most graphs need exactly one auditor, so the revenue is small while the incentive it creates
    ("use fewer auditors") points against *audit everything, always*, which is the strongest claim in the
    product. **Node count is a cleaner complexity proxy** and tracks the measured value curve (≈0 glitch
    sites under 10 nodes, ≈0.1/node above 50) without discouraging anything.
  - **Per-seat for the analyser is right and needs no metering** — its value grows with graph size and
    incident count, which seats already track.
  - **One trade to state publicly rather than discover:** an on-prem compiler loses the "the generator
    improves without every project upgrading a plugin" property that hosted generation provides.

- **A processor with no source keeps offering "Add source", whatever the cause** _(owner, 2026-08-30;
  re-affirming 2026-08-27)_. The live v4 bundle raised a case the original decision may not have had in
  view — a declared processor class, no generated source shipped, and `src/main/java` already configured,
  so adding a root cannot help. **The owner's answer is to keep the button as it is.** The Project panel's
  *wording* still distinguishes the two causes, which is where the correction belongs: one remedy button
  that is occasionally unhelpful beats a row whose control changes shape depending on why something is
  missing. `ProjectModelTest` records the target as deliberate so a later reader does not "fix" it.
- **API key at rest:** stored **cleartext** in `~/.fluxtion-analyser/config`.
- **Display time zone:** **UTC** for all date/time rendering.
- **EventProcessor:** **infer when possible** by scoring candidate processors' `instanceId→field`
  sets against the log's observed instanceIds; fall back to configured/default
  `DemoMarketMakerStrategy` (user can override). Implemented in M4 (needs source parsing).
- **Server control is not an assistant capability** (spec-closed-loop §B.5) — server verbs never
  appear on the action socket; any future agent-initiated server action requires per-action human
  approval. Keeps the FAQ's security guarantee simple and true.
- **Agent fixes arrive as evidence-linked PRs on a branch, never direct edits** (spec-closed-loop
  §A.4) — the M12 guardrail, embedded verbatim in every generated brief.
- **O5 RESOLVED (2026-08-15) — the analyser and `svc-admin-web` complement, and the overlap is
  deliberate.** The analyser is a **dev tool _and_ a production-support tool**; web-admin views **one
  live server** whose log may be rolled or deleted, and suits dev + MCP-driven poking. A low-latency
  production system may have **no admin-web and no MCP at all** — logs are transported to a shared store,
  and **offline analysis across many files is where the analyser shines**. So the topology view and event
  step-through are **replicated into the analyser** (**M21**), because the good view has to exist where
  the logs actually land. Consequence: the analyser needs the **GraphML**, sourced from a file first and
  the server only when one happens to be there.
- **Distribution is the shaded fatjar + JBang; no native bundles** _(2026-08-27)_ — `jbang app install analyser@…`
  is the one-command install (JBang supplies the JDK), `~/.jbang/bin/analyser` is a stable launcher path for MCP
  configs, `--rest` enables the transport without a config edit. A `jpackage`/Homebrew milestone (M41) was spec'd
  and withdrawn the same day: it would have added a Dock icon, a four-runner release matrix and a code-signing
  bill, and solved nothing a user has asked for. Reopen only for a real user who cannot run JBang.
- **The MCP server identity is `fluxtion-analyser`; its executable need not share that name** _(2026-08-27)_ — a
  client registration names the server independently and launches a resolved absolute command. The current JBang
  launcher remains `analyser`; M42 uses it rather than making an install-name migration a prerequisite. A compatible
  `fluxtion-analyser` JBang alias is welcome only after it has been proven to coexist and upgrade cleanly.
- **Rendering stays Swing/Java2D — no embedded browser.** Reusing the JS replay engine via JCEF/JavaFX
  WebView would cost a ~100MB native per-platform dependency and destroy the single shaded fatjar that
  `jbang analyser@…` depends on. FlatLaf remains the only runtime dependency; a hand-rolled layered
  layout is the work, with pure-Java ELK as the fallback (spec-graph-replay §3).

## Open questions

- ~~**When the playground lands a numeric `price` node-log key, the tutorial's graph step becomes
  executable**~~ — **CLOSED 2026-08-30, the same day it was raised.** The playground shipped the numeric
  keys, the tutorial step is executable rather than illustrative, and the figures were re-shot. Left struck
  rather than deleted because a question that was live for four hours is still evidence of how the two
  repos actually worked.


- Graph "last occurrence per record" vs "all occurrences" default. (spec: last; expose toggle.)

_(spec-closed-loop O1–O4 all resolved — statuses recorded in the M18 block above; O5 in Decisions.)_

### Tool-agreement final gate correction

**Frozen prediction:** the complete display gate caught five recovery assertions because TA-7
added Follow fields to an otherwise absent log map. Guard those fields with the loaded-store
boundary; a closed log again has no `context.log`, while Follow remains exposed on open logs.
`SessionRecoveryFrameTest` should then pass unchanged. This is a regression in this branch,
not a new baseline row; analyser 1 / upstream 8 remain open.

**Result:** held; all 50 display tests pass without skips. Clean headless gate 1,766 / 0 / 0 / 49 skips.
