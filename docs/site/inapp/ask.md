# Ask the assistant

Every "ask" in the analyser comes from **one list of questions**, labelled for the assistant in force — *Ask Claude: …*
in the Claude Code mode, *Ask the assistant: …* in the in-app one:

| Question | Where |
|---|---|
| **What happened here?** | right-click one record; the toolbar's **Ask** and the record detail's **Ask about this** with one record |
| **Compare these rows** | right-click two to twenty records (the same buttons with several); **Ask Claude about this diff** in *Diff selected two records* |
| **Did this node run, and what does the evidence establish?** | right-click a node in the topology, with a record selected |
| **Explain this point** | right-click a chart point or note (*Ask Claude: explain this point*, beside the chart's own *Add explanation…*, which is your words) |
| **Explain this finding** | right-click one flagged record |
| **Summarise this window** | right-click the time slider, or a chart's visible window |

![The records' right-click menu in the in-app mode: Ask the assistant: What happened here? enabled for one record; Compare
these rows and Explain this finding disabled, each until its condition holds](../assets/ask-menu.png)

- **The question goes into the composer, not to the model.** It is **added after** whatever you have written there,
  only the added lines are highlighted, and the line above the box says *Press Send to ask … — nothing has been sent*.
  Nothing is sent until you press **Send**. In the Claude Code mode with no session running, **Start is armed**
  (*Start — ask about this*); nothing starts until you press it.
- **It names where to look, never what is there.** A question names record numbers, the log's file name, node ids,
  a chart's name and a time — never a value, a note's words or a finding's text, because those
  can carry text written to steer an AI. The assistant reads the records itself (`analyser_read`), so in the in-app mode
  the questions need *Settings ▸ Assistant ▸ Let the in-app assistant change the views as it answers*; with them off, the items say so and are disabled. A
  question's draft never carries the raw text of your current selection.
- **A chip by the composer says which log the drafted questions are about** — *this log · opened 14:05*, small and
  muted. The question's own words do not name a load; the analyser tells the model which log it checked when it
  sends the message.
- **If the log changes before you Send** — you reopen or reload it — the chip reads *an earlier log · Send will refuse it — ask again*, and a question that names its records is refused,
  *"This question names records of a log that has been reloaded since it was drafted (load 3 → 4) — ask again from the
  records"*, and stays editable. Deleting the whole question from the box drops that check.
- **During a walk with a conversation** (a journey), an ask does not go to any model: it shows the **next recorded
  turn** by playing the step that reveals it. When there is none, the status line says *"The recorded conversation has
  no further turn — end the walk to ask yourself"*.

The start page asks too: its numbered entries and its box place a question for the assistant in force
([The start page](../user-guide/start-page.md#which-assistant-answers)).
