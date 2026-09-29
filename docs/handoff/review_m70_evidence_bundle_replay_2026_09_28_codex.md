# PR #70 — independent feature review

**Verdict: merge after the REQUIRED corrections; not mergeable now.** Review covers the feature since `v1.27.0`, including code already on main. Initial trials used `e948f7b6`; I repeated the requested gates at `2f71c970` and the data-refresh head `faf0fd2c`. §10a and §10b were treated as claims. No implementation changes, merge, provider, key or regeneration were used.

## §7 acceptance

“MET” below is the specified bounded case, not a claim that every possible replay works. The table includes §7's requirement for an executable regression and wrong-result witness.

| Item | Verdict | Independent evidence |
|---|---|---|
| RB-1 | MET | Runner end-to-end tests and real-app demo: the recorded DEMO agrees 8/8; changed risk logic diverges at record 6. Finding 3 limits generalisation. |
| RB-2 | NOT MET | My live generated-processor probe correctly records eight external inputs, including an external breach, while excluding the graph-raised breach. However, removing the identity guard leaves all 47 replay tests green; the required regression is missing (finding 6). |
| RB-3 | NOT MET | A changed nested business value is wrongly excepted and reported AGREES (finding 1). |
| RB-4 | NOT MET as written | Mismatched type/time is refused in tests and the demo. Changing an input's value while retaining its type/time still pairs: “another run is refused” is too strong (finding 5). |
| RB-5 | MET | Real capture frame tests and window-refusal control; whole-log positive capture succeeds. |
| RB-6 | MET | Matching-build replay, foreign-graph refusal, and caught graph-check control. This checks nodes/edges, not build identity. |
| RB-7 | MET | Live generated-processor probe preserves all eight receipt instants. The new ticking-clock regression and its mutation control run successfully after §10b's correction. |
| RB-8 | MET | Format-1 compatibility/member-rule tests and both format mutation controls. |
| RB-9 | MET, revised fixture | §10b's new test observes two service calls, packages their count, runs the recipient processor and verifies divergence at the first omitted call. This replaces the previously unsuitable jar fixture. |

## Findings

All reproductions below were **RAN**, except the explicitly identified documentation/code readings. Portable probe sources, the commands, sanitised subprocess output and gate counts are published in [the review evidence](evidence/pr70-review-2026-09-28/README.md); the script regenerates its DEMO bundles on the recipient machine.

### 1. REQUIRED — a comment can hide a changed business value

`src/main/java/telamin/fluxtion/audit/analyser/bundle/ReplayCompare.java:117`, `:143`.

Add a twelve-space YAML comment immediately after `eventLogRecord:`, before its ordinary four-space header fields. Put a twelve-space `thread: DEMO-old` beneath `nodeLogs.priceListener`; change only that nested value to `DEMO-new` in the comparison log. The real CLI returns **exit 0, AGREES 8/8, one excepted record** (`nested-comment-compare.json`). The comment establishes the inferred header indentation, so a business field is excluded.

Fix: identify the record header structurally; comments and payload fields must never determine its scope. Regression: this exact nested-value change must DIVERGE, while a genuine header-thread-only change still agrees. Register the corresponding wrong-result control.

### 2. REQUIRED — aggregate allocation remains unbounded

`tools/replay/ReplayBundle.java:238`, `:242`, `:247`; cardinality checked later at `:138`.

A small compressed bundle with twelve additional `replay/` members of 6 MiB each, all individually below the limit and correctly hashed, crashes the runner with **OutOfMemoryError** under `-Xmx64m`, before it refuses multiple replay members. Increasing the member count scales the attack to larger heaps. §10b's per-member bound does not fix this.

Fix: validate allowed member cardinality before retaining a second replay/graph, impose an aggregate budget, and stream/spool large permitted members. Regression: a constrained child JVM must refuse this archive by name without OOM or producing output; retain a valid positive control.

### 3. REQUIRED — legitimate event text deletes an audit record

`tools/replay/ReplayBundle.java:183`.

Set the first market-data symbol to `DEMO event: EventLogControlEvent`. The same generated processor logs that record normally in my direct capture. The runner reports seven inputs but writes **seven audit records instead of eight**, because its whole-record substring filter drops the first business record. Comparison consequently diverges at record 0.

Fix: exclude only known runner setup emissions, not records containing a phrase. Regression: this payload must retain its audit record and agree with a direct capture; setup must remain excluded.

### 4. REQUIRED — malformed replay silently loses input

`tools/replay/ReplayBundle.java:391`; `examples/fixture-generator/src/main/java/com/acme/demo/replay/ReplayReader.java:35`.

Remove the separator between the first two replay records and update the manifest's legitimate byte count/hash. The runner exits **0**, claiming six recorded inputs from a manifest declaring seven, and writes six audit records. Both readers use only the first matching event/time in a block. A duplicate-field probe likewise returns the first event silently.

Fix: parse exactly one complete ReplayRecord per document, reject duplicate/extra fields and unmatched trailing content, and validate declared counts where available. Regression: missing-separator and duplicate-field fixtures must refuse before processor execution/output; the ordinary fixture must still run.

### 5. REQUIRED — user guidance overstates pairing and completeness

`docs/site/evidence-bundles/with-an-assistant.md:155`; `tools/capture-bundle-conversations.py:229`.

The page says inputs “cannot be from another run” and infers that no service calls means nothing is missing. Changing the first bid from `100.1` to `999.9` while preserving its type/time yields **seven paired records, no problem and zero uncarried records**. Pairing never compared that payload. No service calls also says nothing about files, random values or other external state.

