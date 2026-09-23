---
name: run-mongoose-server
description: Start and stop this project's local Mongoose server, export its captured run as analyser-readable YAML, and open it with the processor graph.
x-analyser-min-version: 1.12.0
---

# Run the local Mongoose server

## Start here — no key is needed to run

The generated processor **ships with the project**, so it builds and runs with no Fluxtion API key. Do
not run a key preflight before starting: an earlier version of this skill did, the check exits non-zero
when no key exists, and a model following it stopped before the project ever ran — turning the intended
first success into a first failure. A review caught it before it shipped.

The key is needed **only to REGENERATE** the processor after you change the graph. That procedure is at
the bottom of this file, and the check belongs immediately before it.

## Steps

1. Start the server using **this project's own script**, not an invented command:

   ```
   ./scripts/run-server.sh              # the starter's entry point
   ```

   TODO(bundle): if this project names it differently, correct this line when the bundle is generated.

2. Find this running server through the registry entry it publishes under
   `~/.mongoose/servers/`. Use the bundle's declared server name; if it is not running, do not guess a
   port or start a second copy. The registry entry supplies the base URL, auth mode, token, environment
   and process id without putting any of them in this skill.

3. Export the captured Chronicle audit through **this project's own export script**:

   ```
   TODO(bundle): substitute the exact export command and its concrete ./logs/audit-<name>.yaml target.
   ```

   Mongoose does not write analyser-readable YAML directly. The script selects the recorded audit file
   through the registry/API and calls `/api/audit/file/{id}/export?format=yaml`; claiming the run itself
   writes the YAML skips a required step and leaves `load-audit-log` with a path that does not exist.

   **The export is cumulative.** Chronicle capture persists across restarts — the capture ROLLS to a new
   file on a schedule (daily, in the starter) and nothing deletes the old ones — so the YAML holds every run
   still on disk, not only the last: a "new run" export after two redeploys carried three runs. Declare
   provenance and filter by time. To read one run alone, prefer a fresh capture location for that run; if you
   delete old capture files instead, stop the writer first and treat it as a deliberate decision to discard
   evidence. Do not describe the file as one run unless you checked.

4. Open that concrete YAML export in the analyser with the processor's GraphML (see the
   `load-audit-log` skill), declaring the registry server name as provenance.

5. Stop the server through this project's own stop command, so Chronicle capture closes cleanly and the
   registry file is removed.

   TODO(bundle): substitute the exact clean-stop command; do not assume a stop script exists.

   **Confirm the stop was clean — do not assume it.** The stop script has been seen to report *stopped
   server pid …, but its registry entry is STILL at …*: the process ended but the shutdown path left the
   entry behind. Check that the entry under `~/.mongoose/servers/` is gone and the pid is dead. A later
   start overwrites a stale entry, so this is recoverable — but report what you saw, not "stopped cleanly".

6. **Change the input only between a stop and a start.** The starter's configured file source tails its
   input file and delivers an appended line immediately — to the processor that is running NOW. (Other
   event sources have their own delivery rules; this advice is about the file feed the starter ships with.) A line appended before a
   redeploy reached the OLD processor in one session, whose mapper turned it into a zero-priced event
   that the risk check then passed. Stop, edit, rebuild, start.

## Audit evidence

The preserved session observed these limits with **mongoose-plugins 1.0.43**. Do not assume they apply
to a later version without checking that version's result. **Two were fixed in 1.0.44**, each marked
below. Which applies to you is a fact about the project you are in: read `mongoose-plugins.version` in
its `pom.xml` rather than assuming either version.

- Export captured records with `/api/audit/file/{id}/export?format=yaml`. Resolve the server URL,
  authentication and actual file id from the project's registry/API or its shipped export script;
  never guess them. The export is a snapshot, not a live stream.
