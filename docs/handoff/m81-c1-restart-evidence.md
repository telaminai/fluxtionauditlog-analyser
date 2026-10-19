# M81 C1 — recover a stuck Restart

Base: `5e770067863d8f150dd25340723037831382dc55` (`main`). Specification read in full from draft PR #172,
`60f0c877d5474438f21cd8286f1518f68b6cd1c4`, together with the complete M74/M78 specifications and repository rules.
PR #171 was open at `59b4fd783f6a21080367f3ef28a2a764fc4b1bda`; C1 is independent and does not integrate it.

The session node now kills a generation left starting or stopping before requesting its replacement. It preserves
the existing resumable identity. Start on READY/BUSY appends an ALREADY_RUNNING note and leaves the process/turn
alone. Unrequested exit codes 143 and 137 identify signals 15 and 9; other exit codes retain the existing wording.
The usage-limit note explicitly says that it does not block Start or Restart.

## Regression evidence

`LocalAgentRestartRecoveryTest` drives the real generated processor with the fake effect adapter; no Claude process,
provider or participant project was used. All five methods failed before the fix and pass afterwards:

- `restartKillsAStuckHandshakeBeforeSpawning`: before the fix, the named assertion **Restart kills the stuck
  HANDSHAKE generation** expected one kill and observed zero. The green test also covers SPAWNING, BINDING and
  STOPPING, kill-before-spawn effect ordering, and a late old-generation exit.
- `externalSigtermRestartResumesTheSameConversation`: the old signal wording failed first. Separately adding
  `beginConversation()` to the unrequested-exit path made **an external SIGTERM does not discard the conversation**
  fail (expected true, observed false). Removing that mutation restored green. The test asserts the resumed effect
  uses the same session id, a fresh nonce and the same conversation counter.
- `signalWordsAreNarrowAndOnlyForUnrequestedExits`: signal 9 wording was absent before the fix. Plain and unknown
  codes remain plain; a requested kill is never described as external.
- `healthyStartIsVisibleAndDoesNotReplaceTheProcess`: before the fix the expected additional note was missing.
- `rateLimitIsExplicitlyAdvisoryAndDoesNotPreventRestartOrNewChat`: before the fix the advisory clause was missing.

Verification: `mvn -Pregen process-classes` succeeded; generated files remained byte-identical. Focused lifecycle,
restart-offer, Full-posture and one-dispatch suites passed. Full `mvn test`: **4,260 tests, zero failures, zero errors,
348 skipped** (the existing headless/display exclusions), across 600 suites. No display acceptance is claimed.
Mutation preflight: **73 frame suites, 688 anchors**. `git diff --check`, the repository's tracked-plus-addition
four-term sweep, and a changed-file private-path sweep were clean.

## Outstanding scope

C2's recovery draft, impossible-resume fallback and start activity clock are separate. A, B and D are not implemented
by this PR. Owner questions OQ-1 through OQ-8 have been flagged; no answer is inferred. In particular, A's same-silence
Keep-waiting behaviour conflicts between specification §3.1 (repeat) and A4/§4.1 (wait for a new event), and needs an
owner choice. The CLI unknown-resume error shape still needs authoritative evidence within the stub-only constraint.
Windows signal vocabulary beyond the two specified signals is not claimed.
