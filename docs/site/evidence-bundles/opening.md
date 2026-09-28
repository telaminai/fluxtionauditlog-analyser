# Opening a bundle you were sent

Opening a bundle never changes the file you received or your own project. It opens from a disposable working copy.

## With an AI assistant

Give your assistant the
[`open-evidence-bundle`](https://github.com/telaminai/fluxtionauditlog-analyser/blob/main/docs/evidence-bundle/open-evidence-bundle/SKILL.md)
skill and ask it to *"open the evidence bundle I was sent"*. It verifies and unpacks the bundle, opens it and plays
its walk, pointing at each step as it explains it.

## By hand

1. **Verify and unpack:**

    ```
    analyser --unpack breach-0900.fexp
    ```

    Nothing is extracted from a bundle that does not verify. You see `REFUSED:` and the member at fault. On
    success it prints the identity, the two limits and `working copy: <dir>`, by default under
    `~/.fluxtion-analyser/bundles/`. To check without extracting, use `analyser --verify`.

2. **Open the working copy's project:** *Project ▸ Open project…*, then choose
    `<working copy>/profile/project.fluxtion-settings`.
3. **Open its log and graph:** *Audit log ▸ Open log…* on `<working copy>/log/…`, then *Sources ▸ Open GraphML…* on
    `<working copy>/graph/…`.
4. **Play the walk** from the **Reports** tab, and step with ◀ ▶.

To go back to your own work, open your own project again.

## What to expect

- **Targets are current.** Each step's record, chart and graph targets match what the sender saved, because the
  bytes are the same.
- **A caveat, once.** The walk says, once, that the file has not been re-checked since it was read. A bundle
  opens with Follow off, so no re-check has run.
- **Give a chart step room.** At the default window size a chart target can report *"no room … — widen the
  window"*. Enlarge the window and show the step again.
- **No source navigation.** The bundle carries none of the sender's source roots.
- **Your recents change.** The recent-files lists gain the working copy's log, graph and project. Nothing else
  about your settings changes.
