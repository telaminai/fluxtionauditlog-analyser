# Evidence bundles

An **evidence bundle** is one `.fexp` file that carries an investigation to someone else: the audit log you read,
the graph you read it against, and the charts, [reports](../user-guide/reports.md) and spotlight walks you saved.
The person you send it to opens it in their own analyser, from a fresh copy, and plays your walk. Every record,
chart and graph target is lit as you saved it, because the bytes are the same.

It is built for one job: **showing what you found**. When the run was recorded with a replay writer, a bundle can
also carry the run's **replay records**, so the recipient can check the run against their own build.

**You normally do all of this by asking your assistant.** You don't type the operations yourself: *"package this
for the dev team"*, *"include the replay records"*, *"we've been sent this; does our build give the same run?"*.
[Evidence bundles with your assistant](with-an-assistant.md) has four such conversations, recorded from real runs.

![The walk a bundle carries, playing on step 2: the breach record lit with the sender's caption](../assets/bundle-conv-walk.png)

## Two limits, stated everywhere

Every command and operation that reports on a bundle states them:

- **Unsigned.** Verification shows that nothing in the bundle has changed since it was packed. It does not show
  who packed it. If the sender gives you the bundle's *identity* line by another route, comparing the two is what
  ties the file to them.
- **What a replay can claim.** A bundle **without** replay records shows an investigation and states *no replay*:
  it does not re-run anything. A bundle **with** them states instead that the recorded inputs reproduce the log
  *only* on a build whose graph matches, and *only* as far as the processor reads nothing the records do not carry.
  Nothing in a bundle fixes anything.

## What is inside

| member | what it is |
|---|---|
| `manifest.json` | every member's path, sha256 and size, plus the limits. Its sha256 is the bundle's **identity** |
| `log/<file>` | the whole log, byte for byte, or an **excerpt** of it, stated in the manifest |
| `graph/<file>.graphml` | the graph that was open, if one was |
| `profile/project.fluxtion-settings` | saved charts and named focuses, reports and walks, hidden columns |
| `notes/NOTES.md` | your account, when you gave one |
| `replay/<file>` | the run's replay records, when you asked for them; the manifest is then format 2 |

## What does not travel, and why

| left behind | why |
|---|---|
| **flags** | they are not saved anywhere, even on your machine. Put a finding in a walk step or a report instead |
| **a chart with an external CSV series or markers** | its data is a file on your machine, so its path would point at nothing on theirs. It is left out and named |
| **source roots, Maven repos, processors, runbooks, environments, keys, assistant settings** | these describe your machine. Leaving all of them out means nothing can quietly point somewhere else on the recipient's machine |

So on the recipient's side **source navigation is absent**, by design, while the walk, the report and the charts
all work.

## Where to go next

- [Evidence bundles with your assistant](with-an-assistant.md): the conversations, start here.
- [Sending an investigation](sending.md): one operation on the running analyser, whole log or a time window, with
  or without replay records.
- [Opening a bundle you were sent](opening.md): verify it, open it, play the walk, and check a replay against your
  build.
- [Commands and file format](reference.md): the capture operation, the two commands, and the manifest.
- [Try it with the demo](demo.md): the whole round trip on the DEMO log, in one command.
