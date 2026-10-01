# P2 predictions — PR #87 final-review corrections

Starting head: `11df0d26`. Recorded and committed before changing code or running this round's trials.
The prior R1/R2 policy is accepted and remains unchanged.

| ID | Prediction and planned witness |
|---|---|
| P20 | The three F1 narratives will fail the exporter-driven ordinary-prose assertion on the starting production file: quoted ratios, `/status`, and a protocol-relative URL are currently redacted. Applying the existing supported-shape rules inside quotes will leave all three untouched and report no removals. |
| P21 | Each of F2's three narratives will fail a labelled exact-output/removal assertion before the fix. Restricting the quoted candidate to a single path and rejecting unreliable delimiters will fall back to ordinary unquoted handling, preserve each sentence, and report only its path. |
| P22 | The quoted boundary must independently reject sentence punctuation, another delimiter type, a second path start, a parenthesised message, and a single closing quote followed by a letter. Additional cases will pin those guards while keeping a legitimate quoted path containing spaces accepted. |
| P23 | Choose F3 option (a): add corner brackets, double corner brackets and curly single quotes. The existing quoted-Unicode export test expanded to these delimiters will fail on the starting production file, then pass. A refusal-recovery test will show that a spelling recommended by the message actually succeeds. |
| P24 | F4 is disclosure only: Lao, Khmer and Myanmar suffixes will continue to be consumed as path characters. Pin this existing limit in a test and name it in the spec; do not change the accepted unquoted policy in this round. |
| P25 | Removing the new quote shape and boundary checks will be caught at named assertions by the two requested new controls. Existing fourteen rf2 controls and the dialogue control will remain caught after any necessary anchor updates. |
| P26 | The full suite will remain green with the same 218 headless skips and 406 source-mapped reports; preflight will retain 40 frame suites and gain at least the two required anchors from 555. CI must independently establish display zero skips and a complete full mutation gate at the pushed head. |

Implementation choice for F2: use the recommended conservative bound. Accept a quoted span only when its
content is one supported path shape, contains no sentence punctuation, other quote type, or second path
start, and does not end at an apostrophe followed by a letter. Otherwise use the existing unquoted
redaction/refusal policy. A path plus a parenthesised diagnostic must not be reported as one removed path.

F3 takes the recommended additional delimiters. F4 takes disclosure plus a regression, without expanding
the script policy. The separate colon policy and existing numeric-home/space residuals stay out of scope.
These are predictions, not results. Record misses in RESULTS-P2.md after the trials.
