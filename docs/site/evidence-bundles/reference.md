# Commands and file format

The bundle format belongs to the analyser, in four headless commands. None of them opens a window. An installed
analyser is `analyser …`; from a jar it is `java -jar fluxtion-auditlog-analyser-<version>.jar …`.

## Commands

| command | what it does | exit code |
|---|---|---|
| `--bundle-profile <settings> <out>` | writes the part of a project profile (or of your own settings) a bundle may carry: saved charts and named focuses, reports and walks, hidden columns. Prints `left out:` for each chart with external data, `dangling:` for each walk step or report section that showed one, and `redacted:` for each machine path found in prose, which reads `‹path removed›` in the bundle | 0 written · 1 refused (the settings cannot be read, the output exists, or a setting's whole value is a machine path, which is named) · 2 usage |
| `--pack <folder> <out.fexp>` | writes the manifest from every regular file in the folder, then the files, into one zip. Prints `identity:` and the limits. Never overwrites | 0 · 1 refused (a link, an escaping path, a `manifest.json` already in the folder, nothing to pack) · 2 usage |
| `--verify <bundle.fexp>` | checks every member against the manifest without extracting anything. Prints the identity, then `verified: N members…` and the limits | 0 · 1 refused, naming the member · 2 usage |
| `--unpack <bundle.fexp> [--into <dir>]` | verifies, then extracts into a **new** directory named for the identity. Prints `working copy:` | 0 · 1 refused, nothing extracted · 2 usage |

Verification refuses, naming the member:

- a **changed** member (sha256 or size);
- a **missing** member;
- an **unlisted** member;
- a **duplicated** entry;
- a path that **escapes**: absolute, `..`, a backslash, an empty segment;
- a manifest that is missing, not the first entry, duplicated, larger than 4 MiB, unreadable, or of another format;
- a member **larger than its declared size**, refused as soon as it exceeds it.

Verification streams each member through a fixed buffer, so it needs the same small amount of memory for a 4 KB
log as for a 150 MB one. A member the manifest does not list is refused without being read. `--unpack` verifies the
whole bundle before it writes anything, then extracts in a second pass, checking every member again as it writes.

## The manifest (format 1)

```json
{"format":1,"createdAt":"2026-09-28T12:00:00Z","analyser":"1.27.0",
 "log":{"member":"log/demo-quote-audit.yaml"},
 "graph":{"member":"graph/demo-quote-processor.graphml"},
 "members":[{"path":"graph/demo-quote-processor.graphml","sha256":"…","bytes":12653},
            {"path":"log/demo-quote-audit.yaml","sha256":"…","bytes":4053},
            {"path":"profile/project.fluxtion-settings","sha256":"…","bytes":…}],
 "limits":["unsigned: verification detects a changed member; it does not authenticate the sender",
           "no replay: this bundle shows an investigation; it does not reproduce or fix it"]}
```

- The keys come in this fixed order, and the members are sorted by path, so a given folder packed at a given time
  always gives the same bytes.
- **The identity is `sha256:` of the manifest's exact bytes.** It is never stored inside the manifest. A manifest
  re-serialised with the same content is a different bundle.
- The walk and report fingerprints in the profile member already record the log's provenance and record count, so
  the manifest does not repeat them unverified.

## Context fields a capture uses

An agent capturing a bundle reads two fields that exist for this purpose:

- `context.log.generation`: the session's log generation. If it moves while a capture copies, another log was
  opened and the copy is incoherent.
- `context.project.unsavedEdits`: `true` while a project edit waits for its write to the profile file. A capture
  waits for it to clear.
