# `tools/bench` — the loop's conformance bench (§H of the agent-brokered dev loop)

The build-with-AI loop spans three repos (analyser, Mongoose, playground). A contract that spans repos
rots silently unless something runs it — so it gets a conformance bench, homed here (M19), the way the
record format got one (M34.3).

| script | plays | is |
|---|---|---|
| `loop-bench.py` | the **agent** of §C3 steps 3–7 | the test: glob the registry → pick a server → export log + GraphML → drive the analyser → assert the loop closed. PASS/FAIL per step, non-zero exit on any failure |
| `mongoose-stub.py` | the **server** — reduced to the contract | the fixture: writes a `~/.mongoose/servers/<name>` file (UP-MNG-01 shape, mode 600) and serves the export endpoints from the in-tree demo set. Not a Mongoose; a statement of what one must do |

## Run it today — no Mongoose needed

```bash
mvn package -DskipTests
tools/bench/loop-bench.py --stub --launch
```

`--stub` starts the fake server in a temp registry; `--launch` starts a **fresh, never-configured**
analyser from `target/` with `--rest` into an isolated home — which is also the test of M19.7 (an agent
can start the analyser on a fresh machine and reach its socket, with no first-run dialog in the way).
The same path also launches the packaged `--mcp` bridge against that isolated home and proves modern
discovery, `analyser_context` discovery, and its read-only call back into that exact analyser. Both are
torn down at the end (`--keep` to leave them up and poke at the analyser).

## Run it against a real server — the acceptance test for UP-MNG-01/02

```bash
tools/bench/loop-bench.py --registry ~/.mongoose/servers --server risk-engine   # analyser already running with REST on
```

When the mongoose side publishes registry files and serves the export endpoints, this is how it knows it
is done: the same steps pass that pass against the stub.

## Assumptions the bench makes, flagged

- **Auth header.** When a registry file says `authMode: TOKEN` the bench sends `Authorization: Bearer
  <token>`. That is the bench's guess; the server owns the decision (UP-MNG-01). Correct the bench when
  it is made.
- **Audit-file listing shape.** `GET /api/audit/files` → `{files: [{id, name, sink: {type, location}}]}` is
  UP-MNG-04's proposal, not a shipped endpoint.
- **CI.** Not wired (tracker M19.8): the analyser is a Swing app, so a CI job needs a display (`xvfb-run`
  on Linux), and that has not been verified from a Mac. Run it locally before touching any of the three
  repos' loop code.

Rule 1: every string the stub serves comes from `examples/fixture-generator` (`com.acme.demo`), and the
registry file names nothing real.
