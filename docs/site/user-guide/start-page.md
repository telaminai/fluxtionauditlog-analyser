# The start page

The start page is what the analyser shows when nothing is open, and what **Help ▸ Start page** raises over an open
workspace. It asks **What would you like to do?** — a short numbered list, a box for your own words and one button —
and keeps the familiar cards below it, under **Or choose directly**.

## What would you like to do?

| # | Entry | The button | What it does |
|---|---|---|---|
| 1 | **Run me through a demo** | *Play* | Opens the sample project and plays the DEMO journey *Find the first recorded breach* — no AI, no key, no network, nothing sent. Its conversation is scripted and labelled *Simulated conversation*. |
| 2 | **Set up a project from my code** | *Set up…* | Opens *New project…*, which looks at your code's folder, offers what it finds, and lets you choose. |
| 3 | **Open an evidence bundle someone sent me** | *Open bundle…* | Your file chooser, then the bundle's verification. A bundle that does not verify is **not opened** and nothing is written: you see why, with *Close*. A verified one opens as a disposable copy, and then a question about it is drafted. |
| 4 | **Which starter template suits what I'm building?** | *Show templates* | Reads the starter catalogue **when you press the button** (never before), then drafts a question naming its templates. *New project from template…* is beside it. |
| 5 | **Explain an audit log I already have** | *Open log…* | With no log open, your file chooser and the load first; then a question about that log is drafted, naming it and its generation. |
| 6 | **What can this tool do?** | — | Puts a question in the box. |

- **Choosing does nothing yet.** Click a row, use **↑ / ↓** and **Enter**, or press **Alt+1** … **Alt+9**. A flow
  entry (1–5) becomes the chip above the box and gives the button its verb; a prompt entry (6) **adds** its words to the
  box after anything you have typed. Typing a digit in the box types a digit. Nothing is sent, opened, fetched or started
  until you press the button.
- **Your own words** in the box are added to the chosen entry's draft after a blank line (entry 4 puts them in the
  sentence). With no entry chosen, the button is **Send** and the box is your question.
- **The button never starts Claude Code.** A draft for Claude Code is placed in its composer and **Start is armed**
  (*Start — ask your question*); you press Start, and the folder confirmation and the posture in force apply. Start is
  armed only when it would be accepted: with the REST transport off, for example, the draft is still placed and the tab
  offers *Turn on the REST transport* instead.
- **With nothing open**, the assistant opens in its own window with the draft in its composer, so you can see where
  your question went. Over an open workspace it returns there and shows the docked tab.
- **The sample project and a bundle's working copy** are folders the analyser owns, so a Claude Code session for them
  works in the analyser's private scratch folder, and the session header says so. Claude reads the evidence through the
  analyser's verbs.

## Which assistant answers

The page asks the assistant you last chose on the Assistant tab (remembered on this machine only). **Claude Code can
answer** when *Allow the analyser to start Claude Code* is on and `claude` is installed (a file check, nothing is run);
**the in-app assistant can answer** with a provider and key (or a local provider). If the one in force cannot and the
other can, the button reads **Ask with Claude Code** (or **Ask the in-app assistant**) and pressing it chooses that one.

With **no AI configured**, every entry still ends somewhere: the demo plays, a bundle or log still opens, and each entry
offers buttons that run what you would otherwise do yourself — *Assistant settings*, *Connect an AI client…*, *New
project…*, *New project from template…*, *Evidence bundles guide*, *User guide* — and **Copy prompt**. The start page's
*Copy prompt* copies **your question only**: no record text, no glossary, never your REST token. If Claude Code is
switched on but not found, the page lists every place it looked and offers *Install Claude Code*.

When your **full Claude Code** is in force (the preview opt-in in *Settings ▸ Assistant*), the box carries the same
marker as the Assistant tab's composer: a coloured border and the badge *◆ Full Claude Code (preview) · \<mode\>: your
tools and permissions* (◆◆ for `acceptEdits` and `auto`, which ask about fewer actions).

## Prompt packs

You can replace the numbered list for this session by **dropping one Markdown file** (`.md`) on the start page: a
*prompt pack*. Its entries behave exactly as entry 6 does — choosing one adds its words to the box, and nothing is sent
until you press the button. **← Return to main options** (Alt+R) brings the analyser's own list back. A pack is never
saved: not in your settings, a project profile, a shared settings file or an evidence bundle — dropping it is your act,
for this window.

A pack is strict, so that what you see is exactly what it says:

```text
# Title                                   ← one line, at most 60 characters
An optional one-line description.         ← at most 140 characters
                                          ← blank lines may separate any two lines
1. **Label** — the prompt's words.        ← 1 to 9 entries, numbered 1, 2, 3 … in order
2. **Another label** — more words.           label at most 60 characters, words at most 500, on one line
```

- At most 16 KB, UTF-8.
- No HTML, no images, no links, no `{placeholders}`, and no control, format or bidirectional characters (a byte-order
  mark counts as one).
- Nothing else: any other line is refused.

A file that breaks the format is **refused with one named cause and its line** (*"This prompt pack was not used: line
5 — an entry needs a bold label"*), with **See the format**, and the list in force stays. The
[sample prompt pack](sample-prompt-pack.md) is a valid pack you can save and drop.

## Or choose directly

The cards — the guided tour, the sample project, *Open project*, *Open evidence bundle*, *Open audit log*, *Open
GraphML*, *Create project profile*, *Create from template*, and the assistant settings — work exactly as before. You can
also drop an `.fexp` bundle, an audit log, a GraphML file or a Spring XML design anywhere on the page.
