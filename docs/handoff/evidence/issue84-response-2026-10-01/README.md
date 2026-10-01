# Issue #84 response evidence

This is implementer verification, not independent approval. See the [response](../../handoff_issue84_third_review_response_2026_10_01.md)
and [reviewer prompt](../../prompt_issue84_response_review_2026_10_01.md).

`verification.json` preserves the unchanged-current-main reproductions, maintained before/after jar witnesses,
failed control attempts, the broader display failure and its disposition, final REQUESTED/CAUGHT names and
byte-identical restoration results. Host paths are replaced with placeholders. It includes hashes of the exact
starting and candidate jars; `sourceHead` in a raw local run is not a substitute for a built-artifact hash.

The original review and unsuccessful reviewer attempts remain unchanged at commit `5df6e097` on
`review/issue-84-third-review-2026-10-01`. These files add evidence; they do not rewrite the review.

With JDK 21 and cached test dependencies, build the selected revision using `mvn -o -q package -DskipTests`.
Run the maintained witnesses against that explicit jar, under the shared display lock:

```sh
lockf -k /tmp/fluxtion-analyser-display.lock python3 \
  docs/handoff/evidence/issue84-response-2026-10-01/run_frame_witnesses.py bundle \
  --jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar --output target/issue84-bundle
lockf -k /tmp/fluxtion-analyser-display.lock python3 \
  docs/handoff/evidence/issue84-response-2026-10-01/run_frame_witnesses.py walk \
  --jar target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar --output target/issue84-walk
```

The runner deliberately exits nonzero for failed witnesses and retains their output, rather than converting an
expected baseline failure to success. It uses test helpers but never `target/classes` for application code.
It captures DEMO screenshots under an isolated home; inspect images before publishing them. Local raw logs and
screenshots are retained in `target/issue84-response` and are not swept into the public commit.

For mutations, pass each `finalControls.requested` name in `verification.json` as `--case NAME` to
`tools/verify_project_chart_review.py --mode mutations --engine fast --output target/issue84-controls.json`.
Run sequentially under the same lock. Compare names and assertion messages, not merely totals. The full CI shards
run all 580 registered controls, not this targeted subset; read their exact-head collector before claiming green.
