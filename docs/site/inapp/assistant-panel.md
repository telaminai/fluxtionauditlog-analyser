# The assistant panel

This page covers the panel itself, and the analyser's own in-app assistant. Claude Code in the panel has
[its own page](claude-code.md).

## One panel, whichever assistant answers

The **Analyser assistant** tab is one panel, whichever assistant answers in it. At its top, **one control** names how
the assistant works on this machine — **Assistant: Safe analysis ▾**, *Full Claude Code*, *In-app assistant*, *Off* or
*Not chosen* — and opens the [four cards](../admin/profiles.md#choose-how-the-assistant-works); the card you apply also chooses who answers:
your own installed Claude Code ([Claude Code in the analyser](claude-code.md)) for *Safe analysis* and *Full Claude Code*,
or the analyser's own assistant with a provider you configure. How it is paid for is a chip in the header (your Claude
plan, or an API key). Both share:

- **one conversation**, drawn the same way: your turns as you typed them; the assistant's words as safe Markdown
  (headings, lists, code blocks with light colouring, links that open only after asking you); record numbers, node ids
  and chart names as small buttons that take you there; and every action as one compact tool line — `▸ analyser_read`
  and its mark: `✓`, `refused`, `denied`, `approved by you` — that opens to show what was sent (Claude Code) or what came
  back (the in-app assistant);
- **one composer**, with **Send**, **Stop** and **New chat** doing what the mode in force does with them (Claude Code
  adds **Start**; the in-app assistant adds **Copy prompt**); **Enter sends and Shift+Enter starts a new line**, as in
  the start page's box;
- **one row of header chips** — who answers, the model, the profile or the provider, how it is paid for;
- **one line under the header that says what this mode cannot do** — the in-app assistant cannot read your project
  folder or use your own tools; Safe analysis has no shell, edits or web; full Claude Code's own tools are not confined
  by the analyser; with no key, or with the assistant *Off*, it says so there. It also says that **no profile is
  local-only**: what you ask, and the evidence Claude reads through the analyser, is sent to the model provider
  (Anthropic, on your Claude plan or API key), as in any Claude Code session — and, for the in-app assistant, to the
  model provider you chose.

![The assistant panel in the in-app mode: Assistant: In-app assistant, the provider and API-key chips, the limits line, a
question about record #7, one analyser_read tool line and an answer in safe Markdown with record and node
chips](../assets/assistant-in-app.png)

![The same panel in the Claude Code mode, Safe analysis: the Claude Code chips, the folder, Set up a project with Claude…,
and one analyser_report call marked approved by you](../assets/assistant-claude-code.png)

Each keeps its own rules: the in-app assistant's actions, its per-reply cap and round limit; Claude Code's profile,
permissions and approvals. Changing how the assistant works is refused while an answer is in progress.

## The in-app assistant

With the **In-app assistant** card applied, the panel is a live chat with the provider you configure. Its header says
what it is — **Assistant: In-app assistant ▾**, the provider and model it sends to, and the workspace it is about — and
the line under it says what it is doing: *Requesting*, *Running action n*, *Complete*, *Cancelled*, *Superseded*, *Limit reached* or *Failed*.

- **Ask, follow up, cancel.** **Send** (or Enter; Shift+Enter is a new line) sends your question with the selected
  records' context. The assistant can answer and run the same verbs an external client does — each result appears
  under its request as the analyser's **actual** result, labelled *result*, *REFUSED* or *NOT RUN*, and goes back to
  the model, so an answer can rest on what really happened rather than on the model saying it did. **Stop** ends
  the turn at once: a reply or result that arrives afterwards is not shown and runs nothing. Actions that had already
  completed are not undone, and the note says how many. **New chat** starts again.
- **Budgets.** A turn stops at the per-reply action cap, the round limit, or its own per-turn action budget (Settings ▸
  Assistant), and says which.
- **With no log open**, `context`, `topology` and the source verbs still work; verbs that read records refuse by name.
- **When the workspace changes** — you open another project, log or graph — a turn in progress stops, and even a
  completed conversation is kept for reading. Start a **New chat** to ask about what is open now: its history is not sent as if
  it described the new workspace. The assistant's own `open` is expected, and continues.
- **When the investigation filter changes during a turn**, the pending reply or action stops before it can use a
  different view. After a turn completes, changing the filter keeps the conversation usable; the next **Send**
  prepares context under the current filter. A filter change made by the assistant's own action can continue to
  its next action.
- **Pop out.** **Pop out** moves the assistant into its own window, so the conversation stays beside you while the
  analyser changes views; **Dock** (or closing the window) puts it back. It is the same conversation, draft and request
  in either place, and moving it never sends again. The tab says where it went. Scrolling or copying in the docked
  assistant does not end a showing walk.

Nothing is sent until you press Send: not when you open the tab, open a bundle, or play a journey. Claude Code
starts only when you press its **Start** button.

## No API key? Copy-prompt mode

With no key the assistant sends nothing and says so, offering **Configure provider**, **Copy prompt** and **Connect a
CLI assistant**. No key, or working in a different agent (Claude Code, Claude Desktop)? Hit **Copy prompt**. The copied
prompt is a complete, self-contained brief: the selected records, the node-type map, the relevant
source, **and** the log's file path, shape and per-record **byte offsets**. The dialog that opens names every
part it copied, in the order they appear ("the analyser's standing instructions, the glossary, the raw text of 2
records and your question"), and says so when a very large selection had to be cut.

The analyser's **action protocol** (the loopback REST endpoint, its token and the verbs) is copied only when
you tick **Include my local REST token (lets the pasted assistant drive this analyser)** — off every time the
dialog opens, because the token lets whoever holds it drive this analyser. Tick it and an external agent can
not only reason about the evidence, it can **drive this analyser back** — seek more records, aggregate, and
plot a graph to show you the fault — exactly as the in-app assistant does. Without the token the protocol is
left out entirely: every action call needs it. The box appears only while *External MCP/REST access* is on.
