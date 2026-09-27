# Before / after — chart and report lifecycle

Captures for the six fixes in this branch. All four are from the **venue-neutral demo fixture**
(`src/main/resources/demo`, `com.acme.demo`), copied to a scratch source root so no repository path
appears in the frame, and cropped to the region under discussion.

| | |
|---|---|
| `before-chart.png` / `after-chart.png` | the closing step and the x-axis labels |
| `before-spotlight.png` / `after-spotlight.png` | four callouts on consecutive source lines |

Each pair is the **same data, same call sequence, same crop offsets**; the spotlight pair is
additionally the same pane layout (topology + source split), so only the fix differs.

## Recaptured, PR #51 review

`after-spotlight.png` was recaptured with a taller crop: the first one cut callout 4 off
mid-sentence, so the image failed to show the one thing it was there to show — that the fourth
callout is readable. Same frame, same four targets, same build as the branch.

**Known, and not fixed here:** callout 3's leader lands on the pixel where cut-out 3 ends and
cut-out 4 begins. `cutOuts()` trims adjacent cut-outs to meet halfway (here 1127), and `arrow()`
clamps a callout sitting below its target to that target's bottom edge — so the two coincide and the
arrow reads as ambiguous. `arrow()` is untouched by this PR; the behaviour predates it.
