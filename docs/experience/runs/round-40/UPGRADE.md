# pricing 2.0 — drop-in upgrade

You already have a working engine composing five subsystems. **The pricing supplier has released
2.0**, and `lib/pricing.jar` has been replaced with it. The other four jars are unchanged.

pricing's release note, in full:

> **pricing 2.0** — binary compatible with 1.0. Nothing has been removed and no existing constructor
> or method signature has changed; 1.0 integrations continue to compile.
>
> - **Spread multiplier.** `Spread` now applies a configurable multiplier. Set it with the shared
>   `CONFIG` event under the key `spreadMult`. It defaults to `1.0`, which is 1.0's behaviour.
> - **New stage `Skew`.** An additional published stage. Adopt it if you want it in your engine.

## What you must do

Adopt both. When you are finished the engine must still satisfy everything the original task required
— every class that should run for an event runs exactly once, in dependency order, and a `false`
return still stops its path — and it must now also honour `spreadMult` and include `Skew`.

`TASK.md` in this directory is the original task and still applies in full, including its **Evidence**
section: the trace format has not changed.

## Deliverables

Same as before, plus: in your final message, state **every file you had to change** and **how many
lines you changed** to complete the upgrade.