- **D12 — fixed in 1.0.44.** On **1.0.43** `/ws/audit-tail/{processor}` accepted the session's
  connection and delivered no records: a tailer created on the connect thread and read on the executor
  made every tick throw `ThreadingIllegalStateException`, swallowed at DEBUG, and a batch that failed to
  meet the flush threshold was discarded with the tailer already past those records. On **1.0.44** a
  running server delivered every record written after a client connected, checked against the export of
  the same window. **On either version, a connected socket is still not proof of delivery** — that is
  what made the defect invisible for so long, so count what arrives rather than trusting the connection.
  On 1.0.43, use the snapshot export instead.
- **D13:** the original session reported `/api/audit/files` counts/times staying at startup values.
  A later endpoint test did **not reproduce** this: its listing count matched its exported records.
  Treat this as an unresolved observation, not an established endpoint defect. Export and inspect
  the records themselves, stating the export time and provenance.
- Keep each export as a new snapshot file and explicitly reopen it to inspect a later snapshot.
  Do not overwrite a file currently being followed, append a whole cumulative export, or manufacture
  a final separator — least of all one that looks like a stream-end marker, which would be a
  completeness claim you invented.
- **The export's last document — 1.0.44 terminates it; 1.0.43 does not.** On **1.0.43** an export ends
  with its last record and no closing `---`; an ordinary open reads that record as an ordinary record,
  as Format 1 permits, and in an explicit live Follow read it is **pending** until a full separator
  arrives. On **1.0.44** the export writes `---` after the last document as well as between them, so
  nothing is left open. **Completeness is still unknown on both**: nothing writes a stream-end marker
  yet, so no export claims to be whole, and a quiet interval never proves it complete either way.
- The later owner decision is **text audit files first**, owned by the Mongoose host/plugin
  maintainers; the existing analyser opens/follows those files when that delivery ships.
  The M31 Chronicle reader (UP-RDR-01) is a separate future adapter, not the first-release prerequisite.
  Route delivery and starter integration are **TA-5b, still open**. Until they ship, use explicit
  snapshot export/reopen; do not prescribe a polling follower as if it were a shipped project command.

The agent runs the project's export and application commands. The analyser reads evidence files;
it does not discover server logs, deploy, start or stop the application. No new analyser verb is needed.

## Do not start a second instance

If a server is already running for this project, find it before starting another — the published
registry entry is the authority. The same applies to the analyser: if its
status bar reads **MCP elsewhere**, another analyser window owns the endpoint and your questions are
being answered about a different log.

## Regenerating after a graph change — this is where the key is needed

Only run this when you have changed the graph (added or rewired a node). Running the project does not
need it.

1. **Do not rely on the preflight script alone** (review F6). It reads `FLUXTION_API_KEY`, which the
   builder never reads — so it can report success on a value the build will not receive. Check what the
   build actually resolves:

   ```
   # the file the builder reads when no system property is set
   grep -s '^apiKey=' ~/.fluxtion/fluxtion.apiKeyFile >/dev/null && echo "key file present" \
                                                              || echo "NO key file — the build will fail"
   ```

   If the project ships `check-fluxtion-key.sh`, treat a pass as necessary but not sufficient until that
   script is fixed to check a source the builder reads.

2. **Know which source the BUILD actually reads**, because it is not what a shell script reads. Verified
   against `fluxtion-builder` 1.0.64 (`FluxtionConfigManager`):

   ```
   -Dfluxtion.apiKey=…                  system property   [WINS if set]
   ~/.fluxtion/fluxtion.apiKeyFile      apiKey=…          [used when the property is absent]
   ```

   **`FLUXTION_API_KEY` is NOT read by the build.** `FluxtionConfigManager` makes no `System.getenv`
   call at all. A preflight script may read that variable and pass, while the build it precedes never
   receives the value — so if you set only the environment variable, the check succeeds and the build
   still fails. Set the file, or pass the property explicitly.

3. Rebuild. If the build stops at `process-classes`, the key is the reason.
