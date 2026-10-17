# Running a demo

This page is for showing the analyser to an audience: a team meeting, a customer call, a class. It needs no log of
your own and no AI: the analyser ships with a DEMO run, its graph and its source. An assistant adds a live part if
you have one set up.

The order below builds from looking, to asking, to keeping, to sending. Each step stands alone, so stop wherever
the audience has seen enough. The times are suggestions.

## Before you start

- **Install and start** the analyser ([Install & run](../install.md)). It opens on the start page.
- **Make the window large.** At least 1440 × 900 gives chart steps room for their callouts. At a fresh install's
  default size (1200 × 800) a chart step may say *"no room — widen the window"*; widen it rather than skipping the
  step.
- **Mind what is on screen.** The DEMO data is made up, so nothing on screen is anyone's real system.
  If you open your own log later, remember that it may name real systems.
- **Decide on an assistant.** None is needed. To answer live questions, set one up beforehand
  ([Assistant profiles](../admin/profiles.md)); the first question with nothing set up opens the profile choices
  instead of an answer.

## 1. The tour (3 to 5 minutes)

On the start page, choose **1. Run me through a demo** and press **Play**. The analyser opens the sample project and
plays the DEMO journey *Find the first recorded breach*: four steps over a 726-record log, with a scripted
conversation beside them. Step with ◀ ▶. The conversation is labelled *Simulated conversation*, and nothing is sent
anywhere while it plays.

For a shorter opening, press **Guided tour** under *Explore DEMO*: four stops (the records, the topology and its
coverage verdict, a record's detail, and the Reports tab). Both are described in
[Guided tour and sample project](../start/tour-and-sample.md).

Points worth making while it plays:

- Each row is a cycle the processor recorded itself, not a reconstruction.
- The coverage verdict separates *logged* from *did not log*, and does not claim that a silent node did not run.
- Every number the conversation states can be found on screen.

## 2. A prompt pack (5 minutes)

Drop a prompt pack on the start page to replace its list with the questions you want to ask. The
[sample prompt pack](../user-guide/sample-prompt-pack.md) has four on-call questions about the DEMO log. Choose one,
press the button, and let the assistant answer on the canvas: it filters, charts and flags, and you click into each
thing it shows ([Prompt packs](../start/prompt-packs.md)).

With no assistant, choosing an entry still puts the question in the box. **Copy prompt** gives it to any assistant you
use, or answer it yourself from the views.

!!! info "Coming: the Fluxtion philosophy packs"
    Packs that teach how Fluxtion thinks (events, dependencies, dispatch, the audit) are being written, for teaching
    sessions in particular. They are not published yet.

## 3. A walk (5 minutes)

Turn the explanation you just gave into a **spotlight walk**, so it can be given again without you. While something
is lit, right-click the spotlight and choose *Save as new walk…*; add steps with *Add to walk*. Or ask the assistant
to save its explanation as a walk. Play it from **Reports ▸ Spotlight walks**
([Spotlights, walks and reels](spotlights-walks-reels.md#save-it-as-a-walk)).

A walk can carry a conversation too: one you write, or turns captured from the assistant, labelled as what they are
([Walks with a conversation](spotlights-walks-reels.md#walks-with-a-conversation)).

## 4. A reel (2 minutes)

For people without the analyser, record the walk as a **reel**: **Reports ▸ Spotlight walks ▸ More ▸ Record reel…**.
The analyser plays the walk, photographs each settled step, and writes one HTML page that opens anywhere: a title page,
a page per step, and a finish page. The finish page says how to check the pictures: it names the evidence bundle the
session came from, or says plainly that there is none
([Send it as a reel](spotlights-walks-reels.md#send-it-as-a-reel)).

An assistant can record one too, with `walk {name, reel}`. That writes into the exchange folder, so it needs
*Allow assistant file exchange* on.

## 5. An evidence bundle (3 minutes)

Close the loop: the reel shows the evidence, and the bundle lets the audience check it. An **evidence bundle** carries the log, the graph,
the saved charts, reports and walks in one `.fexp` file. The recipient verifies it and plays your walk on the same
bytes in their own analyser ([Evidence bundles](../evidence-bundles/index.md)).

To show the receiving side, download the DEMO journey's bundle from
[Journey: find the first recorded breach](../journeys/find-the-first-recorded-breach.md), check it with
`analyser --verify`, and drop it on the start page. To show both sides with two analysers in one command, run the
[round-trip demo](../evidence-bundles/index.md#try-it-with-the-demo) from the source repository.

## After the demo

- **Your own log**: [Open your own log, step by step](../getting-started.md).
- **Building with Claude**: [Build with Claude](../build/index.md), or **3. Teach me by building an example** on the
  start page, which opens the sample project and drafts a lesson for your assistant.
- **Diagnosing incidents**: [Diagnose an incident](../diagnose/index.md).
