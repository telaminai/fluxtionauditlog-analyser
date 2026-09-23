# Re-review — `spec-mongoose-audit-production.md` at `56025db2` (reviewer: claude)

**Verdict: CHANGES REQUIRED.** MA-4's logging-versus-persistence split is **right in substance**, and I
confirmed it in code and on a running server. But persistence does not add to logging, it **replaces** it,
and stopping capture leaves audit output going nowhere. That is the same silent-no-op shape this spec
exists to remove, and MA-4 doesn't mention it. Separately, **six findings from my first review
(`2718495e`) are still open**. That review was left uncommitted in my own worktree, as instructed, so it
never reached the author. It is attached below, restated briefly, and should be sent with this one.

Reviewed in my own worktree at `56025db2`. Nothing edited, implemented, merged, pushed or released.

---

## Part 1 — MA-4, checked first as requested

### Confirmed (read, and consistent with a running server)

| MA-4 claim | Evidence |
|---|---|
| Mongoose installs a default `LogRecordListener` at boot | `MongooseServer.java:116`, a static lambda `logRecord -> log.info(logRecord.toString())`; `@Log` (Lombok) at `:112` makes `log` a `java.util.logging` logger |
| It is set on every processor | `ServerConfigurator.java:126` and `MongooseServer.java:758` call `setAuditLogProcessor(logRecordListener)` |
| Core's own code uses `auditLog` | `BatchDtoHandler.java:15,17` — at **DEBUG**, so invisible at the default INFO level (`ServerConfigurator.java:106`) |
| Persistence is off by default | `AuditCaptureConfig.java:49`, `enabled = false` |
| When on, it writes a Chronicle queue | today's template run wrote `audit/marketProcessor/20260923F.cq4` + `metadata.cq4t` |
| Console records are not analyser-openable | JUL prefixes every line (`INFO: eventLogRecord:` plus a date line per record), which breaks Format 1 framing |

### M4-1 · High · persistence REPLACES the default listener; it does not add to it (read, consistent with a run)

`ChronicleAuditCaptureService.java:226-234` says the capture listener is composed "IN FRONT of whatever
listener mongoose already installed … we wrap our listener with a delegate that fans to the previous one".
The code does neither: it sets `this.previousListener = null` and calls
`dataFlow.setAuditLogProcessor(captureListener)`, so the JUL listener is simply **replaced**.

