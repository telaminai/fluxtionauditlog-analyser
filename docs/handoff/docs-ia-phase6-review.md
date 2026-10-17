# Docs restructure, phase 6: independent review and dispositions

- **Phase:** 6, *Administer and secure*.
  - `admin/security.md`: the one-page security model, marked *draft for owner review*, now carrying the security
    contract `FaqSecurityContractTest` pins.
  - `admin/external-access.md` and `admin/data.md`, written; `admin/settings.md`, `admin/managed-policy.md` and
    `admin/profiles.md` adjusted.
  - `user-guide/projects.md` absorbs `portable-context.md` and `sharing-setups.md` (both deleted, both redirect), with
    one table of what is kept where.
  - `faq.md` slimmed; its security answer moved to the security page.
  - Tests: `FaqSecurityContractTest` retargeted to the security page; `ShareDisclosureContractTest` follows the sharing
    text to `projects.md`, with a new pin for the *Assistant* share category.
- **Reviewed commit:** `ae578ae1` against `f913d2c5`.
- **Reviewer:** an independent general-purpose agent, working read-only through git. It checked every claim against
  the code, ran the strict build of a clean export, ran the two retargeted suites on that tree (3/3 and 5/5), compared
  the merged pages with the deleted ones, and read the security page as a customer's security reviewer.
- **Result:** 1 BLOCKER, 1 MAJOR, 6 MINOR, 6 NIT.
- **Verified by the reviewer:** the loopback listener (bind, port, token, `Origin` refusal, routes, rate limits, endpoint
  file mode, `--rest` persistence); the six destructive verbs, the hosted allow-list and the hosted `graph {delete}`
  refusal; the approval dialog's defaults, timeout and 60 s grant; Full Claude Code's folder-trust files; the in-app
  assistant's single gate; every share category's cargo and default; the tier and bundle columns of the new table; the
  network clients; the folders; every pinned phrase sitting in an accurate sentence; nothing lost in the merge.

| # | Grade | Finding (file:line at `ae578ae1`) | Disposition |
|---|---|---|---|
| 1 | BLOCKER | `user-guide/project-panel.md:13` still linked the deleted `portable-context.md`, so `mkdocs build --strict` aborted. | **Fixed:** it links `projects.md#portable-context-what-the-team-knows`. **Root cause, also fixed:** this phase's gate ran `mkdocs build --strict -q`, and `-q` hides the warnings that strict mode turns into an abort, so the gate passed a failing build. The gate now runs without `-q` and prints every warning. The pushed commits `6785c1fb` and `f913d2c5` were rebuilt without `-q` and build clean; `e8cd0069` and `2e0dbade` were built clean by their reviewers. `ae578ae1` was not pushed. |
| 2 | MAJOR | `admin/data.md:10`: session-recovery snapshots are separate files in `~/.fluxtion-analyser/sessions/` (`MainFrame.java:10752`, `SessionResumeStore`), not in `config`. | **Fixed:** a row of its own, saying what a snapshot holds (paths, SHA-256, the view with filters and flags). |
| 3 | MINOR | `admin/data.md` was incomplete while the security page says it lists every file: client registrations (`claude mcp add` / `codex mcp add`) and the S3 temp copy (`S3Source.java:44`). | **Fixed:** both rows added. |
| 4 | MINOR | `security.md:30,102`: "Claude Code is refused" with a bundle open read as "cannot be used"; it works in an empty scratch folder and reads through the verbs. | **Fixed** on both lines. The pinned phrase stays, with the scratch-folder sentence beside it. |
| 5 | MINOR | Two old FAQ facts were missing from the model: the per-session bridge file carries only that session's credential, not the token; and under `auto` and `plan` the analyser's destructive verbs are hidden. | **Fixed:** the first is in the private-bridge row; the second in the Full Claude Code row of the profile table. |
| 6 | MINOR | `data.md:48-49` and `faq.md`: "nothing goes on the network" ignored S3, templates and examples. | **Fixed** on both pages. |
| 7 | MINOR | `ShareDisclosureContractTest` read the whole 700-line `projects.md`, so unrelated prose could satisfy "provenance" and "focus"; the FAQ lost two old-name guards. | **Fixed:** the category pins read only the `## Sharing settings with a file` section (the new Assistant pin reads the page, because its warning sits in *What is kept where*); `Profile…` and "REST transport that lets" are guarded on the FAQ again. |
| 8 | NIT | `admin/profiles.md:43`: "One related switch" followed by two. | **Fixed.** |
| 9 | NIT | `security.md:44`: an outside client gets every verb except `propose`. | **Fixed.** |
| 10 | NIT | `security.md:181-182`: a double negative; "closed-world limit" is jargon. | **Fixed:** "silence in the log is reported as silence, never as proof that a node did not run"; the heading adds "what the analyser cannot see". |
| 11 | NIT | `security.md:31`: "byte-identical to a plain build" means little to an admin. | **Fixed:** "With no plugin installed, no third-party code runs" leads; the precise statement follows. |
| 12 | NIT | `security.md:121-123`: `CLAUDE.local.md` was missing from the instruction files (`FullFolderTrust.INSTRUCTION_FILES`). | **Fixed.** |
| 13 | NIT | `projects.md:24`: the Event processors category also exports processor declarations. | **Not changed in this phase.** Inherited from the old sharing page; recorded here, and the *Event processors* row is the place to add it when the Project panel page is next revised. |
| 14 | — | (Raised in passing) the redirects drop old `#fragments`. | **Accepted.** The merged sections have new headings; the page itself is the landing. |

**Regression checks:** `FaqSecurityContractTest` (every destructive verb and every security claim on the security page;
the FAQ keeps its question, its link and its offline answer, and cannot bring back old names);
`ShareDisclosureContractTest` (every category on the sharing section; the Assistant category's switch disclosed, with a
wrong-result witness recorded in the phase report); `DocsUrlResolutionTest` (both redirects); the strict build, now run
without `-q`.
