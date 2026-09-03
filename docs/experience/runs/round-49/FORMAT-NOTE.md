# Note — `expected.txt` is not in the analyser's record format

Found 2026-09-03 while building `ExpectationScorer` on the shipped reader.

`expected.txt` contains **18 `eventLogRecord:` blocks and zero `---` separators**. The experiment
harness wrote it with `String.join("\n", auditLines)`, which drops the document delimiter the format
requires. `YamlAuditReader` therefore reads the whole file as **one** record: the scorer saw 1 scored
event where there are 12.

**Two consequences worth carrying forward:**

1. **The reference logs from rounds 48–53 cannot be opened by the analyser** without repair. They were
   only ever consumed by bespoke Python, which is precisely the practice that produced five scoring
   defects. Any future round MUST write logs the shipped reader can read — that is the cheapest
   possible conformance check, and it is free.
2. **`Main` in the fixtures is the defect, not the format.** The fix is to join with the document
   separator rather than a newline. The conformance corpus in `src/test/resources/conformance/`
   documents the expected shape.

**Not repaired here.** Rewriting committed reference logs would invalidate the comparisons already
published against them; the note records the constraint instead, and the requirement lands on the next
round's harness.

---

## Do not run a whitespace gate over the log fixtures

`expected.txt` and `expected.conforming.txt` both trip `git diff --check` on trailing whitespace.
**Those bytes are format-faithful** — the audit writer emits them, and the shipped reader is specified
against them. Stripping them would silently alter evidence and could change what the conformance
suite is testing.

**Any whitespace or formatting gate MUST exclude these fixtures.** Flagged by a reviewer before it
could bite; recorded here because the next person to add such a gate will not know.
