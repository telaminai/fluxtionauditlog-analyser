# Menu guard follow-ups — frozen predictions

Base: main merge fda14f70. Review read at 88afe42b. Commit this file before trials or changes.

- O-a: expand the existing documentation test and inventory without adding test methods. A fabricated
  AI item in faq.md fails documentedPathsNameExistingItems with file and line; a shortened Audit log
  path in ProjectModel's Java literal fails the same assertion with Java file and line. Settings paths
  refer to dialog tabs and are explicitly excluded. Existing documented paths should mostly pass;
  ambiguous prose boundaries may require extractor corrections, not invented menu aliases.
- O-b: removing each Close log / Close graph interactive assignment fails its existing close test
  at the "declares human intent" assertion. Existing menu-help-s3 remains the control.
- O-c: name both Export settings and Import settings in README; documentation guard stays green.
- O-d: before changing tests, run both complete classes ten times on the display, preserving every
  result. Predict 30 PersonAtTheScreen tests and 60 NamedGraphAndMenuSpotlight tests, all passing,
  since the reviewer could not reproduce the failures in isolation. This does not establish absence
  of flakiness in a full suite. After the focus retry, repeat the same ten runs and predict the same.
  Inspect the second-menu closure before changing its wait; do not mask a late-clear defect with a
  retry. If its cause is not established, leave that test unchanged and report the limitation.
- Expected package totals remain 1985 / 0 / 0 / 97 and display 98 / 0 / 0 / 0, with 19 frame suites.
  Existing methods are extended; no new test method is planned. Python verifier: five; spotlight:94.
- Only the four new controls and existing menu-help-s3 will be run, each from the shared baseline,
  named assertion failure (not error), byte-identical restoration, then green.

The immediately preceding integration ran clean package 1985/0/0/97, display 98/0/0/0 and strict
MkDocs; the merge into main changed no tree bytes. Those are the pre-prediction repository baseline,
not the ten-run display measurement requested here.
