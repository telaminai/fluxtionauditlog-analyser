# Local Spring authoring rehearsal

From the analyser checkout, run:

```bash
./tools/start-spring-demo.sh
```

Stop the demo with:

```bash
./tools/stop-spring-demo.sh
```

The launcher prepares `/tmp/fluxtion-spring-demo/project` on its first run. It builds the
compiler/starter worktree, installs distinct local test coordinates, resolves the copied
sample's classpath, generates its processor and runs its seven-checkpoint scenario. It then
builds the analyser checkout, starts a dedicated analyser, the local docs and the playground,
and opens the two websites. Launch logs are under `/tmp/fluxtion-spring-demo/logs`.

The analyser opens the sample's project profile, audit log, generated graph, Spring XML and
validation result. Its own recent-project list includes the sample. It uses an isolated
home; an existing analyser's settings and MCP endpoint are untouched. The project profile
authorises the sample directory for source, design, ownership-record and diagnostic reads.

Open your MCP client **in the sample project**. Its `.mcp.json` points at this exact analyser
jar and isolated home. The launcher probes that configuration with a real MCP context call.
Clients which support project `.mcp.json` may still ask you to enable/trust it; other clients
need their native registration format. The launcher does not edit global client settings.
Read `LOCAL-DEMO.md` in the sample for the runbook, local contract URL and a starting task.

Start is idempotent while services are running. After teardown, starting again preserves
your Java/XML edits, ownership record, evidence and analyser preferences. It does not rerun
generation over an edited project automatically. Use `./generate.sh` and `./run-scenario.sh`
inside the project when you want new results. `./setup.sh` resolves a changed classpath.
Teardown sends termination only to the process groups started by this launcher, checking
their recorded process birth times. It leaves the project, logs and local Maven artifacts.
Browser tabs and client-owned MCP bridge processes are not closed by teardown.

## Local prerequisites and overrides

This is a convenience harness for the existing local acceptance environment, not a portable
release installer. It needs JDK 21, Python 3.9+, Maven, pnpm, cached Maven dependencies and
the playground's installed dependencies. It defaults to these existing worktrees/fixture:

| Option | Default |
|---|---|
| `--compiler` | `/tmp/fluxtion-spring-authoring-compiler` |
| `--web` | `/tmp/fluxtion-spring-authoring-web` (the checkout, containing `web/`) |
| `--sample` | `/tmp/spring-authoring-acceptance/run1/observer/confirmation-2` |
| `--root` | `/tmp/fluxtion-spring-demo` |
| `--docs-port` | `8000` |
| `--web-port` | `5174` |

`--java-home` overrides macOS JDK discovery. `--no-browser` leaves browser tabs unopened.
For a custom root, pass the same `--root` to teardown. Keep that root outside Git. A directory
without the harness marker is refused. A partially prepared project is retained for diagnosis;
move it aside before retrying first-time preparation.

The sample is a **worked example**, with a locally installed generation provider and test
versions of the builder/starter. Its dependency BOM remains the cached acceptance-fixture BOM.
The launcher gives project scripts JDK 21 and a dummy key override; no paid generation key
is used. Private artifacts and generated sources remain local. A fresh browser download
retains its release pin and is not automatically provisioned like this sample.

This rehearsal does not close the publication check or the fresh-session test through an
unmodified real download's `setup.sh`. Serving the playground also does not establish that
somebody witnessed its graph preview. The known analyser trial findings remain open.
