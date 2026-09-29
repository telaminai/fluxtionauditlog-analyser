# Independent PR77 evidence

Subject `5c79e357`; corrections `9a8ffbde`. See the [review](../../review_pr77_onboard_assistant_2026_09_29_codex.md).

- `PREDICTIONS.md` states when predictions were made; they were source-informed, after the baseline.
- `baseline-counts.txt`, `display-counts.json`, `fixed-headless-counts.json`: actual Surefire counts. Source reports were checked against `src/test/java`; no orphans.
- `pre-fix-failures.json`: named failures produced by the seven new regressions before their corrections. The source test was run separately on a display.
- `original-controls.json`: all 39 requested cases, complete list checked against the request.
- `fix-controls.json`: seven new controls plus the existing key and EDT cases. Both control files retain source hashes, named assertion results and source/class restore flags; machine-local classpaths/commands are deliberately omitted. Full raw harness outputs are retained locally.
- `WorkspaceAttributionProbe.java` and `workspace-probe.txt`: the uncorrected causal-attribution counterexample on the real generated processor. Compile with the test classpath and run `telamin.fluxtion.audit.analyser.analyser.session.WorkspaceAttributionProbe`. It uses only DEMO names and the existing fake effect adapter; no frame/provider/key.
- `old-reader-1.28.0.txt`: actual released-reader output. The jar's embedded Maven metadata says 1.28.0. All four steps were SHOWN, and 36 dialogue/binding keys survived the old reader's own profile rewrite.

Display commands used the shared display lock and JDK 21, one class at a time, with `-Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`. The eight classes were AssistantLiveFrameTest, AssistantHostFrameTest, AssistantNativeFrameTest, ConversationEditorFrameTest, ConversationJourneyFrameTest, WalkPlaybackFrameTest, WalkReviewFrameTest and EvidenceCaptureFrameTest. The corrected JavaSourceSpotlightFrameTest ran separately: 13 / 0 / 0 / 0.

The full headless command was `mvn -o -q test`. Fast-engine calls used explicit `--case` arguments for every name in the JSON summaries, not the full gate. The base and working-tree whitespace checks, tracked/addition/member public-data sweeps, preflight, five Python harness tests and strict MkDocs build completed. No native-input skip occurred in the classes run here. No real provider or generation operation was run.
