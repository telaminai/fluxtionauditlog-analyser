# Spec — the project tells you what it knows

**Status:** implemented 2026-09-26. Closes #20, #21, #22, #23.

## The proposition

Four defects found in one day of using the analyser on a real investigation. They look unrelated —
a missing dialog, a config key, a window title, a delete button — and they are the same defect:

> **The analyser knows something the person needs, and does not give them a way to see it or act
> on it.**

- **#20** The Project panel tells you to "declare a workspace anchor". Nothing in the product declares
  one. `PathForm.refuseWorkspaceRoot` already validates an anchor and returns the reason it is
  refused; no control ever calls it.
- **#21** The exchange directory is machine-wide, so artefacts land in a temp directory rather than
  beside the work they evidence. The analyser knows the project root and cannot use it.
- **#22** `ProjectSession.activeName()` returns the *directory* name, so every profile under one root
  displays the same title. `context` reports the exact settings path; the window does not.
- **#23** A report can be created and replaced by name, never deleted or renamed. The profile only
  accumulates, and there is no route out but hand-editing the file with the project closed.

Each is small. Together they decide whether a person can trust what is on screen, which is the whole
job.

## Why they are one change

They share a shape and a test: **after this, can the person answer "what am I looking at, and where
will my work go?" without an assistant reading `context` for them?** Today the answer is no on all
four counts, and an assistant is the only thing that closes the gap. That is backwards: the assistant
should be a convenience, not the only route to the truth.

They also share a risk. Three of the four write to the active profile, and **project edits auto-save
with no save step and no undo**. #22 is what makes that dangerous — you cannot see which profile you
are about to write to. Fixing #22 without the others leaves the danger; fixing the others without
#22 increases it.

## D-1 — name the profile, not the directory (#22)

`activeName()` keeps returning the project name, because that is what most of the UI wants. A new
`activeLabel()` adds the profile when it is not the canonical one:

```
.analyser/project.fluxtion-settings                  ->  "maker-fxoc"
.analyser/project.reciprocal.fluxtion-settings       ->  "maker-fxoc — reciprocal"
.analyser/project.exp-2026-09-25-contra.fluxtion-settings -> "maker-fxoc — exp-2026-09-25-contra"
```

`ProjectProfile.isProjectProfileFileName` already distinguishes canonical from named; the middle
segment of `project.<this>.fluxtion-settings` is the label. The window title and the Project panel's
project row both use it, and the row carries the full settings path as its detail — the way a source
root row carries its stored form.

**Deliberately NOT changed:** `activeName()` itself. Callers that want the project (cache keys, report
headers, the `project.name` in `context`) must keep getting the project. This adds a label; it does
not rename the project.

## D-2 — a project may say WHERE the exchange directory is, never WHETHER (#21)

New project-tier key `assistant.exchangeDir`, accepted **only** as a project-relative path with no
`..`. Reuse `Runbooks.refusePointer`, which enforces exactly that rule and returns the refusal text.

**Anchored on the project root, NOT on `workspaceRoot`.** The workspace anchor deliberately permits
`..` up to six levels (`PathForm.ANCHOR`), which is safe for source roots because they are inert
lists the analyser only reads. The exchange directory is *written to*, and by the assistant. A
`..`-capable anchor would let a shared profile place it anywhere at or above the project.

**The opt-in stays machine-tier.** If `Allow assistant file exchange` is off, a project-supplied
location changes nothing. A project says where, never whether. Resolution order:

1. project `assistant.exchangeDir` when a project is open, exchange is enabled, and the value passes
2. otherwise the machine `assistant.exportDir`
3. otherwise unset, as today

A configured directory that does not exist is **refused with its reason**, not silently created:
creating directories from a shared profile is the kind of side effect that should be deliberate.
`context.exports` gains `source: "project" | "machine"` so the answer is visible.

## D-3 — a workspace anchor can be declared where the roots are (#20)

`Sources ▸ Source roots…` gains an anchor control at the top.

**It is a depth, not a path.** `PathForm.ANCHOR` accepts only `.`, `..`, `../..` up to six levels, so
the control is a combo of ancestor directories — each showing the directory it resolves to and how
many of the current roots it would make portable — not a file chooser. Choosing one writes
`workspaceRoot`; `PathForm.refuseWorkspaceRoot` validates and its refusal text is shown verbatim.

The Project panel's warn detail already tells people to do this. After this change the instruction
names the menu path, so the remedy is reachable from the warning.

## D-4 — a report can be removed and renamed (#23)

Parity with what charts already have:

- **Delete**, from the Reports tab, behind a confirmation that **names the report and says what is
  lost**. A report cites a log; the confirmation says which, and says what survives — the log, the
  charts and any exported PDF are not touched. Charts set this precedent — Delete is separate from
  Close for exactly this reason.

  *Amended during implementation.* This first said "from the Reports tab **and the Project panel
  row**". The Project panel is reveal-only by design (M37 D-L3: "a display that can mutate state is a
  display people learn not to trust"), and `ProjectPanelIsRevealOnlyTest` pins the `Navigator`
  interface to an exact set of four navigation methods so that this cannot erode by accident. A
  Delete there would be the first mutation on that surface, for a convenience already one click away:
  the report row's **Open** reveals the report in the Reports tab, which is where Delete lives. The
  invariant is worth more than the click.
- **Rename**, matching `graph {name, rename}`.
- **An MCP verb for both**, so an assistant that can create a report can clean up after itself. It
  cannot today, which is how a throwaway diagnostic became permanent in a shipped profile.

Replace-by-name is unchanged. Rebuilding a report under the same name to update it is good
behaviour; the gap is that there was no way out, only in.

## Acceptance

- [x] Two profiles under one root show different titles, and the Project panel row shows the settings path.
- [x] `activeName()` still returns the project name; nothing that keys on it changes.
- [x] A project-supplied exchange directory is used when exchange is enabled, ignored when it is off.
- [x] `..`, `~` and absolute values for `assistant.exchangeDir` are refused with the reason shown.
- [x] A project-supplied directory that does not exist is refused, not created.
- [x] `context.exports.source` says `project` or `machine`.
- [x] The anchor control offers only ancestors, writes only what `refuseWorkspaceRoot` accepts, and
      shows how many roots each choice makes portable.
- [x] Declaring an anchor turns the Project panel's warn rows normal without a restart.
- [x] A report can be deleted from the UI and over MCP; the confirmation names the report and its log.
- [x] A report can be renamed; replace-by-name still replaces.
- [x] Deleting the last report leaves `report.count=0` and a well-formed profile.

All eleven are covered by tests named for the criterion. Two are worth stating because the
implementation moved:

- *Delete from the Project panel row* was dropped, and the spec says why above. The row's Open
  reveals the report where Delete lives.
- *The anchor control offers only ancestors* became **only ancestors below the filesystem root**. A
  wrong expectation in the test for that criterion turned up an anchor at `/`, which every absolute
  path is under: it would have reported every root portable while writing the machine's layout into
  the profile as a run of `..` steps — the best-looking answer in the list and the worst one in the
  file.

## Non-goals

- Changing auto-save. Auto-save is fine; not knowing what you are saving into was the problem.
- Carrying the exchange **opt-in**, or any credential, in the project tier.
- Inferring a workspace anchor from where the roots happen to live. D-C9 settled that: declared once,
  by the person who knows the layout, never guessed.
- The chart and report-rendering defects found in the same session (append-on-`exprs`,
  destroy-on-replace, PDF sections dropped silently, the window-edge step). Different family,
  separate change.
