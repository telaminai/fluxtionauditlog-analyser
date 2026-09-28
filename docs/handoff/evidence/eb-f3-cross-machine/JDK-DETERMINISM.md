# Capture determinism across JDK versions — same machine, two JDKs

The record digest was the one binding I expected could move: it is taken over the store's **parsed text**, not
the file's bytes, so it can follow charset handling, line endings or a parser difference between runtimes. The
chart's run basis cannot move — `walk.0.run.0` is byte for byte the log member's own sha256.

Both runs used `capture-determinism.py` unchanged, on one machine, differing only in the JVM.

| | OpenJDK 25.0.2 | Amazon Corretto 21.0.10 |
|---|---|---|
| normalised profile | `sha256:1429d691394b5c7b08020e946912dd7d133d8e9a1efa32f8c8235e691fc03d36` | **identical** |
| record digest (`s.0.t.0.basis.digest`) | `sha256:84cf4845…52d0` | **identical** |
| chart digest (`s.1.t.0.basis.digest`) | `sha256:ac87d757…7410` | **identical** |
| run basis (`run.0`) | `06a981fa…1b4c` | **identical** |
| fingerprint (`fp.first/last/records`) | 1767258004500 / 1767259092000 / 726 | **identical** |
| raw profile member digest | `74324ba3…` | `c1fb640a…` — **differs, by design** |

That last row is the control. The raw member differs because the profile carries the walk's `created` and
`updated` and a nonce; the normalisation strips clocks and keeps what binds evidence. If the raw digests had
matched, the normalisation would have been doing nothing and the test would have proved nothing.

**So the evidence binding is stable across JDK major versions.** Taken with recipient B — who received a bundle
captured under JDK 25 and played it under JDK 21 with all three targets current — the runtime is ruled out as a
source of drift in both directions.

## What this does NOT show

One machine, one OS, one filesystem, one locale. It rules out the JVM, nothing else.

**The remaining gaps, in the order they are worth closing:**

1. **A case-sensitive filesystem.** Both machines so far were macOS and case-insensitive. `verify` refuses a
   case-only-differing member (tested in the v1 review), but `unpack` onto a case-sensitive disk has never run.
   This machine can produce one with `hdiutil`.
2. **A different OS.** Docker is available here; a Linux run gives a different OS *and* a case-sensitive
   filesystem in one go.
3. **A different locale or charset.** `LANG` and `-Dfile.encoding` reach the same parsed text the record digest
   is taken over.

None of these needs another machine, which is the useful discovery: the gap was never really about machines.
