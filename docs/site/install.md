# Install & run

Requires **JDK 21+**. The app is a single self-contained jar (FlatLaf is bundled) — no installer.

## JBang (recommended)

```bash
# run the latest release
jbang analyser@telaminai/fluxtionauditlog-analyser

# open a log directly
jbang analyser@telaminai/fluxtionauditlog-analyser my-log.yaml

# install an `analyser` command on your PATH
jbang app install analyser@telaminai/fluxtionauditlog-analyser
```

The JBang alias points at the latest release's stable-name asset, so it always fetches the newest build.

!!! note "First run: JBang asks you to trust the download"
    JBang prompts before running a jar from a new source — answer **2** (trust this project's releases)
    or **3** (trust the org) and it won't ask again. To pick up a **newer release** later, run with
    `--fresh` (or `jbang cache clear`) — JBang otherwise keeps using its cached jar.

## Starting it for an agent

```bash
java -jar fluxtion-auditlog-analyser.jar --rest        # or: jbang analyser@telaminai/fluxtionauditlog-analyser --rest
```

`--rest` opens the app with the **localhost REST transport on** and, on a machine that has never run the
analyser, **without the first-run Settings dialog** — a process asked for this launch and a process cannot
answer a dialog. The setting persists (Settings ▸ Assistant shows it on; turn it off there) and the
console says so. It is what an agent runs on a fresh machine before its MCP client connects; a human
launch without the flag is unchanged.

## Plain download

```bash
curl -LO https://github.com/telaminai/fluxtionauditlog-analyser/releases/latest/download/fluxtion-auditlog-analyser.jar
java -jar fluxtion-auditlog-analyser.jar [optional-log-file]
```

Each release also publishes `SHA256SUMS.sha256` for verification.

## Build from source

```bash
git clone https://github.com/telaminai/fluxtionauditlog-analyser.git
cd fluxtionauditlog-analyser
mvn package
java -jar target/fluxtion-auditlog-analyser-*.jar [optional-log-file]
```

## Opening a log

- **File ▸ Open log…**, or drag a log file onto the window.
- **File ▸ Open from S3…** for `s3://bucket/key` (uses your local `aws` CLI credentials).
- Pass a path (local or `s3://…`) as the first command-line argument.

**First run:** the **Settings** dialog opens automatically so you can point the analyser at your source
roots and event processor — see [Getting started](getting-started.md). No log yet? Grab the
[sample audit log](assets/sample-audit-log.yaml).