Keep the bounded type/time policy if intended, but describe exactly that; absence of service calls is not proof of completeness. Amend the page and its generation script. Regression: retain this collision fixture as an explicit limitation and guard against regenerating the stronger claim. The broader CLI caveat is already better than this prose.

### 6. REQUIRED — RB-2's identity protection has no effective regression

`src/test/java/telamin/fluxtion/audit/analyser/bundle/ReplayFixtureTest.java:65`; recorder guard at `examples/fixture-generator/src/main/java/com/acme/demo/replay/ReplayCapture.java:66`.

Removing `event != expected` leaves **47/0/0/0** across `Replay*Test` at `2f71c970`. My live processor probe then records **nine inputs instead of eight**, including two breaches rather than only the external one, and fails its named assertion. Existing fixture checks classify every breach as graph-raised; they do not drive this consumption path. Source restored byte-identically, then 47/0/0/0 again.

Promote that live probe into a regression: external and internally raised events must share a type, and only the external object is recorded. Register an identity-guard mutation. §10b fixed the separate receipt-time regression; this finding does not reopen it.

### 7. SHOULD — valid JSON is refused by the independent manifest reader

`tools/replay/ReplayBundle.java:211`.

Pretty-print an otherwise unchanged valid manifest. `analyser --verify` succeeds; the runner refuses `graph/DEMO.graphml is not listed`. Its regex recognises one whitespace/key-order spelling, not JSON.

Use a real JSON parser and explicit schema validation. Regression: compact, reordered and pretty-printed equivalent manifests must all verify/replay, each with its own exact-byte bundle identity. Malformed JSON must refuse.

### 8. REQUIRED, already known — native documentation captures remain outstanding

`docs/specs/spec-evidence-bundle-replay.md:425`; the four `bundle-conv-*.png` assets.

I inspected the previews and actual demo screenshots. Painted previews do not satisfy the recorded native-capture requirement. Regenerate and inspect them before merge; preserve the distinction between illustrations and observed UI.

### 9. NIT — whole-feature whitespace check is red

`src/test/resources/replay/demo-quote-recorded-audit.yaml:2` and the replayed-audit fixture.

`git diff --check v1.27.0...HEAD` reports 32 trailing-space lines. These are captured producer bytes: do not silently trim them and change the comparison evidence. Document a narrowly scoped fixture exception or another explicit preservation policy.

## Verification and limits

Final-head gate counts are recorded in the accompanying results summary. Runs use JDK 21 on macOS ARM64, isolated worktree/homes, and sequential display work under the shared display lock.

- `mvn -o -q test`: **2855 / 0 / 0 / 170**, 380 reports, no orphan reports. Among frame suites, only `WestColumnStartsCollapsedFrameTest#theRule_aChosenWidthWhileAPanelShows_theRailAloneWhenNoneDoes` ran; the display cases skipped.
- The requested `EvidenceCaptureFrameTest` display command: **15 / 0 / 0 / 0**.
- `mvn -o -q package -DskipTests` and `tools/evidence-bundle-demo.py`: **57 passed / 0 failed**. Only scratch-root constants were redirected to preserve other sessions' evidence. The driver reports machine-tier recent/open-path changes explicitly; its 57 checks do not establish unchanged global settings.
- Fast engine: **33/33** requested feature controls caught, exact requested/caught names checked. JSON confirms named assertion failures, no errors/skips, green baselines, byte-identical source/classes restoration and restored-green runs.
- `mkdocs build --strict`: clean. Rule-one tracked/addition sweeps: empty. Whole-range whitespace result is disclosed above.

CI: at `e948f7b6`, run 36481935369 attempt 1 lost `mouse-loss-table-timer`; attempt 2 caught 479 controls, display 168/0/0/0. At `2f71c970`, run 36485428716 finished **failed**: `mouse-loss-table-hook` survived; display remained 168/0/0/0. I read both failure logs. GateLauncher/fast-engine changes only fix Unicode JSON escaping/splitting; I found no causal path from those changes to these mouse survivors. Their actual native cause remains unverified; the accessibility-bus warning alone does not establish it. Publication update: at `faf0fd2c`, run 36486319466 completed green. Its logs report build 2855/0/0/170, display 168/0/0/0, and 482 controls caught exactly once across four shards. This supersedes the initial pending status, not the defect findings.

READ: framework reference, generated processor, capture node/effect/facts, Python driver and both pre-review disposition tables. No new rule-9 violation identified; synchronous pairing on the EDT remains the declared S3 follow-up. N5's shared exit code is unchanged. M70.R0c is **decided, option (a), data refreshed in `faf0fd2c`**; only documentation screenshots remain there.

Design pushback: keep bundle parsing/schema semantics shared across analyser and runner. Describe pairing as consistency evidence, not run identity; topology equality as graph compatibility, not identical code; and an agreeing log as bounded recorded evidence, not proof of all behaviour. General processor lifecycle/initialisation and external-state reproducibility beyond the DEMO were not established by these trials. Native screenshot regeneration, a real model trial and compiler regeneration were not run.

**Overall:** the basic journey works, but a false AGREES, silent input/output loss, hostile-input exhaustion, a missing live regression and stronger-than-proven guidance prevent approval. Merge only after the REQUIRED fixes have their own failing-then-green checks, the native captures are replaced, and CI at the resulting head is green.
