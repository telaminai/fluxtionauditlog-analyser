# Review, round 3 — `spec-mongoose-audit-production.md` at `71462376` (reviewer: claude)

**Verdict: CHANGES REQUIRED.** The rescope is right in direction, and the bundle check that settled
OD-2 reproduces. But four things block building from this revision:

1. **MA-2 is not specified well enough to build.** Its own D-MA4 contradicts OD-4. The `backend` switch
   that OD-4 depends on is silently ignored by Mongoose today. The text backend's knock-on effects on
   `svc-admin-web` belong to no item. And the marker's lifecycle is unstated: when it is written, on
   which thread, and on restart or roll.
2. **AFMT-3 reproduces today on the developer download.** A marked file holding a record it corrupted
   reads **`complete`, with no finding** in the released analyser. So AFMT-3 blocks MA-2, not "MA-1.3"
   (which no longer exists) and not OD-1.
3. **MA-0 has no acceptance at all**, and it is the first thing to be built.
4. **MA-5's fix cannot be built as worded.** `DataFlow` has no getter for the current listener, and the
   code's own comment says so.

MA-1 has one real problem as well. It keys on `customHandler`, but the population that gets silence is
**any processor without an `EventLogManager`**. That includes AOT processors built without audit, which is
the low-latency profile OD-1 exists to keep reachable.

Reviewed in my own worktree at `71462376` on branch `review/mongoose-audit-production-r3`. Nothing in the
spec was edited, implemented, merged or released. Only this file is committed.

---

## F1 · High · MA-1 keys on the wrong thing: the silent population is "no auditor", not "`customHandler`" (read + prior run)

**The rescope itself holds.** I downloaded the bundle myself: 200, 66,269 bytes, sha256 `4a210ca0…c82b7bc4`.
That is **not** the spec's 65,364 bytes, so the bundle has changed since the author's download. Pin
evidence by digest, not by size. The substance reproduces:

- `customHandler` occurs nowhere in the bundle;
- `MarketProcessorSupplier` loads `generated/MarketProcessor`, which declares
  `public final transient EventLogManager eventLogger` (line 77), wires `eventLogger.clock` (134) and calls
  `initialiseAuditor(eventLogger)` (140);
- `riskCheck` and `rootNode` are registered.

**Does a real population of `customHandler` users lose anything under (ii)?** No, for audit. They get
0 records today, measured, so a refusal takes away nothing that works. (ii) is right for them.

**The swing does go too far in one place: the discriminator.** The bundle turns audit on through the
designer XML (`<property name="logLevel" value="INFO"/>`, `application-context.xml:47`). An AOT processor
built **without** audit has no `EventLogManager` at all. The analyser already records this, verified by
running: `AuditReadiness.java:16-19` says that without `addEventAudit()` the result is "an empty file.
Not degraded, not partial: nothing". `NO_NODE_LOGS`' own message also names that cause first.

Such a processor, listed in `autoStart` or started through `audit.start`, behaves exactly like the
wrapper path:

- the capture sink is created and reports recording;
- nothing is written;
- `POST …/audit/level` returns 200, because `WebAdminService.handleSetAuditLogLevel` (`:801-850`) never
  checks whether the processor has an auditor.

Under **OD-1 = opt-in**, that is not an edge case. It is the profile the owner said must stay
reachable.

**Required.**
- Scope MA-1 to **"a processor with no `EventLogManager`"**, detected by capability. The check is
  `getAuditorById("eventLogger")`: it resolves on a generated processor (public field) and throws on
  `DefaultEventProcessor`. One check covers both populations. Note the `AuditReadiness` N1 limit: a
  custom auditor would be refused wrongly, which is the safe direction to be wrong in.
- MA-1.1 must say whether it **refuses** or **warns**. These differ materially. A hard refusal at boot
  would stop a server that has one unaudited processor in `autoStart`, which is exactly the mixed
  deployment opt-in creates. Recommended:
  - the server boots;
  - `start(name)` for that processor refuses, naming the reason and the remedy;
  - the sink is never listed as recording.
- MA-1.2 lives in **`svc-admin-web` (mongoose-plugins)**, not core. Name the repository and the status
  code (409 or 422, with a body naming the reason), or "not 200" will be met by a 500.
- MA-1.1 says "configuring audit capture for a processor", but capture is configured server-wide. Name
  the three triggers that act per processor: `autoStart`, the `audit.start` admin command, and
  `MongooseAuditCaptureService.start(name)`.

## F2 · High · MA-2: D-MA4 contradicts OD-4, and the backend switch OD-4 depends on is ignored (read)

