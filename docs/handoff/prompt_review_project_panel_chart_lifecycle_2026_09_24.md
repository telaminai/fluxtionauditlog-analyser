# Reviewer prompt: Project-panel navigation, chart lifecycle and restore proposal

Review the following work in `telaminai/fluxtionauditlog-analyser`, pinned to **1e51545d**. Fetch first and use an isolated worktree. This head was verified on `origin/main`; it is already pushed. Do not review a different running release instead.

## Scope

| Commit | Content |
|---|---|
| `35eeb320` | Row-specific Project-panel Open; chart-style persistence |
| `43ce82fc` | Investigation made self-contained |
| `a27b4d13` | Verification record and the chart-definition loss it exposed |
| `38ecc7f3` | Closing retains the definition; explicit Delete chart; persisted open/closed state |
| `1e51545d` | Restore/project-reload semantics proposal, not implemented policy |

There are **two application-code commits and three documentation commits** in this list. `fbe92074` between the earlier changes is unrelated: its message names other work, but its actual diff is two README lines.

Read:

- Root `CLAUDE.md` and `docs/ONBOARDING.md`, including the public-repository sweep.
- `docs/specs/completed/spec-loaded-panel.md`, especially D-L3 and its owner-approved amendment.
- `docs/investigations/profile-project-root-resolution.md`, including the new verification record.
- `docs/proposals/restore-and-project-reload-semantics.md`.
- Relevant canonical project/session-recovery and settings-sharing specifications, found from the tracker.
- The review of the earlier pair: `docs/handoff/review_project_panel_open_style_2026_09_24.md` on branch `review/project-panel-open-style-2026-09-24`, with its executable probes and screenshots. Read it as claims to reproduce, not as authority. It reviewed **43ce82fc**, not this full head.

## Review priorities

1. **D-L3's boundary.** The owner approved revealing a specific existing item. Decide whether each new path only reveals a saved definition or instead authors, edits or discards durable state. Inspect the MainFrame adapter, not just the Navigator method-set test. The report's name is identity; its title is presentation. A new row-action method is not safe merely because its name starts with “show”. Keep creation/deletion outside the Project panel.

2. **Close versus Delete, end to end.** Drive real MainFrame buttons with a project containing multiple annotated charts. Close must remove only a tab; the definition, series, expressions, style, pin, notes and explanation must survive, with `open=false`. Reopen via the Project-panel row and inspect profile bytes as well as the canvas. Verify reopening persists the intended open state. Cancel Delete must change nothing; confirm Delete must remove the named definition and keep it removed after every save/reopen. Test the last tab, all saved definitions closed, and an empty saved collection. Check whether fallback creation and change-listener ordering can recreate a deleted definition or create an unintended one.

3. **Merge semantics.** Attack `syncOpenGraphsIntoConfig` with renames, duplicate names, names colliding with closed definitions, newly added tabs, close after an edit, and project switching. A name-keyed merge must not retain the old name as a ghost chart, overwrite another definition, or revive something deleted. Check name counters and the placeholder tab. Verify the metadata is kept throughout every constructor/copy/import/export path, not only ConfigStore.

4. **Reproduce the earlier findings against this head.** The prior review reproduced three issues: the human style dropdown does not request autosave although `setStyleByName` does; SettingsShare's external-path rewrite drops the saved style; and the “old saved-chart Open was a dead control” history is false at `35eeb320^` (it rendered no Open). Confirm whether each still holds. Extend the import attack to the new `open` component: external series and external markers must preserve both style and closed state. Preserve the distinction between an old defect left unfixed and a regression introduced here.

5. **The restore proposal, hardest after data preservation.** Do not treat the fixture's two `context.restoration.inputs` as proof that only two roles are supported. Inspect the complete recovery model, capture and apply paths: log, topology, design, diagnostics, record position and any captured view state. Distinguish “not captured”, “captured but withheld”, “not present in this fixture” and “lost”. Check whether a published explicit restoration policy already governs each claim. Historical existence of a guard is not by itself evidence that every resulting behaviour was deliberately chosen.

   In particular, review B1's claim that a Project-panel “reopen” offer is reveal-only. Bringing an existing chart view forward is not automatically equivalent to loading a log and changing the session. State which surface may offer navigation to the existing restore decision and which may actually execute it. Do not approve automatic restoration or new capture policy on the owner's behalf. Evaluate the proposed label against the actual recovery scope rather than the sample session alone.

6. **Verification claims.** Separate the script's successful exit from the visible result. The earlier socket topology verb loaded its own graph and did not verify a Project-row click. Unit tests stopping at Navigator prove routing, not the adapter's behaviour. Check the new verification record's actual entrance, what it observed and what remains unverified. Never replace a real button check with a new MCP verb that clicks a Project row.

## Required checks

- Build this exact head with JDK 21. Run `JAVA_HOME=<jdk21> mvn test` and package the jar. Report total, failures, errors and skips separately; do not call skipped cases passing cases.
- Run `mkdocs build --strict` and `git diff --check`.
- Use a real display for alternating two report rows with distinct titles/names, reopening a closed chart, preserving edits on an already-open chart, the actual style dropdown followed by project close/reopen, and Close/Delete confirmation/cancellation. Inspect the rendered result and saved bytes. Use a constructed public-safe fixture if the owner's local one is unavailable; label it as constructed.
- For new regression assertions or claims of protection, include a green baseline and a mutation that fails the named assertion, then restore source byte-identically. A recording Navigator alone cannot protect the new persistence/lifecycle behaviour.
- Run the exact public-repository rule-one sweep before committing review evidence. Visually inspect every screenshot; the text sweep cannot inspect pixels.

## Constraints and deliverable

Review only. Do not implement fixes, edit the subject's specs/tracker/evidence, merge, publish a release, deploy, use a compilation key or run an LLM client trial. Leave other sessions' worktrees alone. Temporary probes/mutations belong only in your disposable worktree, and only review material should enter your review commit. Do not force-push or create merge bubbles.

Write a review on its own branch. Lead with a verdict, then numbered findings with exact file/line and concrete input → wrong result. Separate required corrections, optional improvements and owner decisions. State what you ran, what you inspected and what you could not verify. Give per-item dispositions for the prior review, the Close/Delete change and the restore proposal. Commit and push the review packet; return branch, commit and report path. Do not merely agree with the earlier review or the author’s verification record.
