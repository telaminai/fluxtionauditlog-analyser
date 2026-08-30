# Guided start — from nothing to a tour, driven by your AI assistant

Paste the prompt below into your AI assistant. It installs the analyser, opens it, connects it, and then
walks you through what the tool does **by driving the window while you watch**.

You need **JDK 21+**. You do **not** need a Fluxtion API key, an account, or a project of your own — the
analyser ships with a demo set.

!!! note "Every command is written out on purpose"

    Nothing here says *"run the script at this URL"*. You can read every command before your assistant
    runs it, and so can your security team. The analyser never fetches or executes this page.

## The prompt

```text
Help me get the Fluxtion audit log analyser running, then give me a guided tour.

STEP 1 — install and start it.
Run:
    jbang app install analyser@telaminai/fluxtionauditlog-analyser
    analyser --rest
The second command starts the analyser with its local socket enabled and opens its window.
It BLOCKS, so run it in the background or a second terminal.
If jbang is missing, tell me and stop — installing it is my decision, not yours.

STEP 2 — connect yourself to it.
In the analyser window, I will use  AI ▸ Connect an AI client…  and follow it for my assistant.
Tell me when you are ready for me to do that, wait for me to confirm, then verify by calling
analyser_context and telling me what it reports. If you have no analyser tools after I confirm,
say so plainly rather than pretending — we will fix it before going on.

STEP 3 — give me the tour.
Use the "guided-start" skill if you have it. If you do not, follow these rules:
  - Drive the analyser's UI and tell me what to LOOK AT. Never state a number I cannot see on screen.
  - Call analyser_context before saying "as you can see", to check the view really shows it.
  - Show me three things and then stop: what ran and in what order, what never ran, and one real
    question answered and bookmarked.
  - If something does not work, say so plainly and carry on. I would rather see a real limitation
    than a smooth demo.
Use the demo set at ~/.fluxtion-analyser/demo/ unless I already have my own log open — in which case
ask me before opening anything, because opening a project closes what I have.
```

## What it will show you

**What ran, and in what order.** Position in a record is dispatch order, derived by the compiler before
the program ran rather than reconstructed afterwards from timestamps. That is what lets the record be read
as cause rather than as correlation.

**What never ran.** A list of declared nodes with no execution recorded. It needs the declared graph *and*
the record — neither file produces it alone, and no quantity of log lines will, because a log carries no
list of what was supposed to happen.

**One question, answered and anchored.** A threshold crossing, the record where it happened, and a
bookmark that is still there tomorrow.

## Why the tour works this way

Your assistant is under instructions to **point at the screen rather than tell you the answer**.

That is deliberate. The reason this tool exists is that a record written by execution can be checked
without trusting anyone's account of it — so a tour where the assistant is the source of every claim would
demonstrate the opposite of the product. Everything it tells you, you read off the screen yourself.

If it ever states a figure you cannot see, that is a bug in the tour. Ask it to show you.

## If you would rather not paste a prompt

Install and open it yourself:

```bash
jbang app install analyser@telaminai/fluxtionauditlog-analyser
analyser
```

The Start page has the same demo set behind its own actions, and *AI ▸ Connect an AI client…* does the
connection step whenever you want it. See [Install](install.md) and [Connecting an LLM to the analyser](connect-an-llm.md).
