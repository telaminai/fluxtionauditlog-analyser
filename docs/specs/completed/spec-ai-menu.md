# Spec — the AI menu, and the runbook `description` it needs

**Status:** SHIPPED 2026-08-28 — .1–.7 + D-AI9 on main; review `docs/handoff/completed/review_m43_ai_menu.txt` SOUND, F1 fixed in `ac6a559` (ledger entry open for a reviewer); report `docs/handoff/completed/report_m43_ai_menu.txt`. Written 2026-08-28 (owner-directed: *"a menu item AI for quick access to AI functions"*, with
M38.8 folded in — *"it will need the skills binding to playbook at the same time"*).
**Milestone:** M43, absorbing **M38.8**. **Tracker:** [tracker.md](tracker.md).
**Depends on:** M37 (the Project panel), M38.1/.2 (runbook + glossary pointers), M42 (MCP client setup).

## The proposition

Everything an AI client needs from this app is reachable, and almost none of it is *findable*. MCP setup
lives in two places — Settings ▸ Assistant, and the Start page, which appears only on demand via
*Help ▸ Start page*. So the feature is invisible at exactly the moment someone wants it: mid-session,
with a log open, thinking *"I should hook my LLM up to this."*

And one thing is not reachable at all. Runbook and glossary pointers can only be added by hand-editing
the profile, even though `ConfigStore.writeRunbooks` already writes them — the app is a profile writer
already, it just offers no way to ask.

This milestone adds one menu that gathers the first and supplies the second, and takes M38.8 with it
because adding a runbook through a dialog is precisely where a `description` has to be captured.

## D-AI1 — the Project panel STATES; the AI menu ACTS

M37's D-L3 makes the Project panel reveal-only: every button navigates or copies, nothing changes state.
That is worth keeping, so the mutation goes somewhere else rather than eroding it.

The split is the whole architecture of this milestone: **the panel is where you see what is in force, the
menu is where you change it.** A reader never has to wonder whether a panel button will alter their
project, and a setting never acquires two owners.

## D-AI2 — a menu item OPENS the owner of a setting, or is BOUND to it; it never holds a second copy

Every item does one of three things:

- **opens the one dialog that owns a setting** (*Connect an AI client…* → `McpSetupDialog`;
  *Report exchange directory…* → Settings ▸ Assistant),
- **is a checkbox bound to the same config value** Settings renders — a rendering, not a duplicate,
- **performs a one-shot act** (reveal a directory, open a docs page).

No item keeps state of its own. This is D-L1's rule ("a surface is a rendering of the model, never a
second model") applied to a menu, and the reason is concrete: `KnownKeys` and D-C10 exist because
settings that live in two places drift, and the copy nobody updates is the one still in use.

## D-AI3 — an item whose precondition is missing is DISABLED WITH A REASON

*Runbooks…* with no project open is greyed, and its tooltip says *"needs an open project — File ▸ Open
project"*. It does not open a dialog that then explains itself.

M35 spent a milestone removing six modals that fired at empty screens, and D-L3 chose *Add source* over a
*Go* with nowhere to go. A new menu is the single most likely place to reintroduce them, so the rule is
written here before the first item is added, not after the sixth.

## D-AI4 — nothing on this menu RUNS anything

No *Run runbook*, no *Ask the assistant…*, no *Execute analysis* beyond the existing *File ▸ Run analysis*.
D-C2's no-execute rule is load-bearing for the whole runbook design — the analyser serves a pointer and
never the instructions — and a menu is exactly where "just add a Run item" becomes tempting in six months.

Recorded as a **non-goal at the surface most likely to erode it**, which is the only place a non-goal does
any work.

## D-AI5 — the `description` is DECLARED; the frontmatter may OFFER it, never supply it

This is M38.8, and the tension it has to resolve. A skill-shaped runbook already carries `name` and
`description` in frontmatter, and the documented convention says *"nothing in the analyser parses the
frontmatter"*. Reading the description out of the file at serve time would break D-A2 — the fact would be
inferred, and would change under the profile without anybody declaring it.

