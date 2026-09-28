# EB.F3 — recipient B's run

A second macOS machine (arm64), **JDK 21 Corretto** against machine A's **JDK 25**, a different hostname, a
different home and different absolute paths. Window measured 1440×900. No results exchanged with another
recipient before posting.

## The claim holds

| check | machine A | recipient B |
|---|---|---|
| identity | `sha256:486b0427…8e362` | **character-identical** |
| file sha256 | `0cad9c15…6b9fb` | **identical** |
| step 1 `records:row:5` | SHOWN · CURRENT · available | **same** |
| step 2 `graph:DEMO mid and spread` | SHOWN · CURRENT · available | **same** |
| step 3 `topology:verdict` | SHOWN · CURRENT · available | **same** |

All three screenshots inspected by eye; the targets were visible.

**Stronger than the test was designed to prove.** The two machines ran **different JDK major versions** — 25 on
A, 21 on B — and the manifest's bytes were still identical. The identity is stable across JDK versions, not only
across machines, which nobody had claimed and which is worth knowing.

Source navigation degraded exactly as predicted, and said so rather than failing quietly:

```
class not under an authorised root: com.acme.demo.node.Nodes$PriceListener; roots: []
```

## What the run found, and whose fault it was

B reported that "settings untouched" did not hold: the machine config's hash changed, while **the project
profile was byte-identical on both runs**.

**That was my prompt's error, not the product's.** I wrote "the recipient's own settings must be byte-identical".
EP-A7 says something narrower and correct: *"The recipient's own **project settings** are byte-identical …; any
machine-config write (recents) is known and listed."* B tested the claim I made, and my claim was wrong. The
product met the one in the spec: project profile unchanged, machine writes listed.

**The residual finding is real and is B's, not mine.** `docs/site/evidence-bundles/opening.md` told a recipient:

> *"The recent-files lists gain the working copy's log, graph and project. **Nothing else about your settings
> changes.**"*

It does. `logFile`, `graphmlFile` and `activeProjectPath` change too. The spec's own r3 status table lists them
correctly — "last-opened log and graph, three recents lists, the active project" — and `demo.md` says "recents
and last-opened paths only". Only the page a recipient actually reads was wrong. **Fixed in this commit**, with
the keys named.

B also caught a trap worth recording: a *minimal* own profile gains defaults and a nonce on first open, which
reads as a change. They spotted it, preserved the attempt, and repeated with initialised settings. Anyone
re-running this should initialise the recipient's profile first or they will chase that instead.

## Still open after this run

- **No Linux, and no case-sensitive filesystem.** Both machines were macOS and case-insensitive. Unpacking onto
  a case-sensitive filesystem remains untested, and it is the one difference this pair could not provide.
- **Playback used the REST verb, not native ◀ ▶ clicks.**
- **No system-wide write tracing** — writes were monitored within the isolated home only.
- **No capture on B.** Deliberate: capture determinism across machines is the next experiment, kept separate.

## Verdict

**EB.F3's transport claim is earned.** A bundle captured on one machine verifies to the same identity and plays
with all three bindings current on another, with a different JDK. The remaining gap is a case-sensitive
filesystem, which needs a Linux runner rather than another Mac.
