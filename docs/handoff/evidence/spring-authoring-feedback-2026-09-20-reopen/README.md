# Staged sample close/reopen — 2026-09-20

Owner report: after closing/reopening the locally provisioned sample, everything appears gone and
initialization is unclear. Reproduced the empty-looking state using the same staged jar, a copied
project and an isolated analyser home. The live staged REST endpoint was absent; the probe did not
restart that session. No project scripts, compiler or server were executed.

`probe.py` records the exact experiment and uses the repo's existing analyser API harness. It opens
the profile, then the launcher's explicit sample log, GraphML, design and validation result. It captures
context/screenshot, closes the project, reopens it, captures again, and finally reopens only the log.
Full private replies are at the manifest's `privateRun`. Both public screenshots were visually inspected.

| State | Before close | After project reopen | After explicit log reopen |
|---|---|---|---|
| Active project | yes | yes | yes |
| Runbooks | 2 | 2 | 2 |
| Saved report definitions in context | 1 | 1 | 1 |
| Loaded log | yes | no | yes |
| Chart names visible in context | 5 | 0 | 5 |
| Opened topology | yes | no | no |
| Loaded design | yes | no | no |

The source/context declarations are equal before and after. The reopen reply reports restoration of
two source roots, one processor, five named graphs, two focuses and one report. The design/producer
views clear. Reopening only the log restores chart tabs/definitions, not proof that those definitions
are meaningful against this particular baseline log; some were authored against later desk runs.

`before.png` shows records, loaded producer results and project/runbook information. `after.png` shows
the generic demo start page despite the active project and its surviving runbooks. This is a real
usability gap: the screen does not explain what survived, what needs input and how to resume.

Cause of the launch/reopen difference: `tools/spring-demo.py` explicitly opens log, graph, design and
diagnostics after opening the profile. Those actions are not part of ordinary project opening.
Project boundaries intentionally clear loaded evidence. The profile persists declarations, not a full
session restore recipe. No persistent-definition loss was reproduced. The live profile hash remained
unchanged, and the isolated instance was stopped.

Limits: calls used the action socket through the real Swing application, not menu clicks. This
reproduces the confusing state but cannot establish every action the owner took. The full startup/
quit/relaunch route was not repeated. Existing focused project/profile/verb/spec-link tests also
passed (46 tests, zero failures/errors/skips).

Required direction is recorded in [the journey spec](../../../specs/spec-project-starter-journey.md):
project-aware landing state, declared context on open, and an explicit freshness-checked session
restore offer. This packet is diagnosis, not implementation or evidence of a completed fix.
