---
name: mongoose-local
description: Safely inspect and run this local Mongoose validation project when its recorded gates permit it.
---

# Mongoose local validation

This is a project-local candidate skill, not a released shared Mongoose capability. It exists so a
developer can find the agreed local workflow and so the analyser can offer the file as a runbook. It does
not grant authority to change the project, start a server, delete evidence, or configure MCP.

Read `CLAUDE.md`, `docs/specs/spec-mongoose-analyser-validation.md` and `docs/specs/tracker.md` before
acting. In particular, the CSV-to-`PriceUpdate` mapping, named output, audit configuration and the first
bounded question are not proved yet.

## Allowed workflow

1. Inspect `pom.xml`, `run-server.sh`, `config/server-config.yml`, `data/input.txt` and the test sources.
   Report facts and mismatches; do not infer a mapper or audit location.
2. When the user explicitly asks to build or run, use the project commands in `CLAUDE.md`. Run the
   key preflight without printing any secret, then test/package before a server launch.
3. Before a launch, check for an existing local instance on the declared port. Do not create a second one.
4. Stop only the identified instance when the user asks, and preserve any output/audit artifact that
   supports an acceptance record.

## Registry and analyser boundary

Read `~/.mongoose/servers/<name>` when a Mongoose server has published one; otherwise report the YAML
endpoint as project-local fallback. Never write or repair a registry entry — that is an upstream Mongoose
responsibility. Do not start a second analyser, change an analyser profile, or claim an MCP client is
connected merely because a bridge command or registration exists.

The analyser's **Find skills…** action only offers this file. A person must explicitly add it as a
runbook before it appears in their project profile or AI context.
