# Built-in Claude Code onboarding preview

Preview channel: `v1.41.0-onboarding.1`, from `prototype/builtin-claude-onboarding`, based on main at `6d51aa16`.
The normal release alias and public Pages deployment are unchanged.

## User journey

The [illustrated quickstart](../site/start-with-claude.md) has three stages:

1. Run its pinned JBang command and choose **Build with Claude Code**.
2. Read the installation/authentication checks and Full-mode consent, then choose **Enable Claude Code**.
3. Describe the application in the existing assistant composer and press **Send** once. Claude replies there.

JBang and an authenticated Claude Code installation are prerequisites. JBang may ask to trust the download; an
existing project may require folder trust. Creating a starter still requires review and a destination folder.
The preview is an entry point to a conversation, not an unattended project build.

Build, Demo and Connect remain on the start page after dismissal or restart. A returning Full-mode user goes directly
to the composer without overwriting their permission mode or draft. A new builder sees the complete Full consent on
the checks screen, with Cancel as the default. Setup never starts Claude or sends a question. API-key and key-helper
authentication retain the existing informational, unverified-key disclosure.

The existing session processor owns the decisions; the UI renders the snapshot and performs requested effects.
The processor was regenerated, with no graph-topology change in this correction. The route neither registers an
external client nor enables external access. Existing external-access settings are preserved.

## Review dispositions

| Finding | Correction and regression protection |
|---|---|
| Public command launches a different UI | The guide pins the separate preview release asset. Its checksum and source commit are recorded with the release; the normal JBang alias remains on the normal release. |
| Build disappears after leaving to install/sign in | Persistent start-page entries; `buildEntrySurvivesLeavingSetupAndRestarting` covers cancelling, restarting with the same home and returning to Build. |
| Extra summary and duplicate composer/send actions | Inline consent opens the existing composer; `onboardBuildReachesTheComposerWithoutExternalRegistration` preserves a draft and proves one Send starts one fixture session. |
| Guide ends before a successful reply | A real Full-mode conversation admitted the private bridge, called `analyser_context`, and completed an answer; the final screenshot shows the sent message and reply. |
| Authentication methods excluded by a new gate during revision | Removed that gate; `onboardingPreservesApiKeyAndHelperAuthenticationWithoutCallingItVerified` covers both key methods without claiming they were validated. |
| Late apply acknowledgement could reopen a cancelled Build flow | `cancellingBuildBeforeItsApplyAcknowledgementDoesNotReopenTheComposer` holds the completion, cancels, then delivers it. |

The independent follow-up review found no remaining runtime blocker. Its suggested late-acknowledgement coverage was
added. Generic setup retains its separate Full confirmation; the dedicated inline-consent event is rejected outside
the Build screen. A wrong-result witness deleted that origin guard: `inlineBuildConsentIsRefusedOutsideItsOwnScreen`
failed at the assertion that no profile had been applied. The guard was restored before passing validation. A second witness removed the `wizardOpen` guard; the late-acknowledgement test failed at “must not reopen the composer”, then passed after restoration.

## Validation

- Java 21 versioned `mvn -q package`: 4,483 total, 4,100 executed passes, zero failures/errors, 383 headless skips.
- Related display suites: 18 distinct tests passed with zero skips (`AiSetupWizardFrameTest` 6,
  `AiSetupClientFrameTest` 4, `QuickSwitchFrameTest` 2, `StarterProposalFrameTest` 5,
  `StarterProposalVisibleFrameTest` 1). The older first-run test initially expected the entries to disappear;
  its expectation was updated to the persistent-entry contract, and all six tests in that suite passed on rerun.
- Strict MkDocs build passed; the rendered guide has three stages and three screenshots. Documentation links,
  capture/asset checks and public-data checks are included in the passing package suite.
- Processor regeneration succeeded; no generated-source diff in this correction.
- Repository public-data sweep and whitespace checks passed. The preview jar has the stamped manifest version
  and contains no capture helper. Its checksum is supplied as `SHA256SUMS`.

The live first-message check used installed Claude Code 2.1.311 and the existing sign-in, an isolated analyser home,
Full mode with `acceptEdits`, the real process launcher and the real private bridge. External access stayed false and
personal MCP server count stayed zero. For capture privacy only, the helper added `--strict-mcp-config` and
`--setting-sources project`; it left Full built-in tools available. These are documented [Claude CLI
options](https://code.claude.com/docs/en/cli-reference). This is a controlled Full-mode check, not a test of every user's
hooks, plugins or connectors. Production Full-mode settings loading is unchanged.

The first live pass exposed an attempted terminal question widget. Full-session guidance now asks clarification
questions directly in the conversation. A second live pass completed the same example without that rejected call.
No raw Claude stream, login response, session id or account data is included here.

## Screenshots

`tools/OnboardingDocCapture.java` drives the actual Swing UI against the built jar under a fresh isolated `user.home`.
Without `--live`, it creates DEMO installation/authentication checks and captures the welcome and consent screens.
With `--live`, it uses the existing Claude login, follows Build → Enable → Send, and captures the actual completed
reply. The guide uses DEMO setup images and the inspected real reply; all are unmodified component captures.
Personal-path setup captures from the initial live run were discarded; live mode now skips setup images entirely. Every visible string in the published images was read. The last review’s Start/Send wording mismatch was corrected in the shared consent text and the DEMO consent image was regenerated.

To reproduce, compile the helper against the packaged jar, add its compiled class to a copy of the jar, and launch
that copy using the helper's main class. The bridge requires a single jar classpath. Use a fresh canonical isolated
`-Duser.home`, `-Djava.awt.headless=false`, a neutral working directory and the shared display lock. The arguments are
an output directory and optionally `--live`. Never add the capture class to a distributed jar. Regenerate the asset
inventory with `python3 tools/docs_assets_inventory.py` after updating images.

## Remaining before promotion to the normal release

A complete project created and built by a live Full-mode session still needs its authoring acceptance run, including
template acquisition, bundled skills and the first evidence check. Starter proposal/review UI is covered by fixture
suites. Cross-platform first-time installation and the full normal-release CI/acceptance gates remain open. The preview
does not claim those gates have passed. Publishing the guide to the main documentation site belongs with promotion;
the branch-rendered GitHub guide is available from another machine now.
