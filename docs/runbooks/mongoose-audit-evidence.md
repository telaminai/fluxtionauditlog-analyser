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
  A connected socket is not proof of audit delivery. Workaround: use the snapshot export.
- **D13:** `/api/audit/files` counts and times stayed at their startup values while the queue grew.
  Use that listing for file discovery, not freshness. Export and inspect the records themselves;
  state the export time and provenance. Do not call a startup count the current total.
- Keep each export as a new snapshot file and explicitly reopen it to inspect a later snapshot.
  Do not overwrite a file currently being followed, append a whole cumulative export, or manufacture
  a final separator. A trailing unterminated record is **pending**, not proven complete by a quiet
  interval. The session's separator-rewriting workaround is not a supported completeness rule.
- The supported architectural route for live follow remains the **M31 Chronicle live-store reader**,
  with `supportsFollow`, owned by the **Chronicle reader/plugin maintainers (UP-RDR-01)**. Its delivery
  and starter integration are **TA-5b, still open**. This documentation does not assert that the reader
  is installed or shipped in this project. Until it is, use explicit snapshot export/reopen; do not
  write or prescribe a new polling follower as if it were a shipped project command.

The agent runs the project's export and application commands. The analyser reads evidence files;
it does not discover server logs, deploy, start or stop the application. No new analyser verb is needed.

## Route decision — 2026-09-21

Retain the live-store route already accepted in `spec-agent-brokered-dev-loop.md`, rather than add a
second polling route. Owner: Chronicle reader/plugin maintainers, UP-RDR-01; playground owns bundling
and the project-specific entry instructions. TA-5b stays open until the reader and vendored guidance
ship together. TA-5c is the one later client spot-check, after that shipment. No client session is
permitted or reported by this documentation change.

Evidence: `docs/handoff/evidence/unguided-session-2026-09-21/session-report.md`, §3.2 and §5.1–5.4.
These are preserved observations, not an independent rerun of the plugin endpoints.
