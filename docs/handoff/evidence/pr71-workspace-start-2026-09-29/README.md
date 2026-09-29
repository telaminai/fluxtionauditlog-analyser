# PR #71 independent review evidence

Pinned application: `ea4faa1099ee2dc715c16ffe496bd3c9a6fe3a9d`.
Platform: macOS / Darwin 25.5.0 arm64, Corretto 21.0.8, FlatLaf light/dark.

These are **diagnostic probes, not passing regression tests**. They report the defective states; exit 0 only means the diagnostic completed. No production sources or compiled application classes were changed. Each application process uses a disposable home. Do not reuse a participant project or home.

## Reproduce

From the reviewed checkout, with JDK 21 on PATH:

```sh
mvn -o -q test
mvn -o -q package -DskipTests
```

Use a fresh scratch directory for compiled probes and output. Set `probe_dir` to this evidence directory and `scratch_dir` to that new directory. Then:

```sh
javac -cp 'target/test-classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar' \
  -d "$scratch_dir" "$probe_dir/WorkspaceProbe.java" "$probe_dir/SurfaceProbe.java"
java -Djava.awt.headless=false \
  -cp "$scratch_dir:target/test-classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar" \
  telamin.fluxtion.audit.analyser.analyser.ui.WorkspaceProbe "$scratch_dir" visual
```

Other WorkspaceProbe modes: `tour`, `bundle-race`, `native-drop`, `mixed-drop`, `xml`, `keyboard`. Run SurfaceProbe with just the output directory, using the same classpath. Run display processes **one at a time under the shared display lock**, and keep other UI automation off the display. The review used an exclusive `fcntl.flock` on `/tmp/fluxtion-analyser-display.lock` around every display process. Native-drop needs room for the separate DEMO drag-source window at x=1230 and native Robot input permission. If those requirements are absent, report that check unrun.

`bundle-race` holds the EDT while real background bundle extraction completes, requests the newer DEMO profile on that EDT, then allows the old completion to execute. It does not fake an unpack result or alter production code. This controls scheduling to expose the missing request identity. The other actions use real frame entrance methods/buttons; reflection reads state and reaches private UI entrances.

`native-drop` uses the OS drag manager between two shown windows with a real `javaFileListFlavor`. It prints transfer completion as well as whether the log opened. `tour` instead dispatches directly to the overlay's next-control coordinates; that is deliberately **not** evidence about native mouse routing. The separate keyboard mode uses native Robot keys and verifies focus before starting.

The standalone surfaces use the repository's existing DEMO `Samples` fixture and simple DEMO report/finding/template values. Template inspection never downloads or runs a template.

## Recorded results

Full headless: **2849 / 0 / 0 / 176**, 381 source-mapped XML reports, zero orphans.
Python UI gate: **10 / 0 / 0 / 0**.
Neighbour display classes: **38 / 0 / 0 / 0**.

Selected raw diagnostic output (paths omitted where irrelevant):

```text
returning-no-log: [Open audit log…, Open topology…, Open design…, Open diagnostics…, New project…]
menuShowing=false
newer project survived=false
active is bundle=true
native body logOpened=false
native card logOpened=true
mixed accepted=false
mixed message=Drop an .fexp by itself; no files from this mixed drop were opened.
mixed message showing=false
keyboard initial=Take a guided tour
keyboard Tab=Load an experiment
keyboard Space started tour=true
keyboard Right step=1
narrow findings text=[java.awt.Rectangle[x=81,y=47,width=72,height=36] visible=java.awt.Rectangle[x=0,y=0,width=72,height=36]]
series editor controls=[Add java.awt.Rectangle[x=2,y=0,width=72,height=23] visible=java.awt.Rectangle[x=0,y=0,width=0,height=0], Pick… java.awt.Rectangle[x=76,y=0,width=72,height=23] visible=java.awt.Rectangle[x=0,y=0,width=0,height=0]]
picker width=872 height=532 resizable=false
```

Walk toolbar: More was `(4,27,72,23)` within a toolbar of height 27, and its visible rectangle was `(0,0,72,0)`.

Tour steps 0, 1, 2, 3 each reached `SHOWN` with the intended target names. Inspect the images to see why that alone does not prove a useful introduction. The ungranted XML drop returned a pending acknowledgement, then the real asynchronous result refused it because it was outside authorised roots; it did not add a source root. The supplied frame test separately passed the authorised XML route.

## Visual evidence and limitations

All PNGs here are inspected Swing renders of **displayed** isolated DEMO frames. They are not native desktop screenshots or replacements for the known-outdated documentation images. Every image was opened and read before inclusion.

- `start-light`, `start-dark`, `start-narrow-settled`: fresh home, 1200×800 then 700×650. Wrapping works after layout settles.
- `tour-0` through `tour-3`: the actual saved DEMO walk at 1200×800.
- `walks-narrow`: 220-pixel standalone walk pane; management menu clipped.
- `series-editor-narrow`: 330-pixel graph pane; Add/Pick clipped.
- `findings-light`, `findings-220`: 450/220-pixel finding panes; alignment and header clipping.
- `template-picker`: local catalogue fixture, no download.
- `chart-round`: rendered positive DEMO series with round axes.

Early sandbox GUI launches aborted before running probes. The first SurfaceProbe compile omitted `target/test-classes`; the corrected command above includes it. An immediate resize render showed transient missing cards; settled renders disproved that hypothesis. A repeated visual probe reused a disposable home; the final first-run captures were repeated with fresh unique homes. These are disclosed probe-setup corrections, not hidden retries of failed product tests.

No full mutation gate, native drops on other OSes, screen-reader test, provider, compilation key, or participant project was used.
