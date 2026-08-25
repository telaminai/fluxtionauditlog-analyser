# M34 capability probes — a reader plugin that declares the awkward answers

Throwaway, out of the build. It exists because **no source in this tree can exercise M34.2**: the
native YAML reader is `TOTAL`-ordered and has no graph, so the code paths that degrade a capability
had nothing to degrade against. Verifying them by reading the diff is exactly what this milestone
keeps catching as insufficient.

Two readers, each claiming something the built-in path cannot:

| reader | `.ext` | ordering | graph |
|---|---|---|---|
| `PartialProbeReader` | `.probe` | **PARTIAL** | DECLARED |
| `InferredProbeReader` | `.iprobe` | PARTIAL | **INFERRED** |
| `BrokenProbeReader` | `.bprobe` | TOTAL | **`graph()` throws** — the unreachable-registry case |

Between them they reach: the suppressed ordinal badge, the step-through wording, the standing
order caveat, `coverage`'s refusal on an inferred graph, and the execution-shading caveat.

```bash
JAR=$(ls ../../../target/fluxtion-auditlog-analyser-*.jar | grep -v original | head -1)
mkdir -p out/META-INF/services
javac -cp "$JAR" -d out probe/*.java
printf 'probe.PartialProbeReader\nprobe.InferredProbeReader\nprobe.BrokenProbeReader\n' \
  > out/META-INF/services/telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader
(cd out && jar cf ../m34-probe.jar .)
mkdir -p ~/.fluxtion-analyser/plugins && cp m34-probe.jar ~/.fluxtion-analyser/plugins/
printf 'x\n' > run.probe && printf 'x\n' > run.iprobe && printf 'x\n' > run.bprobe
```

Then, with the analyser running: `open {log: "…/run.iprobe", format: "inferred-probe"}` and read
`topology` / `coverage`.

**Installing a plugin jar is arbitrary code execution** (M31 D-P3). This one is yours, built from
source you can read in one sitting — but delete it from `~/.fluxtion-analyser/plugins/` when you are
done, because a probe that claims capabilities nothing else claims is not something to leave lying
in a real profile.
