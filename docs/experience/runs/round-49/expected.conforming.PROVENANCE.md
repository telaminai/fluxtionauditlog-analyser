# Provenance — `expected.conforming.txt`

**DERIVED, NOT ORIGINAL EVIDENCE.**

| | |
|---|---|
| source | [`expected.txt`](expected.txt) — **unchanged, still authoritative** |
| source sha256 | `3eaea891435d1d31e28d64c7be731e3de102a3b9af392034239573066e241d75` |
| derived sha256 | `aa58ce3787afbd8a9f2020fbc4eb12c97864e8e26f08bc05b39993795ea6a8be` |
| records | 18 |
| transform | insert the `---` document separator between records. **Nothing else is altered** — no value, key, ordering or timestamp. |
| generated | 2026-09-03, M48.11 |

## Why this file exists

`expected.txt` was written with `String.join("\n", ...)`, so the shipped reader parses the whole file
as ONE record. See [`FORMAT-NOTE.md`](FORMAT-NOTE.md). The original is preserved rather than repaired,
because rewriting historical evidence would invalidate comparisons already published against it.

## Why the provenance is HERE and not in the log

The first version of this derivative carried these facts as `#` comment lines at the head of the log.
Independent review found that **the reader absorbed them into the first record as metadata** — so a
file created to be format-conforming was neither conforming nor honest about itself, and its
provenance had become bogus record data.

**A derived evidence file must contain only evidence.** Anything said *about* it belongs beside it.

## Verifying

```
shasum -a 256 expected.txt              # must equal the source sha256 above
shasum -a 256 expected.conforming.txt   # must equal the derived sha256 above
```
