# PR #155 — tooling-integrity corrections for implementor review

Status: implemented, **not independently approved**. Base: `dd70ea80e933dab770a897d581a0c74c3736b091`.
Correction branch: `fix/pr155-tooling-integrity`. Do not merge or run a live model trial as part of this review.

Read the [unchanged review](review_pr155_tooling_2026_10_19.txt), then this response. The original reviewer authored
these corrections at the owner's request; their tests are author verification. The implementor is the next reviewer.

## Dispositions

| Finding | Correction | Regression and wrong-result witness |
|---|---|---|
| F1 — unbound oracle | `score_run` now takes a file path, reads its bytes once, checks its actual SHA-256 against the run's oracle pin, and only then parses that same read. No caller-supplied digest/dictionary. All seal inputs are mandatory; launch pins cannot overwrite cycle-owned metadata. | `RunIntegrityTest.test_changed_sealed_oracle_is_refused`: old code returns exit 0 and PASS after a sealed FAIL; correction refuses with exit 2 and preserves the previous score. Missing pin/input and ownership-override controls also run. |
| F2 — incomplete evidence | Required captures must exist; the three sink counts must be nonnegative integers, with no drops/failures and parsed count equal to total. Snapshots require bounded `recordSeq` and finite observer times. Operator times must be finite/ordered; unknown reaping is not success. Full also requires its CLI transcript. | `test_export_count_must_match_sink_total` and `test_missing_answers_are_not_proof_of_no_contradiction`: old PASS becomes VOID. A present empty answer stream remains distinct and can pass; required phrases remain informational. Missing/invalid counts, sequences and reaping are covered. |
| F3 — counts mistaken for ownership | Compare ordered fact/act streams and require the stable references/choices in `ACT_FIELDS` under each operator act's `fact` object. Do not call this independent causal attestation. | `test_person_action_order_must_match`: equal totals with reversed Start/Send no longer pass. `test_a_wrong_decision_identity_is_not_the_matching_person_act` covers wrong request, wrong choice and absent identity, with a matching control. |
| F4 — live seed aliases | Provenance rejects seed-root and nested symlinks before reading artifacts; seed destinations cannot be links. Copy preserves links only so an unexpected concurrent change can be detected/refused after copying, rather than dereferenced. | `test_external_file_and_directory_links_are_not_frozen_seeds`, `test_a_seed_root_alias_is_not_a_frozen_seed`, `test_destination_alias_is_refused_before_copying`: original accepts them; correction refuses before ordinary writes. Regular seed/copy/digest controls remain green. |
| F5 — obsolete permission shape | Read current `allow`, `by`, `written`. Count delivered allows by source separately from denials, and report undelivered responses. Reject obsolete unsupported answer fields rather than inventing denials. | `CurrentJavaContractTest.test_actual_permission_answers_are_classified_by_allow_and_delivery` invokes `RecordPermissionFacts.java` against this checkout's compiled Java records. Original counts zero grants; correction sees one person grant, one actual denial, one pre-allow and one undelivered response. |
| F6 — mirror drift / frozen-only CI check | Example URL stripping uses Java-style whitespace rules; the NBSP suffix is now refused. The corpus has 90 Java-recorded cases. New `--check` mode compares current Java output and never rewrites expected verdicts. CI invokes it and the current-record tests **after** `mvn -B verify`. | `test_example_nbsp_is_not_normalised_away` is assertion-red on the original. `test_ci_executes_current_java_after_compilation` pins placement. `test_current_parser_check_detects_drift_without_rewriting_expected_verdicts` deliberately changes a disposable expected verdict: check exits 1, names the case and leaves both passing/failing input fixtures byte-identical. |

## What changed outside the scorer

- The existing 41-test suite uses the new file-path API and current permission fields.
- The synthetic passing fixture names its actual oracle digest and supplies stable act references; the original
  session-record YAML remains unchanged. Its 42 existing trailing-space diagnostics are preserved evidence, not a
  newly cleaned/generated capture.
- The protocol/scorecard describe required input validation, ordered correlation, current permission fields,
  symlink refusal and explicit separately sealed correction revisions. The original sealed run is not overwritten.
- CHANGELOG and G14-W tracking point here. No analyser production Java, UI, generated processor or GraphML changed.

## Verification and reproduction

From the repository root, JDK 21; Maven serially:

```sh
python3 tools/test_m74_onboarding_runner.py
python3 tools/test_m74_onboarding_integrity.py
mvn -o test
python3 tools/m74_record_pack_crosscheck.py . --check
python3 tools/test_m74_onboarding_contract.py
```

The first two are offline Python suites. The final two require current compiled classes; missing compilation is
an error, not a silent skip. No token, model, API key or hosted generator is used.

Author verification: original suite 41 passed; integrity suite 20 passed; current-Java contract suite
2 passed; 90 current Java pack verdicts agree, with no expected-file rewrite in check mode. The initial 16-test
integrity suite produced **24 named assertion failures, zero errors**, against unchanged production code; the
actual-Java permission witness also failed at its named grant assertion. A later seven-method check against the
original scorer loaded in memory produced eight named failures (the file/directory-link subcases), zero errors.
Source/class restoration is not claimed for an engine mutation: production files/classes were never replaced.
Before/after evidence is retained in [the evidence summary](evidence/pr155-tooling-integrity-2026-10-19/results.txt).

Full Maven counts, hygiene and final checks are recorded in that evidence summary. Read CI for the **exact current
branch SHA** yourself: a static-check workflow is not the full build, display, self-test, eight shards and collector.
Do not treat this handoff or any partial run as approval.

## Where to attack

1. Score a valid run, then change the oracle bytes (including whitespace-only edits), remove the pin, or try to
   override protected fields through `--pins`. Confirm refusal cannot overwrite the original score.
2. Remove required captures; supply null/boolean/negative counts or out-of-range snapshot sequences. A missing
   file must not become an empty successful stream. Keep the present-empty-answer control.
3. Preserve counts while reordering acts, reusing another permission request, or changing its Yes/No. Confirm the
   future operator harness can record the required references **from its own observed controls**, not from the
   model's account. This schema change is deliberate and needs implementor review before any live trial.
4. Try seed and destination aliases. This helper trusts the frozen source and operator-selected parent path; it
   is not an atomic filesystem snapshot or a sandbox against a hostile concurrent writer.
5. Check delivered/non-delivered and pre-allowed/person permissions using actual current records, not hand-written
   obsolete strings. Check that the CI authority comparison is non-writing and really follows compilation.

## Not done / limits

No live virgin-LLM trial, sandbox/login verification, paid provider, credential access, participant data, session
regeneration, release, deploy or merge. `launch` remains an explicit refusal: the live operator harness is not
implemented by this change. The dictionary-taking Python scorer API is intentionally replaced; unmerged harness
callers must pass the oracle file path. A cycle seal/configuration is not a cryptographic signature against an
operator who deliberately rewrites both the configuration and evidence. Stronger independent origin attestation
remains R3. Strict MkDocs was unavailable locally; no site page or navigation is changed by these corrections.

The next session should judge these changes and their regressions, not accept the original reviewer's own fixes
on the strength of this response. Integrate only after that review and the normal exact-head gates; do not merge
this branch automatically.