On today's template run (persistence on, `marketProcessor` auto-started), the server console for the last
start holds **5** audit records — four `EventLogControlEvent`s and a `LifecycleEvent`, all at startup —
and then none, while events kept flowing to Chronicle. That is what replacement predicts. (I'm not
using a record-by-record comparison with the export: the export in that directory covers an *earlier*
start in the same session, so the two sets don't overlap by construction.)

**Why it matters for MA-4's table.** "Audit records reaching a listener — ON by default" is true **only
while persistence is off**. Turning persistence on moves records off the console and into a file only
the export endpoint can read. The table presents the two as additive; they're alternatives.

### M4-2 · High · stopping capture leaves audit output going nowhere (read)

`stopRecording()` (`ChronicleAuditCaptureService.java:237-246`) installs `NoOpLogRecordListener.INSTANCE`,
not the previous listener. So after capture is stopped — through the admin API, for example — the
processor's audit records go to **neither the console nor a file** until the server restarts. The comment
promising to restore "the previous listener … in stopRecording" is false, because the previous listener
was never kept.

That's this spec's defect class exactly: a live processor, auditing apparently on, output silently
discarded. **Required:** MA-4 should name both M4-1 and M4-2, and the fix belongs with MA-4 or MA-2 —
keep the previous listener, fan out to it while capturing, and restore it on stop. That's the behaviour
the comment already describes; the code just doesn't do it.

### M4-3 · Medium · "no shipped example enables persistence" is false for the developer download

The owner's question was about **the developer download**. The playground's `analyser-bundle` template —
the download virgin LLM sessions used today — ships with persistence **on**:

```yaml
auditCapture:
  enabled: true
  backend: chronicle
  directory: ./audit
  autoStart:
    - marketProcessor
```

The claim holds for Mongoose core's own repository (I found no core example that enables it), so scope the
row to "core examples". Also, `auditCapture` does appear in one core markdown file —
`design-doc/backpressure-and-slow-consumer-handling.md:309` — though not under `docs/`. Minor, but MA-4
rests on "undocumented".

**Consequence for OD-4.** Part of OD-4 is already decided in practice: the developer download has
persistence on, as Chronicle, and relies on `svc-admin-web`'s export. The open question is really the
text-versus-Chronicle default, plus whether core's own examples should match the playground.

### M4-4 · Low · MA-4's acceptance is sound

"Ends up with an audit log they can open in the analyser, without knowing that `auditCapture` exists" is
the right acceptance, and falsifiable: a fresh session following getting-started either can open a file
or it can't. It's the virgin-LLM test's shape; say that it will be run that way.

---

## Part 2 — findings from the first review, still open at `56025db2`

Restated briefly; the full text is in `review_mongoose_audit_production_2026_09_23_claude.md`, which I
recommend sending with this one.

- **F1 · Central · still open.**
  - Line 42 still calls `complete` "a confident false one", and line 45 keeps MA-1 before MA-2 "as a
    requirement".
  - Reproduced on both published jars: `complete` is a *true* container claim. The harm is that the reader
    emits **no warning on an empty log**, marked or not (`NO_NODE_LOGS` fires only when records exist).
  - **MA-1 does not remove the case.** `EventLogManager.processingComplete()` publishes only when some node
    logged at the current level; at WARN, an info-only handler produced zero records. So a correctly
    audited processor at a quiet level also writes an empty log.
  - The fix is an analyser-side "empty log" finding. With it, the ordering constraint disappears.
- **F2 · still open.** Lines 180, 202 and 211-213 still describe a four-node coverage denominator. The
  analyser takes coverage from the **paired graph** (`CoverageScope`); with no graph it records
  `noGraph` / `nothingToJudge` and makes no claim. MA-1.4 cannot be met as written, and its
  `NO_NODE_LOGS` clause passes vacuously on an empty log.
- **F3/F4 · addressed, independently.** The author's (b) spike found the `EventLogControlEvent` →
  `calculationLogConfig` trap (lines 106, 166), matching my F4, and OD-3 (c) — generating the shape at build
  time — avoids the private-bracket drift my F3 described. Good.
- **F5 · still open.** OD-1 and `UP-FLX-51`'s `NONE` default: an always-on manager defaulting to `NONE` is
  installed but silent until a level is set, and the post-fix cost is still unmeasured.
- **F6 · still open.** D-MA2's per-node parity can't travel in the marker: §1a admits only `streamEnd`,
  `streamEndRecords` and `logTime`, and any other key makes it an ordinary record. It's an acceptance-time
  comparison only.
- **F7 · still open.**
  - Line 322-323 still says the `NONE` corruption's description could not be found, citing
    `tracker.md:131`.
  - It is **AFMT-3** at `tracker.md:494` and in `docs/proposals/mongoose-audit-format/README.md:102-106`
    and `:267-271`, with a repro.
  - It's a dependency here: MA-1.3 drives levels through the same control event, and OD-1's `NONE` default
    touches the same path.
- **F8 · still open.**
  - MA-2.1 passes on an empty log.
  - MA-2.3 needs defined kill points: after the marker's separator is flushed, `complete` is correct.

## What I ran versus what I only read

**Ran:** MA-4's claims checked against Mongoose core `develop` `17a03b4`; the template's audit layout and
server console from today's A2/A3 runs (5 console records at the last start; Chronicle queue on disk).
Part 2 carries forward what I ran in the first review: the five-case probe on both published jars and the
two runtime experiments.

**Read, not run:** `ChronicleAuditCaptureService` replace-and-no-op behaviour (M4-1's run evidence is
consistent with it, not a controlled test); `ServerConfigurator`; `AuditCaptureConfig`; the revised
spec's D-MA1 (c) section and the author's spike files.

**Not done:** no controlled experiment of stop-then-restore (M4-2); no boot of core's own examples.
