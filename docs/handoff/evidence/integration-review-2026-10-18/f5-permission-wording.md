# Integration F5 — Full-mode consent wording

Owner: `fix/owner-test-small` (FIX-6), with the M77 wizard surface. Before-fix production: `27667c78`.

The displayed offer promised “commands still ask you”. Full loads the person's CLI permission settings and shows only permission requests the CLI sends; it does not intercept every execution. An allowed command therefore need not raise the analyser's dialog. The [official permission documentation](https://code.claude.com/docs/en/permissions), read during review, additionally describes automatic filesystem commands under acceptEdits. This runtime implication is **INFERRED** from source and the upstream contract; no paid/live CLI command was run.

**VERIFIED rendering witness:** `BuildModeOfferPanelTest#theOfferRendersAndReports`, **1 / 1 / 0 / 0** before the copy change. The actual banner failed `the mode offer must not promise approval for every command`. The corrected test requires the rendered automatic-command disclosure and permission-rule qualification, plus the matching wizard note. It is not merely an assertion against the constant the component ought to render.

The fix changes copy only, not permissions, defaults, pre-allows or decisions. The existing node test still pins what Apply writes. The user guide now makes the same qualification, including permitted read-only commands in Plan mode. Final full and affected-display counts appear in the integration report.
