# The start page

The start page is what the analyser shows when nothing is open, and what **Help ▸ Start page** raises over an open
workspace. It is one surface: it asks **What would you like to do?** — a short numbered list, a box for your own words
and one button — and the analyser's own actions follow it as one compact strip, **Or open directly**, with your recent
projects and evidence bundles under **Recent**.

## What would you like to do?

| # | Entry | The button | What it does |
|---|---|---|---|
| 1 | **Run me through a demo** | *Play* | Opens the sample project and plays the DEMO journey *Find the first recorded breach* — no AI, no key, no network, nothing sent. Its conversation is scripted and labelled *Simulated conversation*. |
| 2 | **Set up a project from my code** | *Set up…* | Toward **Claude Code**: your folder chooser, then a set-up question is placed and Start armed — Claude looks at the folder and **proposes** the set-up, you answer each item, and the analyser applies the ones you accept ([onboarding](assistant.md#set-up-a-project-with-claude)). Toward the in-app assistant (which cannot read a folder): *New project…*, which offers what it finds and lets you choose. |
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
  armed only when it would be accepted: in an evidence bundle's working copy, for example, the draft is still placed and
  the tab says why Start is refused.
- **With nothing open**, the assistant opens in its own window with the draft in its composer, so you can see where
  your question went. Over an open workspace it returns there and shows the docked tab.
- **The sample project and a bundle's working copy** are folders the analyser owns, so a Claude Code session for them
  works in the analyser's private scratch folder, and the session header says so. Claude reads the evidence through the
  analyser's verbs.

## Which assistant answers

The page asks the assistant you last chose on the Assistant tab (remembered on this machine only). **Claude Code can
answer** when *Allow the analyser to start Claude Code* is on and `claude` is installed (a file check, nothing is run);
**the in-app assistant can answer** with a provider and key. If the one in force cannot and the other can, the button
reads **Ask with Claude Code** (or **Ask the in-app assistant**) and pressing it chooses that one.

**The first question with nothing set up** — no assistant chosen on this machine and none able to answer — opens the
[four cards](assistant.md#choose-how-the-assistant-works) (*Safe analysis*, *Full Claude Code*, *In-app assistant*,
*Off*) in the assistant's own window instead of an answer, and the start page says *Choose how the assistant works
first — then ask again*. Your words stay in the box. This happens once: after a card is chosen, or if you close the
cards without choosing, the page never opens them by itself again. The flows (the demo, set-up, a bundle, the
templates, a log) never open them: they work with no AI.

With **no AI configured**, every entry still ends somewhere: the demo plays, a bundle or log still opens, and each entry
offers buttons that run what you would otherwise do yourself — *Assistant settings*, *Connect an AI client…*, *New
project…*, *New project from template…*, *Evidence bundles guide*, *User guide* — and **Copy prompt**. The start page's
*Copy prompt* copies **your question only**: no record text, no glossary, never your REST token. If Claude Code is
switched on but not found, the page lists every place it looked and offers *Install Claude Code*.

When your **full Claude Code** is in force (the *Full Claude Code* card, or its switch in *Settings ▸ Assistant*), the
box carries the same marker as the assistant's composer: a coloured border and the badge *◆ Full Claude Code · \<mode\>: your
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

### Example entries

An entry can instead point at an **example project to download** — an archive of one pinned commit:

```text
5. **Label** — example: https://github.com/OWNER/REPO/archive/<40-character commit>.zip
5. **Label** — example: https://github.com/OWNER/REPO/archive/<commit>.zip sha256:<64 hex characters>
```

- It shows with a download badge (**⤓**). **Nothing is fetched** when the pack is dropped or the entry chosen; only the
  entry's own **Download example…** button fetches, after a confirmation that **leads with the publisher** — *host /
  owner / repo @ short commit* — marked **third-party** unless the address is in the Telamin GitHub organisation. An
  address is all that is proven: GitHub serves a commit from any fork of a repository under the parent's address, so a
  Telamin address is shown as *a Telamin repository address (the code may come from a fork)*. Then come the exact
  address, the full commit, the integrity digest (or that there is none) and the folder you choose (a new or empty one).
- Only `https` from **github.com**, **codeload.github.com** and **gitlab.com**, and only an archive of a **40-character
  commit**: a branch or tag can change under you, so `…/archive/main.zip` or `…/archive/v1.0.zip` is refused. No git,
  clone, hooks or submodules are used.
- With `sha256:`, the archive's digest is checked **before anything is unpacked**, and a mismatch is refused, by name.
  GitHub does not guarantee that the bytes of its generated `/archive/<commit>.zip` stay the same — only the files
  inside them — so a digest on such an address may stop matching one day; the analyser then refuses rather than
  accepting it silently. A digest is most useful on an archive whose bytes are published once and kept (a release
  asset); this build accepts only commit-pinned addresses, so the commit is what pins a GitHub archive.
- The archive is unpacked through the same contained installer as the starter templates (no paths outside the folder,
  no links, size caps), with **no file made executable**. **Nothing is built, run or installed.** You are then offered to
  open the folder as a project (*New project…*'s offer, which you confirm). Downloading never trusts the folder for
  Claude Code: that is Start's own question, asked there.
- There is no *validated* or *approved* mark: an example is the publisher's, and the analyser says only whose it is.

## Or open directly

Under the question, every action the analyser offers without typing is one small button — an icon and a short label,
grouped by what it is for. Hover one for what it does (the sentence the old card carried); each is reachable with
**Tab** and pressed with **Space** or **Enter**, and each does exactly what its card did.

| Group | Buttons |
|---|---|
| Explore DEMO | **Guided tour** (four spotlight stops in the local DEMO project) · **Sample project** (the same DEMO log, topology and source, at your own pace) |
| Open | **Project** · **Evidence bundle** (verified, then a disposable copy; no replay) · **Audit log** · **GraphML** |
| Create | **Project profile** (settings for existing source; no code generated) · **From template** |
| Connect an assistant | **Codex** · **Claude** · **Other MCP client** — the local bridge set-up for each |
| Settings | **Settings** (source and assistant settings: the open project's, or the global defaults) · **Fluxtion key** (present or not; needed only to regenerate a processor) |

You can also drop an `.fexp` bundle, an audit log, a GraphML file, a Spring XML design or a prompt pack (`.md`)
anywhere on the page.

## Recent

**Recent projects** and **Recent evidence bundles** sit side by side (one under the other in a narrow window). A row
names the workspace or the bundle and its path; its **…** button removes it from the list, reveals it in your file
manager or renames it. A bundle's row also carries its sender's note — their words, not a verified fact.
