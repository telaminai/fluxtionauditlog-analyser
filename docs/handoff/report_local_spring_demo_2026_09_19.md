# Local Spring rehearsal launcher — 2026-09-19

Owner requested one start script and one teardown script, with a prepared sample, analyser
project and recent-project entry, project MCP configuration, and local playground/docs.

## Delivered

`tools/start-spring-demo.sh` and `tools/stop-spring-demo.sh` delegate to `tools/spring-demo.py`.
See [local instructions](../admin/local-spring-demo.md). The scripts prepare the existing local
acceptance sample using distinct branch builder/starter coordinates, a local provider and the
cached fixture BOM. Generated sources, private artifacts and machine-specific `.mcp.json`
remain outside the checkout. Subsequent starts retain the project instead of regenerating it.

The analyser is built from this checkout and copied into the demo directory. Its isolated home
holds the recent-project list, export permission and REST endpoint. The profile authorises the
sample project, including its ownership record, for M66 freshness checks. Startup opens the
project, 12-record audit log, compiled graph, design XML and validation result. The project
`.mcp.json` uses the same jar and home. The launcher verifies that exact command with MCP.

Teardown checks recorded PID birth times and process-group ownership before stopping services;
project files, evidence, local test artifacts and client-owned bridge processes are retained.

## Verified

- Initial preparation: compiler/starter package, local artifact installation, project setup,
  validation and generation passed. Receipt: local route, successful build, compilerRan true.
- Real scenario: seven state/sink checkpoints and exactly Checked(10,1), Checked(40,1).
- Docs and playground HTTP endpoints return 200. A real MCP initialize/context exchange using
  the generated configuration succeeds. Context contains the expected active project, log,
  graph and design. The recent-project entry persists in the isolated config.
- Repeated start reuses running services. Teardown then restart succeeds; 19 source, record,
  MCP and evidence files stay byte-identical. Restart does not regenerate user source.
- Three automated checks: stale PID identity does not kill an unrelated process; stopping an
  owned service retains project edits; an already prepared project bypasses regeneration.
- `mvn -q test` under JDK 21: 1,688 tests, no failures/errors, 31 display skips. The first
  sandbox run could not bind transport-test sockets; the permitted rerun passed.
- Python compile, shell syntax, MkDocs strict, diff whitespace and rule-1 checks pass.

An initial launcher attempt incorrectly treated a shebang process's changed command string
as a dead process. Identity now uses stable process birth time, and ignores zombies. The
leftover docs server from that attempt was stopped explicitly; the corrected lifecycle passed.

## Limits / review scope

This is a machine-local convenience harness, dependent on the documented worktrees and
existing acceptance sample; it is not a portable release installer. It does not install
playground dependencies automatically. A fresh browser download is not rewritten to the local
test coordinate. Reviewers should attack partial-start cleanup, process ownership, retained
edits, client configuration and the first-time preparation assumptions.

Serving the browser page is not a witnessed graph-preview check. The original analyser DX
findings remain open, including the framework-sink graph mismatch visible on this sample.
Publication and the real-download fresh-session gate remain open. The reply reviews supplied
by the owner, their attribution correction and G15–G17 are recorded as intake in the analyser
tracker; this task has not merged either compiler review branch or fixed those findings.
