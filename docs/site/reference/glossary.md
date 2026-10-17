# Glossary

Words these docs use with a fixed meaning.

**Analyser verb**
:   One action the analyser offers to an assistant or a script: `open`, `filter`, `graph`, `report` and so on. Every
    way in uses the same verbs ([Verbs](verbs.md)). An MCP client sees each one as a tool named `analyser_<verb>`.

**Approval**
:   The dialog in which you allow or deny one call Claude Code in the analyser wants to make. *Allow once* covers
    exactly that call ([Approvals](../inapp/approvals.md)).

**Audit log**
:   The record a Fluxtion processor writes of each event it handled: one record per cycle, with an entry from each
    node that logged ([Log format](../log-format.md)).

**Audit level**
:   How much a node logs. A node appears in a record only if it wrote audit output at the level in force, so a node
    that logged nothing may still have run.

**Bridge (MCP bridge)**
:   The small headless program (`analyser --mcp`) your MCP client launches. It forwards each tool call to the running
    analyser over its loopback listener ([Connect your client](../connect-an-llm.md)). Claude Code in the analyser has
    its own **private bridge**, with a per-session credential.

**Bundle (evidence bundle)**
:   One `.fexp` file carrying an investigation: the log, the graph, saved charts, reports and walks, and your notes.
    Verified on opening, unsigned ([Evidence bundles](../evidence-bundles/index.md)).

**Canvas**
:   The analyser's shared view, which you and an assistant both work on: what is open, filtered, charted, flagged and
    lit.

**Coverage**
:   Which of the graph's nodes logged in a run, which did not, and which are excluded and why
    ([Reading coverage results](../user-guide/topology.md#reading-coverage-results)).

**Exchange folder**
:   The one folder an assistant may write files into, and read external series from, once *Allow assistant file
    exchange* is on ([Settings reference](../admin/settings.md)).

**External MCP/REST access**
:   The switch that opens the analyser's loopback listener to outside clients and scripts. Off by default
    ([External access](../admin/external-access.md)).

**Finding**
:   The note, and optional fix or assessment, written on a flagged record. A finding is *fault* or *confirmation*.

**Flag**
:   A mark on a record, with an optional finding. Flags belong to the session: opening another log clears them.

**Focus (named focus)**
:   A saved set of topology nodes, with the reason it was saved.

**Full Claude Code**
:   The assistant profile that runs your own Claude Code inside the analyser, with your settings, skills, hooks, MCP
    servers and tools, under your permission rules and the mode you choose ([Claude Code in the analyser](../inapp/claude-code.md)).

**GraphML**
:   The file the Fluxtion compiler writes beside a generated processor, describing its nodes and edges. The analyser
    reads it as the processor's graph.

**In-app assistant**
:   The analyser's own assistant: a chat with the model provider you configure, on your API key
    ([The assistant panel](../inapp/assistant-panel.md)).

**Journey (conversation journey)**
:   A walk with a conversation shown beside it as it plays, labelled as simulated, recorded or edited.

**Off**
:   The assistant profile with no assistant in the analyser. Nothing is sent from it; outside clients are not affected.

**Pairing**
:   Whether a graph fits a log: the analyser checks, and says so, rather than assuming.

**Processor (event processor)**
:   The Fluxtion `EventProcessor` class that produced a log. The active one maps each node in the log to its source.

**Profile (assistant profile)**
:   How the assistant works on this machine: *Safe analysis*, *Full Claude Code*, *In-app assistant* or *Off*, chosen
    with **Assistant: … ▾** ([Assistant profiles](../admin/profiles.md)). Not the same as a project profile.

**Project profile**
:   The file `<project>/.analyser/project.fluxtion-settings`, holding a project's settings and context
    ([Projects, profiles and sharing](../user-guide/projects.md)).

**Prompt pack**
:   A Markdown file of questions that replaces the start page's list for a window ([Prompt packs](../start/prompt-packs.md)).

**Provenance**
:   Which system a log came from, as declared by whoever opened it or matched from a project environment.

**Reel**
:   A walk recorded as one self-contained HTML page, ending on the evidence bundle behind it or on a statement that
    there is none ([Send it as a reel](../teach/spotlights-walks-reels.md#send-it-as-a-reel)).

**Replay records**
:   The recorded inputs of a run, packed into a format 2 bundle so you can feed them to your own build and compare the
    result with `--replay-compare` ([Evidence bundle format](../evidence-bundles/reference.md)).

**Report (investigation report)**
:   A saved, ordered list of references to evidence, with labelled narrative, that re-renders against the log open now
    ([Investigation reports](../user-guide/reports.md)).

**Runbook pointer**
:   A project-relative path to a runbook in the repository. The analyser stores where it is, never what it says, and
    never runs it.

**Safe analysis**
:   The default assistant profile: Claude Code that reads your evidence, with Read, Grep and Glob in the folder and no
    shell, edits or web; it changes what is open or writes a file only after you approve each call.

**Spotlight**
:   A temporary highlight with a caption, lit by you or an assistant to show where to look. Not saved, and not
    evidence ([Spotlights, walks and reels](../teach/spotlights-walks-reels.md)).

**Start page**
:   What the analyser shows when nothing is open: *What would you like to do?*, *Or open directly* and *Recent*
    ([The start page](../user-guide/start-page.md)).

**Walk (spotlight walk)**
:   A saved, named sequence of spotlight steps, played with ◀ ▶ on the analyser itself.

**Work mode**
:   Whether this session is for investigating or building: *AI ▸ Work mode ▸ Investigate / Build*, or a guess from what
    is open ([Runbooks, glossary and posture](../ai-and-runbooks.md#what-this-session-is-for-work-mode-and-the-mode-selector-record)).

**Working copy**
:   The disposable folder an evidence bundle is unpacked into, under `~/.fluxtion-analyser/bundles/`. Claude Code is
    refused there.
