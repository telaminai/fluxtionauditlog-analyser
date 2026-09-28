# Sending an investigation

## With an AI assistant

Connect an assistant to the analyser ([Connecting an LLM](../connect-an-llm.md)). Give it the
[`capture-evidence-bundle`](https://github.com/telaminai/fluxtionauditlog-analyser/blob/main/docs/evidence-bundle/capture-evidence-bundle/SKILL.md)
skill, and ask it to *"capture this investigation as an evidence bundle"*. It will:

1. pause Follow while it copies, and turn it back on afterwards;
2. **refuse, by name,** when the session cannot be captured coherently:
    - no log is open;
    - a load is still pending;
    - the log changed on disk since it was read, or its identity is not established;
    - the log is not one plain file (a rolled set, a directory or a remote store);
    - a project edit has not yet been written to the profile file;
3. copy the log and graph, have the analyser write the profile member, and pack;
4. check that no other log was opened while it copied. If one was, it deletes the bundle.

It then tells you where the file is, its **identity** line, and anything that was **left out**.

## By hand

```
analyser --bundle-profile <settings> <folder>/profile/project.fluxtion-settings
analyser --pack <folder> <out>.fexp
```

Put the log in `<folder>/log/` and the graph in `<folder>/graph/` first. `<settings>` is the open project's
profile (the Project panel shows it, and `context.project.settings` names it) or, with no project open, `~/.fluxtion-analyser/config`. Save anything
you want to send before you capture: a bundle carries what is **saved**, not what is only on screen.

`--bundle-profile` prints a `left out:` line for each chart with external data. It prints a `dangling:` line for
each walk step or report section that showed one of those charts. Those will say so on the other side.

**No machine path leaves.** A path written inside prose, such as a report narrative saying *"we saw it in
/Users/…/quote.yaml"*, is replaced by `‹path removed›`. It is listed on a `redacted:` line so you see exactly what
was removed, and the recipient sees the marker in the report. A setting whose whole value is a path stops the
export instead, naming it. Relative paths, URLs, times and ratios are left alone.

## Before you send it

- **Size.** The whole log is inside, so a bundle is about the size of its log. A real incident's log can be tens or
  hundreds of MB, too big for most email. Excerpts are planned for a later version.
- **Send the identity separately** (for example, in a chat message) if the recipient needs to know the file is the
  one you packed.
- **Findings as walks.** Flags do not travel. A walk step with a caption on the record says the same thing, and it
  does travel.