So: `runbook.N.description` is **stored in the profile**, declared like the name and gated like the path.
When the *Add runbook…* dialog is pointed at a skill-shaped file it **reads the frontmatter to PREFILL the
name and description fields**, and the human accepts or edits them before anything is stored.

That is M35.4's shape exactly — *discovery offers, and never selects*. The file suggests; the person
declares; what is served is what was declared. It also means a runbook whose frontmatter later changes
does not silently change what `context` says, which is the property D-A2 exists to protect.

Runbooks written to the documented convention need no rewriting, which was the point of documenting it
ahead of this slice.

## D-AI6 — adding a pointer goes through the existing gate, and shows the refusal

*Add runbook…* calls `Runbooks.refuse(name, path)` and renders the reason in the dialog rather than
disabling the OK button silently. Absolute paths, `..` escapes, URL schemes and command-shaped strings are
refused exactly as they are at the profile loader, the share import and the share export — a fourth
entrance to the same gate, not a fourth gate.

The description is inert text and is gated the same way the name is: length-bounded, one line, no control
characters. It is never executed, never resolved, never fetched.

## D-AI7 — pointers are written to the PROJECT when one is open

A runbook pointer is portable context (D-C1 tier 3); its value is that it travels to a colleague. So with
a project open the write goes to the project profile, and the menu says which file it wrote to. With no
project open the item is disabled (D-AI3) rather than quietly writing to the user's own settings, where it
would help nobody and surprise the person who later opens a project.

## D-AI8 — the share label must NAME the new cargo

`SettingsShare` labels are a contract: *"Runbook LOCATIONS (paths in your repository — never their
contents)"*. A stored `description` is authored text that now travels with the pointer, so the label must
say so — the rule is that a person reads the checkbox and knows what leaves. Whatever the wording lands
on, a test asserts the label mentions the description, in the same way the M38 categories are pinned.

## D-AI9 — a status light, and the thing it must not claim (owner, 2026-08-28)

A green/red indicator for the AI connection, in the status bar and mirrored on the menu. The trap is
named in `McpSetupState`'s own javadoc and the light must not spring it:

> *"a green local state never gets misrepresented as 'an AI client is connected'"*

Those are two different facts. This process can know **continuously and for free** whether its own local
transport is up; whether a client is actually talking to it is a separate fact that needs a probe and is
true only at the moment it is measured. So the light reports the first and says so in its words: **"MCP
ready"**, never "MCP connected".

Four states, because `McpSetupState.LocalStatus` already distinguishes them and collapsing them would
lose the one that actually confuses people:

| State | Light | Means |
|---|---|---|
| `READY` | green | this window is serving; a client can reach it |
| `OFF` | grey | the transport is off — not an error, a choice |
| `STARTING` | amber | enabled, no live endpoint published yet |
| `OTHER_INSTANCE` | amber | **another analyser window owns the endpoint** — your client is talking to a different log |

`OTHER_INSTANCE` is why this is worth building. It is invisible today, it is the state most likely to
waste an hour, and it already carries an accurate sentence to show.

**There is no red, and that is a decision** (owner asked, 2026-08-28). Red means *broken, act now*, and
nothing in this set is broken: an off transport is a CHOICE, and another window owning the endpoint is a
SURPRISE. Colouring a deliberate decision as a fault is how an indicator becomes something people stop
reading — the same argument that keeps the coverage caveat off a perfect score. Red stays unspent, so it
still means something on the day something genuinely fails.

**And "tested" is deliberately NOT in the light.** A probe launches a bridge process and is true only at
the instant it ran; a light showing a green tick from ten minutes ago asserts something it does not
currently know. That is the declared-never-inferred line the rest of the app holds, so the probe stays in
the setup dialog where a person asked for it and can see when it ran. The light reports the fact that is
continuously true and free to check; the dialog reports the fact that has to be measured.

**"No config" reads as grey, not red**, for the same reason — and its tooltip carries the remedy rather
than a diagnosis, so the state that needs an action says what the action is.

