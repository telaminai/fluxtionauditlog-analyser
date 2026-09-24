# Proposal: say what "Restore last session" and a project reload actually bring back

**Status:** proposal, nothing built. Raised 2026-09-24 by the owner while verifying M68.2/M68.3 at a real
display: *"reloading a project does not bring the log and graphs back after closing"* and *"a restore last
session only brings some of the state back"*.

Both behaviours are **pre-existing and deliberate**, dated from history — neither is an M68 regression:

- `GraphTabs.restore` early-returns when no log is loaded (`store == null`). That guard dates to
  `e965afa2`, the initial public release.
- A project reopen restores settings, not the session. That is stated in the verb echo itself —
  *"puts it back — the settings, not the session: reopen what `closed` names"* — and dates to `1fab905e`
  (M35.8 review N1/N3).

So this proposal is not a bug report. It is about the **distance between what the app promises and what it
does**, which is the same class of problem M68.2 fixed: an Open that opened nothing read as broken
software, and so does a Restore that restores two files under a name that suggests a session.

## What actually happens today (verified, not inferred)

Measured against a build of `main` at `38ecc7f3`, by reading the profile bytes after each step:

| you do | you get |
|---|---|
| close a chart | tab closes, definition kept, `graph.N.open=false` written (M68.3) |
| reopen the project | settings only. No log, so `store == null`, so **no charts** — an empty canvas |
| then reopen the log | charts rebuild from the profile; open ones return, closed ones stay closed ✅ |
| Restore last session | reopens the **log** and the **topology**, and charts follow from the log load ✅ |

Restore works well *because* it loads a log. The charts are not in the restoration record at all — they
come back as a side effect of the log arriving. `context.restoration.inputs` lists exactly two entries,
`role: log` and `role: topology`.

**Not captured, so never restored:** the filter, flags, the selection and step cursor, the spotlight, the
open/selected tab, and the posture. Everything log-derived that a person set by hand.

## The two gaps

**1. "Restore last session" is named for more than it restores.** A person reading it expects to be put
back where they were. It reopens two files. The state that makes a session feel like *theirs* — the filter
they narrowed to, the rows they flagged, the record they had selected — is exactly what is missing. The
honest options are to narrow the name or widen the capture; the current pairing is the only combination
that misleads.

**2. Reopening a project silently gives an empty canvas.** The verb echo explains the settings/session
split to an agent. A person clicking in the UI is told nothing and simply finds their charts gone. It is
recoverable in one step — open the log — but nothing on screen says so.

## Options

Deliberately separated, because they are independent decisions.

### For the restore name/capture mismatch

- **A1 — narrow the promise.** Rename to something that says what it does ("Reopen last log and topology")
  and let the offer list the two files. Cheapest, honest immediately, zero new persisted state. Loses
  nothing that works today.
- **A2 — widen the capture.** Add filter, flags and selection to the restoration record. Closest to what
  people mean, but it is new persisted state with real questions attached: flags against a log that has
  since grown, a filter naming a dimension the reopened log no longer has, and D-A2's rule that a restored
  fact must say where it came from. Each needs a refusal story, not just a field.
- **A3 — both, staged.** A1 now, A2 as a separate piece of work once the refusal rules are settled.
  **Recommended.** It removes the misleading promise this week without committing to state the app cannot
  yet honestly verify.

### For the empty canvas after a project reload

- **B1 — say it on screen.** When a project is applied and no log is open, the Project panel's log row says
  so and offers the reopen. Reveal-only, so it sits inside D-L3 as amended; no behaviour change.
  **Recommended.**
- **B2 — reopen the previous log automatically.** Convenient, and wrong for the reason the project switch
  closes the log in the first place: a project is a session boundary (M35.5), and silently pulling a log
  from the old session across it is how a person ends up reading one system's log under another's
  settings. Not recommended.
- **B3 — offer it, never take it.** A project reload that finds a log in the restoration record offers it
  the way `projectOffer` already offers settings. Consistent with M35.4/D-AI5 — discovery offers, a person
  declares. Reasonable alternative to B1, slightly more machinery.

## Non-goals

Not proposed: changing the settings/session split itself. Closing the log on a project switch is correct
and load-bearing, and nothing here argues otherwise. The complaint is about disclosure and naming, not
about the boundary.

## Evidence pointers

- `GraphTabs.restore` / `doRestore` — the `store == null` guard, and (M68.3) the skip for closed charts.
- `MainFrame.syncOpenGraphsIntoConfig` — why charts survive a close since M68.3.
- `SessionRecovery` / `context.restoration` — the two captured inputs and `identityScope`.
- `docs/investigations/profile-project-root-resolution.md` — the M68.2/M68.3 work this was found during.
