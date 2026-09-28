# Opening a bundle you were sent

Opening a bundle never changes the file you received or your own project. It opens from a disposable working copy.

## Ask your assistant

> **You:** The dev team sent `recorded-run.fexp`. Check it's intact, then check our build of the quote processor
> gives the same audit log on that run.

Your assistant runs the commands below for you and tells you what they said: intact or refused; which build it
replayed into; whether the replayed audit log **agrees** with the bundle's, or where it first **diverges**. Then it
opens the bundle and plays the sender's walk. [Evidence bundles with your assistant](with-an-assistant.md) has that
conversation, and one with a build that diverges, recorded from real runs.

![On the recipient's machine: the bundle opened from its working copy, the sender's walk playing on the breach record](../assets/bundle-conv-received.png)

## One line, then three opens

```
analyser --unpack breach-0900.fexp
```

It verifies every member before it writes anything. On a bundle that does not verify it prints `REFUSED:` and the
member at fault, and extracts nothing. On success it prints:
- the **identity**: compare it with the one the sender gave you, if they gave one;
- the two **limits**: unsigned, and either *no replay* or what a replay can claim;
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

## Does your build give the same run?

When the bundle carries replay records (`--verify` prints `replay: …`), replay them into your own build and compare:

```
jbang tools/replay/ReplayBundle.java --bundle recorded-run.fexp --processor <your processor class> \
      --cp <your build> --out replayed-audit.yaml
analyser --replay-compare recorded-run.fexp replayed-audit.yaml
```

- The runner refuses a build whose graph is not the bundle's, naming the difference, before it runs anything.
- `--replay-compare` prints `AGREES, N of N records`, or `DIVERGES at record k`, naming the first difference.
  Only `endTime` and `thread`, when and where a cycle ran, are allowed to differ.

A build with the same graph and different behaviour replays and diverges. Your assistant can then light the record
where it happened:

![The divergence lit: record 6, whose risk monitor entry (in the detail pane) the changed build never writes](../assets/bundle-conv-divergence.png)

The rule and the messages are in [Commands and file format](reference.md#-replay-compare-comparing-a-replayed-log).

## What to expect

- **Targets are current.** Each step's record, chart and graph targets match what the sender saved, because the
  bytes are the same. For an excerpt, the sender's analyser re-based them onto it.
- **A caveat, once.** The walk says, once, that the file has not been re-checked since it was read. A bundle
  opens with Follow off, so no re-check has run.
- **Give a chart step room.** At the default window size a chart target can report *"no room … — widen the
  window"*. Enlarge the window and show the step again.
- **No source navigation.** The bundle carries none of the sender's source roots.
- **Your recents change.** The recent-files lists gain the working copy's log, graph and project. Nothing else
  about your settings changes.
