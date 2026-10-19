# Release notes — draft, 2026-10-18

For the developers who use the analyser day to day, and for anyone who scripts it. The source of truth is
`CHANGELOG.md ▸ [Unreleased]`; every line below is a shortened form of an entry there. Where the two differ, the
CHANGELOG is right.

## What you will notice first

1. **Turning on external MCP/REST access now asks first.** From every door — *Set up AI*, the AI menu's checkbox,
   *Settings ▸ Assistant* (on save) and *Connect an AI client* — one confirmation, with *Cancel* the default.
   Turning it off is still one click, and `--rest` still turns it on at start-up without asking. A shared settings
   file can no longer turn it on: it is set on each machine only. **If your team turned access on through a shared
   settings file, tick it once on each machine.**
2. **The profile cards are gone. The assistant's header has a quick switch instead.** The header control is one
   short chip — **Safe ▾**, **◆ Full ▾**, **In-app ▾**, **Off ▾**, or **Set up AI…** while nothing is set up. It
   opens a menu: **Safe analysis · ◆ Full Claude Code… · In-app assistant · Off · Set up AI…**. A row applies at
   once. ◆ Full Claude Code asks for its confirmation first, and a row that cannot work as it stands opens the
   *Set up AI* screen that finishes it.
3. **Set up AI…** is the one place to choose how AI works with the analyser: **No AI**, **your own AI client**,
   **Claude inside the analyser** (Safe analysis or Full Claude Code) or **both**. It checks that Claude Code is
   installed and signed in, without sending anything. For your own client it walks through turning on access,
   registering the analyser and a live check that your client really reached this window. It also draws a small
   *Where data goes* picture for the option you pick. *AI ▸ Connect an AI client…* and the start page's *Connect an
   assistant* block are replaced by it. The old dialog is still reachable under *More options…*.
4. **The assistant panel is quieter.** The docked panel shows the mode chip, **✎** (New chat), **⋯**, the
   conversation and the composer. The model, billing, folder, limits and operator lines are now in
   **⋯ ▸ About this session…**. The state is one or two words on the chip (*answering…*, *needs you*). A refusal or
   an exit is one banner with **Restart**, plus **Details** when something needs attention. Every answer is headed
   *Claude · unverified*.
5. **Hidden filters are shown on screen and in `context.hidden`.** When a filter hides records, the Records title
   says *3 of 21 shown · 18 hidden*. A line under it names each filter with its own undo, and the *Event types*
   toggle shows a badge. An empty table says why. A search set by an assistant shows in the Search box. The
   assistant is told too: `context` carries a new **`hidden`** field,
   `{count, byFilter: [{filter, hides, excludedTypes?}]}`.

## For scripts and integrations: additive only

- **The verbs and the REST endpoint file (`~/.fluxtion-analyser/rest-endpoint`) are unchanged.** Nothing was
  removed or renamed, and no existing reply changed shape.
- **`context.hidden`**: new, additive (above).
- **`context.starters`** (in the `project` section): new, additive. It lists the starter templates: id, name, description, how the
  graph is authored, where it runs, and whether its build needs a Fluxtion key.
- **`source {…, text: {from, to}}` or `{…, text: {around?, context?}}`**: an optional parameter that returns source
  lines as data. It is read-only, limited to authorised source roots, and capped at 200 lines and 32 KB a call,
  with any cut stated. Without `text`, `source` and its reply are unchanged.
- **`graph {restore}`**: an optional parameter. `{restore: true}` lists the chart bin, and `{restore: "name or id"}`
  restores one. Deleted and replaced chart definitions are now recoverable.
- **The `fluxtion-analyser` MCP bridge sends extra request headers**: `X-Analyser-Client` (the client's own name,
  ASCII, shown as its claim) and `X-Analyser-Probe` on Set up AI's check calls, so a check is never counted as your
  client. They are informational. A client or proxy that forwards headers needs no change.
- **Two refusal messages changed their words. Their status codes did not change**: the hosted-only listener's 403,
  and the bridge's -32001 *not running* error. Key on the code, never on the text.
- **The analyser no longer prints the full REST token to its console.** Read it from the endpoint file, and check
  its `pid`. *Copy prompt* includes the token only when you tick *Include my local REST token*.
- **A request nested more than 512 levels deep** is refused with 400 *malformed JSON*. Before, the connection closed
  with no reply.

## New

- **The starter path: build a new application with Claude.** *Build a new application with me* on the start page
  reads the starter catalogue and drafts your question. Claude Code can propose a starter (`propose {kind: starter}`);
  only your **Apply** downloads it, into a new or empty folder you choose. The analyser then does the following:
  - writes the project's skills, each checked against a pinned digest;
  - asks one review, with every row at No except the Spring design's folder;
  - opens the project and its Spring design;
  - offers the first build's graph when it appears;
  - offers to restart Claude Code in the new project with a draft placed. Nothing is sent until you press Send.

  *Project ▸ New project from template…* runs the same steps. Switching projects part-way cancels the remaining
  steps, and the downloaded files stay.
- **Claude Code in the Assistant tab**, off by default. **Safe analysis** reads your evidence and asks before every
  call that changes what is open or writes a file. **Full Claude Code** is a separate opt-in that runs your own
  Claude Code, with its settings, tools and permission rules. Approvals are asked in the analyser, with *Deny* the
  default and no *Always allow*. Settings changes that can wait are offered as a restart.
- **The start page asks *What would you like to do?*** There is a numbered list, a box for your own words and one
  button, and the demo needs no AI, key or network. Prompt packs (one Markdown file dropped on the page) replace the
  list for the session.
- **One list of questions for the assistant** on the records, charts, topology and time slider: *What happened
  here?*, *Explain this point* and others. They are added to the composer and sent only when you press Send.
- **Each turn shows its time**, and **↑** recalls your previous questions.
- **Recording a Claude Code conversation into a walk**, and example entries in prompt packs pinned to a commit.
- **The docs site is organised by task**, and redirects keep the old addresses working.

## Fixed (selection)

- A Java source spotlight asked right after `goto` or `topology {select, source: true}` no longer answers
  *superseded*. It also lights beside the graph on the Topology tab.
- The sample project finds its own event processor and reaches its sources.
- An approval no longer times out unseen: its window is brought to the front.
- A proposal waiting for review is always visible, even with the assistant behind another tab.
- Full Claude Code's report permission ends with the project, the destination or your own edit of the report.
- A very large `source {text}` window returns the capped lines with a notice instead of an empty success.
- The Event types checklist and the Records table always keep at least three rows each.

## Security

- **Source roots** can never expose your home folder or a credential folder, for every caller.
- **No assistant profile is local-only**, Safe analysis included. What you ask, and the evidence Claude reads, goes
  to the model provider.
- **Text the analyser did not write is shown as its characters, never as HTML**, and nothing is fetched.
- **Invisible "smuggled" characters** are refused in prompt packs and dropped from drafted questions.

See `CHANGELOG.md ▸ [Unreleased]` for the full list and the exact wording.
