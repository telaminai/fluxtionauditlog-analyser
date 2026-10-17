# Docs restructure, phase 3: independent review and dispositions

- **Phase:** 3, the split of `user-guide/assistant.md` (about 1,250 lines) by task.
  - It became six *Diagnose in the analyser* pages under `inapp/`, *Assistant profiles* (written in full) and *Managed
    policy (planned)* under `admin/`, *Set up a project with Claude*, *Spotlights, walks and reels*, and the *Verbs*
    reference.
  - "Is the log whole?" moved to `log-format.md`, and "Reading coverage results" to `topology.md`.
  - The page is deleted, and its URL redirects to `inapp/index.md`, which maps each old section to its new home.
- **Reviewed commit:** `1877e700` against `f7f2374a`.
- **Reviewer:** an independent general-purpose agent, working read-only.
  - It split the old page into 971 sentence fragments and searched the new site for each one.
  - It checked the new prose against the code at HEAD.
  - It ran a strict build of a clean export and ran the retargeted tests in that export.
  - It read the pages as the in-app diagnoser and as the admin.
- **Result:** no BLOCKER. 2 MAJOR, 8 MINOR, 7 NIT.
- **Verified by the reviewer:**
  - The move is faithful: nothing substantive was lost or duplicated, and no link or image broke.
  - Every required UI name matches the code: the four profiles and *Not chosen*, *Assistant: … ▾*, *Work mode*,
    *External MCP/REST access*, the not-local-only wording, *1 more request is waiting*, *allowed: it changes nothing*,
    and the drafted-question chip.
  - The redirect keeps the `#anchor`, and the in-app URLs resolve.
  - The six retargeted suites pass, and none of them is vacuous.

| # | Grade | Finding (file:line at `1877e700`) | Disposition |
|---|---|---|---|
| M1 | MAJOR | `inapp/transcript.md:47` and `inapp/assistant-panel.md:48-49` said in-app results are labelled *result*, *REFUSED* or *NOT RUN*. The code draws `✓`, `refused: …` and `denied: not run (…)` (`ui/AssistantPanel.java:656-662`). The marks table also lacked eight Claude Code marks (`LocalAgentAdapter.markWords`). | **Fixed.** Both pages now give the real marks. The transcript page has one table for the in-app assistant and one for every Claude Code mark `markWords` can produce. |
| M2 | MAJOR | `inapp/index.md:41-43`: "with an API key, each finished answer shows its cost" is true only for Claude Code. The in-app assistant shows no cost. | **Fixed.** The paragraph now separates the two: Claude Code on a plan, Claude Code on an API key, and the in-app assistant, which shows no cost because your provider bills the key. |
| m3 | MINOR | `transcript.md`'s intro said "the same whichever assistant answers", but the body names only *Claude Code*. In-app answers are marked *Assistant*. | **Fixed.** The intro names both markers and says the description holds for both. |
| m4 | MINOR | `log-format.md:102` says "five answers" above a six-row table. The audit flagged this; the text moved unfixed. | **Fixed:** "six answers". |
| m5 | MINOR | `teach/spotlights-walks-reels.md`: the design-line paragraph split "One thing lit" from its image, and the Java caveats opened *Save it as a walk*. The audit flagged both; they moved unfixed. | **Fixed.** Each paragraph now closes its section, as **Source targets** and **Java source targets**. |
| m6 | MINOR | The `inapp/approvals.md` intro said Safe analysis asks about every call of the six verbs, and missed the page's own two exceptions. Its line 19 repeated the intro. | **Fixed.** The intro names both exceptions (non-mutating sub-actions; calls the settings refuse), and the repeated sentence is merged. |
| m7 | MINOR | Stale pointers outside the site: the `docs/admin/docs-site.md` tree, `docs/specs/spec-authoring-token-lowering.md:263`, and the `tools/heldout-client.py` docstring. | **Fixed.** The docs-site tree is redrawn for the task-first layout; the spec and the docstring point at the new pages. |
| m8 | MINOR | Two link rewrites landed on the overview where the verb reference fits (`projects.md:237`, `records-and-filtering.md:45`). The verbs page's long MCP section has no `open` or `context` headings. | **Fixed.** Both links point at `reference/verbs.md`. The MCP section now has `### spotlight`, `### source`, `### open`, `### context` and `### Read-only, reversible and destructive` headings, so the anchors are stable. |
| m9 | MINOR | `FaqSecurityContractTest`'s untrue-phrase loop checked only 3 of the split pages. | **Fixed.** It now checks all nine assistant pages, `admin/managed-policy.md` included. |
| m10 | MINOR | The audit's §2 P2 gap 7, the "It refused. Why?" table, was not written anywhere. | **Fixed.** `inapp/assistant-panel.md` now has a *When the panel refuses* section. Every message in it is quoted from the code: `AssistantPanel.IN_APP_NO_KEY` and `OFF_LIMITS`, `AssistantProfileChoice.BUSY_*`, `LocalAgentSession.BUSY_TEXT`, the `AssistantSurface` chip, `LocalAgentPermission.ASK_QUESTION_NOTE`, and the plan-mode mark. |
| n1 | NIT | `ask.md:13` said "(its menu is new)", and two pages had stray double blank lines. | **Fixed.** |
| n2 | NIT | `build/set-up-with-claude.md:4` said "the same folder checks as Start", but Start is now on another page. | **Fixed.** "Start" links to `../inapp/claude-code.md#start`. The Claude Code page's bold run-in paragraphs became headings for this. |
| n3 | NIT | `inapp/index.md:71-75` was restructure meta, and its link text differed from the nav title. | **Fixed.** The paragraph reads "Related pages in other sections", and its link text matches the nav. |
| n4 | NIT | `reference/verbs.md:282`: "Those last two" follows the `propose` sentence. | **Fixed.** The sentence names `screenshot` and `report`'s file outputs. |
| n5 | NIT | The `PublishedSpecExamplesTest` constant was still named `ASSISTANT`. | **Fixed.** It is now `LOG_FORMAT`. |
| n6 | NIT | Lost, trivial: "MCP requests address the current offer when handled." | **Fixed.** Restored in `projects.md` ▸ *Restore an earlier session*. |
| n7 | NIT | The `processorDeclarations` paragraph is in both `projects.md` and `project-panel.md`. This predates phase 3; phase 3 removed a third copy. | **Deferred** to the Admin phase, which merges projects, portable context and sharing. |

**Regression checks:**

- The retargeted docs tests now pin the moved text on its new pages:
  - the Safe and In-app rows on *Assistant profiles*;
  - the in-app heading on *The assistant panel*;
  - every verb on *Verbs*;
  - the stream-end states on the log format page;
  - the Java caveat on *Spotlights, walks and reels*.
- `FaqSecurityContractTest` scans all the split pages.
- `DocsUrlResolutionTest` checks every redirect.
- The strict build validates the anchors.

**Not covered by a test:** the in-app and Claude Code mark tables (M1). They are transcribed from
`AssistantPanel.toolLine` and `LocalAgentAdapter.markWords`, and no test compares them. This is a candidate for the
Reference phase: generate the tables, or pin them.
