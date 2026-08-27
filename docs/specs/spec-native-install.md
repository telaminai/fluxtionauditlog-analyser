# Spec — one-command install: a native app with a stable launcher, no JDK to find

**Status:** PROPOSED 2026-08-27 (owner asked for it after the 1.10.0 readiness review). **Milestone:** M41.
**Tracker:** [tracker.md](tracker.md) ▸ M41. **Reviewer:** challenge every decision below; the ones marked
*owner* need the owner's call before the slice that depends on them starts.

## The proposition

Today's install page is honest and it is four hurdles long: find a JDK 21 (or JBang), fetch a jar, put an
**absolute path to that jar** into an MCP client's config, and edit `~/.fluxtion-analyser/config` *while
the app is closed* to turn the transport on. Every one of those is "ordinary shell work" — the docs say so
— and every one is where a support engineer stops. The assessment that prompted this spec put it in one
line: *for support, yes if someone sets it up for them.* The demo set, the start page and the sample
conversations all begin **after** the hurdle; this spec removes the hurdle.

What "one command" means here, precisely:

```bash
brew install telaminai/tap/fluxtion-analyser        # macOS, Linux
winget install Telamin.FluxtionAnalyser              # Windows (later slice — needs a manifest accepted upstream)
jbang app install analyser@telaminai/fluxtionauditlog-analyser   # anyone with a JVM habit — unchanged
```

and after it: `fluxtion-analyser` is a command on the PATH, it carries its own Java runtime, it is the
same thing the Dock/Start menu launches, and `fluxtion-analyser --mcp` is what an MCP client runs — a
**stable, predictable location** instead of "wherever you saved the jar".

What is deliberately **not** in it: an installer that writes into another program's config. The
analyser prints the exact snippet with its own resolved path; the user (or their agent) puts it where it
goes. *Offer, never act* holds at the install step too, and the security story in
`assistant.md` ▸ *Setup is shell work, not an MCP tool* stays exactly as written.

## What exists and stays

| Thing | Today | After M41 |
|---|---|---|
| Shaded fatjar (`fluxtion-auditlog-analyser.jar`) | the only artefact; the Decisions block pins it as the reason for *no embedded browser* | **unchanged**, still published under the stable name; JBang and the plain download keep working |
| `jbang-catalog.json` alias | `jbang app install analyser@…` | unchanged |
| `--mcp`, `--rest`, `--help`, a log path | `Main` | unchanged; the native launcher passes them through |
| `~/.fluxtion-analyser/` (config, `rest-endpoint`, `demo/`, `plugins/`, exchange dir) | per-user state | **never touched by install, upgrade or uninstall** (D-N5) |
| Release workflow (`release.yml`, `workflow_dispatch` + version) | one Ubuntu job: stamp, tag, jar, GitHub release, docs refresh | grows a **matrix job** that builds native bundles and attaches them to the same release (D-N2) |

## D-N1 — native bundles via `jpackage`, one per platform, runtime included

`jpackage` (JDK 21, in the box) turns the fatjar plus a `jlink`'d runtime into a platform bundle:
macOS `.dmg` (arm64 **and** x86_64 — two runners, two bundles), Windows `.msi`, Linux `.deb` and `.rpm`.
The runtime is minimal: `jdeps --print-module-deps --ignore-missing-deps` on the shaded jar, plus
`jdk.crypto.ec` (TLS to S3/LLM endpoints) and `jdk.localedata`; expect 60–90 MB per bundle, ~25 MB
compressed. The jar is an unnamed module, so it goes in via `--input`/`--main-jar`, not `--module`.

Pinned launcher options (they are the same ones `jbang-catalog.json` already carries, and they are how
the launcher stays identical to `java -jar`):

```
--java-options --enable-native-access=ALL-UNNAMED
--app-version ${version}          # MAJOR.MINOR.PATCH — the release workflow's regex already enforces it
--vendor Telamin --copyright "© Telamin" --name "Fluxtion Analyser"
--mac-package-identifier dev.telamin.fluxtion.analyser
```

*Rationale:* it is the only route that removes the JDK prerequisite without changing the program. GraalVM
native-image was considered and **rejected**: Swing under native-image is unsupported territory, the
build would take an hour per platform, and it would fork the runtime from the one every test runs on.
*Rule 1 consequence:* the bundle metadata (vendor, copyright, identifier) is new text the sweep must
see — it lives in the pom/profile, not in a CI secret, so `DemoAssetsTest`'s sweep-line reading covers
it like everything else.

