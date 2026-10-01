# Upstream-owned specs

Designs for the Fluxtion compiler, runtime, builder, Maven plugin, public AI instructions and starter template. They
were written in this repository because the analyser measured the problem, and they hold that evidence; the
implementation belongs to the owning repository. Moved here from `docs/specs/` on 2026-10-01 so the analyser's own
spec set lists only analyser work. Links between them and back into `docs/specs/` are still checked by
`SpecLinksResolveTest`.

Each is also listed in [upstream-asks.md](../upstream-asks.md) ▸ *Upstream-owned specs*.

| Spec | Owner | Status | Tracker |
|---|---|---|---|
| [spec-generated-dispatch-performance.md](spec-generated-dispatch-performance.md) | fluxtion-compiler, fluxtion runtime | partly shipped (runtime 1.0.15, compiler 1.0.67); the determinism spine is not started | M50 |
| [spec-manifest-optimisation-metadata.md](spec-manifest-optimisation-metadata.md) | fluxtion-builder, fluxtion-maven-plugin | proposed (W14) | M50 |
| [spec-binary-audit-encoding.md](spec-binary-audit-encoding.md) | Fluxtion core (the analyser measured it) | part shipped 2026-09-09 | M52 |
| [spec-binary-audit-reader.md](spec-binary-audit-reader.md) | fluxtion-compiler | the module was withdrawn; the reader shipped inside the analyser; an owner decision is open | M52 |
| [spec-builder-component-resolution.md](spec-builder-component-resolution.md) | fluxtion-builder, Maven plugin | proposed for upstream review | M48 |
| [spec-component-catalogue.md](spec-component-catalogue.md) | fluxtion-builder, Maven plugin | proposed | M48 |
| [spec-minimal-authoring-instructions.md](spec-minimal-authoring-instructions.md) | the public Fluxtion AI instructions | proposed; superseded in part by `spec-authoring-modes.md` | none directly (see `spec-authoring-modes.md`) |
| [spec-native-ready-template.md](spec-native-ready-template.md) | the starter template | draft | M51 |
