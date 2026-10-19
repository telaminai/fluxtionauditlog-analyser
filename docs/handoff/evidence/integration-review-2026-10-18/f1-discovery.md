# Integration review F1 — discovery failure

Owner: `feature/propose-starter`, PR #167. Starting tree: `ddccd5c5df8c4f95aeff68040c2f64e298df870e`.

`MainFrame.runStarterDiscovery` posts `StarterInstallFailed` from its background error callback. Previously
`StarterProject.onInstallFailed` accepted that fact only in DOWNLOADING. Once skills finished and discovery began,
the error was ignored: the chain stayed DISCOVERING and the proposal APPLYING indefinitely; another starter was refused as busy.

Maintained regression: `StarterProjectTest#discoveryFailureDoesNotStrandTheStarter`, driven through the generated
processor. Apply, install, finish skills, post discovery's failure. It checks the terminal state, the proposal's failure,
the retained-files disclosure, no project open, rejection of stale/late outcomes, and admission of another creation.

Executed against unchanged production code: **1 / 1 / 0 / 0**, assertion
`discovery failure must not leave the chain busy ==> expected: <FAILED> but was: <DISCOVERING>`.
After the fix: full `mvn -o test` **4186 / 0 / 0 / 326**. Affected display suites under the shared display lock,
`StarterProposalFrameTest,TemplateCatalogueFrameTest`, with both headless properties false: **3 / 0 / 0 / 0**.
Counts are total / failures / errors / skips, JDK 21 on macOS. No live model or remote generator was used.

An initial post-fix run exposed a mistake in this new test's secondary assertion: it expected REFUSED rather than
the existing proposal contract's FAILED. That expectation was corrected; the primary before-fix wrong-result witness
above is unchanged. Logs of both attempts remain in ignored local build output. This is not claimed as a real
filesystem discovery-fault injection: the regression delivers exactly the error fact the real adapter emits.

Only a handler body changed. No constructor wiring, handler signature, event type or dispatch topology changed;
regeneration is not needed for this fix and generated files were not edited.
