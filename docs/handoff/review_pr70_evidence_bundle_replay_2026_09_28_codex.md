# PR #70 review — evidence bundle replay

Reviewed `v1.27.0...feat/evidence-bundle-replay` through `faf0fd2c` on 2026-09-28. Treat spec §10a and §10b as prior claims; this review checked beyond them. M70.R0c was decided (option a) and refreshed in `faf0fd2c`. The four painted bundle-conversation screenshots remain open for native regeneration.

**Verdict: not yet.** RB-4 is not met, and the recipient runner accepts malformed replay input. The findings below need regression checks with wrong-result witnesses before closure (CLAUDE.md rule 8). No merge, rebase, force-push or release was performed.

## Findings

1. **REQUIRED — capture accepts inputs that do not belong to the logged run.** `ReplayPairing.java:107` matches a replay entry to an audit record by simple event name and timestamp, without checking its payload. I changed the first fixture replay bid from `100.1` to `999.1`, leaving name and time intact. Both files returned `problem=null`, seven paired records and zero uncarried records. Capture can package wrong inputs and call them paired; a subsequent divergence may then be attributed to the recipient's build. Compare the event payload with the log where the log makes that possible; refuse or qualify a pairing the log cannot establish. Add a wrong-payload test beside `ReplayPairingTest` and a mutation control that removes the payload check. Correct the categorical “another run is refused” wording in `CHANGELOG.md:13` and the bundle site pages. This is RB-4's wrong-result witness.

2. **REQUIRED — the recipient runner accepts malformed replay documents.** `tools/replay/ReplayBundle.java:390` uses `Matcher.find()` for event and time and never validates the entire document. A direct call to `ReplayBundle.read` accepted a document consisting of a non-record preamble, one event/time pair and trailing garbage as one input. Malformed bundle input can therefore produce successful runner output with content silently ignored, leaving the comparator to report divergence rather than the runner to refuse the input. Validate the exact separator, header, event and time lines, and the absence of extras. Add an end-to-end refusal test with this malformed document and a mutation control that restores partial matching.

3. **SHOULD — whole-log size and untrusted-input bounds conflict.** `tools/replay/ReplayBundle.java:65,250` caps *every* expanded member at 512 MiB, including a log that the runner only scans. A valid 513 MiB log can verify in the analyser but the runner will refuse it. The runner also holds the replay member, parsed entries and complete generated audit text in memory, with no aggregate ZIP entry or expanded-byte budget. The resource-exhaustion case was **not run**; this is a code-path finding. Stream the log and audit output, give untrusted input an aggregate budget, and test a valid log above a low configured limit plus many small hostile members.

4. **SHOULD — the recipient guide overstates processor identity.** `docs/site/evidence-bundles/with-an-assistant.md:209` says the runner checked that the recipient build *is* the bundle's processor. `ReplayBundle.graphDifference` compares node IDs and edges. The demo's changed-risk-limit build passes that graph check, then diverges. Say “the node IDs and edges match” and add a docs assertion that prevents the identity claim from returning.

## §7 acceptance

| Item | Verdict | Evidence |
|---|---|---|
| RB-1 | MET | Two-analyser demo: seven inputs yielded eight agreeing records; changed risk limit diverged at record 6. |
| RB-2 | MET | Fixture and codec tests cover an external breach event as well as one raised by the graph. |
| RB-3 | MET | Comparison tests and focused controls catch changes outside `endTime` and `thread`. |
| RB-4 | **NOT MET** | Changed-bid witness above passed pairing. |
| RB-5 | MET | Demo refused a replay with a time window. |
| RB-6 | MET | Demo refused a graph mismatch before output. |
| RB-7 | MET | Receipt-instant test and mutation control passed. |
| RB-8 | MET | Format-1 read tests and format/member controls passed. |
| RB-9 | MET | Service-call fixture test observes calls and divergence at the first call. |

## Evidence rerun

- At `faf0fd2c`, `mvn -o -q test` passed: 2,855 tests, zero failures or errors; frame tests skipped in the headless run. The explicit `EvidenceCaptureFrameTest` display command passed 15/15.
- `mvn -o -q package -DskipTests && python3 tools/evidence-bundle-demo.py`: 57 PASS, 0 FAIL, using two real analysers. `mkdocs build --strict` passed. The rule-1 sweep printed nothing.
- At `2f71c970`, all 33 requested `rp-`, `rc-`, `rn-`, `rq-`, `eb-format1-` and `ax-` mutation controls were caught; requested and executed names matched exactly. `faf0fd2c` refreshed demo data and fixtures, and `gh pr checks 70` finished green at that head.
- A prior CI run failed a native mouse mutation. Its failed log names `mouse-loss-table-hook` (the review prompt named `mouse-loss-table-timer`). The timer control was caught locally. The changes to `GateLauncher.java` and `mutation_gate_fast.py` only alter JSON escaping and splitting; they do not appear to affect that mouse assertion.

The runner and comparator can demonstrate agreement for a replay actually produced by the recipient's build, subject to the stated gap for reads the records do not carry. Name/time pairing alone does not establish that the bundled inputs are the logged run's inputs, and matching node IDs and edges does not establish build identity. The known event-thread pairing read remains open as recorded in spec §10a; I found no new frame-side session decision contrary to rule 9.
