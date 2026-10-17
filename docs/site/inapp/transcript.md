# Reading the transcript

The assistant panel draws one conversation the same way, whichever assistant answers: your turns as you typed them,
the assistant's words as safe Markdown, and every action as one compact tool line. Claude Code's answers are marked
*Claude Code*, and the in-app assistant's *Assistant*; the description below names Claude Code, and holds for both.

## How the conversation is drawn

Your turns sit on a band marked *You*; Claude's are marked *Claude Code* with *the
model's words, not a verified result*. Claude's words are shown as **safe Markdown** — headings, lists, quotes,
emphasis, inline code and code blocks (lightly tinted for Java, XML, YAML and JSON) — and everything else as its
characters: HTML is shown as text, an image is *[image: …]* and its address (never fetched), nothing is run. Terminal
escapes and invisible or direction-reversing characters are shown as marks (`␛[31m`, `⟨U+202E⟩`), so what you see is
what you copy. Each answer has **Copy** (its words as written, never a tool line) and each code block **Copy code**;
a copy that holds such characters says how many.

- **Links** open only when you click them and confirm a dialog showing the scheme and host on their own line, the host
  in its plain ASCII (punycode) form — so a look-alike address cannot pass — and only for `http` and `https`.
- **Chips.** A record number (*record #12*), a node written as code or after the word *node*, or a chart's name becomes
  a small button that **only navigates** — it selects the record, shows the node, or opens the chart. An answer written
  about an earlier load of the log (before it was reloaded) shows its chips muted, and a click says why and goes
  nowhere.
- **Tool calls** are one line each — `▸ analyser_read ✓`, `refused`, `denied by you`, `approved by you · ✓` — and a
  click on `▸` (a 24-pixel target) shows the call's input. A screen reader hears the line's own words. **Alt+↑ / Alt+↓** move between turns. Light and dark themes both work.
- **When your full Claude Code is in force**, the composer has a coloured border and the badge *◆ Full Claude Code
  (\<mode\>) — on: it runs at the next Start · your tools and permissions* (then *— running*): the state first, so a
  narrow tab never hides it — with ◆◆ and a stronger colour for `acceptEdits` and `auto`, which ask about fewer actions.
  That colour is full Claude Code's alone: a denied call is a neutral ink with its words, a status note its own colour.
  **Send** reads *Send to full Claude Code*, and the tab and the assistant's own window carry the same ◆ marker. The
  marker follows the session, so it appears and goes with what the analyser actually started.

## Tool lines and their marks

Each action the assistant takes is one line, `▸` and the tool's name, then a mark. Click `▸` to open it: a Claude Code
line shows the call's input as it was sent; an in-app line shows the analyser's result, the same result the model was
given (up to 4,000 characters on screen).

**The in-app assistant's marks:**

| Mark | Means |
|---|---|
| `✓` | the call ran |
| `refused: …` | the analyser refused it, and says why |
| `denied: not run (…)` | it did not run, for example because the turn was stopped first |

**Claude Code's marks:**

| Mark | Means |
|---|---|
| `✓` | the call ran |
| `refused` | the analyser refused it, and the line says why |
| `approved by you · ✓` | you allowed it in the approval dialog, and it ran |
| `allowed: it changes nothing` | a sub-action that changes nothing stored (playing a walk, say), allowed without asking |
| `denied by you` | you denied it in the approval dialog |
| `denied: not answered within 120 s` | the approval dialog timed out |
| `denied: Stop` | you pressed Stop while it waited |
| `denied: another approval was waiting` | a second request arrived while one was open, and its turn ended first |
| `denied: too large to show you in full` | the request could not be shown in full, so it was not asked |
| `denied: not allowed in Safe analysis` | Safe analysis does not offer the tool |
| `denied: not available in this Full Claude Code mode` | the mode in force hides the tool |
| `denied: answer in the conversation instead` | Claude tried its own question tool; answer in the composer |
| `denied: the mode stays as you chose it` | Claude tried to change the permission mode |
| `denied by the analyser's settings` | a setting refused it (file exchange off, say) |
| `withdrawn by Claude Code` | Claude Code withdrew the request before you answered |

[Approvals](approvals.md) explains when a dialog is shown.

A chip by the composer, such as *this log · opened 14:05*, says which log a drafted question is about
([Ask the assistant](ask.md)).
