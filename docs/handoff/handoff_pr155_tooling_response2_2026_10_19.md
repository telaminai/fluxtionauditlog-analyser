# PR #155 tooling — response to the independent agent review

Author verification only; not independent approval. Review base: `3c8dd23e4eafa3a03e63233ffa0c8c3ff4d4d1e5`.
Branch: `fix/pr155-tooling-integrity`. The six-finding correction and its original evidence remain in history.

Read [the independent second-pass review](review_pr155_tooling_pass2_2026_10_19.txt), then this response and the
delta. The review text is preserved with scratch locations generalised for portability; its verdict and findings
are unchanged. Its original DEMO probes are retained as a portable script in the evidence folder.

## Dispositions

| Finding | Correction | Regression / wrong-result witness |
|---|---|---|
| P2: unsupported answer objects certify no contradiction | `claim_checks` requires every nonempty answer row to carry string `text`; no absent-key default. Refuses with exit 2 before writing a score. Present-empty captures remain allowed, absent captures remain VOID; required phrases stay informational. | `RunIntegrityTest.test_nonempty_answer_rows_require_text_not_a_silent_empty_default`: message-only and empty objects previously scored PASS; now refuse and preserve the previous score bytes. Null/boolean/numeric/list/object text names `answers.jsonl` in its refusal. A valid forbidden phrase still gives FAIL. |
| P2: current person decisions bypass correlation | The inventory now holds 47 current onboarding control records, including row answers, restart/chip/New chat, no-log, Ask again, setup folder, profiles/setup/access and starter choices. Every supported fact needs an ordered act with matching references/choices. Unknown fact-bearing operator kinds also make the run VOID. Recursive typed equality rejects boolean/integer substitution inside lists. | `test_current_person_decisions_without_operator_acts_void_the_run`: the five reviewed current controls now yield VOID with their exact missing-act names, still PASS with a matching act, and VOID with a wrong identity. `test_unknown_operator_kinds_expecting_a_fact_are_not_silently_ignored` pins fail-closed operator vocabulary. `CurrentJavaContractTest.test_current_person_controls_require_ordered_matching_operator_decisions` constructs actual current Java records for all 47 controls: matching control, missing press/fact, missing/wrong fields, and reversed stream. |

`RecordPersonFacts.java` owns an explicit Java inventory independent of the Python map; it constructs records with
reflection so real constructor normalisation and record `toString()` run. It uses DEMO references, booleans and
vocabulary only, not a UI, key, model or network. The contract test checks the two inventories agree. Changing the
Java list to copy a shortened Python map would defeat the guard: reviewers should inspect both against current
`SessionEvents` and its posting sites. New control types require a deliberate inventory/contract update; this is
not an automatic proof about every future UI control or arbitrary navigation outside onboarding.

Proposal `review` acts now carry `fact: {proposalId, itemId, yes}`; Apply follows row-answer facts in publication
order. `proposals.itemsReviewed` reads `fact.itemId`; the former legacy `item` fallback remains for historical pure
proposal statistics, but cannot pass boundary correlation without the required fact fields. R5's obsolete claim
that the record cannot distinguish No is corrected. Origin proof remains explicitly outside this correlation:
two recorded streams agreeing does not independently prove who pressed.

## Verification

JDK 21, offline tooling / DEMO fixtures only:

```sh
python3 tools/test_m74_onboarding_runner.py
python3 tools/test_m74_onboarding_integrity.py
mvn -o test
python3 tools/test_m74_onboarding_contract.py
python3 tools/m74_record_pack_crosscheck.py . --check
python3 docs/handoff/evidence/pr155-tooling-integrity-2026-10-19/independent-negative-probes.py
python3 tools/verify_project_chart_review.py --mode preflight --output target/chart-preflight.json
python3 -m unittest tools/test_project_chart_review.py
```

Before changes to production Python, the three new integrity methods yielded 13 named assertion failures,
zero errors: two missing-text variants incorrectly returned exit 0, five wrong-text-type cases already refused
but did not name the bad capture, five uncorrelated current facts still passed, and one unknown operator kind
still passed. The new actual-Java contract run with the baseline Python loaded in memory fails at the inventory
assertion (one failure, zero errors). No production file/class swap or engine-mutation restoration is claimed.
The [evidence](evidence/pr155-tooling-integrity-2026-10-19/response2-results.txt) records after results and limits.

## Limits / reviewer attacks

- No application Java, generated processor or GraphML changes; no session regeneration. No live operator harness,
  model, hosted compiler, credentials, participant projects, GUI run, merge or release.
- The Java witness establishes current record construction and scorer correlation, not that a future live
  operator harness successfully observes and records every UI field. The live harness remains unimplemented.
- A capture with string `text` is understood for phrase scoring; no new ticket-to-turn completeness assertion is
  made. The explicit empty-file policy is retained, not silently strengthened into requiring an answer.
- Check a malformed row after a previously valid score, a normal forbidden answer, present-empty versus missing,
  a real No row, a Later restart, list-valued choices, no-argument controls, and reordered but equal totals.
- Read the latest CI for the exact branch head; earlier green/static-only or in-progress runs do not qualify.