**D-MA4 (`:145-148`) is false for the writer OD-4 chose.** "The separator is not this writer's job; it
belongs to the export formatter" was right when the marker travelled through the export. OD-4 makes the
developer default Mongoose **writing the text file directly** (`:124`, `:207-212`), with no export in the
path. Then the writer owns:

- every `---` between records;
- the one after the marker (§1a rule 1).

`ProducerDiagnostics`' own `UNSEPARATED` message says the sink must `append("---\n")`. D-E8's premise
("under today's exporter") does not apply. D-MA4 needs splitting: the Chronicle path keeps the exporter's
separator, and the text path writes its own.

**`backend` is read by nothing (read).** On Mongoose `develop` `17a03b4`:
- `AuditCaptureConfig.backend` (`:58`) has no caller of `getBackend()` anywhere in `src/main`;
- `MongooseServer` (`:210-214`) constructs `ChronicleAuditCaptureService` whenever `enabled` is true.

So `backend: text` is accepted and silently ignored today, and so is a typo. The OD-4 switch is currently
a config change that does nothing and says nothing. That is this spec's defect class.

**D-MA3 is aimed at the wrong key.** "A `streamEnd` value … refused at configuration time" (`:140-143`)
has no referent: `streamEnd` (`normal`/`stopping`) is chosen by the writer at the moment it stops, not
configured. The value that needs refusing by name is **`backend`**.

**The text backend's blast radius is in no item.**
- `DirAuditIntrospectionService` is constructed over the Chronicle service (`MongooseServer.java:213`).
- `svc-admin-web`'s file listing, export and websocket tail all read Chronicle.
- Under `backend: text` each of them must either become backend-aware or refuse by name. Otherwise the
  admin UI shows no files and the tail connects and delivers nothing, which is D12's shape again.
- The bundle's `export-audit.sh`, the `run-mongoose-server` skill ("Mongoose does not write
  analyser-readable YAML directly") and AF-6 all change with it.
- This is MA-2 or MA-4 scope, and neither lists it.

**Which backend gets a marker is undecided.** MA-2 describes the text writer only. The deployed
configuration keeps Chronicle, and its exports will read `unknown` for ever unless the capture appends a
marker excerpt at stop, which the 1.0.44 exporter would then terminate. Problem 2 (`:27-28`) says
"every export reads `unknown`", and as specified MA-2 does not change that for any export.

**Required.**
- Split D-MA4 by backend.
- Retarget D-MA3 at `backend` (and at any other value that is actually configured).
- Add backend selection plus a backend-aware or refusing `svc-admin-web` to MA-2 or MA-4.
- Decide the Chronicle marker explicitly, even if the answer is "not in this spec". The status line's
  "none blocking" (`:6`) is then false until that is decided.

## F3 · High · MA-2's marker lifecycle is unspecified (read, against the stream-end spec)

"Ending with a stream-end marker" does not say when. An implementer has to choose each of the following,
and each has a wrong answer:

| Question | Why it matters |
|---|---|
| **When** is it written: `stopRecording`, the server shutdown hook, roll? | Missing any of these leaves a clean stop reading `unknown` |
| **On which thread?** | Records are appended on the processor thread. `stop` comes from the admin thread. A marker written from the admin thread under load can count a record that has not been appended yet, or miss one being appended. Either gives a wrong count on a clean stop |
| **Restart:** new file, or append? | Appending after a crash puts the crashed run's unmarked records into the next run's segment. The next marker counts only its own run, so the reader reports `more_than_declared` on a file that lost nothing (stream-end D-E3, per-segment counting). A new file per start avoids this, and it also removes the "export is cumulative" trap for text |
| **Roll:** by day or size, as Chronicle does (`FAST_DAILY`, `rollSize`)? | Every rolled set reads `unknown` by design (D-E5: a set is never `complete`). If text rolls, say so and say that each file carries its own marker. Otherwise "developer opens the file and sees `complete`" is false after midnight |
| **Retention** (`retainHours`) | The janitor deletes files. Say whether it applies to text |

**Kill points (MA-2.3, `:156-159`): the scoping is right.** Add two cases:
- killed **between** records (after a separator, before the next record) → `unknown`;
- **stop under load** → `complete`, with `declaredRecords` equal to the records actually appended.

The second is the thread question as a test.

**MA-2.2 and MA-2.4 are written for the export path.**
- MA-2.2, "an export without one reads as today", has no meaning for a direct writer. Restate it as: a
  text file whose writer never reached stop reads `unknown`, and MA-0 fires if it is empty.
