# Opening a bundle you were sent

Opening a bundle never changes the file you received or your own project. It opens from a disposable working copy.

## One line, then three opens

```
analyser --unpack breach-0900.fexp
```

It verifies every member before it writes anything. On a bundle that does not verify it prints `REFUSED:` and the
member at fault, and extracts nothing. On success it prints:
- the **identity**: compare it with the one the sender gave you, if they gave one;
- the two **limits**: unsigned, and no replay;
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
- **No source navigation.** The bundle carries none of the sender's source roots.
- **Some machine settings change; your project's do not.** Your own project profile is untouched, byte for byte.
  What does change is this machine's record of what was last open: the three recent lists (logs, graphs,
  projects), the last-opened log and graph, and the active project. Measured on a second machine
  (`docs/handoff/evidence/eb-f3-cross-machine/`), not assumed.
