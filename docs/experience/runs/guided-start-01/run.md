# Guided start — first real drive against a running analyser (2026-08-30)

**What this is and is NOT.** The tour's three beats were driven against a real `analyser --rest` on the
shipped demo set. **It is not the held-out evidence D-G5 asks for**: no fresh model ran it, I drove it
myself, and the MCP transport was substituted with the REST socket. It tests the tour's *substance* — the
verbs, the arguments, the demo data and whether each beat has anything to show — and nothing about how a
cold client behaves.

## Environment

| | |
|---|---|
| Build | `target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar`, this tree |
| Transport | `--rest`, endpoint `~/.fluxtion-analyser/rest-endpoint`, envelope `{"action":…, "params":{…}}` |
| Data | the demo set the jar ships, at `~/.fluxtion-analyser/demo/` |
| Display | present; the app opened its window |
| Ended | the owner closed the app mid-run — **not** a crash, and not the harness reaping it, which is what I first assumed |

## Result: beats 1–3 all work. Two real defects in the skill I wrote.

### G1 · BLOCKING for the tour — beat 2 shows NOTHING on the log the skill chose

The skill sent the tutor to `demo-quote-audit-traced.yaml` for *"what never ran"*. Measured across all
three demo logs against the same graph:

| log | declared | covered | uncovered |
|---|---|---|---|
| `demo-quote-audit-traced.yaml` | 6 | 6 | **0** |
| `demo-quote-audit.yaml` | 6 | 5 | **1** |
| `demo-quote-series.yaml` | 6 | 5 | **1** |

**The most distinctive beat in the tour — the one unlike a log viewer — would have reported zero.** I
picked the traced log for soundness (absence is only proof when tracing is on) and did not check that it
had anything absent to show. Exactly the class of error the loop keeps catching: a decision that is
correct in principle and never run.

**Fixed, and the fix is better than the original.** Beat 2 now opens the *untraced* log where there is one
uncovered node, then reads the analyser's own note aloud — it states this is *"never logged"*, not proven
*"never ran"*. So the beat demonstrates the denominator **and** the refusal, which is a stronger
demonstration than a bare number. The traced log becomes an optional contrast: same graph, coverage 1.0,
a different thing provable — which is the build-time tracing decision made visible.

### G2 · `flag` takes an ARRAY, and the singular is refused

```
flag {"recordIndex": 5}   → {"ok":false,"error":"flag needs byteOffsets[] or recordIndexes[]"}
```

The skill's beat-3 sketch showed `analyser_flag {...}` with no arguments, so an agent would guess the
singular — which fails, in front of the person being shown. The real shapes are now written out.
*Verified: the singular is refused. The plural form is taken from the error message and was not itself
executed before the app closed.*

### G3 · There is no verb that lists graphable keys

`series` needs an expression, and `context` does not report what keys the open log carries. My skill said
*"look at what keys exist rather than assuming"* without saying how — advice that cannot be followed. Now
it says to `read` a record first, and names one that works on the demo (`riskMonitor.liveOrders`, 160
points, a crossing with `recordIndex` and `byteOffset`).

### G4 · The demo set cannot be installed by the agent

`DemoAssets.install()` runs only from a Start-page demo action. I called it directly as a **rig step** and
recorded it as such. So the tour has a second human pause (after MCP registration), and the skill now says
to stop and ask rather than implying the agent can fix it.

Worth a decision later: an `open` of a demo path could install on demand. Not changed speculatively.

### G5 · A returning analyser restores its previous session

The first `context` reported a log and project from an earlier run. So D-G7's *"is anything open?"* branch
will usually fire on **restored state**, not on work the person just did. Treating it as theirs is still
right — it is theirs — but "nothing is open" is the rare case for a returning user, not the default.

## One preliminary read I retracted before recording it

The first `open` echoed `appliesToOpenLog: false` while `context` said `applies: true`, which looked like
a real inconsistency. Re-running against all three logs showed the echo was describing the graph against
the **previous session's** still-open log. No defect. It did produce a genuine instruction — read the
pairing from `context`, not from the echo — which is now in the skill.

## Still unverified

The held-out run: a fresh, context-free client following the docs-site prompt from installation, through
the human MCP pause, to all three beats. Nothing here substitutes for it.
