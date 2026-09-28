---
name: capture-evidence-bundle
description: Package the investigation open in the analyser (the log, its graph, the saved charts, reports and walks) into one verifiable .fexp file to send to someone else. It does not replay or reproduce the run.
x-analyser-min-version: 1.27.0
---

# Capture an evidence bundle

Use this when the person wants to **send** what they found: the log they read, the graph they read it against, and
the charts, reports and spotlight walks they saved. The result is one `.fexp` file. The recipient opens it with
`open-evidence-bundle`.

**Say what it is before you make it:**
- **Unsigned.** Verification shows that nothing in the bundle changed. It does not show who sent it.
- **No replay.** It shows an investigation. It does not reproduce the run or fix anything.

The executable version of every step below is `tools/evidence-bundle-demo.py` (`capture()`) in the analyser's
repository. If this page and that script disagree, the script is the one that was run.

## What goes in, and what never does

| in the bundle | never in it |
|---|---|
| the **whole** log file, byte for byte | flags: they persist nowhere, so write the finding as a walk step or report |
| the graph that is open, if one is | a chart with an external CSV series or markers: it is **left out and named** |
| saved charts and named focuses, reports and walks, hidden columns | source roots, Maven repos, processors, runbooks, keys, assistant settings, any path |

The analyser decides the right-hand column (`--bundle-profile`), not you. Do not edit the profile it writes.

## Steps

1. **Pause Follow.** If `analyser_context` shows `log.following: true`, call `analyser_open {"follow": false}`, and
   turn it back on at the end, whatever happened. A file that is still growing cannot be copied coherently.

2. **Refuse, by name, if the session cannot be captured.** Read `analyser_context` and stop with the reason if:
   - there is no `log.path`: *no log is open*;
   - `inFlight` is present: *a load is pending; wait for it, then capture*;
   - `log.identity.state` is `replacement` or `unverified`: *the file is not established to be the one that was
     read; reopen it*;
   - `log.freshness.state` is `changed-on-disk`: *the file changed since it was read; reopen it*;
   - `log.freshness.members` is not exactly one member with `directory: false`: *a rolled set, directory or remote
     store; v1 bundles one plain file*.

   `log.identity` may be **absent**. That means no identity check has run yet, which is not a refusal. Say so
   in the result: the bundle's own sha256 is then the only statement of which bytes were read.

   Then, if a project is open, **wait for `project.unsavedEdits` to be `false`** (poll `analyser_context`; about
   ten seconds is plenty). Project edits reach the profile file after a short debounce, and the bundle copies the
   file. A walk saved a moment before capture is otherwise missing from the bundle: that happened, which is why this
   step exists. If it never clears, a write is failing: stop, and point at the status bar.

3. **Record `log.generation`, then assemble a folder** (a fresh, empty directory of your choosing):

   ```
   <folder>/log/<the log's file name>        copy of log.path
   <folder>/graph/<the graph's file name>    copy of graphPairing.graphPath, when a graph is open
   <folder>/profile/project.fluxtion-settings
   ```

   Write the profile with the analyser, never by hand:

   ```
   analyser --bundle-profile <settings> <folder>/profile/project.fluxtion-settings
   ```

   `<settings>` is `project.settings` from `context` when a project is open. Otherwise it is the person's own
   settings file, `~/.fluxtion-analyser/config`. Relay every `left out:`, `dangling:` and `redacted:` line it
   prints. A left-out chart is not in the bundle, and a walk step or report section that showed it will say so on
   the other side. A `redacted:` line is a machine path found in prose (a narrative, a caption): it reads
   `‹path removed›` in the bundle. A non-zero exit means a path-valued key (a whole value that is a path): stop and
   show the message.

   Then pack:

   ```
   analyser --pack <folder> <out>.fexp
   ```

   It prints `identity: sha256:…`, the bundle's name: the sha256 of its manifest. Give the person that line. The
   recipient's `--verify` prints the same one. A non-zero exit means nothing was written. Show `REFUSED: …`.

4. **Re-read `analyser_context`. If `log.generation` moved, delete `<out>.fexp` and stop:** another log was
   opened while you copied, so the bundle mixes two sessions. It is the rule the analyser applies to a walk save,
   applied to the copy. Delete the folder in every case; the `.fexp` is the only thing to keep.

## Tell the person

- where the `.fexp` is, and its **identity** line;
- what was **left out** and why;
- the two limits above, in your own words, once.

Sending it is theirs to do. The whole log is inside, so a real incident's bundle can be as large as its log (tens to
hundreds of MB). Say so before they try to email one.
