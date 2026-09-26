# Framing fixtures — CONSTRUCTED, not captured

M68.3 (spec-evidence-integrity D-E9, acceptance 10). Every file here was written by hand for the test that uses it. None
is a replay of a session.

- `client-shape-collapsed.yaml`: nine `eventLogRecord:` blocks and no `---`, the shape the G14 recovery packet's
  `run1.log` had (its PDF shows the structure). The names are the public-safe `DEMO` placeholders; the values are
  illustrative.
- `quoted-key-one-record.yaml`: one legal record whose quoted values contain the header key as literal text, once
  on a single line and once inside a multi-line double-quoted value. It must NOT be reported as two records. This is
  the false verdict M68.3 corrects.
- `apostrophe-then-collapse.yaml`: two records run together, where the first has an apostrophe in a plain value. The
  apostrophe must not open a quote and hide the second header (the false-negative guard).
- `unterminated-tail.yaml`: two records, the last without a closing `---`. It is legal Format 1, and the tail is a
  record on a static open.

The one real, non-constructed collapsed log in the repository is the starter sample
`docs/handoff/evidence/sg1-release-2026-09-21/sample-run.txt`, used as the cross-check.
