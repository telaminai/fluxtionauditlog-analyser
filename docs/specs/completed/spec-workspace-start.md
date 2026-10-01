# Workspace start page and Swing reading surfaces

**Archived 2026-10-01:** shipped, and moved to `docs/specs/completed/`. Released in 1.28.0 (M71). The optional M71.F1 stays in the live tracker ▸ M71.

**Status:** PR #71 with review corrections, 2026-09-29. This records the owner's newer start-page direction and supersedes the layout and no-log navigation portions of [the shipped start-page spec](spec-start-page.md). Its empty-state and no-first-run-modal principles still apply.

## Intent

An empty analyser opens to a full-width workspace choice, rather than placing a long brochure in the records half of the investigation split. The choices name the work a person wants to do. Opening a project, log or graph returns to the workspace; **Help → Start page** can raise the choice page again without closing the work. Controls remain ordinary Swing actions and use the same project and log entrances as menus and the action socket.

## Choices and consequences

| Choice | Result |
|---|---|
| Take a guided tour | Open the bundled DEMO project, save its four-step product-introduction spotlight walk if absent, and play it through the ordinary session walk path. The tour steps show records, topology, a selected record and Reports. The saved walk can be replayed later from Reports. It never replaces a walk already saved under that name. |
| Open evidence bundle | Choose an `.fexp`; verify and unpack it off the event thread into a new disposable working copy, open its project, optional GraphML and one audit log. Show the bundle identity, working-copy path and its limits. Refusal changes no active project. This does not execute a replay. |
| Create project profile | Open the existing new-project flow; no template download is implied. |
| Create from template | Open the existing template catalogue and destination flow. |
| Recent projects | Show the machine's recent-project list by workspace name, with the profile path available to distinguish entries; selecting one uses an explicit project switch and opens the project workspace even if it has no log. It does not reopen a prior log implicitly. |
| Open project | Choose an existing project workspace even when it is absent from the recent list. |
| Open audit log / Open GraphML | Use the existing file chooser and open paths. A standalone graph can show the investigation workspace without a log. |
| Open sample project | Install the bundled DEMO assets under the analyser's local demo directory, create a project profile there if absent, and open that project, log and graph. It requires no API key or network and never overwrites an existing demo profile. |

The start card replaces the entire application work area. The time-range controls, toolbar, left navigation, output tabs, menus and status bar are hidden while it is shown; they return together in the project or evidence workspace. This keeps irrelevant controls from competing with the first decision. A project is itself a workspace: once selected, the start card gives way to the investigation layout. The page remains scrollable and its action cards reflow rather than clip at narrow widths.

The choices are grouped by outcome. **Explore DEMO** offers the guided tour and the same sample for independent exploration. **Open your work** contains project, evidence-bundle, audit-log and GraphML entrances plus recent profiles. **Create a project** distinguishes a settings-only profile from an installed template. **Assistant and settings** offers the local assistant bridge first, source settings second, and optional processor regeneration last. The redundant incident chooser is not presented on this page: the bundle and audit-log entrances already name its two destinations. At desktop widths the DEMO/create groups are on the left and open/settings on the right; narrow windows stack DEMO, open, create, settings. Recent entries show the profile filename before the full path so profiles in one project can be distinguished. The hero names the accepted drop formats.

Help → Start page always shows those same choices, including over a project with no log. If a project, graph, design or log workspace exists, **Return to workspace** is visible and restores it without changing evidence. Project declarations stay in the workspace Project panel. Verification progress and file-drop refusals appear on the start page itself because the application status bar is hidden there.

Each choice has a small theme-coloured vector icon beside its title. Icons reinforce the action's meaning but the text and accessible name carry it; they must remain legible in both light and dark themes and at display scaling.

The source-settings action opens the ordinary Settings dialog. With a project open, source edits apply to that project; without one, they apply to global defaults. Returning to Start does not change which settings tier is active.

The same page accepts drag and drop. A single `.fexp` uses the verified bundle open; a `.graphml` opens Topology; a Spring `.xml` uses the design-session open; other files keep the audit-log open behavior, including logs with unusual names. A dropped `.fexp` alongside other files is refused as an ambiguous mixed request. For a log, graph and design dropped together, the first of each type is opened and extra files are named as ignored.

Bundle opening is one session request from the start. Verification and unpacking report back with the original operation ID; a newer project, bundle, log, or explicitly opened GraphML supersedes a late result. Reader-supplied graph facts do not cancel preparation; after preparation is accepted, the bundle’s own graph effect retains its log operation. A successful current result then applies the bundled project before opening its graph and log. A stale completion, including a stale refusal, changes no workspace and shows no obsolete warning.

## Reading surfaces

Producer findings separate the result context from each finding. A finding shows severity and code, then the message, reason, suggested fix, optional XML declaration, location explanation and available source actions. Text from a producer result is rendered as plain Swing text, not HTML; all existing statements and source-action semantics remain. Cards, borders and severity colours derive from the current light or dark theme and are rebuilt after a theme change.

At a 220-pixel sidebar width, walk management remains reachable through More and the walk/step list stacks vertically. At a 330-pixel chart width, the series editor tracks viewport width so Add, Pick and formula resolution stay visible. Finding codes, explanations and fixes wrap within the reading width.

## Acceptance checks

1. With no project, log or graph, the start page occupies the whole content area with no time-range control, left tabs or application menus; opening evidence restores all workspace controls. Help can raise the start page and return without closing evidence.
2. Every listed action reaches a distinct, truthful existing flow. A recent project becomes the active project workspace; a sample project has a real profile. The guided-tour choice saves and plays an ordinary spotlight walk, and every step lights the UI it describes.
3. A malformed or unverifiable `.fexp` refuses before any project switch. A verified bundle opens only members under its extracted working copy, names its identity and limits, and does not imply sender authentication or guaranteed replay.
4. At desktop width the four activity groups form two columns; on a narrow window they stack without overlap. All cards and recent paths remain reachable by vertical scrolling; keyboard and accessibility names identify every action.
5. Producer findings remain readable at narrow width and in light and dark themes, with every message, reason, fix and declaration intact. Unavailable source actions stay disabled and explain why.
6. The public-data sweep prints nothing beyond the rule files' exemption, Maven tests pass, and display-backed UI tests catch a wrong full-width layout or a missing action.
7. File drops on the start page and active workspace route each supported type to the same entrance as its menu or card; a `.fexp` or Spring XML never falls through to the audit-log reader.

`python3 tools/test_start_workspace.py` drives the display-backed frame tests with real DEMO files and Swing file-list transfers. It checks that the expected cases actually ran, with no skip, including a verified `.fexp` drop, a native file-list drag onto explanatory text, a delayed bundle/result race, visible mixed-drop refusal, narrow panel geometry, a Spring design/GraphML/log sequence, a recent project with no log, a sample project with its own profile, and every step of the saved guided tour. `BundleOpenReplayTest` and the `ws-stale-bundle-verification-refused` mutation control pin the session decision independently of worker timing.

The reviewer should challenge this information hierarchy and the wording of each choice with concrete first-run and return-user scenarios. In particular, assess whether the distinction between a project profile, an evidence bundle and the sample project is understandable without knowing this repository's terminology.
