# The native-ready starter template — what the playground must ship for 1.6 ns (Design Spec)

_Status: **DRAFT v1**, 2026-09-07 · Owner: greg.higgins · Milestone **M51**_

_Companion to [tracker.md](tracker.md), [spec-template-from-analyser.md](spec-template-from-analyser.md)
(M19.5 — the analyser fetches a template by catalogue id),
[spec-onboarding-example.md](spec-onboarding-example.md) (the bundle contract) and
[`fluxtion-performance-configuration.md`](../proposals/fluxtion-performance-configuration.md) (M50/W9 —
every figure quoted here comes from there, and from `../experience/runs/round-58…60/NOTES.md`)._

_Raised by the owner, 2026-09-07: **"We have templates the web playground hosts that the analyser
downloads — we want the template to be the best one for native use."**_

---

## A · The gap, read live

The catalogue at `https://fluxtion-playground.dev/starter-templates/index.json` (`catalogue: 1`,
14 templates) already advertises one:

```
fluxtion  aot  fluxtion-aot.starter.json  "Fluxtion AOT (native-ready)"
```

Its full contents, read live on 2026-09-07:

```json
{ "build": "maven", "javaRelease": 21, "basePackage": "com.example.myapp",
  "type": { "fluxtion": true, "mongoose": false },
  "fluxtion": { "processorName": "MyProcessor", "nodeClassName": "RootNode", "triggerClasses": [],
                "definition": "builder", "builderStyle": "imperative", "compileMode": "aot",
                "host": "bare", "events": [ { "name": "PriceUpdate", "kind": "class", "fields": [] } ],
                "auditLogging": true, "messageSink": true, "addTestShell": true } }
```

**`compileMode: "aot"` is the only thing in it that concerns native, and AOT generation is not native
readiness.** Of the eight settings that decide whether a native image reaches 1.6 ns/event, this
template carries none, and one of them (`auditLogging: true`) is the single most expensive default
there is. A user who picks the template named *native-ready*, builds a native image and measures it
gets somewhere between **5.5 and 29 ns/event** — 3.5× to 18× off — and **nothing tells them.** The
program is correct; it is just slow, and every one of the misses is silent.

That is the gap. The name is a promise the artifact does not keep.

## B · What "best for native" is, item by item

Each row is measured on a real generated processor; the *cost of omitting* column is what the user
pays for the template not carrying it.

| # | What the template must carry | Cost of omitting | Where it lives |
|---|---|---|---|
| B1 | `config.performanceProfile(LOWEST_LATENCY)` — drops framework auditors, dirty filtering, node registration | up to **18×** (29.08 → 5.03 for the clock alone; 5.07 → 1.57 for node registration) | generated builder source |
| B2 | `@OnTrigger(failBuildIfMissingBooleanReturn = false)` and the same on `@OnEventHandler` — **void triggers on every node** | dirty flags and guards on every dispatch; the profile cannot set it for you | the template's node source |
| B3 | `generateReachabilityMetadata = true`, so the compiler emits `META-INF/native-image/…/native-image.properties` carrying `-H:PriorityForceInline=<Processor>.*` | **3.5×** (1.57 → 5.56), silent | generated builder source |
| B4 | the loop shape: processor constructed **inside** the method that loops, never escaping it, and **nothing between the constructor and the loop** | 3.5–4×; this is what defeats escape analysis | the template's `Main` |
| B5 | a `ClockStrategy` supplied before entering that method | **5.8×** (29.08 → 5.03) if the Clock auditor is kept and reads the system clock per event | the template's `Main` |
| B6 | Maven `instrumented` and `native` profiles wired to `native-maven-plugin` | no PGO at all — and native without PGO is **slower than every JIT measured** (6.54 vs 5.47) | `pom.xml` |
| B7 | a **collect-until-it-lands script**, and the profile it produces committed under `src/pgo/` | a freshly collected profile lands only some of the time; a kept one reproduces every time | `tools/collect-pgo.sh`, `src/pgo/` |
| B8 | a benchmark and a threshold, run in the same job that builds | the difference between a landing build and a missing one is 3.5× and **invisible** — same image size, same log, no warning | `Bench.java`, the script's exit code |

