# Evidence bundles

An **evidence bundle** is one `.fexp` file that carries an investigation to someone else: the audit log you read,
the graph you read it against, and the charts, [reports](../user-guide/reports.md) and spotlight walks you saved.
The person you send it to opens it in their own analyser, from a fresh copy, and plays your walk. Every record,
chart and graph target is lit as you saved it, because the bytes are the same.

It is built for one job: **showing what you found**.

## Two limits, stated everywhere

Every command and operation that reports on a bundle states them:

- **Unsigned.** Verification shows that nothing in the bundle has changed since it was packed. It does not show
  who packed it. If the sender gives you the bundle's *identity* line by another route, comparing the two is what
  ties the file to them.
- **No replay.** A bundle shows an investigation. It does not re-run the processor, reproduce the incident or fix
  anything.

## What is inside

| member | what it is |
|---|---|
| `manifest.json` | every member's path, sha256 and size, plus the limits. Its sha256 is the bundle's **identity** |
| `log/<file>` | the whole log, byte for byte, or an **excerpt** of it, stated in the manifest |
| `graph/<file>.graphml` | the graph that was open, if one was |
| `profile/project.fluxtion-settings` | saved charts and named focuses, reports and walks, hidden columns |
| `notes/NOTES.md` | your account, when you gave one |

## What does not travel, and why

| left behind | why |
|---|---|
| **flags** | they are not saved anywhere, even on your machine. Put a finding in a walk step or a report instead |
| **a chart with an external CSV series or markers** | its data is a file on your machine, so its path would point at nothing on theirs. It is left out and named |
| **source roots, Maven repos, processors, runbooks, environments, keys, assistant settings** | these describe your machine. Leaving all of them out means nothing can quietly point somewhere else on the recipient's machine |

So on the recipient's side **source navigation is absent**, by design, while the walk, the report and the charts
all work.

## Where to go next

- [Sending an investigation](sending.md): one operation on the running analyser, whole log or a time window.
- [Opening a bundle you were sent](opening.md): verify it, open it and play the walk.
- [Commands and file format](reference.md): the capture operation, the two commands, and the manifest.
- [Try it with the demo](demo.md): the whole round trip on the DEMO log, in one command.
