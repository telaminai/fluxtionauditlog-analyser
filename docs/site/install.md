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

# pick up a NEWER release — JBang otherwise keeps running the jar it cached on first use
jbang --fresh analyser@telaminai/fluxtionauditlog-analyser

# ...or clear the cache; this also refreshes an installed `analyser` command on its next run
jbang cache clear
```

The JBang alias points at the latest release's stable-name asset. JBang downloads that jar **once** and then runs
its cached copy, so after a new release you still get the old version until you run with `--fresh` or clear the
cache — *Help ▸ About* shows which version is running.
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

`--rest` opens the app with **External MCP/REST access on**, so a process can drive it on a machine
that has never run the analyser. The setting persists (*Settings ▸ Assistant ▸ External MCP/REST access*
shows it on; turn it off there), and when the flag has just turned it on the console says so. It is what an
agent runs on a fresh machine before its MCP client connects; a human launch without the flag is
unchanged — and neither one opens a dialog before you can use the app.

What the console prints: the URL and only the token's **first 8 characters**
(`[analyser] assistant REST: http://127.0.0.1:… (token 1a2b3c4d…)`), plus, on a first run with no
configuration yet, where the endpoint file is. The full token is never printed. **Automation reads the
token from `~/.fluxtion-analyser/rest-endpoint`** — mode 600, `{url, token, pid, startedAt}`, rewritten with
a fresh token on every launch — and checks that `pid` is a live process before trusting it (a crashed
analyser can leave a stale file). An MCP client needs none of this: its bridge reads the file itself. See
[Scripts and CI over REST](diagnose/rest-automation.md).

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

- **Audit log ▸ Open log…**, or drag a log file onto the window.
- **Audit log ▸ Open log from S3…** for `s3://bucket/key` (uses your local `aws` CLI credentials).
- Pass a path (local or `s3://…`) as the first command-line argument.

**First run:** the **start page** opens — no configuration dialog appears. It offers a demo set that
ships in the jar, so you can be looking at real records in one click, and its *Connect an assistant* buttons set up
the local bridge when you want an LLM driving the analyser. Point it at your own source roots and event processor
whenever you like, from **Project ▸ Settings** or a project profile — see
[Getting started](getting-started.md). No log yet? Grab the
[sample audit log](assets/sample-audit-log.yaml).