**B7 is the item that did not exist before this month** and it is the one a template is uniquely
placed to deliver. Round 60 established that the PGO profile decides the mode and that the compiler
reproduces it: four rebuilds from a landing profile gave 1.60/1.66/1.68/1.67, three from a missing one
gave 5.71/5.63/5.61. What varies is *collection*. So the template's job is not to hand the user a fast
binary — it is to hand them **the loop that finds a good profile, and the place to keep it.**

## C · The tension this spec has to resolve, not hide

**B1 says drop the auditors. The analyser exists to read what the auditors emit.** The same catalogue
serves both: `analyser-bundle.starter.json` feeds the analyser, `fluxtion-aot.starter.json` claims
native readiness, and a naive "native template" would generate a processor that produces no audit log
at all — which would be a worse product, not a faster one.

**Resolution: one source tree, two named build shapes, and the default keeps the log.**

| shape | configuration | ns/event | what you get |
|---|---|---|---|
| **`audited`** (default) | `performanceProfile(AUDITED)` + `addAuditedEventLog(LogLevel.INFO)` | ~5.2 | the audit log the analyser reads, **without** the 208 bytes/event the naive setting costs |
| **`fastest`** | `performanceProfile(LOWEST_LATENCY)` | ~1.6 | no audit log, no node lookup, no dirty filtering |

The template generates both configurations from one graph definition, selected by a Maven property, so
a user can measure the cost of their own observability rather than being handed a default that decided
it for them. **The default must remain `audited`** — a starter that emits nothing to analyse fails the
pathway the catalogue exists to serve.

## D · Mechanism

### D1 · A `native` block in the starter schema — **UP-PG-04**

The starter files are declarative input to the playground's `buildStarterZip` generator. Adding native
readiness is therefore a schema addition plus generator output, not a new template format:

```json
"native": {
  "enabled": true,
  "shape": "audited",              // "audited" (default) | "fastest"
  "gc": "serial",                  // "epsilon" only for a bounded benchmark, never a service
  "pgo": { "dir": "src/pgo", "collectScript": true, "attempts": 10 },
  "benchmark": { "enabled": true, "targetNs": 2.0 }
}
```

Absent block ⇒ today's behaviour exactly. **No existing template changes shape**, which keeps
`template-bench.py`'s pinned catalogue green.

### D2 · What the generator emits when `native.enabled`

1. **The builder source** carries B1 and B3 — `performanceProfile(...)` per `shape`, and
   `generateReachabilityMetadata(true)`.
2. **The node source** carries B2 — every `@OnTrigger`/`@OnEventHandler` with
   `failBuildIfMissingBooleanReturn = false`, so the template teaches the idiom by using it.
3. **`Main`** carries B4 and B5, with the shape rules as comments that say *why*, because every one of
   them cost this project a wrong figure when broken.
4. **`pom.xml`** carries B6 — `org.graalvm.buildtools:native-maven-plugin` (0.10.6 at the time of
   writing), goal `compile-no-fork`, `instrumented` and `native` profiles, one option expression per
   `<buildArg>`, and `--pgo=${project.basedir}/src/pgo/app.iprof` rather than the stock
   regenerate-every-build `default.iprof`.
5. **`tools/collect-pgo.sh`** carries B7 — the loop, with the three silent failure modes guarded:
   an instrumented run that is killed rather than exited writes no profile; a profile carried across a
   *rebuild of the instrumented image* is worse than none (8.0 ns, reported as `PGO: user-provided`
   with no warning); a workload that misses a deployed path is compiled worse than with no profile.
6. **`src/pgo/README.md`** carries the honest part: **a committed profile is valid for the graph that
   produced it.** Edit the graph and rerun the script. The template must not ship a profile that
   pretends to survive the user's first edit — that would be the same failure as the current name.
7. **`Bench.java` + the script's threshold** carry B8.

### D3 · A catalogue field so the picker can say so — **UP-PG-05**

`index.json` entries carry `name`, `description`, `file`, `type`, `mode`. Add one field:

