# M81 B — bounded effective-context investigation

**Historical investigation, not an approval or execution-binding proof.** The owner subsequently explicitly
deferred B; see the [final disposition](m81-b-deferral.md). Candidate PR #184 remains unmerged at published head
`27c63b29`. Its isolated WIP and the checks below are preserved, not included as production changes here.
The owner authorised investigating and implementing a session-local, Bash-only helper, with ordinary asking for
missing, suppressed, timed-out, stale, mismatched or unreliable observations. That did not authorise input
rewriting, an execution proxy, a managed-policy bypass or impersonating another host. None was added.

## Primary evidence and limits

Read on 2026-10-19:

- [Official hooks reference](https://code.claude.com/docs/en/hooks): matching handlers run in parallel; handlers run
  with the current directory and CLI environment; PreToolUse supplies tool input and tool-use id; other hooks can
  replace input; a command-hook timeout normally continues through permission handling. The common `cwd` field is
  the directory when the hook is invoked, not a reservation of the later executor's directory.
- [Official environment precedence](https://code.claude.com/docs/en/env-vars#precedence): settings can replace launch
  variables and re-apply them while a session runs. The Desktop/self-hosted-runner exception is not this analyser.
- [Official settings reference](https://code.claude.com/docs/en/settings-reference#env): environment is reapplied
  on settings changes. The hooks reference separately states that server-managed and OS-managed updates need not
  run ConfigChange hooks; absence of that event does not establish unchanged environment.
- [Official worktree reference](https://code.claude.com/docs/en/worktrees): a session or subagent can acquire a
  different execution root. A root-script identity cannot simply be borrowed by those contexts.
- [Official SDK control handling](https://github.com/anthropics/claude-agent-sdk-python/blob/main/src/claude_agent_sdk/_internal/query.py)
  and [protocol types](https://github.com/anthropics/claude-agent-sdk-python/blob/main/src/claude_agent_sdk/types.py):
  hook callbacks and permission callbacks are separate requests. The permission context exposes tool-use and
  subagent identity, not an effective-environment reservation held through execution.
- Static inspection of the vendor-published `@anthropic-ai/claude-code-darwin-arm64` **2.1.282** artifact
  ([public package](https://www.npmjs.com/package/@anthropic-ai/claude-code-darwin-arm64/v/2.1.282)), archive SHA-256
  `738ce2deba0060eef3cdc55b7aaa6c4c45ecaacdd371bc81ac9088d638204efe`: settings environment is assigned into the
  process environment, and the Bash root-reset check reads the environment. Its permission-request schema also
  exposes `matched_ask_rule`, `classifier_approvable` and `requires_user_interaction`; these must not be ignored when
  considering automatic script approval.

No vendor binary was executed. No live CLI/model, paid provider, participant project, real assistant configuration
or credentials were read. Static source and documented orderings establish limits; they are not a live execution probe.

## What a helper could establish

| Observation or check | Establishes | Does not establish |
| --- | --- | --- |
| Invocation cwd, its real path and the two inherited booleans | The helper's own invocation context | The later Bash executor's unchanged context |
| Exact tool-use id and canonical input digest | Correlation with the permission request, if the observation is authentic | Unchanged settings or executor state |
| Generation/ticket scope, bounded lifetime and one-use consumption | Refusal of stale, replayed or cross-conversation receipts | Authenticity or execution binding |
| Final permission-input comparison after hooks | A changed input does not reuse the earlier input's receipt | Side effects or environment changes that leave input identical |
| Fresh host-side real-path/content read | Script identity at the approval read | A file descriptor or execution lock held by the CLI |
| An authenticated local transport | Who possesses that transport capability | That the report came from the genuine helper at the relevant execution point |

Checking both stdin `cwd` and the helper's actual real cwd would be necessary: the CLI may choose a fallback
directory if its current one was removed. A mismatch, unavailable real path, symlink escape, unsupported helper
identity or unverified receipt provenance must retain asking. A secret shared with the Full child is not, by
itself, proof against another hook or same-user process invoking or imitating that helper. This investigation
does not claim a demonstrated exploit or unforgeable transport; no receipt endpoint or self-report acceptance
path was installed.

## Concrete ordering counterexamples

These are counterexamples allowed by the documented mechanics, **not claimed live-CLI reproductions**.

1. An honest PreToolUse helper reports the expected root and both booleans enabled for a particular tool-use/input.
2. Another matching hook is still running in parallel. Before all hooks finish, a settings update replaces a guard
   variable. Such an update can arrive without a ConfigChange hook. The command and tool-use id need not change.
3. The ordinary permission request arrives with exactly the same input and id. The earlier receipt still matches,
   but does not establish the CLI's effective guard values at this permission boundary, let alone at execution.

A final-input digest would correctly reject a neighbouring case where another PreToolUse hook replaces the
command. It does not close the unchanged-input settings case above. Nor can a short receipt lifetime prove that
no change occurred during that interval. This is a gap **before approval**, distinct from the already disclosed
residual risk of a file changing after a fresh approval read. No claim is made that hooks rewrite input after
the host permission callback; that ordering was not established.

Suppressed/disabled hooks and hook timeouts are simpler: no usable observation is available, so ordinary asking
must continue. An unsolicited, fabricated, duplicate, stale or cross-scope report likewise cannot be promoted
to authority merely because its boolean fields claim success. Worktree and subagent scope require their own
binding; neither can borrow the main launch's root identity.

## Maintained failclosed witness in preserved B WIP

`LocalAgentScriptTrustReplayTest.aRuleForcedOrManualAskCannotBeReclassifiedAsScriptTrust` sends the actual adapter
synthetic permission frames, through the real generated permission node. Before the correction, a frame carrying
`matched_ask_rule` silently pre-approved: the named assertion **a rule-forced or manual ask must not be replaced by
script pre-approval** failed, expected one dialog, observed zero. The isolated direct-JUnit result was one named
assertion failure, no errors/skips. After parsing a bounded manual-required boolean and retaining that observation
for both classification and the separate check, the maintained test passed for rule-forced, user-interaction,
explicitly non-classifier-approvable and malformed-flag neighbours: **one test, zero failures/errors/skips**.

The WIP registry entry `m81-script-trust-rule-forced-ask` removes that guard for the same named method. Its preflight
found **76 frame suites, 703 anchors**. This probe used isolated `javac`/direct JUnit against cached dependencies;
no Maven or display window was taken from concurrent parent work. No final full Maven run or fast-registry
execution for this WIP source is claimed. The registered mutation was also applied directly: removing the guard
produced the same named one-assertion RED, then source SHA-256 equality confirmed byte-identical restoration and
the method returned GREEN. Final isolated direct-JUnit neighbours passed **37 tests, zero failures/errors/skips**;
registry self-tests passed **5 tests** and strict MkDocs passed. These checks do not replace the unrun final gates.

## Conclusion

The supported helper mechanics found here can report a bounded **snapshot**, not the requested binding from the
checked context to actual execution. No production helper or receipt-based pre-approval should be enabled on this
evidence. The launch-only WIP candidate still has a script-preapproval path; it is not a completed failclosed
effective-context implementation and must not ship. Keeping a PR draft does not change its runtime behavior.

The owner subsequently explicitly chose deferral, also after a read-only absolute-path assessment. Rewriting tool
input, substituting an execution proxy, freezing settings or impersonating a privileged host would change the
contract and needs a separate decision. More launch flags or a hook's self-report do not solve the proof obligation.
The [final disposition](m81-b-deferral.md) records the unchanged-script/build-input distinction and the separate
security-review boundary for future broader consent. D proceeds independently, excluding B production changes.
