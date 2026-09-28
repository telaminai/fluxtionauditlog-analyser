---
name: open-evidence-bundle
description: Verify and open an evidence bundle (.fexp) someone sent, in a fresh working copy that leaves your own project untouched, then play its spotlight walk. It shows an investigation; it does not replay the run.
x-analyser-min-version: 1.27.0
---

# Open an evidence bundle

Use this when the person has been **sent** a `.fexp` file and wants to see what it shows. Nothing here changes the
received file or the person's own project. Opening happens in a disposable copy.

The executable version of every step below is `tools/evidence-bundle-demo.py` (`open_bundle()` and `play()`) in the
analyser's repository.

## Steps

1. **Verify and unpack, in one step:**

   ```
   analyser --unpack <bundle>.fexp
   ```

   It verifies every member against the manifest before it extracts anything. It exits non-zero and extracts
   **nothing** if a member is changed, missing, unlisted, duplicated or has a path that escapes the folder. Stop
   there and show the `REFUSED: …` line: that bundle is not the one that was packed. On success it prints:
   - `identity: sha256:…`: compare it with the identity the sender gave you, if they gave one. That comparison is
     the only thing that ties this file to them;
   - `limit: unsigned…` and `limit: no replay…`: relay both, once;
   - `working copy: <dir>`: everything below opens from there. The copy goes under
     `~/.fluxtion-analyser/bundles/` by default, and `--into <dir>` chooses another place.

   To check a bundle without extracting it, `analyser --verify <bundle>.fexp` prints the same identity and verdict.

2. **Open the bundle's project, in a call of its own:**

   ```
   analyser_open {"project": "<working copy>/profile/project.fluxtion-settings"}
   ```

   A project switch is a session boundary: do not combine it with `log` or `graphml`. The person's own project is
   not modified. To return to it afterwards, open it again.

3. **Open the log and the graph together:**

   ```
   analyser_open {"log": "<working copy>/log/<file>", "graphml": "<working copy>/graph/<file>",
                  "provenance": "evidence bundle <bundle file name>"}
   ```

   Leave out `graphml` if the bundle has no `graph/` folder. The log loads in the background: wait until
   `analyser_context` shows `log.records` and no `inFlight`, then read `graphPairing` for the fit.

4. **Play the walk.** `analyser_context.walks.saved` lists what came in the bundle. Then:

   ```
   analyser_walk {"name": "<walk>", "play": true, "step": 1}
   ```

   Step with `{"name": "<walk>", "play": true, "step": <n>}`, or let the person use the strip's ◀ ▶.
   `context.walks.showing` says what each target resolved to:
   - `state: CURRENT` means the target matches what the sender saved, because the bytes are the same;
   - `available: false` has a `reason`. The one to expect is *"no room at … — widen the window"*: a chart
     target needs space, and a fresh install's default window is too small for one. Ask the person to enlarge the
     window, then show the step again.

   The walk's first step states its own view. The sender's live filter and selection are not carried.

## What will not work, and should not

- **Source navigation.** The bundle carries none of the sender's source roots, so Java source views are simply
  absent. That is by design (no machine paths travel), not a fault to fix.
- **Flags.** They do not travel. A finding the sender wanted you to see is a walk step or a report.
- **A chart the sender's capture listed as left out.** Its data was an external file that is not in the bundle.

## Tell the person

What the walk shows, step by step, pointing as you go. **Never say the bundle proves who sent it or that the
incident was reproduced.**