```json
"native": "ready"     // "ready" | "capable" | "none" (absent ⇒ "none")
```

- **`ready`** — generated from a `native` block; the acceptance bench in §E passes against it.
- **`capable`** — AOT, will build a native image, carries none of §B. **This is what
  `fluxtion-aot.starter.json` is today**, and labelling it honestly is part of this work.
- The analyser's `File ▸ New project from template…` shows the badge and can filter on it.

**Rename or relabel.** "Fluxtion AOT (native-ready)" must either become `native: "ready"` by carrying
§B, or lose the parenthetical. Shipping the current name beside a truthful field is worse than either.

## E · Acceptance — the bench, because a cross-repo contract rots unless something runs it

This is the M19 rule (`tools/bench/README.md`) and the M34.3 rule applied again. Extend
`tools/bench/template-bench.py` with a `--native` leg that, against the **deployed** catalogue:

| # | Assertion | Fails when |
|---|---|---|
| E1 | every entry declaring `native: "ready"` fetches and scaffolds | a template is added and is unreachable |
| E2 | the generated `pom.xml` has both profiles, and each `<buildArg>` is one option expression | the plugin silently ignores a shell-shaped arg |
| E3 | the generated builder sets the profile for its `shape` and `generateReachabilityMetadata(true)` | B1/B3 regress |
| E4 | every generated `@OnTrigger`/`@OnEventHandler` sets `failBuildIfMissingBooleanReturn = false` | B2 regresses |
| E5 | `Main` constructs the processor inside the loop method and puts nothing between the constructor and the loop | B4 regresses — checked structurally, not by comment |
| E6 | `mvn -Pinstrumented package` produces an image, the collect script produces a profile, and `mvn -Pnative package` consumes it | the PGO wiring is broken end to end |
| E7 | the built image's `native-image.properties` origin line appears in the build log | B3 is present but not being read off the classpath |
| E8 | **the benchmark lands under `targetNs`, or the run reports which attempt was best and exits non-zero** | the whole point |

E6–E8 need a GraalVM and take minutes, so they are `--native` opt-in, like the existing `--live` leg.
E1–E5 are static and run by default. **E8 must never be quietly skipped** — a bench that hides a
missing build reads exactly like one that passed, which is the defect this whole milestone is about.

## F · Non-goals

- **Making every template native.** Most are interpreted by design; `native: "none"` is the honest
  label, not a backlog item.
- **Shipping a profile that survives graph edits.** It cannot; §D2.6 says so in the artifact itself.
- **Choosing the user's GC.** `--gc=epsilon` belongs to a bounded benchmark and to nothing else; the
  default is `serial`.
- **Promising determinism.** The build lottery is upstream
  ([oracle/graal#14387](https://github.com/oracle/graal/issues/14387)); the template's answer is
  §B7 — collect until it lands, then keep the profile.

## G · Risks

| Risk | Mitigation |
|---|---|
| The template teaches `LOWEST_LATENCY` and users lose their audit log without noticing | default is `audited`; the two shapes are named and their costs tabulated (§C) |
| A committed profile goes stale silently and the user ships a slow binary believing otherwise | E8 gates on the measurement, not on the profile's presence |
| The plugin version and PGO flags drift | pinned in `versions`, and E6 exercises them against the real toolchain rather than asserting the text |
| Native support is Oracle GraalVM only; PGO is not in CE | `native: "ready"` states the requirement in the catalogue entry's `description`, where the picker shows it |

## H · Cross-repo asks

Both belong to the **playground** repo and are drafted here in the shape
[`upstream-asks.md`](../proposals/upstream-asks.md) expects:

- **UP-PG-04 · `native` block in the starter schema** (§D1/D2) — generator emits the pom profiles, the
  collect script, the shaped builder and `Main`, and the `src/pgo/` home.
- **UP-PG-05 · `native` field in the catalogue** (§D3) — plus relabelling
  `fluxtion-aot.starter.json`, whose name currently claims more than it carries.

Sequencing: UP-PG-05 can land alone and immediately — it is one honest field and one name. UP-PG-04 is
the work.