- MA-2.4's reason, "a known-good export has no trailing separator", has been **stale since 1.0.44**, which
  terminates the last document (D-MA4 itself says so). For the text writer, "known-good" needs a named
  baseline: say, the 1.0.44 export of the same run captured by Chronicle, compared modulo the marker.

## F4 · High · AFMT-3 reproduces on today's download, and a marker vouches for the corruption (RUN)

**Reproduced** on the freshly downloaded bundle's `MarketProcessor`, compiled against
`fluxtion-runtime-1.0.16` (the bundle's version), with a sink on `eventLogger`:

| Control event before one `PriceEvent` | Record |
|---|---|
| none, global INFO/DEBUG, per-node DEBUG/INFO/TRACE | well-formed |
| per-node **ERROR** or **WARN** on `riskCheck` | well-formed; `riskCheck`'s info lines correctly suppressed |
| global NONE | no record, which is correct |
| unknown node NONE | well-formed |
| **`riskCheck` NONE** | `mainonPriceEventPriceEvent{symbol=AAPL, price=195.3, volume=1200}195.31200`. No header, no keys, and `rootNode`'s values run together |
| **`rootNode` NONE** | the whole record is `mainonRiskCheck` |

So the defect is **per-node NONE on a registered node, and only that**.

**The tracker's repro no longer reproduces it.** `LevelTest3.java` targets `volumeTotal`, which is not a
node in today's bundle, so re-running it shows every case clean. Anyone checking whether AFMT-3 is "still
live" would conclude it is fixed. Update the repro to `riskCheck`/`rootNode`.

**Why it is a dependency of MA-2 (RUN, published 1.19.0 jar, digest `826daf64…`).**
- A file holding good record, AFMT-3 record, good record, then a marker declaring 3 reads
  `{state: complete, recordsRead: 3, declaredRecords: 3}` with **no finding**.
- The corrupt document carries no `eventLogRecord:` key, is counted as a record, and nothing flags it.
- With MA-2 shipped, the marker turns that silence into a verified completeness claim.

**Reach (read).**
- The admin endpoint sets levels **globally only** (`handleSetAuditLogLevel` reads `level` alone), so a
  REST user cannot trigger it.
- Java code can, through `DataFlow.setAuditLogLevel(level, sourceId)`.
- The corruption is in the runtime's record encoder, so it reaches JUL, Chronicle and a text writer
  alike.

**The stated reasons are stale** (`:296-298`):
- "MA-1.3 drives levels through the same control event": MA-1.3 is now "a regression check" and the
  level-driving acceptance is superseded;
- "OD-1's `NONE` default": OD-1 is opt-in, and the `NONE` default is UP-FLX-51's;
- "See the Ordering block": that block says "see below". The two pointers point at each other, and
  neither says **what AFMT-3 blocks**.

**Required.** State that AFMT-3 **gates MA-2's acceptance**, meaning a marked file must not vouch for a
record the runtime corrupted. Either:
- the runtime fix lands first; or
- MA-0 grows a check for a document that carries no `eventLogRecord:` key (cheap, same class, see F5),
  and MA-2's acceptance includes a per-node-NONE run.

Keep the UP-FLX-51 warning as a second, independent reason.

## F5 · High · MA-0 has no acceptance, and "qualifies the verdict" risks a seventh state (RUN + read)

`:61-69` is two paragraphs. There is no acceptance, no finding name, no regression check, and no
statement of where it goes. It is the first thing the author would build.

**Does it close case B? Yes, if it is keyed on `index.size() == 0` and placed before the early return at
`ProducerDiagnostics.java:135`.** Today every empty shape returns there with no finding. Run on the
published 1.19.0 jar:

| Case | state | findings today |
|---|---|---|
| A: marker declaring 0, nothing else | complete | none |
| B: today's empty export | unknown | none |
| G: zero bytes | unknown | none |
| H: whitespace only | unknown | none |
| I: two empty marked segments | complete | none |
| C/D: five records, all without node entries | complete/unknown | `NO_NODE_LOGS` (already right) |
| F: an AFMT-3 record under a marker | complete | none (F4) |

The quiet-level case (the spec's "1 record at WARN") is **already** caught by `ONLY_CONTROL_EVENTS`,
because that one record is the control event. MA-0 needs only the zero case, and the spec should say so,
or someone will widen it.

**"Qualifies the verdict … 'complete, and empty'" (`:64-65`) must not become a state.** The six states
are a published contract (§1a, D-E3) that three store paths and `context` agree on. Make it a
`ProducerDiagnostics` finding beside an unchanged `complete`.

**Required acceptance, proposed:**
1. A, B, G, H, I and a rolled set of empty members each raise the empty-log finding as a **warning**
   (not a `COMPLETENESS_NOTE`) on the status bar, in the tooltip, in `context` and in the report. The
   stream-end state is unchanged in every case.
2. C, D and E are unchanged: `NO_NODE_LOGS` still fires alone on C and D, and E stays clean.
3. In Follow, an empty file opened before its first record shows the finding. It **clears on every
   surface** when a record arrives. (Diagnostics are recomputed on `added > 0`, `MainFrame.java:4375`,
   but the status text is set at open; assert both.)
4. A file whose zero records come from SOURCE_DAMAGE says both things, damage first, matching the
   existing damage-first ordering.
5. Optionally, per F4: a document with no `eventLogRecord:` key is named (F4).
6. Each is a fixture in the conformance corpus, with a mutation witness proving it fails without the
   change.

## F6 · High · MA-5: the fix can't be built as worded, and the acceptances miss three silent cases (read)

**No getter.** `DataFlow` in `fluxtion-runtime-1.0.16` exposes `setAuditLogProcessor` and no getter
(`javap`). The code's own comment at `ChronicleAuditCaptureService.java:227-229` says: "We can't read
back the existing listener through Fluxtion's API." So "keep the previous listener" (`:259`) needs a
mechanism. The one available without an upstream change: Mongoose already owns the listener it
installs (`MongooseServer.logRecordListener`, `:116`, set at `:758` and `ServerConfigurator.java:126`),
so pass it into `attach`/`start`. Otherwise it's an upstream ask for a getter. Name which.

**"The previously installed listener" is ambiguous.** The processor author's own listener is already
gone before capture starts. The bundle's `MarketProcessorSupplier.java:29` installs `System.out::println`
("the default JUL sink is invisible in the Playground console"), and `MongooseServer.java:758` replaces
it on registration. That fits the JUL-prefixed console seen on the A2/A3 runs. So the listener MA-5
restores is Mongoose's, and the bundle's listener is dead code under Mongoose. Say which listener the
acceptance means. The bundle comment belongs to MA-4.

**Do the acceptances catch both failures? Yes, as written, provided the test installs its counting
listener the way Mongoose does.** Add three silent cases:
1. **start → stop → start**: no double wrapping, and each record reaches each destination exactly once.
2. **Re-registration while recording (read, not run).**
   - `stopProcessor` removes the processor but does not stop capture (`MongooseServer.java:849-853`).
   - Re-adding the name calls `attach`, which swaps in the new `DataFlow` (`:82-91`).
   - Then `start` returns early because the sink `isRecording()` (`:101-104`).
   - The new instance keeps the JUL listener, the capture file receives nothing, and `liveSinks` says
     recording.
   - I did not run this; it is a plausible fourth silent path and cheap to test.
3. **Isolation:** a listener that throws must not stop the other destination receiving the record.

Make fan-out and restore a contract of `MongooseAuditCaptureService`, tested for **every** backend.
F2's text backend will otherwise reintroduce MA-5a.

## F7 · Medium · Structural: live content sits in the appendix, and the body still carries the old MA-1 (read)

The appendix is labelled "none of this is load-bearing … read it only if revisiting whether an auditor
should be installed" (`:343-347`). Against the body:

| Where | What contradicts |
|---|---|
| `:477-504` OD-1 | The **live** OD-1 decision the status line cites (`:6`) exists only here. So does its surviving caution: "a `NONE` default must not ship onto an undiagnosed corruption path" (`:498-499`). Both are in the section readers are told to skip |
| `:554-566` | The bundle check that **decided OD-2** is evidence for a live decision, but it lives in the appendix and is missing from "Verified by running" (`:323-333`) |
| `:501-504` | "A user who explicitly opts in gets silence." Under the rescope there is no opt-in mechanism for this path: OD-1's flag was for installing an `EventLogManager` in `DefaultEventProcessor`, which is withdrawn. **OD-1 is moot in the same way OD-3 is.** The live opt-in is the existing AOT build-time `addEventAudit`/`logLevel`. Say that rather than "decided" |
| `:68-69` | "MA-1 stands on its own merit — the handler's log lines": the withdrawn MA-1 |
| `:182` | "customHandler … produces nothing … Fixed by **MA-1**": MA-1 no longer fixes it; it says so |
| `:196-199`, `:203`, `:233-234` | MA-4 still says "MA-1 and MA-2 make audit logging possible" and "MA-1 and MA-2 have not delivered". MA-1 is not on the developer journey (the author's own bundle check) |
| `:296-298` | AFMT-3's reasons cite the superseded MA-1.3 and OD-1 (F4) |
| `:305-306` | The only live Ordering block is a subsection of a section titled **WITHDRAWN** (`:30`, `:71`). Promote it to its own `##` heading: "skip the withdrawn section" is a reasonable reading habit and would skip it |
| `:335-336` | "Not established: whether any AOT path in Mongoose is also affected." It is established: AOT **without audit** is affected (F1), and the bundle's AOT **with** audit is not |
| `:6` vs `:215` | "Open owner decisions: none blocking" beside "Still open under OD-4" and, after F2, the Chronicle-marker question |
| `:361` vs `:520` | Inside the appendix, one auditor list omits `serviceRegistry` and the other says it was omitted |

The pattern is the one the author named: each rescope added new text and left in place the text it
replaced. The appendix makes this worse, because it tells readers to skip exactly where the leftovers
sit. **Recommended:** move OD-1 (restated as moot or status quo) and the bundle evidence into the body,
then re-read every body mention of "MA-1" against the one-line definition at `:89-90`.

## F8 · Low · Smaller points

- **Evidence pinning:** the bundle size in the spec (65,364) no longer matches (66,269). Record the sha256,
  and say the bundle is regenerated on demand.
- **MA-4 acceptance:** "downloads Mongoose and follows the getting-started path" should name the
  playground `analyser-bundle` as that download, and list what changes there: `server-config.yml`
  `backend`, `export-audit.sh`, the `run-mongoose-server` skill, the bundle's dead `System.out`
  listener (F6).
- **MA-3** pointer: fine.

---

## Required corrections, in order

1. **MA-0:** add the acceptance (F5); state that it is a finding and not a state; place it before
   `ProducerDiagnostics.java:135`.
2. **AFMT-3:** state that it gates MA-2; replace the stale reasons; update the repro's node name (F4).
3. **MA-2:**
   - split D-MA4 by backend;
   - retarget D-MA3 at `backend`;
   - add backend selection and the `svc-admin-web` consequences;
   - specify the marker lifecycle (when, thread, restart, roll, retention);
   - add the two kill points;
   - restate MA-2.2 and MA-2.4 (F2, F3).
4. **MA-5:**
   - name the listener mechanism, given there is no getter;
   - name which listener is "previous";
   - add start/stop/start, re-registration and isolation;
   - make it a contract of the capture service (F6).
5. **MA-1:**
   - discriminate by capability, not by `customHandler`;
   - decide refuse versus warn, as boot-safe;
   - name `svc-admin-web` and the status code;
   - name the three triggers (F1).
6. **Structure:** fix the items in F7's table, and re-mark the status line's "none blocking".

## What I ran versus what I only read

**Ran:**
- the public bundle download: 200, 66,269 bytes, sha256 `4a210ca0a4b71a88ecb964da0cded066e2d73942346bf292ebd14149c82b7bc4`;
  I searched it for `customHandler`, `eventLogger` and audit config;
- AFMT-3 on that bundle's `MarketProcessor` against `fluxtion-runtime-1.0.16`: the original repro, then
  every level on `riskCheck` and `rootNode`;
- nine cases (A–I) against the published analyser 1.19.0 jar (digest `826daf64…`), through
  `HeapLogStore` and `ProducerDiagnostics`, including the AFMT-3-under-a-marker case.

**Read, not run:**
- Mongoose `develop` `17a03b4`: `ChronicleAuditCaptureService` (attach, start, stop, the no-getter
  comment); `MongooseServer` (`:116`, `:210-214`, `:758`, `:770-773`, `:849-853`); `ServerConfigurator`;
  `AuditCaptureConfig` (`backend` unread);
- mongoose-plugins `3f5fd03`: `WebAdminService.handleSetAuditLogLevel`;
- `DataFlow` via `javap`;
- the analyser's `ProducerDiagnostics`, `AuditReadiness` and the MainFrame follow refresh;
- stream-end spec D-E3, D-E5, D-E8.

**Not done:**
- no booted server;
- the F6 re-registration path is unrun;
- no build of an AOT processor without audit (F1 rests on `AuditReadiness`' recorded run of 2026-08-27,
  not a new one);
- no measurement of `EventLogManager` cost.

The experiment sources (probe cases A–I, `LevelTest4/5`) are in my worktree under `.review-tmp/`,
uncommitted.
