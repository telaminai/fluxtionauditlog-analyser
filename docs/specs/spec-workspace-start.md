# Workspace start page and Swing reading surfaces

**Status:** proposed for PR #71 review, 2026-09-29. This records the owner's newer start-page direction and supersedes the layout and no-log navigation portions of [the shipped start-page spec](completed/spec-start-page.md). Its empty-state and no-first-run-modal principles still apply.

## Intent

An empty analyser opens to a full-width workspace choice, rather than placing a long brochure in the records half of the investigation split. The choices name the work a person wants to do. Opening a project, log or graph returns to the workspace; **Help → Start page** can raise the choice page again without closing the work. Controls remain ordinary Swing actions and use the same project and log entrances as menus and the action socket.

## Choices and consequences

| Choice | Result |
|---|---|
| Take a guided tour | Open the bundled DEMO project, save its four-step product-introduction spotlight walk if absent, and play it through the ordinary session walk path. The tour steps show records, topology, a selected record and Reports. The saved walk can be replayed later from Reports. It never replaces a walk already saved under that name. |
| Load an experiment | Choose an `.fexp`; verify and unpack it off the event thread into a new disposable working copy, open its project, optional GraphML and one audit log. Show the bundle identity, working-copy path and its limits. Refusal changes no active project. This does not execute a replay. |
| Investigate an incident | Ask whether the available evidence is an audit log or a bundle, then use the corresponding open path. |
| Author a new project | Open the existing new-project flow; no template download is implied. |
| Author from template | Open the existing template catalogue and destination flow. |
| Recent projects | Show the machine's recent-project list; selecting one uses an explicit project switch and opens the project workspace even if it has no log. It does not reopen a prior log implicitly. |
| Open audit log / Open GraphML | Use the existing file chooser and open paths. A standalone graph can show the investigation workspace without a log. |
| Open sample project | Install the bundled DEMO assets under the analyser's local demo directory, create a project profile there if absent, and open that project, log and graph. It requires no API key or network and never overwrites an existing demo profile. |

The start card replaces the entire application work area. The time-range controls, toolbar, left navigation, output tabs, menus and status bar are hidden while it is shown; they return together in the project or evidence workspace. This keeps irrelevant controls from competing with the first decision. A project is itself a workspace: once selected, the start card gives way to the investigation layout. The page remains scrollable and its action cards reflow rather than clip at narrow widths.

Each choice has a small theme-coloured vector icon beside its title. Icons reinforce the action's meaning but the text and accessible name carry it; they must remain legible in both light and dark themes and at display scaling.

Optional source setup on the start page is labelled as **global source defaults**. Project source settings can override those defaults; the wording must not imply that changing global roots silently edits every project.

The same page accepts drag and drop. A single `.fexp` uses the verified bundle open; a `.graphml` opens Topology; a Spring `.xml` uses the design-session open; other files keep the audit-log open behavior, including logs with unusual names. A dropped `.fexp` alongside other files is refused as an ambiguous mixed request. For a log, graph and design dropped together, the first of each type is opened and extra files are named as ignored.

## Reading surfaces

Producer findings separate the result context from each finding. A finding shows severity and code, then the message, reason, suggested fix, optional XML declaration, location explanation and available source actions. Text from a producer result is rendered as plain Swing text, not HTML; all existing statements and source-action semantics remain. Cards, borders and severity colours derive from the current light or dark theme and are rebuilt after a theme change.

## Acceptance checks

1. With no project, log or graph, the start page occupies the whole content area with no time-range control, left tabs or application menus; opening evidence restores all workspace controls. Help can raise the start page and return without closing evidence.
2. Every listed action reaches a distinct, truthful existing flow. A recent project becomes the active project workspace; a sample project has a real profile. The guided-tour choice saves and plays an ordinary spotlight walk, and every step lights the UI it describes.
3. A malformed or unverifiable `.fexp` refuses before any project switch. A verified bundle opens only members under its extracted working copy, names its identity and limits, and does not imply sender authentication or guaranteed replay.
4. On a narrow window, all cards and recent paths remain reachable by vertical scrolling; keyboard and accessibility names identify every action.
5. Producer findings remain readable at narrow width and in light and dark themes, with every message, reason, fix and declaration intact. Unavailable source actions stay disabled and explain why.
6. The public-data sweep prints nothing beyond the rule files' exemption, Maven tests pass, and display-backed UI tests catch a wrong full-width layout or a missing action.
7. File drops on the start page and active workspace route each supported type to the same entrance as its menu or card; a `.fexp` or Spring XML never falls through to the audit-log reader.

`python3 tools/test_start_workspace.py` drives the display-backed frame tests with real DEMO files and Swing file-list transfers. It checks that the expected cases actually ran, with no skip, including a verified `.fexp` drop, a Spring design/GraphML/log sequence, a recent project with no log, a sample project with its own profile, and every step of the saved guided tour.

The reviewer should challenge this information hierarchy and the wording of each choice with concrete first-run and return-user scenarios. In particular, assess whether “experiment,” “incident,” and “sample project” are understandable without knowing this repository's terminology.
