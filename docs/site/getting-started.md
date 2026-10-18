# Open your own log, step by step

You have an audit log from your own processor and want to read it. This page takes you from opening the file to a
log whose lines lead to their source. Only the first step is needed; each later one adds a view.

You need the analyser running ([Install & run](install.md)) and a log your processor wrote
([Producing an audit log](producing-a-log.md)). To look around first with nothing of your own, use the
[guided tour or the sample project](start/tour-and-sample.md).

## 1. Open the log

- **Local file**: **Audit log ▸ Open log…**, drag the file onto the window, or pass its path as the first command-line
  argument (`analyser my-log.yaml`, or `java -jar … my-log.yaml`).
- **A recent one**: **Audit log ▸ Open recent audit log**.
- **From S3**: **Audit log ▸ Open log from S3…** with an `s3://bucket/key` address. It streams the object with your
  local **`aws`** command-line tool and its credentials (profiles, SSO); no AWS SDK is needed. A non-default profile or
  region goes in **Settings ▸ Performance & S3**.
- **Text or binary**: a text log (`---`-separated records) and a Fluxtion binary (`FLXA`) log both open the same way.

Large files open too: a file above the memory threshold (500 MB by default, in **Settings ▸ Performance & S3**) is
memory-mapped instead of loaded into memory, so multi-gigabyte logs open.

You can read, filter, search and chart the records now ([Records, detail & filtering](user-guide/records-and-filtering.md),
[Graphs](user-guide/graphs.md)). The status line and the Project panel say what the log claims about itself, for example
whether it says it is whole ([Is the log whole?](log-format.md#is-the-log-whole)).

## 2. Add the processor's graph

**Sources ▸ Open GraphML…** opens the graph the Fluxtion compiler wrote beside your processor. **Sources ▸ Find GraphML
in source roots…** lists the graphs it finds, ranked by how well each fits the log you have open.

With the graph, the Topology tab shows which nodes logged and which did not, and steps through a cycle node by node
([Topology & step-through](user-guide/topology.md)). The analyser says whether the graph fits the log; it does not
assume it.

## 3. Add the source

1. **Source roots**: **Sources ▸ Source roots…**, then *Add* the source folders for your processor and its node
   classes. A log line or a node then leads to the method that wrote it ([Source navigation](user-guide/source-navigation.md)).
2. **Your event processor**: **Sources ▸ Event processor…**, its fully qualified class name, marked active. The
   analyser uses it to map each `instanceId` in the log to a source file. It is usually inferred when the log opens.

These belong to the project when one is open. To keep them for next time, make a project for this application:
**Project ▸ New project…** ([Projects, profiles and sharing](user-guide/projects.md)), or let Claude Code propose the
set-up ([Set up a project with Claude](build/set-up-with-claude.md)).

## 4. Ask about it (optional)

Select a record and press **Ask** on the toolbar. The question is drafted in the assistant's composer and sent only when
you press Send ([Ask the assistant](inapp/ask.md)). Which assistant answers, if any, is your choice
([Assistant profiles](admin/profiles.md)). No assistant set up? **Copy prompt** gives the question to any assistant you
use. To drive this window from Claude Code, Codex or another client in your terminal, see
[Connect your client](connect-an-llm.md).

If you later **regenerate a Fluxtion processor**, that build needs a different key: the Fluxtion build key, not an
assistant's. **AI ▸ Fluxtion API key…** (also on the start page) writes the usual `~/.fluxtion/fluxtion.apiKeyFile`;
named local profiles let one machine switch between, for example, a work and an evaluation key. The analyser reports
only whether a key is present; it does not check it or show it again. A `-Dfluxtion.apiKey` passed to the build
overrides the file, and the builder does not read `FLUXTION_API_KEY`. Existing generated processors and the bundled
demo run without this key.

## Watch a log as it grows (Follow)

Turn on **Follow** (the toolbar toggle, or **Audit log ▸ Follow (tail)**) to watch a local text log that another
process is still writing. It polls the file, adds each newly completed record and scrolls to the newest, keeping your
flags, filters and selection. Open charts re-extract as records arrive. A chart showing the whole log grows with it;
one at the live edge slides; one zoomed into the middle stays put. While following, the status line keeps what it said
at load (the log's provenance, its record count and its warnings, including a time-order problem in the new records)
and says so on the same line if a read fails.

Follow is not available for an S3 log, a memory-mapped file or a binary log. An exported snapshot does not grow:
export again and reopen it instead.

## Finding the right menu

| Menu | What it changes |
|---|---|
| **Project** | Open, create, save or close a project; run saved analyses; import or export settings. |
| **Sources** | Source roots, event processors and Maven repositories; open a topology, a design or producer diagnostics. |
| **Audit log** | Open a local or S3 log, follow new records, add external CSV series or export records. |

**Records** holds flags and the selected records' actions; **Theme**, **AI** and **Help** are separate. Closing a log
keeps the project and its saved chart definitions. **Project ▸ Close log and topology** closes both and keeps the
project. **Help ▸ Start page** brings the start page back over what you have open.

![Project menu](assets/projects-file-menu.png)
![Sources menu](assets/sources-menu.png)
![Audit log menu](assets/audit-log-menu.png)

Every Settings tab is described in the [Settings reference](admin/settings.md).