The tooltip is the existing `detail` string — one message, not a second wording that can drift from it
(D-AI2 again). Clicking the light opens the same MCP setup dialog the menu does.

## Non-goals

- **Not a second Settings dialog.** Every setting keeps exactly one owner.
- **Not an executor** (D-AI4).
- **Not a runbook editor.** The menu manages *pointers*; the file is edited in the user's editor and
  reviewed in git. *Open* already shows it read-only (M42-era viewer).
- **Not frontmatter parsing at serve time** (D-AI5) — only at the moment of offering, in a dialog a
  human is looking at.

## Open question (owner)

1. **Menu name and position.** Proposed **`AI`**, placed after *Records*. Not *"AI assistant"*: the
   assistant is one specific thing in this app (the in-app panel), and this menu is mostly connection and
   context. The docs nav already settled on *Working with AI*; a one-word menu matching that vocabulary
   avoids a second name for the same idea. Say if you want the longer form.

## The menu

```
AI
  Connect an AI client…              → McpSetupDialog                    (D-AI2)
  ☐ Local MCP / REST enabled         → bound to the Settings value       (D-AI2)
  ─────
  Runbooks…                          → add / remove pointers             (D-AI5, D-AI6, D-AI7)
  Domain glossary…                   → the vocabulary pointer, same shape
  ─────
  Report exchange directory…         → Settings ▸ Assistant
  Show exchange directory            → reveal; disabled + reason when off (D-AI3)
  ─────
  Working with AI (docs)             → the site page
```

## Acceptance

- [ ] Every item opens an owner, is bound to one config value, or performs a one-shot act; a test asserts
      no item holds state of its own (D-AI2).
- [ ] Items with an unmet precondition are disabled and their tooltip names the remedy; a test covers the
      no-project and exchange-off cases (D-AI3).
- [ ] No item executes a runbook or an analysis; a test asserts the menu names no execution path (D-AI4).
- [ ] `runbook.N.description` is stored, served in `context.runbooks[]`, and shown on the Project-panel
      row; it is never read from the file at serve time (D-AI5).
- [ ] Pointing *Add runbook…* at a skill-shaped file prefills name and description; editing them before
      OK changes what is stored; the stored value never changes afterwards because the file did (D-AI5).
- [ ] Every refusal from `Runbooks.refuse` is rendered with its reason (D-AI6).
- [ ] With a project open the pointer lands in the project profile and the dialog names the file written
      (D-AI7); with none, the item is disabled.
- [ ] The share label names the description as cargo, asserted by test (D-AI8).
- [ ] `KnownKeys` owns the new key's family, and the M38.7 drift test still passes.
- [ ] Docs: *Working with AI ▸ Runbooks* gains the menu route and the prefill behaviour; the Project panel
      page gains the description row; CHANGELOG; tracker; a generated screenshot of the menu.

## Slices

- **M43.1** The menu itself — the four navigate/bind items (Connect, MCP toggle, exchange directory,
  docs), with the disabled-with-a-reason discipline and its tests. No new model.
- **M43.2** `runbook.N.description` end to end — model, `ConfigStore`, `KnownKeys` family, `context`,
  Project-panel row, share label. This is **M38.8**, and it lands before any UI can capture a description.
- **M43.3** *Runbooks…* — add and remove pointers through `Runbooks.refuse`, project-tier write, refusal
  reasons rendered.
- **M43.4** The skill-shape prefill — read frontmatter to OFFER `name`/`description`, human confirms
  (D-AI5).
- **M43.5** *Domain glossary…* — the same dialog shape for the single vocabulary pointer.
- **M43.6** Docs, generated shot, CHANGELOG, tracker.

## Why fold M38.8 in rather than ship it alone

M38.8 was scheduled as a small standalone slice: store a description, serve it, show it. Fold it here
because the menu is the first thing that would have to *ask* for one, and a dialog that adds a runbook
without capturing its description would ship a second entry point that has to be revisited immediately.
The order matters — M43.2 before M43.3 — for the same reason: a UI that writes half a record teaches the
storage a shape it then has to unlearn.
