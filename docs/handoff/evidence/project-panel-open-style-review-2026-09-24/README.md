# Project-panel Open and chart-style review evidence

Subject: main **43ce82fc**, reviewing **35eeb320** and **43ce82fc**. See the [review](../../review_project_panel_open_style_2026_09_24.md).

These are **review probes**, not installed application features or production test gates. No production source was changed. The display probe uses a built main jar and an isolated placeholder project, with two reports whose names differ from their titles, two charts and eight constructed audit records. It does not use the owner's fixture or application home.

## Results

- [Gate counts](gate-counts.json): Maven package/test phase, 1,887 total / 0 failures / 0 errors / 62 skipped; ten cases in the two new suites. Strict MkDocs build also passed.
- [Display output](display-output.txt): real MainFrame, real Robot clicks. Checks 1–4 pass. A human dropdown choice reopens as Stairs; the programmatic setter control reopens as Line. Legacy default passes. External Line reopens as Stairs.
- [First asserted style failure](first-style-assertion-failure.txt): the earlier version stopped when the expected persisted Line key was absent. The completed probe records that failure as an observation so the remaining checks can run; its zero exit is **not** an all-checks-pass verdict.
- [Profile output](profile-style-output.txt): six actual ProjectProfile save/load cases. Plain Line/Points survive, both external-data forms lose both styles.
- [Historical output](historical-output.txt): predecessor ProjectModel + ProjectPanel produce zero Open buttons on a saved-chart row.

The source files beside this README reproduce these checks. `DisplayProbe` reflects existing fields to inspect selected components and construct the saved-but-not-open fixture. It does not install a new action or substitute a Navigator. It uses existing open/close operations for setup; the row actions themselves come from mouse clicks. Style selection also uses the actual popup's Line cell. The programmatic setter is explicitly a separate control. Do not interact with the mouse during this probe.

## Reproduction

From a checkout of the subject revision, using JDK 21:

```sh
export JAVA_HOME=/path/to/jdk21
export PATH="$JAVA_HOME/bin:$PATH"
mvn -q package
mkdocs build --strict
probe_dir=$(mktemp -d /private/tmp/project-panel-probe.XXXXXX)
packet=docs/handoff/evidence/project-panel-open-style-review-2026-09-24
jar=target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
javac -cp "$jar" -d "$probe_dir" "$packet/DisplayProbe.java" "$packet/ProfileStyleProbe.java"
java -cp "$probe_dir:$jar" ProfileStyleProbe
java -Djava.awt.headless=false -cp "$probe_dir:$jar" DisplayProbe "$probe_dir/run"
```

The last command needs a real display and OS permission to generate mouse events and capture the screen. It sets its own isolated `user.home`. The successful observation run was on macOS with Corretto 21.0.8; this is not a portability result for the native popup on other systems.

To reproduce the historical row, compile the two **old** UI classes ahead of the current jar:

```sh
mkdir -p "$probe_dir/before"
git show '35eeb320^:src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ProjectPanel.java' > "$probe_dir/before/ProjectPanel.java"
git show '35eeb320^:src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ProjectModel.java' > "$probe_dir/before/ProjectModel.java"
javac -cp "$jar" -d "$probe_dir/before" "$probe_dir/before/ProjectPanel.java" "$probe_dir/before/ProjectModel.java" "$packet/HistoricalProbe.java"
java -Djava.awt.headless=true -cp "$probe_dir/before:$jar" HistoricalProbe
```

No complete predecessor build is claimed: the two classes implementing the old row/model rendering are taken directly from its tree and compiled against unchanged supporting APIs in the subject jar.

## Screenshots

All are actual window captures, visually inspected before commit. They contain constructed placeholder data.

| Capture | What it establishes |
|---|---|
| [01 second report](01-second-report.png) | The second row selects its distinct report body. |
| [02 first report](02-first-report.png) | The first row switches the visible selection and body back. |
| [03 saved chart](03-open-saved-chart.png) | Closed saved view recreated with series, notes, pin and both axes. |
| [04 edited chart](04-existing-edited-chart.png) | Third series and changed pin survive reselection. Object identity is checked by the probe. |
| [04b UI Line](04b-ui-line-selected.png) | The human dropdown really selected Line before project close. |
| [05 UI reopen](05-ui-line-reopened.png) | The same chart returns as Stairs. |
| [05b setter control](05b-programmatic-line-reopened.png) | Programmatic Line survives the same project reopening path. |
| [06 legacy](06-legacy-stairs.png) | An absent style declaration retains the old Stairs default. |
| [07 external](07-external-line-reopened.png) | An explicitly saved external Line chart renders as Stairs after profile load. |

No source mutation campaign or application fix was performed. The probes distinguish positive controls from observed failures; they do not claim a regression has been closed.