## D-N2 — built in the release workflow, attached to the same release, checksummed

`release.yml` gains a second job after the tag exists: `matrix: [macos-14 (arm64), macos-13 (x86_64),
windows-latest, ubuntu-latest]`, each running `mvn -Ppackage` (D-N3) and uploading its bundle with a
**stable name** (`fluxtion-analyser-macos-arm64.dmg`, `…-macos-x64.dmg`, `…-windows.msi`,
`…-linux.deb`, `…-linux.rpm`) alongside the versioned one, exactly as the jar is today.
`SHA256SUMS.sha256` covers all of them. Release is a rare, manual event, so the macOS/Windows runner
minutes are not a cost worth optimising for; **CI stays Ubuntu-only** and the matrix never runs on push.

**Smoke test per platform, in the matrix, headless** (this is the part CI has never been able to do for
a Swing app): install the bundle on the runner, then `fluxtion-analyser --help` must print usage and
exit 0, and `fluxtion-analyser --mcp` must answer an MCP `initialize` over stdio — the bridge touches
no AWT class by design (spec-assistant-actions-mcp §9), so it is a real end-to-end check of the
launcher, the runtime and the classpath on every platform we ship. `tools/bench/loop-bench.py` already
knows how to speak to the bridge; the smoke reuses it rather than growing a second client.

## D-N3 — one reproducible local build: `mvn -Ppackage`

Everything the matrix does is a Maven profile a developer runs on their own machine and gets the same
bundle from — `jdeps` → `jlink` → `jpackage` via `exec-maven-plugin`, no shell script that only CI
reads. A packaging step that cannot be run locally cannot be debugged locally, and the M36/M37
screenshot incidents were both of that species (a surface nobody could see until it shipped).

## D-N4 — a stable launcher on the PATH, and the analyser prints its own MCP snippet

Homebrew installs the cask **with a `binary` stanza** so `/opt/homebrew/bin/fluxtion-analyser` exists;
the `.deb`/`.rpm` install `/opt/fluxtion-analyser/bin/fluxtion-analyser` and a `/usr/bin` symlink;
the MSI adds its `bin` to the user PATH. Desktop MCP clients still do not inherit a shell PATH, so the
docs keep saying *absolute path* — the difference is that the path is now **predictable per platform**
and, more to the point, the analyser can tell you what it is:

```bash
$ fluxtion-analyser --print-mcp-config claude-code
claude mcp add fluxtion-analyser -- /opt/homebrew/bin/fluxtion-analyser --mcp

$ fluxtion-analyser --print-mcp-config claude-desktop
{ "mcpServers": { "fluxtion-analyser": { "command": "/opt/homebrew/bin/fluxtion-analyser", "args": ["--mcp"] } } }
```

