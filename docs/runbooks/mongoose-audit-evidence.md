# Mongoose audit evidence — canonical starter runbook text

Status: TA-5a documentation source; TA-5b must vendor this into the generated hosting/analyser runbook.
It is not evidence of a released starter update. The matching section is in the canonical
`run-mongoose-server` skill; their parity is tested.

## Audit evidence

The preserved session observed these limits with **mongoose-plugins 1.0.43**. Do not assume they apply
to a later version without checking that version's result.

- Export captured records with `/api/audit/file/{id}/export?format=yaml`. Resolve the server URL,
  authentication and actual file id from the project's registry/API or its shipped export script;
  never guess them. The export is a snapshot, not a live stream.
- **D12:** `/ws/audit-tail/{processor}` accepted the session's connection but delivered no records.
  Later endpoint evidence on the same release found a tailer created on the connect thread and
  read on the executor: each tick throws `ThreadingIllegalStateException`, swallowed at DEBUG.
  A connected socket is not proof of audit delivery. Workaround: use the snapshot export.
- **D13:** the original session reported `/api/audit/files` counts/times staying at startup values.
  A later endpoint test did **not reproduce** this: its listing count matched its exported records.
  Treat this as an unresolved observation, not an established endpoint defect. Export and inspect
  the records themselves, stating the export time and provenance.
- Keep each export as a new snapshot file and explicitly reopen it to inspect a later snapshot.
  Do not overwrite a file currently being followed, append a whole cumulative export, or manufacture
  a final separator. Ordinary snapshot opens include the EOF record without a closing separator,
  as Format 1 permits; completeness remains unknown. In an explicit live Follow read that tail
  is **pending** until a full separator arrives; a quiet interval never proves it complete.
- The later owner decision is **text audit files first**, owned by the Mongoose host/plugin
  maintainers; the existing analyser opens/follows those files when that delivery ships.
  The M31 Chronicle reader (UP-RDR-01) is a separate future adapter, not the first-release prerequisite.
  Route delivery and starter integration are **TA-5b, still open**. Until they ship, use explicit
  snapshot export/reopen; do not prescribe a polling follower as if it were a shipped project command.

The agent runs the project's export and application commands. The analyser reads evidence files;
it does not discover server logs, deploy, start or stop the application. No new analyser verb is needed.

## Route decision — 2026-09-21

Superseded by the owner's text-first decision in `docs/proposals/mongoose-audit-format/README.md`
(revision 7, incorporated from main). Mongoose host/plugin maintainers own file delivery; playground
owns the corresponding project instructions. TA-5b stays open until that route and vendored guidance
ship together. TA-5c is the later post-shipment client spot-check. No client session or fresh endpoint
experiment is performed by this documentation response.

Evidence: `docs/handoff/evidence/unguided-session-2026-09-21/session-report.md`, §3.2 and §5.1–5.4.
These are preserved observations, not an independent rerun of the plugin endpoints.

Later D12/D13 evidence: `docs/proposals/mongoose-audit-format/README.md`, live endpoint review.
