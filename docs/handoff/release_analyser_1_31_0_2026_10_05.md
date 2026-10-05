# Analyser 1.31.0 release evidence

Status: prepared; publication and download verification pending.

Candidate: `c66f744d5fd7039be70032d79747f9a67df1514a`. Application tree is the reviewed
PR #92 integration at `787f2e1a`; the subsequent change reconciles the tracker only.
This release includes PR #87 redaction, PR #103's ten issue-84 corrections, PR #104's
released-stack adoption, and PR #92's admin-record acceptance. It does not include PR #88.

## Executed checks

Counts below are total / failures / errors / skips.

| Check | Result |
|---|---|
| JDK 21, `mvn -o -q package` | 3179 / 0 / 0 / 248; 419 Surefire reports; no orphans |
| Main CI build, run 36864001774 | 3179 / 0 / 0 / 248 |
| Main CI display, same run | 246 / 0 / 0 / 0 |
| Main CI mutation collector, same run | 628 controls caught exactly once across four shards |
| Docs-only main CI, run 36865693802 | Success; frame, mutation workers and loop bench correctly skipped |
| `python3 tools/verify-m46-agent-api.py` | 16 checks; no failures; 24.38 s |
| `python3 tools/verify-m48-handoff.py` | 18 checks; no failures; 12.53 s |
| `python3 tools/verify-m64-spotlight.py` | 94 checks; no failures; 74.43 s |
| `python3 tools/verify-session-restart.py` | PASS; five checks across three independent JVMs; 15.84 s |
| `python3 tools/capture-conversations.py --require-images` | Seven scenarios completed; five native screenshots captured |
| `mkdocs build --strict` | Exit 0 |
| Whitespace and tracked public-data sweeps | Clean |

The four smoke scripts ran sequentially under the shared display lock. All application instances
used isolated homes and DEMO data. The capture wrapper assigned a unique, neutral scratch root
so it could not disturb another capture session. Every screenshot was read by eye: title bars
present, DEMO content only. The images now show the current Context / Facts / Canvas layout.
The transcript's only change was JSON member ordering, so its original bytes were restored.
No hosted provider, compiler key or model-client session was used. The full mutation gate was
read from CI, not repeated locally.

## Attempts retained

- The first smoke wrapper blocked before launching any check because it combined two lock
  mechanisms. It was stopped and rerun with one shared lock; this was not an application failure.
- The documentation-validation Maven attempt inside the execution sandbox returned
  3179 / 0 / 36 / 248: local socket binds were denied. Its log and XML reports were retained;
  the unrestricted retry returned 3179 / 0 / 0 / 248 across 419 reports. The earlier package run and main CI were also green.

## Remaining work is not claimed complete

Upstream quoting of raw multiline values remains UPS-1a. The start-page DEMO refresh is the
separate UPS-2b owner decision. PR #103's optional improvements, the remaining bundle issues,
M70 whole-feature assurance, and the onboard assistant's outstanding live/native/cross-machine
acceptance retain their tracker status. Issue #102 discloses the unchecked source revision;
it does not establish source-content identity. The original runaway-mouse incident remains
unconfirmed; the released modal-focus cancellation does not claim to explain it.

## Publication

Pending: release workflow, tag, public artefact hashes and manifests, stable download route,
release-notes page and docs deployment.
