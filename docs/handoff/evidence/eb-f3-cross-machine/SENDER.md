# EB.F3 — the bundle, and what machine A saw

EP-A6 has only ever been run on one machine with two homes. Two homes share a filesystem, a clock, a JDK, a
hostname and a path layout, which is most of what could break. This is the artefact for running it across three.

**DEMO data only.** The log and graph are the analyser's own demo fixtures; the manifest was swept for real
names and is clean.

## The bundle

| | |
|---|---|
| file | `demo-cross-machine.fexp`, **19,543 bytes** |
| identity | `sha256:486b0427ffea0f261183dd40cfd027da835d5ac69057b817b0b8e9c28398e362` |
| sha256 of the file itself | `0cad9c15be0950722c4cdc1ac96c38a2c83a48b6362e6ca35095e3ca5006b9fb` |
| members | `manifest.json`, `graph/demo-quote-processor.graphml`, `log/demo-quote-series.yaml`, `notes/NOTES.md`, `profile/project.fluxtion-settings` |

It holds a walk, **DEMO cross-machine walk**, with one target of each kind, because each is bound differently:

1. **a record target** — `records:row:5`, bound by that record's digest;
2. **a chart target** — `graph:DEMO mid and spread`, bound by the run basis (file digests plus record count);
3. **a topology target** — `topology:verdict`, bound by the graph digest.

## What machine A saw

`--verify`:

```
identity: sha256:486b0427ffea0f261183dd40cfd027da835d5ac69057b817b0b8e9c28398e362
verified: 4 members, each matching the manifest's sha256 and size
limit: unsigned: verification detects a changed member; it does not authenticate the sender
limit: no replay: this bundle shows an investigation; it does not reproduce or fix it
```

The walk, played on the sender before capture — all three steps `SHOWN`, all three targets `CURRENT`:

```
step 1 SHOWN [('records:row:5',              'CURRENT', True)]
step 2 SHOWN [('graph:DEMO mid and spread',  'CURRENT', True)]
step 3 SHOWN [('topology:verdict',           'CURRENT', True)]
```

Pairing: *the graph declares all 5 node(s) this log writes*. Log: 726 records, generation 1.

## Machine A

| | |
|---|---|
| OS | `Darwin 25.6.0` (macOS), **case-insensitive** filesystem |
| Java | `25.0.2 LTS` |
| hostname | a local `.local` name |
| window | 1440×900 — see the note below |

**A note on the window, and why it is in this record.** My first attempt captured with a default window and the
chart step came back `CURRENT` but **not available**: *"no room at 192×268 px — widen the window"*. That is
EB.F1, accepted by the owner. It matters here because **if a recipient's window is small, step 2 will fail for
that reason and not because anything about the bundle is wrong.** Set 1440×900 or larger before judging step 2.

## What the recipients are testing

Not "does it open" — whether the three things that make a bundle portable survive a different machine:

- the **identity** must be character-identical on every machine, or the manifest's bytes are not stable;
- all three **targets must be `CURRENT`** on a machine that has never seen this log;
- the recipient's **own settings must be byte-identical** afterwards.

And one thing to record rather than assert: **what a recipient cannot do.** They have none of the sender's
source roots, so source navigation will be dead. That is expected; write down what is degraded, so the docs can
say it rather than a demo discovering it.
