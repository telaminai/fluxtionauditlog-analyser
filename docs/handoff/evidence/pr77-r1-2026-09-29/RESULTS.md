# PR #77 R1 implementation evidence — 2026-09-29

Starting PR head: `9a8ffbde37637d310e47f40b64afe5ba84807599`. Isolated worktree:
`/private/tmp/pr77-r1`. JDK 21. No paid provider was contacted: frame cases use the local
`FakeProvider` and DEMO fixtures. The independent review branch and its evidence were left intact.

## Before / wrong result

The reviewer's `WorkspaceAttributionProbe` on the starting head reported:

```
ViewFilterChanged during provider request: REQUESTING
Independent open while assistant open held: RUNNING_ACTION
Next action requested: 11
Frozen: false
Basis adopted: no project · log DEMO-persons-log.yaml (#1) · no graph
```

That is the wrong result: the old turn adopted the person's log and asked for its next action.
With the corrected graph, the same probe stops at the first counterexample:
`ViewFilterChanged during provider request: SUPERSEDED`. Its later `getLast()` throws because no
assistant action was requested. The added regressions independently drive the competing-open
sequence without the filter change.

## Correction and checks

The assistant action's ticket and action id now travel through the shared `open` dispatcher,
the request, pending reader and final frame application. The session node owns the decision:
another client's open supersedes immediately; its own open waits until the accepted log,
source graph and reset view have been applied before advancing. Failure and cancellation
retire the allowance. The same node includes the investigation filter in the turn's basis,
allowing its own filter changes and superseding external ones.

`AssistantLoopTest` asserts the own-open barrier, a competing person's request, refusal of
late results, reader/application failure, cancellation, owned and external filter changes,
and an external graph fact. `AssistantScopeRaceFrameTest` drives the real frame, dispatcher,
delayed reader and loopback provider: own open → chart, competing person open → no chart,
and held provider reply → person changes filter → no chart. These assert the wrong chart
is absent, not just that a fact was posted. The new class is registered in both CI display lists.

The first attempt to run the pre-existing own-open test failed because it carried no action
origin; it was changed to drive the real causal contract. One existing mutation control
(`oa-workspace-supersedes`) initially survived: its old fixture's open request was already
stopped by the new request guard. Its named test was replaced with an external graph-fact
witness, which exercises the fallback basis guard directly. Both failed attempts are
retained here; they were not counted as passes.

## Local gates

- Full headless Maven suite: **2950 tests / 0 failures / 0 errors / 197 skips** in **399
  source-mapped reports, 0 orphans**. The skipped cases require a display. An initial sandboxed
  run had 36 loopback socket permission errors; the identical command outside the sandbox passed.
- Seven relevant display classes, sequentially under the shared display lock: **38 / 0 / 0 / 0**.
  This includes all three new frame races, assistant live/host/native, asynchronous workspace,
  Java-source and source-freshness tests.
- Preflight: **39** registered frame suites, **519** mutation anchors.
- `tools/test_project_chart_review.py`: **5 passed**.
- Targeted fast mutation gate: **16 requested, 16 caught** at their named assertions, with
  byte-identical source/class restoration and restored-green runs. This includes four new R1
  controls, the three existing OA ownership/ticket controls, all seven review-added R2–R5
  controls, plus the key-error and EDT guards. The output is `/private/tmp/pr77-r1-mutations.json`.
- Generated Java copies are byte-identical. Strict MkDocs, `git diff --check`, and the tracked
  public-data sweep passed (no output beyond the sweep command's expected empty result).

Session processor and GraphML were regenerated with the owner's separate authorisation.
An initial generator bootstrap compilation failed; a second attempt hit the sandbox's DNS
restriction; the successful regeneration ran outside the sandbox. No generated source was
hand-edited. The first generation exposed an `@OnTrigger` ordering error in these new handlers;
the handlers were made direct event handlers and regenerated again before testing.

Exact-head CI and the PR comment are recorded separately after push.
