# Proposed `mongoose-local` shared skill contract

**Status:** Design for review only — do not implement or distribute it until V1–V4 in the paired
validation spec have concrete evidence. **Canonical validation spec:**
[`../specs/spec-mongoose-analyser-validation.md`](../specs/spec-mongoose-analyser-validation.md).

## Intent

Provide one user-local skill that can operate a conforming Fluxtion/Mongoose starter project safely. It
coordinates the project's published commands; it does not contain business logic, own a server, create
AI registration, or replace project documentation.

## Discovery contract

The skill must find, then report before it acts:

- `pom.xml` and the Java/Maven build entry point;
- the project launch script (currently `run-server.sh`);
- the Mongoose YAML deployment descriptor and configured local admin endpoint;
- declared feed fixture, sink/output and audit-capture paths; and
- the supported preflight command (currently `check-fluxtion-key.sh`).

If any required item is absent or ambiguous, the skill stops with a precise diagnosis and suggested
project-side contract, rather than guessing names or searching user directories.

## Supported operations

| Operation | Preconditions | Required result |
|---|---|---|
| `inspect` | Project path | Report detected commands, config, port, inputs/outputs/audit declaration; no writes. |
| `preflight` | `inspect` succeeds | Run the project key/preflight without echoing credentials. |
| `build-test` | Preflight succeeds | Run the project build/test loop and report exact command/result. |
| `start` | Packaged artifact; no duplicate server | Start one local server, report process identity/admin URL/output paths. |
| `status` | Project path | Determine whether the declared local endpoint/process is live without starting it. |
| `stop` | A known server instance | Stop only the instance started/discovered for that project and confirm it releases the endpoint. |
| `collect-audit` | Audit capture is explicitly configured | Return the actual artifact path/format or a clear “not configured/not produced” result. |
| `open-in-analyser` | Existing analyser chosen by human; compatible artifact | Hand off the artifact path; never start a second analyser or claim it is connected. |

## Safety rules

- Never display, persist, copy or pass an API key/token to an AI client config.
- Never start a second local instance on the same declared port; identify the existing instance first.
- Never delete output/audit data automatically. Name cleanup targets explicitly and obtain confirmation
  where it would destroy evidence.
- Never infer a CSV/object mapper, audit destination, client registration or analyser compatibility.
- Treat Mongoose config and domain code as project-owned; the shared skill may read/report them but
  changes them only through an explicitly requested project work item.
- A bridge command or `Check connection` result is not evidence that a client imported/approved the
  registration; client-side confirmation and a bounded MCP tool result are separate operations.

## Project-specific responsibilities

Each generated/hosted project retains versioned ownership of event types, graph nodes, YAML topology,
fixture data, mapping/validation policy, output semantics and tests. It should expose the discovery
markers above in its README/config/scripts. A project script is appropriate only for an operation that
cannot be expressed through the shared skill's stable contract.

## Graduation evidence

Before calling this reusable, exercise it successfully on two starter-shaped local projects and one
intentional failure case (for example, no typed mapper or no audit artifact). Capture the discovered
contract, exact commands/results, server start/stop result and analyser handoff outcome for independent
review. Only a repeated gap after that evidence justifies a custom Mongoose plugin, analyser feature or
MCP tool.
