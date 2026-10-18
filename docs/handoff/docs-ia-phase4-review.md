# Docs restructure, phase 4: independent review and dispositions

- **Phase:** 4, *Build with Claude*.
  - `build/index.md` states the thesis and absorbs `the-loop.md`, which is deleted and redirects to it.
  - `build/validate-intent.md` (nav: *Check your criteria against the evidence*) says what exists today and marks the
    planned acceptance checks as planned.
  - `build/set-up-with-claude.md` is checked against the code and gains an *After the set-up* section.
  - The Spring pages say which assistant can build; `producing-a-log.md` covers the binary (`FLXA`) reader.
- **Reviewed commit:** `e8cd0069` against `7f7d0c6f`.
- **Reviewer:** an independent general-purpose agent, working read-only through git. It checked every claim against
  the code at the commit, ran a strict build of a clean export, checked the redirect, compared the deleted page with
  its replacement, read the M76 spec to confirm nothing unbuilt is described as shipped, and read the pages as a
  customer developer and as a newcomer.
- **Result:** no BLOCKER. 2 MAJOR, 7 MINOR, 3 NIT.
- **Verified by the reviewer:** the thesis wording (no "proves the intent", no partial proof implied); the M76 checks
  boxed as planned and matching spec r2; S7, S8 and S2 against `AskCatalogue` and `StartPagePrompt`; every verb and
  argument used; report behaviour; the menus and Settings tabs named; the binary reader's recognition, missing fields,
  no Follow and cut-tail report; the redirect keeping its `#anchor`; the sweep.

| # | Grade | Finding (file:line at `e8cd0069`) | Disposition |
|---|---|---|---|
| 1 | MAJOR | `validate-intent.md:56,67-80`: "a finding that must last goes into a report", and reopening the same report on the next run. A `finding` section renders the live flag (`report/ReportResolver.java:136-145`); opening a log clears flags (`ui/MainFrame.java:6439`), and bundles do not carry them. | **Fixed.** Flags are described as belonging to this run and session. A lasting conclusion goes in a narrative, a walk caption or the exported PDF. The repeatable part of a report is its `table` and `chart` sections, and the page says a `finding` section will report its flag missing on the next log. |
| 2 | MAJOR | `producing-a-log.md:189-190`: only an undeclared unit was said to be refused, and `--declare-unit` was said to write into the file. `BinaryAuditReader.checkUnit` refuses nanoseconds too, and the tool writes a copy. | **Fixed.** "Only millisecond binary logs open"; the tool command is quoted with `--out <copy>`, and the copy is what you open. |
| 3 | MINOR | `validate-intent.md:63-64`: `rowWhen` offered for every table kind; reports refuse it on aggregate, bucket and coverage tables. | **Fixed.** Scoped to `read` and crossings tables. |
| 4 | MINOR | `validate-intent.md:19-20`: two of three example criteria had no recipe. | **Fixed.** The order is now range, "none after", "for each". The page says the third can only be approximated today and links the planned form, and gives the two-step recipe for "none after" (`firstRecordIndex`, then `filter.from`). |
| 5 | MINOR | `build/index.md:68-70`, `spring-authoring.md:63-65`: "the analyser asks you about each request" holds only in `default` and `acceptEdits`. | **Fixed** on both pages. |
| 6 | MINOR | `producing-a-log.md:176-177`: the binary bullet sat in the Mongoose settings list; the synthetic byte offsets were not mentioned. | **Fixed.** The binary description moved into the note, which now lists five differences, including that `read` by `byteOffset` finds nothing in a binary log. |
| 7 | MINOR | `build/index.md:73-74` vs `spring-authoring.md:69-71`: different wording for Safe analysis; terms undefined; the in-app assistant's view changes need a switch. | **Fixed.** One wording on both pages, a short gloss of each term, the switch named, and a link to *Assistant profiles*. |
| 8 | MINOR | `build/index.md:51`: *Set up a project from my code* described for Claude Code only. | **Fixed.** With the in-app assistant or no AI it opens *New project…*. |
| 9 | MINOR | `spring-authoring-getting-started.md:115-116`: whether the model-choice runs cover Claude Code in the analyser. | **Fixed.** One bullet: the runs drove Claude Code from a test harness outside the analyser (`authoring-ladder-baseline-2026-10-13/RESULTS.md`); Claude Code in the analyser was not measured separately. |
| 10 | NIT | `validate-intent.md:33`: "never over a sample" above a table that includes `read`. | **Fixed.** "Counts and crossings are computed over the whole log". |
| 11 | NIT | `validate-intent.md:89`: replay compare needs a bundle captured with replay records. | **Fixed.** |
| 12 | NIT | The-loop's *Why it matters* pitch and its closing "try it" links were lost; how Full Claude Code differs from a terminal is only linked. | **Fixed.** A short *Why it matters* section closes the page with links to the tour and the sample log; the Full Claude Code bullet says the difference is on the linked page. |

**Also found during the fixes, recorded for the owner:** a shared settings file's *Assistant* category carries the
External MCP/REST access switch (product finding P3 in `docs-ia-product-findings.md`).

**Regression checks:** the strict build validates every link, anchor and the redirect; `DocsUrlResolutionTest` checks
that the redirect's target is a published page. The claims on these pages are prose about behaviour that the code's
own tests pin (`ReportResolver`, `BinaryAuditReader.checkUnit`, `AskCatalogueTest`); no new docs-pinning test was
added in this phase.
