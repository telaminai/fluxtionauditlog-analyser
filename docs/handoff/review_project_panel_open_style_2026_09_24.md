# Review: row-specific Project-panel Open and plot-style persistence

Reviewed **35eeb320** and **43ce82fc**, using a build of **43ce82fc** (main when fetched). The intervening **fbe92074** README change is outside this review. No application source, specification or tracker was changed. During this review, origin/main advanced to **1e51545d** through a verification record, a Close/Delete change and a restore proposal. Those are outside this verdict; the [follow-up prompt](prompt_review_project_panel_chart_lifecycle_2026_09_24.md) scopes their review.

**Verdict: the navigation amendment and implementation are sound; changes are required before calling the style fix complete.** The actual Project-panel buttons passed display checks 1–4. Check 5 fails through the human's dropdown, despite passing through the programmatic setter. An additional profile-import path loses style. One historical correction is itself false.

Evidence: [executable probes, output and screenshots](evidence/project-panel-open-style-review-2026-09-24/README.md). The probes use the built main jar, an isolated home, and a constructed placeholder project. They do not touch the owner's project or running analyser. Project/log acquisition uses the existing executor; every Open under test is a **Robot mouse click on the real Project-panel button**, not an action verb or a recording Navigator.

## Required corrections

### F1 · Medium — choosing Line in the UI still loses the choice on project close/reopen

**Sites:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphPanel.java:176–180,981–989`; `src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphStylePersistenceTest.java:82–91`.

**Reproduced on the real display.** Open a saved chart, choose **Line** from the actual style dropdown, allow the save debounce to settle, and close/reopen the project and its log. The on-screen choice is Line before close; the profile still contains `graph.1.style=step`; reopening renders Stairs. Explicitly invoking the existing pending-write flush does not help, because no save was requested.

The combo listener only updates `ChartPanel`. `setStyleByName` updates the same combo **and calls `mutated()`**. The new `styleIsAPersistableMutation` test exercises that setter rather than the human path. The investigation's assertion that style has always been a persistable mutation needs this qualification. The storage omission was fixed, but the UI notification omission remains. This is incomplete coverage of the claimed fix, not a newly introduced listener regression.

**Control:** changing the same chart through `setStyleByName("line")` writes `style=line`; the same project-close/reopen sequence then displays Line. The unstyled legacy profile displays Stairs as intended. The screenshots preserve both outcomes.

**Required:** make a genuine dropdown change notify persistence, without double-notifying the programmatic setter. Add a regression exercising the combo's real change event and a project-profile round trip; keep a control that removes the notification and fails the named assertion. There is no ordinary Save command to compensate: the File menu explains that project settings save automatically.

### F2 · Medium — external-data charts lose their saved style during profile import

**Sites:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/config/SettingsShare.java:320–352`, especially `350–352`; the compatibility constructor in `config/GraphSpec.java:25–31`.

**Reproduced headlessly through `ProjectProfile.save/load`, and on the real display.** Save a Line or Points chart containing either an external CSV series or an external CSV marker. Its file contains the correct style. Loading that profile produces `declaredStyle=null`, hence `style=step`. The external-series example also renders Stairs in the actual MainFrame.

`SettingsShare.preview` rewrites external paths by constructing a new `GraphSpec` with the old constructor. It omits the newly added style component. Plain charts skip this reconstruction and retain their style. This affects both project profiles and settings import, independently of F1: the test starts with an explicitly persisted style, not a dropdown edit.

Observed results:

| Chart | Written style | Loaded style |
|---|---|---|
| Plain | Line / Points | Line / Points |
| External series | Line / Points | Stairs / Stairs |
| External marker | Line / Points | Stairs / Stairs |

**Required:** carry the declared style through this copy, preserving an absent declaration as absent. Test both external paths through the actual profile/import layer. Removing the copied style must fail those assertions; a ConfigStore-only round trip cannot catch this.

### F3 · Medium — the spec amendment and investigation invent a former dead Open button

**Sites:** `docs/specs/completed/spec-loaded-panel.md:85–90`; `docs/investigations/profile-project-root-resolution.md:79–81,129–131`; also `ui/ProjectModel.java:57–60` and `ui/ProjectPanelOpenRevealsTheRowsItemTest.java:17–19,84–86`.

**Reproduced from the parent of 35eeb320.** I compiled that revision's `ProjectModel` and `ProjectPanel` and rendered a saved-chart row. Result:

```text
Target=NONE, path=null, row Open buttons=0
```

The old panel adds an Open button only for named target cases; `NONE` reaches the empty default. With no path there are no path buttons either. Thus this was a **missing navigation action**, not a visible button wired to nothing. The older investigation's factual statement that saved charts had no per-row Open was correct. Whether the old restriction was desirable is a separate policy question, now settled by the owner.

