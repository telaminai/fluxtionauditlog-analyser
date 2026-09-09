# C++ controls

The same discipline as the Java controls, for the C++ generation target: fixed graphs, fixed inputs, a
checksum every arm must agree on, and bands in `../control-bands.tsv` that `validate-controls.sh` enforces.

## Why the sources are here and not only in a scratch directory

The Java native controls point at binaries in a session scratch directory, so they can be re-*run* but
not re-*built*. These can be rebuilt from this directory, which is the difference between a control that
survives the machine being wiped and one that does not.

## What is here

| path | fixture |
|---|---|
| `conv/bench.cpp` | the 30-node, 5-event-type converging graph — node bodies and the driver |
| `ladder/bench.cpp` | the 4-node price ladder, four separate nodes |
| `ladder/fat_bench.cpp` | the same calculations collapsed into one node |

Each file supplies only the **node bodies and the driver**. The processor itself is generated: the
`.cpp` includes a header the build script emits by running the Java generator with
`-Dfluxtion.sourceGeneratorId=cpp` over the same fixture classes in `../src/` that the Java arms use.
**That is the point** — the control measures generated code, not a hand-written stand-in. Every C++
number quoted before these existed came from a hand-written model, and it was 3.7x off what the
generator actually produced.

## Building

```bash
./build-controls.sh            # needs the compiler reactor installed and clang++ on PATH
```

Prerequisites, and the script fails loudly naming any that are missing:

- `fluxtion-generator-cpp` installed to the local repository (it carries the emitter, the Velocity
  template and the header-only C++ runtime as resources)
- `fluxtion-runtime` installed
- the fixture classes compiled — the same ones the Java arms use
- `clang++` supporting C++17

## The one thing to check before trusting a number

**The checksum.** Every arm prints `v=`, and the C++ and Java arms must agree exactly:

| fixture | checksum |
|---|---|
| conv | `v=120.5988` |
| ladder | `v=2185441000` |

Three invalid cross-language comparisons were caught by that field during this work and by nothing else
— including one that reported an 8x Java win because the C++ arm had different arithmetic.
