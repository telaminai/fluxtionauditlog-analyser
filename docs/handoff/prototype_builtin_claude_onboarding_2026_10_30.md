# Built-in Claude Code onboarding prototype

Status: local prototype; not released. Based on main at `6d51aa16`.

## Intended journey

Launch the analyser normally, with no `--rest` or external-client registration. On a fresh profile, choose
**Build with Claude Code**, **Explore a demo**, or **Connect another AI client**. More AI options and Not now remain available.

Build opens the existing Claude installation/sign-in checks directly with Full Claude Code selected. The existing
Full-mode confirmation remains mandatory. Setup neither starts Claude nor sends a question. Its completion offers
**Describe your application**, selecting the existing Build entry while preserving any text already typed.
The normal Start building action places a draft in the assistant. Send starts the session through the existing
folder-trust and private-bridge flow. A starter proposal still needs the person's folder selection and approval.
External access remains off on a fresh profile; existing external-access settings are not changed.

## Prototype scope

The view renders the existing session snapshot. Two setup origins and one suggested action reuse the existing events
and effects; there is no second setup state machine, CLI installer or new MCP verb. Existing registrations are untouched.
The session processor was regenerated using the configured service (compiler 1.0.83); this also refreshes its generated
failed-cycle handling from the prior compiler version. Generated output was not edited by hand.

No public website deployment or new `--setup-client` flag is included. Existing users can reach the full setup through
Set up AI; the three-button introduction is the first-run surface.

## Candidate one-line launch instructions

A future `start-with-ai.md` should tell the outside assistant to:

1. Check for JBang and the required Java runtime; explain any missing prerequisites.
2. Launch `jbang analyser@telaminai/fluxtionauditlog-analyser` without `--rest`. Preserve JBang's first-run trust prompt.
3. Hand the developer to **Build with Claude Code** inside the analyser. Do not register an external MCP server.
4. If requirements were already discussed, provide a short handoff the developer can paste into the built-in session.

The proposed website URL is not live as part of this prototype. Missing Claude or a missing login uses the existing
setup explanation rather than claiming the application is connected.

## Acceptance and remaining work

### What was demonstrated

- Java 21 `mvn -q package`: 4,096 executed tests passed, with 382 display-dependent tests skipped in the headless run
  (4,478 total; zero failures or errors).
- Display run: all 15 tests in `AiSetupWizardFrameTest`, `AiSetupClientFrameTest`, `StarterProposalFrameTest`, and
  `StarterProposalVisibleFrameTest` passed, with no skips. These use fixture agents. The new walkthrough follows
  Build → installation/sign-in checks → explicit Full confirmation → Describe your application → initial draft.
  It asserts that setup has not started Claude and has not enabled external access.
- Focused regression checks include `AiSetupTest`, `AiSetupWordingTest`, `StartPanelNamesTest`, `OneDispatchModelTest`,
  and `ForeignTextIsPlainTest`. The wrong-result witness removed the onboarding Full-mode preselection: the new
  `onboardingBuildRequiresConsentAndNeverRegistersAnExternalClient` test failed with expected FULL / actual SAFE.
  The change was restored before the passing package run.
- The built jar's first-run screen was captured and visually inspected with an isolated analyser home. The display
  walkthrough's setup, summary and draft screens were also inspected.
- A real installed Claude Code 2.1.311, using its existing sign-in, passed the installation/sign-in checks, admitted
  the private analyser bridge and completed one bounded read-only question. This live smoke selected **Safe analysis**:
  external access stayed false, personal MCP server count was zero, and an ANSWER entry completed. The prompt asked
  for the current project/log context; the harness checked admission and completion, not the exact tool invocation or
  answer text. No raw transcript, account data or credentials are included in this report.
- The live smoke used a temporary single-jar test harness added to a copy of the built artifact. An earlier
  multi-entry-classpath harness was correctly refused by the existing bridge-launcher guard; no production resolver
  change was needed. The deliverable jar contains no smoke harness.
- `git diff --check` and the repository's public-data sweep passed.

### Remaining before release

This is a working local prototype, not a completed release or one-action project build. It still keeps two distinct
actions: **Start building** places the initial requirements in the assistant; **Send** starts Claude. A later UX decision
can simplify that transition without hiding which action starts Claude or which folder is trusted.

A complete project created by a live **Full Claude Code** session has not been demonstrated in this prototype. Starter
proposal/review behavior is covered by the fixture-backed display suites; real template acquisition, installation of
the bundled skills and the first build need an end-to-end authoring acceptance run. The public JBang catalog still
launches the released analyser, not this local prototype. The proposed website handoff page and release validation
remain open. No external client configuration, website, release or deployment was changed.
