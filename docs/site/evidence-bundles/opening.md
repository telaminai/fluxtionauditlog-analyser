# Opening a bundle you were sent

Opening a bundle does not edit the file you received or your own project files. It switches the active
workspace to a disposable working copy and adds that copy to your recent files.

In the Swing analyser, choose **Start page → Open evidence bundle** and select the `.fexp` file. The analyser
verifies and unpacks it off the event thread, then opens its project, audit log and any GraphML. It shows
the verified identity, working-copy path and the bundle's limits. Verification detects changed members;
it does not authenticate the sender. A replay, when present, is not run by this action.

The bundle label belongs to the operation that verified and applied the profile, including a comparison of the
profile content actually loaded. A failed application cannot lend that label to a later ordinary project open,
even at the same pathname. Opening a profile manually after `--unpack` is an ordinary project open, not a claim
that the current workspace was applied by the bundle operation.

When borrowing **GRAPHS**, incoming notes and series replace the live chart definitions before the profile is
saved. Source roots remembered for a bundle remain remembered when their directories are temporarily offline:
an unrelated reports import does not remove them. Removing a visible source root deliberately still removes
that remembered anchor.

## One line, then three opens

```
analyser --unpack breach-0900.fexp
```

It verifies every member before it writes anything. On a bundle that does not verify it prints `REFUSED:` and the
member at fault, and extracts nothing. On success it prints:
- the **identity**: compare it with the one the sender gave you, if they gave one;
- the **limits**: unsigned, plus the replay and source-data limits that apply to this bundle;
- `excerpt: the log is records … of …, not the whole log`, when it is an excerpt;
- `working copy: <dir>`, by default under `~/.fluxtion-analyser/bundles/`. `--into <dir>` chooses another place.

Then open the working copy, on the action socket or from the menus:

```
open {project: "<working copy>/profile/project.fluxtion-settings"}      Project ▸ Open project…
open {log: "<working copy>/log/<file>", graphml: "<working copy>/graph/<file>"}   Audit log ▸ Open log…
walk {name: "<walk>", play: true}                                        the Reports tab ▸ Play
```

Open the project on its own first: a project switch is a session boundary. The sender's notes, if any, are in
`<working copy>/notes/NOTES.md`. To go back to your own work, open your own project again. To check a bundle
without extracting it, run `analyser --verify <bundle>.fexp`.

## What to expect

- **Targets are current.** Each step's record, chart and graph targets match what the sender saved, because the
  bytes are the same. For an excerpt, the sender's analyser re-based them onto it.
- **A caveat, once.** The walk says, once, that the file has not been re-checked since it was read. A bundle
  opens with Follow off, so no re-check has run.
- **Give a chart step room.** At the default window size a chart target can report *"no room … — widen the
  window"*. Enlarge the window and show the step again.
- **Source needs your roots.** The bundle carries none of the sender's source roots. Add a local source anchor to
  navigate Java. A saved Java caption uses name/line lookup: the source revision has **not** been compared with
  the version the caption described. The overlay and `context.walks` disclose this independently of source/run pairing.
- **Your recents change.** The recent-files lists gain the working copy's log, graph and project. Nothing else
  about your settings changes.

## Working-copy cleanup

Each open extracts a fresh copy. Opening another bundle, or choosing **Private settings → Clear unused
copies**, can remove copies that no analyser is using. A pending open also holds its copy. The original
`.fexp` files are never removed.

Cleanup requires a same-host ownership marker and an available exclusive file lock. Older unmarked
copies, copies from another host, and copies whose ownership cannot be checked stay in place. Closing
or switching the project releases its copy once its log and graph are no longer in use; deletion happens
on a later bundle open or an explicit cleanup. An abrupt process exit releases its locks too. This is
coordination between cooperating analysers, not protection against another program replacing local files.
Copies extracted with `--into` outside the standard working-copy directory are not cleaned automatically.

Borrowing with `import {bundle}` reads off the UI thread. Applying the selected categories checks
cancellation and the project lifetime again on the UI thread. If you switched projects during the read,
even away and back, the borrow is refused without applying it; retry in the intended project.