**Required:** correct the history in the amendment, investigation and code/test commentary. Keep the authorised navigation change. Do not turn a legitimate policy change into a repair of a control the reviewed predecessor did not render. If a different build actually displayed a dead button, identify that build and preserve its evidence separately.

## D-L3 judgement

**I accept opening a saved-but-not-open chart as reveal.** A definition already exists in the profile. Constructing its Swing view does not author a new chart definition, just as opening a document viewer does not create the document. MainFrame resolves the row's name against existing saved definitions, or selects an existing tab; it does not fall back to creating an arbitrary named chart. `openSaved` selects an existing panel before applying any saved definition. When constructing a missing view, it suppresses edit notifications while using the existing restore path.

The location of the limit is appropriate: **the Navigator contract defines the permitted request, and the adapter must enforce it**. Exact method-set and bytecode tests prevent accidental expansion of the panel's dependency surface; they do not prove the adapter's behaviour. A four-method allow-list alone would not catch an implementation of `showGraph` that overwrote edited tabs. The display probe verifies the same panel object survives, alongside the added series, changed pin and caption. It also checks that materialising the closed view leaves the saved definitions unchanged.

An optional wording improvement is to replace the absolute “nothing ... mutates state” with “does not author, alter or discard saved definitions or discard open edits.” Selection and tab materialisation necessarily change transient view state. This clarifies the approved rule without widening it.

The report label/identity split is correct: `ProjectModel` uses title for presentation and name for `Row.item`; the panel passes that identity to `ReportsPanel.select`. The real-window fixture deliberately uses different names and titles. Saved-chart labels and identities currently share the same name but are carried separately. Other ProjectModel rows either navigate to a tab/settings page or use their separate file path; I found no other item-selection path using the abbreviated presentation label as identity.

## The five display checks

| Check | Result and scope |
|---|---|
| 1. Second report | **Pass.** Its button selects `report-two`; the visible body is the second report. |
| 2. First report | **Pass.** Its button switches back to `report-one` and its distinct body. |
| 3. Saved, closed chart | **Pass.** Open creates/selects its view; both series, explanation/note, right axis and pin survive. Saved definitions compare equal before/after. |
| 4. Already-open edited chart | **Pass.** The exact GraphPanel object survives; the added third series, changed caption and changed pin survive after selecting another chart and opening this one again. |
| 5. Line save/reopen; legacy default | **Fail for the human dropdown (F1).** The programmatic setter control survives; a legacy profile without the key opens as Stairs. External-data profiles expose the separate F2 failure. |

Check 3 uses a **constructed UI state**: after normal profile/log loading, the harness detaches and unbinds one view while preserving its saved definition. It does not claim that Close graph is the way users reach this state (that control edits the saved collection). Setup edits for check 4 use the real panel methods; the Open being tested is always a mouse click. This is not a replay of the owner's project.

I inspected all nine committed screenshots. They show the actual built main window and only placeholder data. The profile/log operations use the executor in process; no socket verb for clicking a row was added. Earlier harness attempts failed because a row was not yet scrolled on screen, or because a keyboard popup selection did not choose Line; those were harness failures, not product findings. The final run uses the real popup's Line cell and actual mouse events. F1's first asserted failure and the completed observation run are both preserved.

## The other historical correction

The warning about the earlier `analyser_topology` trial is **correct**: a verb that itself loads a graph cannot demonstrate what the Project-panel button did. The new topology-row unit test proves the panel asks for `showTab("Topology")`, not that a populated topology appeared on screen. The investigation acknowledges the MainFrame gap later, but “The UI-test gap is closed” and “Only the button-level test below covers the reported behaviour” still overstate that test's reach. Prefer “Navigator routing is covered; frame behaviour requires the display checks.”

I did not replay the original topology/profile-root scenario, so I do not independently endorse its 13-node observation. It is not one of the five requested checks. The report/chart results above do establish their actual MainFrame behaviour.

## Checks run and remaining limits

- `JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home mvn -q package` — **1,887 tests, 0 failures, 0 errors, 62 skipped**, parsed from Surefire XML; packaging succeeds. This runs Maven's test phase. It is 1,825 executed non-skipped cases, not 1,887 passing cases plus skips. The two new classes account for ten cases.
- `mkdocs build --strict` — **pass**.
- Real macOS display: `DisplayProbe` against the packaged **43ce82fc** jar — checks and failures above; all planned observation stages completed.
- `ProfileStyleProbe` — six project-profile save/load cases, with the four external-data losses above.
- `HistoricalProbe` — predecessor classes compiled separately; no old saved-chart Open button.
- `git diff --check` and the repository's rule-one sweep — clean before committing this review packet.

No implementation was changed and no fix is claimed closed. No main push, merge, release, key use or LLM trial. No broad display-suite run or source mutations were performed for this review. These review probes are evidence, not new production regression gates; the required fixes need their own committed tests and seen-red controls. The built jar and isolated home are local scratch, not release artefacts. The primary checkout was left untouched.
