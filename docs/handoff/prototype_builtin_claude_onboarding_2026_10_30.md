# Built-in Claude Code onboarding preview

Preview channel: `v1.41.0-onboarding.1`, from `prototype/builtin-claude-onboarding`, based on main at `6d51aa16`.
The normal release alias and public Pages deployment are unchanged.

## User journey

The [illustrated quickstart](../site/start-with-claude.md) has three stages:

1. Run its pinned JBang command and choose **Build with Claude Code**.
2. Read the installation/authentication checks and Full-mode consent, then choose **Enable Claude Code**.
3. Describe the application in the existing assistant composer and press **Send** once. Claude replies there.

The guide offers JBang's zero-install launcher, which downloads JBang and Java as needed; its checks guide Claude
Code installation and authentication before a conversation. JBang may ask to trust the download; an existing project may require folder trust. Creating a starter
still requires review and a destination folder.
The preview is an entry point to a conversation, not an unattended project build.

Build, Demo and Connect remain on the start page after dismissal or restart. A returning Full-mode user goes directly
to the composer without overwriting their permission mode or draft. A new builder sees the complete Full consent on
the checks screen, with Cancel as the default. Setup never starts Claude or sends a question. API-key and key-helper
authentication retain the existing informational, unverified-key disclosure.

The existing session processor owns the decisions; the UI renders the snapshot and performs requested effects.
The generated processor is unchanged from main in this PR. Regeneration with compiler 1.0.83 is isolated in a
[separate draft PR #214](https://github.com/telaminai/fluxtionauditlog-analyser/pull/214) with its own compatibility evidence and revert path. The route neither registers an external
client nor enables external access. Existing external-access settings are preserved.

## Review dispositions

| Finding | Correction and regression protection |
|---|---|
| Public command launches a different UI | The guide pins the separate preview release asset. Its checksum and source commit are recorded with the release; the normal JBang alias remains on the normal release. |
| Build disappears after leaving to install/sign in | Persistent start-page entries; `buildEntrySurvivesLeavingSetupAndRestarting` covers cancelling, restarting with the same home and returning to Build. |
| Extra summary and duplicate composer/send actions | Inline consent opens the existing composer; `onboardBuildReachesTheComposerWithoutExternalRegistration` preserves a draft and proves one Send starts one fixture session. |
| Guide ends before a successful reply | A real Full-mode conversation admitted the private bridge, called `analyser_context`, and completed an answer; the final screenshot shows the sent message and reply. |
| Authentication methods excluded by a new gate during revision | Removed that gate; `onboardingPreservesApiKeyAndHelperAuthenticationWithoutCallingItVerified` covers both key methods without claiming they were validated. |
| Late apply acknowledgement could reopen a cancelled Build flow | `cancellingBuildBeforeItsApplyAcknowledgementDoesNotReopenTheComposer` holds the completion, cancels, then delivers it. |

The earlier independent follow-up review found no remaining onboarding runtime blocker. Its suggested late-acknowledgement coverage was
added. Generic setup retains its separate Full confirmation; the dedicated inline-consent event is rejected outside
the Build screen. A wrong-result witness deleted that origin guard: `inlineBuildConsentIsRefusedOutsideItsOwnScreen`
failed at the assertion that no profile had been applied. The guard was restored before passing validation. A second witness removed the `wizardOpen` guard; the late-acknowledgement test failed at “must not reopen the composer”, then passed after restoration.

## Validation before the review split

- Java 21 versioned `mvn -q package`: 4,483 total, 4,100 executed passes, zero failures/errors, 383 headless skips.
- Related display suites: 18 distinct tests passed with zero skips (`AiSetupWizardFrameTest` 6,
  `AiSetupClientFrameTest` 4, `QuickSwitchFrameTest` 2, `StarterProposalFrameTest` 5,
  `StarterProposalVisibleFrameTest` 1). The older first-run test initially expected the entries to disappear;
  its expectation was updated to the persistent-entry contract, and all six tests in that suite passed on rerun.
- Strict MkDocs build passed; the rendered guide has three stages and three screenshots. Documentation links,
  capture/asset checks and public-data checks are included in the passing package suite.
- The published preview included 1.0.83 regeneration. The current PR removes that regeneration; see the split below.
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

A documentation review follow-up keeps the three stages and explains the existing install/sign-in controls,
`acceptEdits` versus `default`, access beyond the working folder, usage on the person's own Claude/API account,
persistent settings and the Off switch. It identifies the initial temporary working folder and the later project
folder, and retains the review, restart and Send steps after starter creation. No automatic build is promised.
The Claude quickstart and the application's legacy installation-help URL were checked on 2026-10-30: both returned
HTTP 200, with the latter redirecting to the current setup page. This follow-up changes documentation only;
the published preview binary remains built from `b355d8dd`. Java 21 `mvn -q test` passed again with 4,100 executed
tests, 383 headless skips and zero failures/errors; strict MkDocs, the public-data sweep and whitespace checks passed.

At normal-release promotion, replace the quickstart's pinned preview URL with
`jbang --fresh analyser@telaminai/fluxtionauditlog-analyser` only after the catalog's normal-release jar contains the
illustrated flow. Verify its version and first-run path, recheck external setup links, and remove the preview notice
with that change. Update the target in all three launch variants together, retaining Java 21.
Keep the pinned commands while this remains a prerelease.

A complete project created and built by a live Full-mode session still needs its authoring acceptance run, including
template acquisition, bundled skills and the first evidence check. Starter proposal/review UI is covered by fixture
suites. Cross-platform first-time installation and the full normal-release CI/acceptance gates remain open. The preview
does not claim those gates have passed. Publishing the guide to the main documentation site belongs with promotion;
the branch-rendered GitHub guide is available from another machine now.


## Review follow-up: isolated processor and cancellation during checks

The generated Java and resource files are restored byte-for-byte to main (`6d51aa16`).
The separate regeneration branch is `fix/session-processor-1.0.83` ([PR #214](https://github.com/telaminai/fluxtionauditlog-analyser/pull/214)), also based on main.
The existing immutable `v1.41.0-onboarding.1` jar remains built from `b355d8dd` and
therefore includes **both** the onboarding and regeneration changes. Its live reply
and screenshots are preview evidence, not an execution of the newly isolated PR tree.
Do not infer that the preview resolves the regeneration's audit-close issue.

`cancelDuringChecksThenReturnThroughBuildPreservesDraftAndSettings` holds the real
background auth-probe boundary, cancels while it is pending, reopens Build, lets the
new check succeed, then delivers the old negative result. The displayed result stays
signed in, the old result is recorded as stale, the draft/model/permission settings
survive, and only explicit consent changes the permission mode. No AI session starts.

Wrong-result witness: temporarily remove the auth probe identity guard. The old negative
result replaces the new result with `NOT_SIGNED_IN`, and this test fails. The guard
was restored before final validation. Initial runs with only an `argLine` override
were skipped by Maven; they are excluded from desktop evidence. The verified display
command uses `-Djava.awt.headless=false`, and report XML must show zero skips.

### Consent scope and future changes

Saved Full-mode permission has **no global consent version today**. Returning users
keep their existing choice; editing the global consent text alone will not re-prompt.
Folder trust separately fingerprints the working folder's trust inputs and rechecks
changed inputs. These mechanisms must not be described as equivalent.

This PR uses the existing Full scope. Before any future material expansion of access,
add a persisted consent version and require a fresh explicit click for older or missing
versions before launching Full. Ordinary wording fixes should not reset consent.
Include migration, cancellation, returning-user and no-launch-before-consent tests in
that change. This is an open release gate for the expansion, not implemented versioning.

The current inline consent and illustrated guide already explain access beyond the
working folder and `acceptEdits`. The guide explains the temporary working folder,
project-folder trust, account usage charges, persistent choice and Off, and the
review/open/restart/Send handoff after starter creation. Zero-install commands remove the
separate JBang prerequisite. The catalog alias replaces the pinned jar only at normal release.

### Desktop coverage map

The earlier 383 headless skips were display-dependent tests across the application;
they were not 383 failed onboarding tests. The previous 18-test display selection
covered these onboarding routes; the new cancellation test and existing folder/tool
consent suites extend it:

| Display suite | What it checks |
|---|---|
| `AiSetupWizardFrameTest` | Build inline Full consent with Cancel default, one Send, draft preservation; cancel/restart; cancel during checks and stale result; generic setup and responsive layouts. |
| `AiSetupClientFrameTest` | Existing external-client path, registration and external-access confirmation/cancellation. |
| `QuickSwitchFrameTest` | Separate Full confirmation, Cancel default and unchanged settings on cancel; profile settings and Off. |
| `SafeChoiceFrameTest` | Real Full folder-trust, Safe folder and tool-approval dialogs share safe defaults and button order. |
| `LocalAgentPermissionFrameTest` | Real tool-approval dialog default/keyboard behavior, delayed Allow, missing wording, deny/allow/Stop in the frame. |
| `StarterProposalFrameTest`, `StarterProposalVisibleFrameTest` | Starter review, safe destination validation and visible proposal flow. |

State-machine witnesses for origin guards and late acknowledgements remain headless
by design, alongside actual desktop consent checks. This coverage is not a claim that
all skipped desktop tests have run, or that a live first project has been built.

Post-split validation (Java 21, generated processor restored to main):

- `mvn -q test`: 4,484 total; 4,100 executed passes, 384 display-dependent skips,
  zero failures/errors. The added display test accounts for the extra headless skip.
- Display command: `mvn -q -Djava.awt.headless=false -Dtest=AiSetupWizardFrameTest,AiSetupClientFrameTest,QuickSwitchFrameTest,StarterProposalFrameTest,StarterProposalVisibleFrameTest,SafeChoiceFrameTest,LocalAgentPermissionFrameTest test` under the shared display lock and isolated fixture homes.
  **25 tests passed, zero failures/errors/skips**: 7 + 4 + 2 + 5 + 1 + 2 + 4 respectively.
  The XML reports, not only Maven's exit code, were checked for skips.
- The stale-probe wrong-result witness failed with `NOT_SIGNED_IN`; restored checks pass.
- Strict MkDocs, the public-data sweep and whitespace checks passed.
- Independent read-only agent review found no further defect in the split or new test;
  the regeneration audit-close blocker and remaining live/cross-platform/release gates
  stay open. Global consent versioning remains the future-expansion gate above.


## Zero-install entry point (2026-10-30)

The quickstart now starts with the documented Bash or PowerShell bootstrap command,
with plain JBang alongside it for existing installations or a separately managed install.
All three commands retain `--java 21` and the pinned preview URL. The normal-release
catalog already declares `"java": "21+"`; it still points at the normal release, so
switching this preview guide to the short alias would open the wrong flow.

Sources checked: [JBang zero install](https://www.jbang.dev/documentation/jbang/latest/installation.html#zero-install-curlbash-and-powershell),
[download page](https://www.jbang.dev/download/), and the launchers reached through
[sh.jbang.dev](https://sh.jbang.dev) and [ps.jbang.dev](https://ps.jbang.dev).
The guide explains download trust, caching under the home directory and optional
permanent command setup. No PATH installation is performed by the documented launch.

Measured smoke: the Bash pipeline on macOS, using a fresh temporary `JBANG_DIR` and
an existing Java 21 installation, downloaded JBang **0.143.0**, displayed its trust
prompt, accepted **Trust once**, and printed the preview's `--help` output identifying
**1.41.0-onboarding.1**. The application help path returns before configuration or
Swing initialization. This checks bootstrap, argument forwarding, trust interaction
and artifact identity; it is **not** a no-Java clean-machine or first-window test.
PowerShell syntax was checked against the official launcher but was not executed.

The bootstrap currently defaults to Java 17 if no JDK exists; application selection
with `--java 21` can require another JDK download. Include that in real first-run timing.
The guide makes no under-five-minutes claim. Before normal release, complete this matrix
on graphical machines with no Java/JBang installation or caches, recording OS/architecture,
network conditions, trust interaction, elapsed time to first window and to first Claude reply:

| Platform | Clean first-window / first-reply timing |
|---|---|
| macOS | Open; the existing-Java CLI smoke above does not satisfy this. |
| Linux desktop | Open. |
| Windows PowerShell; Windows Bash where supported | Open. |

Validation: Java 21 `mvn -q test` passed with 4,100 executed tests, 384 display-dependent
skips and zero failures/errors. Strict MkDocs passed; rendered HTML retains three stages
and all three launcher blocks, with the preview target, Java option and PowerShell
subexpression intact. Public-data and whitespace checks passed. This is documentation
only; the previous 25-test desktop result is not presented as a new zero-install run.
