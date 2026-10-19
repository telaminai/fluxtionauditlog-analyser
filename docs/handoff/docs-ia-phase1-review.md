# Docs restructure, phase 1: independent review and dispositions

- **Phase:** 1, the skeleton: the task-first nav, Home, Start here, redirects and `DocsUrlResolutionTest`.
- **Reviewed commit:** `e3e75027` against `031545cc`, on branch `docs/site-restructure`.
- **Reviewer:** an independent general-purpose agent, working read-only. It checked claims against the code at HEAD,
  links and redirects, persona fit, and the nav against the audit's §5.1.
- **Fixes:** the commit that follows `e3e75027` on the same branch.

| # | Grade | Finding (file:line at `e3e75027`) | Disposition |
|---|---|---|---|
| B1 | BLOCKER | `.gitignore:31` (`build/`) ignored `docs/site/build/`. The three Build pages existed only on disk, so a clean checkout's strict build failed, and Home and Start here linked to pages that would 404. | **Fixed.** `!docs/site/build/` added to `.gitignore` and the three pages committed. Regression check: `DocsUrlResolutionTest` now resolves pages against `git ls-files` minus `exclude_docs`, not the disk, and a new `everyNavPageIsPublished` fails on a nav page git does not track. Wrong-result witness: before the pages were staged, it failed naming all three. The phase gate now builds a `git archive` of the index, not the working tree. |
| M1 | MAJOR | `start-here.md` "always true" bullet 1 left out that Full Claude Code's Start runs hooks and connects MCP servers before any Send (`ui/LocalAgentPanel.java:76-78`, `ui/ConfigPanel.java:83-86`). | **Fixed.** The bullet now carries the code's own sentence. |
| M2 | MAJOR | Bullet 1, "until you choose an assistant and press Send", did not fit an outside client, which has no Send. | **Fixed.** It now reads "until you ask", and defines asking as pressing Send or prompting a connected client. |
| M3 | MAJOR | Bullet 2, "you can undo them", is false for `graph {delete}`. That call is permanent (`llm/VerbSchemas.java:129`), not marked destructive (`mcp/McpTools.java:92-93`), and pre-allowed in Safe analysis (`McpTools.java:185-189`). | **Fixed in the docs.** "Reverse" now comes with the means (widen the filter, close the chart, clear the flag), and the bullet says deleting a saved chart's definition cannot be reversed. One correction to the finding: Claude Code in the analyser cannot call `graph {delete}` at all, because the server refuses it (`net/ActionServer.java:402-404`), so the exposure is outside clients and the in-app assistant. **Code follow-up (not in this docs PR):** mark `graph {delete}` destructive for outside clients, with a regression check. Recorded for the owner; it changes product behaviour, so it needs its own reviewed PR. |
| M4 | MAJOR | Bullet 3 missed two exceptions: saved reports, walks and charts are written to the project profile with file exchange off (`config/ProjectProfile.java:242-264`), and outside clients have their own tools. | **Fixed.** The bullet is scoped to "a file an assistant names through the analyser" (screenshot, PDF or CSV, reel, bundle). It says saved reports, walks and charts are kept in the project, and that Full Claude Code and any outside client have tools of their own. |
| M5 | MAJOR | `start-here.md:7-9`: "Claude Code can run commands only if you turn on Full Claude Code" is wrong for Claude Code in a terminal. | **Fixed.** The sentence is scoped to the Claude Code the analyser starts; a client you run yourself works under its own rules. |
| m1 | MINOR | The demo door said "Press **1. Run me through a demo**", but choosing an entry does nothing and the button reads **Play** (`AskCatalogue.properties:44`). Home had the same problem, and its "one click" was true only for *Guided tour*. | **Fixed** on both pages. "Choose … then press **Play**"; the Get started card names *Explore DEMO ▸ Guided tour*. |
| m2 | MINOR | "With the assistant Off … nothing … sends your evidence" ignored External MCP/REST access, which is a separate setting (`config/AppConfig.java:92`). | **Fixed.** The sentence now needs both off. |
| m3 | MINOR | "each node that reacted … and what each one computed" overstates: what is recorded depends on the audit level. | **Fixed** on Home and Start here: "each node that logged … at the audit level it was given". |
| m4 | MINOR | `DocsUrlResolutionTest` could pass vacuously. It checked files on disk (the B1 failure), and it skipped non-literal call sites. Its `""` known target came from `openUserGuide("")`, which opens the in-app help. | **Fixed.** Pages resolve against git. Every `openDocsPage(`/`openUserGuide(` call that is neither a literal nor on a reviewed allow-list (the declarations and `MainFrame`'s forwarder) fails. `openUserGuide("")` is no longer counted as a site target; the site root comes from `AskPointers.DOCS_URL`. Duplicate-heading suffixes are not modelled; that can only cause false failures. |
| m5 | MINOR | The `user-guide/index.md` redirect lands on a stub that linked to only two pages. | **Fixed.** `inapp/index.md` lists the canvas pages, and says where the rest of the old user guide now lives. |
| n1 | NIT | Start here's outside-client door left out "or another MCP client". | **Fixed.** |
| n2 | NIT | Some nav labels differ from page headings or from §5.1 (*Projects, profiles and sharing* over *Working across projects*; *Connect your client*; *Investigation reports*). | **Accepted for now.** Each heading is aligned when its page is rewritten: *Connect your client* in phase 2, projects in the Admin phase. |
| n3 | NIT | `assets/README.md:38-39` still said "Working with AI ▸ Connecting an LLM". | **Fixed.** |
| n4 | NIT | The CHANGELOG said "Moved and removed pages redirect"; phase 1 removed one page and moved none. | **Fixed.** The line says "A page that moves or is removed redirects…", which stays true as later phases move pages. |

## Gates after the fixes

- **Strict build:** `mkdocs build --strict` on a `git archive` of the staged tree passes, with anchors validated.
- **Headless tests:** `mvn -o -q test` passes (the run is recorded in the phase report). Frame suites are skipped
  headless, as usual.
- **Sweep:** the four-term sweep over `git ls-files` prints nothing.
- **Paths and names:** no added line contains a machine-specific home path, and no customer or company names were added.
