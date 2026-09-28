# Predictions — EB.F6 and EB.F11, before running anything

Written 2026-09-28 against `05f3ea3c` and `f573d792`, from the commit subjects and the review brief only.

| # | prediction | why |
|---|---|---|
| P1 | **`store.size()` is not exactly what the session published as read.** A live read leaves a trailing record pending, and `context.log` carries `trailingRecordsPending` and `trailingRecordsIncluded` as separate counts. If the frame excerpts `0..size-1` while the session published a different number, `sourceRecords` and the bundle disagree by one. | The brief names it, and the two counts exist precisely because the store and the read are not the same thing. Off-by-one at a boundary that only appears under Follow is the classic. |
| P2 | **"Under Follow, changed-on-disk is growth" is false for truncation and for same-length rewrite.** The claim is that identity refuses those first; I doubt identity is consulted before freshness in every path. | The commit states the premise as though it were established. A premise stated that confidently in a commit message is the thing to test. |
| P3 | **`syncOpenGraphsIntoConfig()` returns early when definitions are withheld, and the bundle then carries stale or missing chart definitions with nothing saying so.** | The brief flags the early return. A silent divergence between what is on screen and what is captured is exactly the class EB.F11 exists to close, so closing it in one place and leaving it in another would be ironic and likely. |
| P4 | **`ProjectProfile.write` against a DELETED profile file misbehaves** — it reads the existing file to carry unknown keys and may mint a nonce. Read-only was fixed; absent may not have been. | The fix was driven by read-only. Absent is the neighbouring case, and neighbouring cases are what a targeted fix misses. |
| P5 | **A recipient cannot tell `sourceRecords` is "what was read" rather than "the log's size".** | The manifest carries `readSoFar: true` and `--verify` says so, but the number beside it is the thing a reader will quote. Honesty about partiality is the whole point of the flag. |
| P6 | **Carried-over unknown keys from the existing profile reach the bundle**, and `BundleProfile`'s allow-list filter is the only thing standing in the way. | `write` carrying unknown keys forward is a real mechanism; whether the second filter catches everything it should is the question. |

**What would show me wrong:** if the frame takes the published record count rather than `store.size()`, and the
withheld-definitions case either refuses or is stated, then P1 and P3 are wrong and the pair is tighter than the
brief's own hints suggest.