It **prints and exits** — headless, before any AWT class, like `--mcp` and `--help`. It never writes
another program's file. Three targets (`claude-code`, `claude-desktop`, `codex`) mirror the tabs already
on `assistant.md#configure-your-client`, and the printed text is generated from the same source those
tabs are generated from (or the docs page quotes the command's output via the capture harness), so the
two cannot drift — the drift class D-I1 names, avoided the same way.

The path it prints is the running executable's own (`ProcessHandle.current().info().command()`), which is
why the JBang install prints `~/.jbang/bin/analyser` and a plain `java -jar` run prints the `java` binary
with `-jar <jar>` — correct in every case without a platform table.

*Alternative rejected:* an `install` subcommand that edits the client config. It would be the one place
the analyser writes into a file it does not own, and `claude mcp add` already exists for exactly this.

## D-N5 — state is the user's, not the package's

`~/.fluxtion-analyser/` is created by the app on first run and belongs to the user. Install does not
create it, upgrade does not migrate it, uninstall does not remove it — the docs say so in one sentence
and the cask has no `zap` stanza for it. Projects (`*.fluxtion-settings`) are already elsewhere. A
support engineer who reinstalls must find their environments, runbook pointers and destinations where
they left them; the M38 portable-context work is worth nothing if an upgrade forgets it.

## D-N6 — versioning and the in-app notes keep working

`versions:set` already stamps the pom at release; `--app-version` takes the same value. The in-app
*What's new* reads notes bundled in the jar, so it needs nothing. A **`--version`** flag is added
(prints the stamped version, headless) because `brew`/`winget` upgrade logic and a support ticket both
need it and today there is no way to ask a running binary what it is.

## D-N7 — signing and notarisation (*owner*)

Unsigned, a macOS `.dmg` opens to *"cannot be opened because the developer cannot be verified"* and the
`.msi` to a SmartScreen warning. Removing that needs an **Apple Developer ID** (paid, yearly) and a
**code-signing certificate** for Windows, both stored as repository secrets and both a recurring cost
with a person's name on them. Three positions, the owner picks:

1. **Ship unsigned now, sign later.** The cask carries the standard `--no-quarantine` note and the install
   page documents the one right-click; Linux is unaffected. Cheapest, honest, and what most small open
   tools do.
2. **Sign before shipping bundles at all.** Nothing native ships until the certificates exist.
3. **Sign macOS only.** Most of the intended users are on it; Windows gets the MSI with the warning.

The spec proceeds under **1** unless told otherwise; nothing in D-N1–D-N6 changes under 2 or 3, only
the release job gains a signing step.

## D-N8 — Homebrew tap and winget are cross-repo

The tap (`telaminai/homebrew-tap`, cask `fluxtion-analyser` pulling the stable-name `.dmg`/`.deb`,
`sha256` bumped by the release workflow via a `repository_dispatch` or a tap-side workflow that watches
releases) and the winget manifest live outside this repo. They are filed as **UP-DIST-01** (tap) and
**UP-DIST-02** (winget) in [upstream-asks.md](../proposals/upstream-asks.md), each gated on M41.2 having
produced the asset it points at — a cask cannot be written before the file it checksums exists.

## What this does NOT do

- **No embedded browser, no JavaFX** — the Decisions block's reason for the single fatjar is unchanged;
  the bundle adds a runtime around the same jar, it does not change what is in it.
- **No file association.** Audit logs are `.yaml` and the analyser cannot claim a type it shares with
  the world; `.fluxtion-settings` association was considered and dropped — a project is opened from
  inside the app or by `open {project}`, not by double-click, and a second launch path is a second
  thing to keep honest.
- **No auto-update.** The package manager is the updater; the in-app notes say what changed.
- **No headless server mode.** `--mcp` needs the GUI running for *calls* (not for connecting) exactly as
  today; the install spec does not touch the transport.

## Acceptance

1. On a machine with **no Java installed**, `brew install telaminai/tap/fluxtion-analyser` then
   `fluxtion-analyser` opens the app on the start page; `fluxtion-analyser --version` prints the release
   version; `fluxtion-analyser --print-mcp-config claude-code` prints a command that, pasted, registers a
   working bridge (`analyser_context` answers once the app is up).
2. The same four checks pass from the `.msi` on Windows and the `.deb` on Ubuntu, from the same release.
3. The release matrix's headless smoke (`--help`, `--mcp` `initialize`) passes on all four runners before
   any bundle is attached; a failing platform fails the release, it does not ship three of four.
4. `mvn -Ppackage` on a developer's Mac produces a bundle byte-comparable in content (not signature) to
   the released one for that platform.
5. Uninstall and reinstall leaves `~/.fluxtion-analyser/` intact and the last project reopens.
6. The sweep (`CLAUDE.md` rule 1) passes with the packaging profile in the tree, and the bundle's visible
   strings (app name, vendor, copyright, About box) carry no swept term — read them, the sweep cannot
   see inside a `.dmg`.
7. `install.md` is platform-tabbed and every command on it is one the capture harness has run.

## Delivery slices

1. **M41.1 — `mvn -Ppackage` locally** (D-N1, D-N3, D-N6): profile, `jdeps`/`jlink`/`jpackage`,
   `--version` and `--print-mcp-config` (D-N4, both headless), unit tests for the snippet generator and
   the flag parsing. Verified by building the macOS bundle by hand and reading its strings.
2. **M41.2 — release matrix** (D-N2): the four-runner job, stable names, checksums, the headless smoke.
   First exercised on a **pre-release** tag so a broken bundle never sits under `releases/latest`.
3. **M41.3 — tap + docs** (D-N4, D-N5, D-N8): file UP-DIST-01, write the cask, re-tab `install.md` and
   `getting-started.md` ("you need JDK 21+ and nothing else" becomes "you need nothing"), update
   `connect-an-llm.md` and `assistant.md` to lead with `--print-mcp-config`. The playground's Download
   page (M19.3/.4 gate) can point at the bundles from here.
4. **M41.4 — signing** (D-N7, *owner*) and **winget** (UP-DIST-02): when the certificates and the
   manifest acceptance exist; nothing else waits on them.
