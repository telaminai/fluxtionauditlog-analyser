# The runaway records table — what is known, and how to capture the evidence

A field diagnostic for one open defect. Written for someone who was not there.

## The symptom

During a 1.26.0 demo the **records table began scrolling and extending its selection with nothing touching it**.
It did not stop. Only restarting the application cleared it. The owner's description of the onset:

> "when I moved to the top of the jtable it started the uncontrolled scroll"

## What is established

Measured on the running app, in the affected session:

| time | selected rows | anchor → last byte offset |
|---|---|---|
| 11:46:53 | 1 | 7890 |
| 11:46:55 | 91 | 7890 → 65749 |
| 11:46:57 | 100 | 7890 → 71698 |

That anchor was set **programmatically**, through the action socket's `goto`, with no mouse in play. The table was
already in a drag. A **fresh process**, same log, same call, held at one row across four samples. So the fault is
**state stuck inside the JVM**, not a wrong code path that would misbehave from cold.

The symptom is `javax.swing.Autoscroller` still running. It stops only when the **table itself** processes a
`MOUSE_RELEASED` (`JComponent.processMouseEvent`). **So a release went somewhere other than the table.**

## What is ruled OUT

**The spotlight overlay did not swallow it.** The overlay is the frame's glass pane, and the first theory was that
it took the release while a walk raised it mid-gesture. That is wrong, and the evidence is direct:

- `java.awt.Container.LightweightDispatcher` keeps a **mouse grab**: for a release whose button was already down,
  `isMouseGrab` is true, `mouseEventTarget` is not recomputed, and the event is retargeted to the component that
  took the **press** — whatever is on top. Drags are retargeted the same way.
- Measured on a real frame with a real `JTable` and the real overlay, posting the gesture to the **frame** so it
  travels through the dispatcher: press on the table → raise the glass pane → release gives
  **`[TABLE release]`**. The overlay receives nothing. Identical with and without the attempted fix.

A PR built on that theory ([#60]) was closed unmerged. Its tests only passed because they called
`overlay.dispatchEvent(...)` directly, bypassing the router — a path the application never takes.

## What remains

The release is reaching *something*, and it is not the glass pane and not the table. The live candidates are
**another window** taking the release or the grab mid-gesture:

- a dialog opening under the pointer,
- a heavyweight popup (a menu — note that a right-click on a spotlight opens the walk save menu),
- a tooltip window,
- the OS delivering the release outside the frame entirely.

A posted event cannot model which window the OS chooses, so **this needs a physical mouse**, and therefore
instrumentation rather than a test. `java.awt.Robot` is not a way round it: its input is dropped on the affected
machine without Accessibility permission, the same limit that causes the focus-bound test skips.

## Capturing the evidence

Run the app with the trace on:

```bash
java -Danalyser.mouseTrace=/tmp/mousetrace.log -jar fluxtion-auditlog-analyser-1.26.0.jar
# or -Danalyser.mouseTrace=stderr
```

It is **off unless asked**, installs a passive `AWTEventListener`, consumes nothing, and changes no behaviour.
It never throws into startup: a bad destination disables the trace and says so on stderr.

Then **reproduce the runaway**: open a log, play a spotlight walk, and click around the records table while steps
land — press on the table as a step arrives is the shape that produced it. Stop as soon as the table starts
scrolling on its own.

### Reading the trace

One line per press, release and click. Drags are counted, not logged, so the file stays readable.

```
2026-09-28T11:50:26Z START trace=/tmp/mousetrace.log note="press/release/click with source window; drags counted"
2026-09-28T11:51:02Z PRESSED  btn=1 down=[1] src=JTable srcWindow=JFrame(Fluxtion Audit Log Analyser) under=JTable onTable=true sel=1 adjusting=false popupShowing=none
2026-09-28T11:51:03Z RELEASED btn=1 down=[] src=JTable srcWindow=JFrame(...) under=JTable onTable=true sel=7 adjusting=false popupShowing=none hadPress=true drags=14
```

| field | what it answers |
|---|---|
| `src` / `srcWindow` | **where the event actually went** — the question this whole trace exists for |
| `under` | the deepest component under the pointer, which may differ from `src` during a grab |
| `down` | which buttons the trace believes are held |
| `hadPress` | whether this release is paired with a press the trace saw |
| `onTable` | whether the event reached the records table |
| `sel` / `adjusting` | the table's selection size, and whether it thinks a gesture is in progress |
| `popupShowing` | every showing window that is not the main frame — dialogs, menus, tooltips |
| `drags` | drag events since the last press |

### The line that matters

```
SUSPECT tableIsMidDragButNoButtonIsDown size=… grew=… dragsSinceLastPress=… note="the release that should have ended this gesture went elsewhere - the RELEASED line above says where"
```

`SUSPECT` fires when **the table believes a drag is in progress while no button is down** — the stuck state
itself. It deliberately does *not* fire merely because a selection grew with no button down: every programmatic
selection does that (a walk step, `goto`, a spotlight), and a diagnostic that cries at normal behaviour is one
nobody reads.

**When you have a `SUSPECT` line, read upwards to the last `RELEASED` (or its absence).** That names the window
and component that took the release, which is the answer.

## Captured traces

They go in [`evidence/runaway-table/`](evidence/runaway-table/), which also holds the capture protocol. **At the
time of writing there is no captured trace** — the instrument is built and verified, the reproduction has not
happened, and a diagnosis cannot start before it.

Note from the first run of the instrument: a `SUSPECT` line with `size=0` is **noise from a log open**, not the
fault. The rule now requires a non-empty selection and a prior real press; see the evidence directory's README.

## The question to answer

1. Where did the release go — which `srcWindow`, which component? Or did no `RELEASED` arrive at all?
2. What was showing at the time (`popupShowing`)?
3. Given that, what is the smallest correct fix, and roughly how big is it?

The fix is **not** obvious from here, and the previous attempt failed by guessing before measuring. Please do not
propose one without a trace showing where the release went.

[#60]: https://github.com/telaminai/fluxtionauditlog-analyser/pull/60
