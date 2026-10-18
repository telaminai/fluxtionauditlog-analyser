# Prompt packs

A **prompt pack** is one Markdown file of questions to start from. Drop it on the start page and its entries replace
the analyser's numbered list for this window. Choosing an entry adds its words to the box, as entry 8 (*What can this
tool do?*) does; nothing is sent until you press the button.

Packs are a way to hand a colleague, a class or an audience the questions worth asking first. A pack only holds
questions: it cannot run, open or fetch anything by itself.

## Use a pack

1. Save the pack's `.md` file.
2. Drop it anywhere on the start page.
3. Choose an entry: click it, use **↑ / ↓** and **Enter**, or press **Alt+1** … **Alt+9** (**⌘1** … **⌘9** on macOS).
   Its words are added to the box, after anything you typed.
4. Press the button to send it to the assistant in force.

**← Return to main options** (**Alt+R**; **⌘R** on macOS) brings the analyser's own list back.

A pack is **never saved**: not in your settings, a project profile, a shared settings file or an evidence bundle.
Dropping it is your act, for this window only.

## The sample pack

[Sample prompt pack](../user-guide/sample-prompt-pack.md) is a valid pack: four questions an on-call engineer asks
first about the DEMO log, and one example entry whose address is a placeholder. Save the file and drop it on the start
page to try it: [download it from GitHub](https://raw.githubusercontent.com/telaminai/fluxtionauditlog-analyser/main/docs/site/user-guide/sample-prompt-pack.md).
A test reads that file on every build and fails if it stops being a valid pack.

!!! info "Coming: the Fluxtion philosophy packs"
    Packs that teach how Fluxtion thinks (events, dependencies, dispatch, the audit) are being written. They are not
    published yet; this page will offer them when they are.

## Write your own

```text
# Title                                   ← one line, at most 60 characters
An optional one-line description.         ← at most 140 characters
                                          ← blank lines may separate any two lines
1. **Label** — the prompt's words.        ← 1 to 9 entries, numbered 1, 2, 3 … in order
2. **Another label** — more words.           label at most 60 characters, words at most 500, on one line
```

Write each prompt so it stands alone: it is sent as written, after anything the person typed. Name what the
assistant should look at (*"in this log"*, *"the coverage verdict"*), and ask for a computed answer where one exists
(*"how many"*, *"where does it first"*) rather than a reading of a few records.

### The reader is forgiving about format

It reads, and tidies, the variations Markdown writers use:

- `-`, `--`, `–` or `:` instead of the em dash `—`, and `**Label:** words`;
- an entry with **no bold label**: `1. Label — words` keeps *Label*; otherwise the entry's first words, up to its first
  sentence and at most 60 characters, become the label, and the whole line is the prompt;
- `-`, `*` or `+` **bullets** instead of numbers, read as 1, 2, 3 …, and `1)` for `1.`;
- extra blank lines, trailing spaces, and no description line.

When it changed anything, a quiet note under the pack's title says what, and the pack is used; nothing waits on it.
For example: *"Read with small fixes: a "-" or "\*" bullet read as the next number (lines 3, 4); trailing spaces
removed (line 1)."*

### What is refused

What is unsafe or out of bounds is still refused:

- more than 16 KB, or not UTF-8; more than 9 entries; a title, description, label or prompt over its length;
- HTML, images, links (outside an example entry), `{placeholders}`, and control, format, bidirectional or invisible
  characters — a byte-order mark counts as one, and so do variation selectors and invisible fillers such as U+3164,
  which can carry text no one sees; one U+FE0F directly after an emoji (the emoji form of ⚠, for example) is allowed;
- no title line, no entries, or a number out of order;
- an example entry that is not exactly its strict shape;
- any other line after the entries, or an unclosed `**`.

A refused file names **one cause and its line**, for example *"This prompt pack was not used: line 5 — HTML is not
allowed"*, with **See the format**. The list in force stays.

### Example entries

An entry can point at an example project to download, an archive of one pinned commit:

```text
5. **Label** — example: https://github.com/OWNER/REPO/archive/<40-character commit>.zip
```

Nothing is fetched until the person presses **Download example…** and confirms the publisher, the address, the commit
and the folder. [Starter templates and examples ▸ Example entries](templates-and-examples.md#example-entries) explains
what is checked, and why a branch or tag is refused.
