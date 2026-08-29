# `tools/bench` — the loop's conformance bench (§H of the agent-brokered dev loop)

The build-with-AI loop spans three repos (analyser, Mongoose, playground). A contract that spans repos
rots silently unless something runs it — so it gets a conformance bench, homed here (M19), the way the
record format got one (M34.3).

| script | plays | is |
|---|---|---|
| `loop-bench.py` | the **agent** of §C3 steps 3–7 | the test: glob the registry → pick a server → export log + GraphML → drive the analyser → assert the loop closed. PASS/FAIL per step, non-zero exit on any failure |
| `mongoose-stub.py` | the **server** — reduced to the contract | the fixture: writes a `~/.mongoose/servers/<name>` file (UP-MNG-01 shape, mode 600) and serves the export endpoints from the in-tree demo set. Not a Mongoose; a statement of what one must do |
| `bundle-bench.py` | the **static half of M19 P3** | checks a generated directory or zip against `m19-bundle/3`: safe inventory, exact profile ABI, contract/mirror, source/GraphML, skills/frontmatter/provenance/version, executable commands and no placeholders. It does not claim the live run |
| `bundle-client-bench.py` | the **fresh-analyser/MCP half of M19 P3** | opens a producer-run bundle in a disposable analyser home, verifies profile/runbooks/provenance, log/GraphML pairing and coverage, then proves the packaged MCP bridge sees the same state |

## Preflight a generated analyser bundle

```bash
tools/bench/bundle-bench.py /path/to/generated-project-or.zip --analyser-version 1.12.0
python3 -m unittest tools/bench/test_bundle_bench.py
```

The analyser version is explicit so an old default cannot silently approve a skill requiring a newer
analyser. The checker accepts both an unzipped project and the actual download zip without extracting it.
It fails one-based profile members, a singular `eventProcessorFqn`, missing/extra runbook declarations,
unknown contract versions, guide drift, unsafe zip paths, missing generated source/GraphML, undiscoverable
GraphML, unsupported `x-analyser-min-version`, non-executable lifecycle scripts and surviving
`TODO(bundle)`/`/path/to/` markers.

A green preflight alone is not P3 acceptance. After the producer has used a downloadable-equivalent
bundle and published Mongoose plugin version to run → export → stop with no key, drive its result through
the consumer half (under `xvfb-run` on a headless Linux host):

```bash
mvn package -DskipTests
tools/bench/bundle-client-bench.py /path/to/unzipped-bundle \
  --log logs/audit-example.yaml \
  --graphml src/main/resources/com/example/generated/Processor.graphml \
  --expected-records 23
```

The client bench creates a never-configured analyser home and deliberately makes two `open` calls:
project first, then YAML + GraphML. A project switch is a session boundary, so combining all three in
one call would return success while leaving the log unopened. It then exercises the packaged stdio MCP
bridge via modern discovery, `tools/list`, and `analyser_context`; importing application classes is not
accepted as transport evidence.

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
- **CI.** `.github/workflows/ci.yml` packages the analyser and runs this exact stubbed launch under
  `xvfb-run` on Linux. That guards registry discovery, YAML/GraphML export, the fresh isolated analyser,
  REST actions and the packaged MCP bridge together. A real-server run remains the acceptance check for
  Mongoose's side of the contract.

Rule 1: every string the stub serves comes from `examples/fixture-generator` (`com.acme.demo`), and the
registry file names nothing real.
