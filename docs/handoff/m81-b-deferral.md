# M81 B — explicit deferral, no feature shipping

The owner explicitly chose **defer option A** on 2026-10-19, after receiving the read-only assessment below.
Draft [PR #184](https://github.com/telaminai/fluxtionauditlog-analyser/pull/184) is closed **without merging or
deleting its branch**. No B production source is included in this documentation-only disposition. Existing Full
Bash permission behavior is preserved by excluding the candidate, not by claiming its code now asks everything.

## Preserved attempt and its limits

Published candidate `27c63b29d690b4437d2b03f0a474b075da9cb658` and its
[initial evidence](https://github.com/telaminai/fluxtionauditlog-analyser/blob/27c63b29d690b4437d2b03f0a474b075da9cb658/docs/handoff/m81-b-script-trust-evidence.md)
remain available. The isolated B worktree retains the subsequent dependency merge `1281c417` and all uncommitted
guarded-launch, scope, forced-ask, generated-source, consent and control work. None is pushed as a shipping fix.
Initial green tests did not establish the effective-context guarantee, and no final full/display/control gates
are claimed for that WIP. The [bounded investigation](m81-b-effective-context-investigation.md) records both its
primary evidence and the maintained forced-ask wrong-result witness. No receipt-based allow path was installed.

## Narrow absolute-path variant: assessed before building

The owner proposed allowing only one simple Bash invocation whose script token is the script's absolute real
path with a matching digest, refusing any input containing `run_in_background`, and asking everything else.
The read-only assessment still found no supported execution/foreground binding:

| Primary source | Documented behavior | Consequence for the proposed variant |
| --- | --- | --- |
| [Environment reference](https://code.claude.com/docs/en/env-vars), `CLAUDE_CODE_SHELL_PREFIX` | A configured executable wraps Bash's assembled shell invocation, including setup, receiving it as an argument. | **Inference:** the wrapper may omit delegation, replace execution or add effects although the command's script token and digest match. |
| [Hook reference: persisted environment](https://code.claude.com/docs/en/hooks#persist-environment-variables), `CLAUDE_ENV_FILE` | The file runs as shell code before each Bash command, not merely as inert key/value data. | **Inference:** an `exit`, `exec` or additional command can preempt or supplement the unchanged script. |
| [Environment precedence](https://code.claude.com/docs/en/env-vars#precedence) | Settings normally replace inherited values and can reapply them to a running session. | Host launch flags alone do not certify effective per-call settings. |
| [Bash timeout behavior](https://code.claude.com/docs/en/tools-reference#foreground-commands-that-move-to-the-background) | A foreground command reaching its timeout moves into the background unless background tasks are effectively disabled or bare mode applies. | Refusing the explicit input field does not suppress implicit backgrounding. |

An absolute path removes working-directory ambiguity in the initial filename; it does not neutralise a wrapper
or prelude, reset the script's own working directory, or lock the checked bytes through execution. The documented
shell state also includes startup aliases/functions/options. No assumption that these necessarily intercept an
absolute token is needed: the documented wrapper and prelude are sufficient counterexamples.

Hooks can replace input before execution, but this assessment **does not claim** that an input-rewriting hook
runs after the host permission callback. That ordering was not established. A final-input comparison could reject
changed input without resolving an unchanged-input wrapper/prelude case.

This is documentation-based analysis and explicitly labelled inference, **not a live exploit reproduction**.
No vendor binary, model or paid provider was run; no real assistant configuration or credentials were read.
The owner also named `JAVA_TOOL_OPTIONS`, `BASH_ENV` and `PATH`; this disposition does not assert their exact
current vendor pass-through/filtering without primary evidence. The verified wrapper/prelude evidence suffices.

## Identity versus build authority

An unchanged digest-matched wrapper may execute model-edited build files, plugins and tests. The original spec
already disclosed that identity was not effect and arguments were not sandboxed. The owner now chooses deferral
of that broader authority, rather than relabelling it as a newly introduced accidental contract breach.

Any future consent must honestly describe **letting the assistant run this project's build without asking,
including assistant edits**, and receive a separate security review. It is not this milestone's deferred narrow
script-identity feature. D's output offers proceed independently; they do not run builds or grant permissions.

## Verification of this disposition

This branch starts at safe A head `c393891e90f754e4e58f866bd6f90b056fe672fa`, which includes C and the explicitly
integrated working-folder prerequisite, but no B production ancestry. The owner subsequently merged C/A and that
prerequisite into main `5bb43b573e425f2b6041d95302caf0e62fed7ef7`. Both active branches integrated that exact main
by normal merge before further work; the disposition's against-main diff is now documentation-only. Documentation
gates and exact-head CI are reported in the disposition PR; historical B results are not reused as evidence that
this different source was tested.

Initial disposition `17f126c9` gates: full `mvn -o -q test` passed **608 suites, 4,288 tests, zero failures/errors,
352 headless display skips**; preflight found **76 frame suites, 689 anchors**; strict MkDocs passed.
An earlier sandboxed attempt encountered denied socket/process probes and was stopped; it is an environment
failure, not a regression witness or a green gate. The correctly permissioned fixture-only rerun is the result above.
No new runtime behavior or regression closure is claimed by this documentation-only change.

After integrating main `5bb43b57`, the updated-source full run passed **608 suites, 4,302 tests, zero
failures/errors, 352 headless skips**, with all 608 reports source-mapped and no orphan reports. Strict MkDocs
passed again; preflight found **76 frame suites, 695 anchors**. Exact updated-head CI is separate from these local gates.
