# Docs restructure, phase 5: independent review and dispositions

- **Phase:** 5, *Get started* and *Demo and teach*.
  - New pages: `start/tour-and-sample.md`, `start/templates-and-examples.md`, `start/prompt-packs.md` and
    `teach/index.md` (*Running a demo*).
  - `getting-started.md` became *Open your own log, step by step*; its Settings tour became `admin/settings.md`, written
    as a full Settings reference.
  - The start page's page gained its Build and Teach pathways. `guided-start.md` and `tutorial-playground.md` were
    fixed. `evidence-bundles/demo.md` merged into the bundles index, with a redirect. `projects.md`'s template section
    became a pointer.
- **Reviewed commit:** `2e0dbade` against `6785c1fb`.
- **Reviewer:** an independent general-purpose agent, working read-only through git. It checked claims against the
  code, ran a strict build of a clean export and seven related test suites (27 tests, no failures, no skips), checked
  that every in-app docs target still resolves, compared what left the old pages with where it landed, and read the
  pages as a newcomer, a teacher and an admin.
- **Result:** no BLOCKER. 1 MAJOR, 12 MINOR, 6 NIT.
- **Verified by the reviewer:** every prompt-pack limit and message; the example-entry rules; the template archive's
  containment; the start-page entry numbers against `START_ORDER`; the DEMO tour, journey and demo folder; every
  Settings label, default and tier; Follow's limits; the bundle limits and the `replay/` member; nothing lost from the
  old getting-started, start-page or demo pages; the philosophy packs only as "coming", unlinked; the authoring journey
  only as planned.

| # | Grade | Finding (file:line at `2e0dbade`) | Disposition |
|---|---|---|---|
| 1 | MAJOR | `admin/settings.md:62-64,105`: the *Assistant* share category carries four settings (`SettingsShare.java:237-241`, imported at `:644-648`), not two; `sharing-setups.md:18` and `managed-policy.md:3-6` contradicted the code. | **Fixed.** All four rows are marked †, and the text says External MCP/REST access is among them. `managed-policy.md` says the one switch that can come from a shared settings file. `sharing-setups.md` is merged into `projects.md` in phase 6, where the category's row and a warning name the switch. Recorded for the owner as product finding P3, and pinned in phase 6 by `ShareDisclosureContractTest.theAssistantCategoryDisclosesTheExternalAccessSwitch`. |
| 2 | MINOR | `admin/settings.md:60-61` vs `:103`: "never in a project profile" vs the project exchange folder stored in the project. | **Fixed.** The first statement names the exception. |
| 3 | MINOR | `admin/settings.md:109-110`: the History tab's **Clear all** was missing (`ConfigPanel.java:1004`). | **Fixed.** |
| 4 | MINOR | `templates-and-examples.md:56-57,63`: the executable list and the command list were too narrow (`TemplateArchive.POSIX_EXECUTABLES`, `commandsFor`); Windows gets a README pointer. | **Fixed.** Both lists now follow the code, and the Windows README pointer is stated. |
| 5 | MINOR | `templates-and-examples.md:80-101`: the 64 MB cap, the 6- and 2-minute deadlines and the redirect rule were missing. | **Fixed** in one *Bounded* bullet (`ExampleSource.MAX_ARCHIVE_BYTES`, `ExampleDownload.FETCH_DEADLINE_MS` / `UNPACK_DEADLINE_MS`, `ExampleArchiveClient.MAX_REDIRECTS`). |
| 6 | MINOR | `templates-and-examples.md:85-86`: the fork wording misquoted (`ExampleSource.java:142` uses parentheses). | **Fixed.** Quoted exactly. |
| 7 | MINOR | Flags described three ways; session recovery restores them for an unchanged log (`MainFrame.java:10791`, `:10946`). | **Fixed** in `guided-start.md`, `evidence-bundles/index.md` and `build/validate-intent.md`: flags are not exported or packed, and local session recovery restores them only for the same unchanged log. |
| 8 | MINOR | `evidence-bundles/index.md:38`: a finding section is not "empty" but *unresolved*, naming the missing flag (`ReportResolver.java:141-145`). | **Fixed.** |
| 9 | MINOR | `evidence-bundles/index.md:17-20`: the format-2 limit was shortened (`EvidenceBundle.LIMITS_REPLAY`). | **Fixed.** The clause "only as far as the processor reads nothing the records do not carry" is restored. |
| 10 | MINOR | Content dropped from projects.md's template section: folders outside the project for your own projects; "not silently opted back into support"; each settings file checked against its own folder, including the leave-and-come-back case; plus the one-top-level-folder rule. | **Fixed.** All four are on `templates-and-examples.md`. |
| 11 | MINOR | The in-app **See the format** opens `user-guide/start-page/#prompt-packs`, which had become a summary one click from the format. | **Fixed without changing the app:** the start page's section keeps a three-line format box and links to the full format. |
| 12 | MINOR | `teach/index.md:22-31`: "1. The tour" described the demo journey, while "Guided tour" is a different start-page action. | **Fixed.** The heading reads "1. The demo journey". |
| 13 | NIT | `tour-and-sample.md`: the `journey/` files were missing from the sample project table (`DemoAssets.java:46-47`). | **Fixed.** |
| 14 | NIT | `prompt-packs.md`: `1)` numbering and `+` bullets are accepted (`PromptPack.java:171`); some refusals only implied. | **Fixed.** |
| 15 | NIT | `getting-started.md:40`: **Sources ▸ Event processor…** is the direct menu item; settings.md could say the Sources menu opens these tabs. | **Fixed** on both pages. |
| 16 | NIT | `teach/index.md:82`: `analyser --verify` assumes the JBang command. | **Fixed.** The `java -jar` form is added. |
| 17 | NIT | `mkdocs.yml:94`: the redirect could land on the demo section's anchor. | **Not changed.** The redirect map holds page-to-page entries that `DocsUrlResolutionTest` checks; the bundles page's first screen links the section. |
| 18 | NIT | `user-guide/projects.md:45`: "makes it the active project" holds only when the download ships a profile. | **Fixed** in the phase 6 rewrite of `projects.md`. |

**Regression checks:** the strict build validates every link and anchor; `DocsUrlResolutionTest` checks the in-app
targets and the new redirect; `PromptPackTest` reads the sample pack; `EvidenceBundleDocsTest` lists the bundle pages.
The share-category disclosure (finding 1) gets its own pin in phase 6.
