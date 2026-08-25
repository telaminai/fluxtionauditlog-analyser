# Getting started

From zero to a graphed, explained log in a few minutes. If it's your **first run**, the analyser opens
**Settings** automatically so you can point it at your sources up front.

## Quick start (~5 minutes)

1. **Run it** — `jbang analyser@telaminai/fluxtionauditlog-analyser`, or download the jar and
   `java -jar …`. See [Install & run](install.md).
2. **Open a log** — **File ▸ Open log…**, or just drag a log file onto the window. No log yet? Download
   the [sample audit log](assets/sample-audit-log.yaml) and open it, or see
   [Producing an audit log](producing-a-log.md).
3. **Add your source roots** — **File ▸ Settings… ▸ Source roots** → *Add* the source folders for your
   processor and its node classes. This is what lets you click a log line straight to its code, and what
   grounds the assistant's explanations in real source.
4. **Pick your event processor** — **Settings ▸ Event processor** → add its fully-qualified class name
   and mark it active. The analyser uses it to map each `instanceId` in the log to a source file.
5. **(Optional) Add an LLM key** — **Settings ▸ LLM** if you want in-app explanations. No key? Skip it
   and use **Copy prompt** with any agent (see [Assistant](user-guide/assistant.md)).

That's it — now [filter](user-guide/records-and-filtering.md#the-shared-filter),
[graph](user-guide/graphs.md), and [explain](user-guide/assistant.md).

---

Working with an AI? [Connecting an LLM to the analyser](connect-an-llm.md) is the third step — the
agent drives this same window through MCP, and you review what it renders.

## Opening logs

- **Local file** — **File ▸ Open log…**, drag a file onto the window, or pass a path as the first
  command-line argument (`java -jar … my-log.yaml`).
- **Recent** — **File ▸ Open Recent**.
- **Big files** — files above the memory threshold are memory-mapped instead of loaded into heap, so
  multi-GB logs open fine (see *Performance & S3* below).

### From S3

**File ▸ Open from S3…** and give an `s3://bucket/key` URL. It streams the object using your local
**`aws` CLI** credentials (profiles / SSO) — **no AWS SDK required**. Set a non-default profile or region
under **Settings ▸ Performance & S3**.

---

## Settings — everything to get running

Open **File ▸ Settings…**. The dialog is tabbed — Source roots, Maven repos, Event processor, LLM,
Performance & S3, Assistant and History:

### Source roots
The source folders for your processor and node classes. With these set, clicking a nodeLog line opens
the exact class/method, and the assistant explains against real code. A root that can't be found shows
**red**. See [Source navigation](user-guide/source-navigation.md).

### Maven repos
Local Maven repositories (default `~/.m2/repository`) are searched for `*-sources.jar` when a class isn't
under a source root — so third-party node sources resolve too. Tick *Don't search local Maven
repositories* to opt out.

### Event processor
Add the fully-qualified class name(s) of your `EventProcessor`(s) and mark one **active**. Its node-field
declarations are how the analyser turns an `instanceId` into a source file, and how a selected record
scrolls the Source view to the handler that ran.

### LLM (embed your own model)
Configure in-app explanations:

- **Provider** — `anthropic` (Claude) or `openai`.
- **Model** — the model id.
- **API key** — stored locally in cleartext (a single-user desktop tool). It **never leaves your
  machine** and is **never** included in an exported settings file.
- **Base URL (optional)** — point at a proxy or compatible endpoint.

!!! tip "No key is fine"
    Leave the key blank and the assistant runs in **copy-prompt mode** — it builds a ready-to-paste
    prompt (evidence + the analyser's action protocol) for Claude Code, Claude Desktop or any agent.

### Performance & S3
- **Memory threshold (MB)** — logs at/below it load into heap; larger ones are memory-mapped
  (`0` = always memory-map). Applies to the next file opened.
- **AWS profile / region (optional)** — used by *Open from S3*.

### Assistant
Bounds for the two-way action loop: **max action rounds** and **max actions per reply**, and whether the
in-process executor and the optional **localhost REST transport** (off by default) are enabled.

### History
Clear remembered **searches**, **saved graphs** and **recent files**.

!!! note "Themes"
    Switch **Light / Dark / IntelliJ / Darcula** any time from the **Theme** menu — the tables, source
    and charts recolour to match.

---

## Tailing a live log (Follow)

To watch a log as it grows, turn on **Follow** (the toolbar toggle or **File ▸ Follow (tail)**). It polls
the open **local, heap-loaded** file, appends newly-completed records and auto-scrolls to the newest —
preserving your flags, filters and selection. (Follow isn't available for S3 or memory-mapped files.)

---

## Where next

- [Graphs](user-guide/graphs.md) — plot node values and formulas over time
- [Assistant](user-guide/assistant.md) — explain records, and let the assistant drive the analyser
- [Source navigation](user-guide/source-navigation.md) — jump from a log line to the code
- [Sharing setups](user-guide/sharing-setups.md) — export/import your whole configuration
