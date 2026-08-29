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
After installation, run `analyser [optional-log-file]` from your PATH.

!!! note "The JBang name and MCP name are deliberately different"
    JBang installs the executable as **`analyser`** (`~/.jbang/bin/analyser`). MCP clients register it
    under **`fluxtion-analyser`**, then launch that exact executable with `--mcp`. The registration label
    is a client-side name, so it does not need to match the executable name.

!!! note "First run: JBang asks you to trust the download"
    JBang prompts before running a jar from a new source — answer **2** (trust this project's releases)
    or **3** (trust the org) and it won't ask again. To pick up a **newer release** later, run with
    `--fresh` (or `jbang cache clear`) — JBang otherwise keeps using its cached jar.

## Starting it for an agent

```bash
java -jar fluxtion-auditlog-analyser.jar --rest        # or: jbang analyser@telaminai/fluxtionauditlog-analyser --rest
```

`--rest` opens the app with the **localhost REST transport on**, so a process can drive it on a machine
that has never run the analyser. On a first run it prints where the REST endpoint file is, rather than
expecting anyone to read the screen. The setting persists (Settings ▸ Assistant shows it on; turn it off
there) and the console says so. It is what an agent runs on a fresh machine before its MCP client
connects; a human launch without the flag is unchanged — and neither one opens a dialog before you can
use the app.

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

**First run:** the **start page** opens — no configuration dialog appears. It offers a demo set that
ships in the jar, so you can be looking at real records in one click, and it links to *Connect an AI
client* when you want an LLM driving the analyser. Point it at your own source roots and event processor
whenever you like, from **File ▸ Settings** or a project profile — see
[Getting started](getting-started.md). No log yet? Grab the
[sample audit log](assets/sample-audit-log.yaml).
