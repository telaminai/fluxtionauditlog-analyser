# Vendor integration feedback — fifth snapshot

The full staged project, including evidence, desk, vendor source/jars and local configuration, is saved
outside this public repository at:

`/Users/greg/fluxtion-local-evidence/spring-demo-20260919T214833Z/project`

Its sibling `manifest.json` hashes 252 copied entries; verification found zero files changed during
copying. The snapshot's enclosing directories have owner-only access. It survives temporary-directory
cleanup, but retains machine-specific paths and dependencies; it is not a portable installation.
Do not publish that complete snapshot: it includes private local provisioning and unredacted artifacts.

This public packet preserves the restructured [35-issue feedback](ANALYSER-FEEDBACK.md),
[vendor predictions and participant results](vendor/PREDICTIONS.md), one visually inspected screenshot,
and selected probe outputs. The [companion authoring report](AUTHORING-DOCS-FEEDBACK.md) adds 157 lines
of documentation advice beyond round4; the review assesses that advice separately. The manifest pins
these files, the private snapshot manifest and source revisions. [Review decisions](../../review_staged_spring_feedback_2026_09_19.md#fifth-addendum--vendor-components-issues-2935)
separate reproduced behaviour, source reads and untested claims.

Independent checks on a disposable copy:

1. Add `acmeRisk` to the outer `nodeBeans` list; run only the keyless starter `regenerate` command.
   Exit 0 creates the empty shell preserved in `review-probes/generated-shell.java`, adds an empty
   ownership entry, and records validate/regenerate `ok`. Compile the shell with JDK 21 and put its
   output directory before the genuine vendor jar: reflection finds zero declared methods and the
   shell's origin (`shadow-proof.txt`). No compiler-service build was run.
2. Run regenerate again to establish a stable baseline, add a harmless extra ZIP entry to the copied
   vendor jar, and repeat regenerate. Jar SHA-256 changes; validate/regenerate input hashes do not
   (`jar-receipt-probe.json`). This is an identity-coverage probe, not another business-logic attack.
3. Run the participant's `vendor/check_var.py` on the saved genuine and tampered logs: 11/11 rows,
   zero mismatches (exit 0), versus 9 mismatches (exit 1). This repeats their checker, not an independent
   mathematical certification or a fresh processor run.
4. Parse saved GraphML: `MarketPrice → priceBook` and `Quote → acmeQuoteFeed` exist;
   `MarketPrice → acmeQuoteFeed` does not. The saved generated MarketPrice handler calls `onQuote`,
   and all nine MarketPrice records in the saved combined run contain `acmeQuoteFeed`.

The original running project was not edited or restarted. No key was used, no publication was attempted,
and the recorded full-build, constructor-failure, collision and runtime-version claims were not rerun.
