# Review — evidence bundle v1 (`dff81924..e0e67b6d`)

Adversarial review, 2026-09-28. Predictions were committed before anything was run, in
`review_evidence_bundle_v1_predictions_claude.md` on this branch, and are scored below.

**Verdict: merge after the two REQUIRED fixes.** The design is better than its own disclosure suggests — the
format verifies fully in memory before writing anything, and every structural attack I could think of was
already refused by construction. The two REQUIRED items are both in the part a recipient is asked to trust:
an unbounded read that turns `--verify` into a crash on a 2 MiB file, and a stated guarantee about the profile
that is false as written.

## 1. What I ran

| command | result |
|---|---|
| `mvn -o -q package -DskipTests` | exit 0 |
| `mvn -o -q clean test`, counted from fresh Surefire XML | **2724 tests, 0 failures, 0 errors, 150 skipped**, 364 suites — matches RESULTS.md exactly |
| `python3 tools/evidence-bundle-demo.py` | **20 passed, 0 failed**; timings `unpack 0.11s · open 0.43s · walk 0.52s · received→walk-end 1.07s` |
| `--mode preflight` | **30 frame suites, 372 anchors**, no orphans — matches |
| six hand-crafted hostile bundles | see §3 |
| `--bundle-profile` against a crafted profile | see §3, F2 |

**Skips reported separately, as asked:** 150 skipped in the headless run, all frame suites needing a display.

**The "never touches your own `~/.fluxtion-analyser`" claim holds.** I hashed my own config before and after the
driver: byte-identical, and the directory listing unchanged. That claim is true.

**Not run, and why:** the `--default-window`, `--keep` and `--open` driver modes; the display gate
(`--mode display`); the `eb-*` mutation controls (22 of them); `mkdocs build --strict`; the skills followed
literally against a live socket. Budget. Every finding below stands on something I executed.

## 2. Predictions, scored

| # | prediction | outcome |
|---|---|---|
| P1 | a hostile zip gets past `--unpack` (duplicate manifest, duplicate member, or case-only names) | **WRONG, and this is the good news.** All three are refused: `more than one manifest.json`, `the manifest lists a member twice: log.yaml`, `unlisted member: LOG.YAML`. The author said "no test"; the *code* was right. |
| P2 | `--unpack` writes before verification completes | **WRONG.** `read()` buffers every entry, verifies, and only then extracts. A deliberate two-pass design. |
| P3 | no size limit; a zip bomb is accepted | **RIGHT, and worse than predicted** — see F1. |
| P4 | the path-shape check false-positives on prose | **WRONG in the direction predicted, right in the mirror** — it *under*-matches, and leaks. See F2. |
| P5 | `project.unsavedEdits` is hand-placed state | **NOT CHECKED** — I ran out of budget before reading `MainFrame.context`. |
| P6 | at least one driver check is vacuous | **PARTLY RIGHT.** Two are absence-assertions that pass if a context key is renamed. See F4. |
| P7 | RESULTS.md overstates something | **WRONG on everything I checked.** Test counts, preflight anchors, the 20/20, the timings and the untouched-home claim all reproduced. |
| P8 | the moved-generation deletion is never provoked | **NOT CHECKED.** |
| P9 | the "continuing walk" rule misses a case | **NOT CHECKED.** |
| P10 | the skills and the driver disagree | **NOT CHECKED.** |

Two right, four wrong, four unchecked. The wrong ones matter: I expected a weak format and found a careful one.

## 3. Findings, most severe first

### F1 — REQUIRED. `--verify` dies on a 2 MiB file: the read is unbounded

**`EvidenceBundle.readAll`, line 264**, called from `read()` at line 152. Every entry is read fully into a
`byte[]` and held in a map for the whole bundle, *before* any check runs — including the size check at line 176,
which compares `bytes.length` against the manifest **after** the bytes are already in heap.

**Reproduction.** A 2,088,082-byte bundle with one unlisted 2 GiB member of zeros:

```
$ java -Xmx512m -jar analyser.jar --verify bomb.fexp
java.lang.OutOfMemoryError
  at EvidenceBundle.readAll(EvidenceBundle.java:264)
  at EvidenceBundle.read(EvidenceBundle.java:152)
  at EvidenceBundle.verify(EvidenceBundle.java:136)
```

The member is **unlisted**, so it would have been refused — the refusal never runs.

**Why it is REQUIRED.** `--verify` is the one command a recipient runs on a file they were handed by someone
else, before trusting it. The failure is a crash, not a refusal, and the two read very differently to a person
deciding whether a bundle is safe.

**It also bites honestly.** The spec mandates whole-log-only in v1, and logs in real use run to 64MB and 142MB,
so a legitimate bundle needs heap larger than itself, twice over. This is not only an attacker's problem.

**Regression that would catch it:** verify a bundle whose declared members exceed a cap, and assert a *refusal*
naming the member rather than an error — plus a streaming digest so `readAll` never holds a whole member.

### F2 — REQUIRED. The profile's "holds no paths" guarantee is false as written

**`BundleProfile.PATH_SHAPED`, line 15**, is anchored: `^(/|~[/\\]|~$|[A-Za-z]:[/\\]|\\\\|file:)`. It only
rejects a value that **begins** with a path. A path anywhere else in the value passes, and the code's own
refusal message says *"an evidence bundle's profile holds no paths"*.

**Reproduction.** Take a captured DEMO profile, put a machine path mid-sentence in a report narrative, export:

```
report.0.s.0.text=we saw it in /Users/demo-person/private/logs/secret-venue.yaml and also file\:///etc/passwd

$ analyser --bundle-profile leak.fluxtion-settings out.fluxtion-settings
profile …: saved charts and focuses, reports and walks, hidden columns; nothing else
$ grep -o "/Users/demo-person/private/logs/secret-venue.yaml" out.fluxtion-settings
/Users/demo-person/private/logs/secret-venue.yaml
```

Accepted, with no warning, and the path is in the bundle.

**Why it is REQUIRED.** EP-A9 is the check that decides what leaves the machine. Report narratives are written
by people and by assistants, and quoting a path — *"we saw it in /var/log/…"* — is the most ordinary thing in
such a sentence. What leaks is the sender's directory layout, their username, and whatever the directories are
named after. For a bundle handed to an outsider, that is the wrong kind of surprise.

**Two honest qualifications.** The `file:` form did not survive, because of properties escaping. And a
*narrower* claim — "no profile **key** is a path" — may be what was meant and may well be true; but that is not
what the code says, nor what EP-A9 promises.

**Regression:** a profile whose narrative embeds an absolute path, asserting refusal (or redaction) and naming
the key. Add the mirror case — ordinary prose containing `~5%` or a bare colon — so the fix does not over-refuse.

### F3 — ADVISORY. Two packs of identical evidence produce different identities

`pack`'s javadoc says *"identical content packs to an identical identity"*, and it does — for a fixed
`createdAt`. The CLI never fixes one:

```
identity(good)  = sha256:607ca1f2e7f7ce61…
identity(good2) = sha256:2019f6066c227842…      # same folder, packed twice
```

So the identity is not a content address, and two people cannot tell that two bundles hold the same evidence.
That may be intended — D-2 says the identity is the manifest's bytes, and the manifest carries a timestamp —
but the javadoc claim is stronger than the behaviour. Either add `--created-at` for reproducible packing, or
soften the sentence.

### F4 — ADVISORY. Two driver checks pass if a context key is renamed

`tools/evidence-bundle-demo.py:412` and `:414` assert *absence*:

```python
check(not ctx.get("source", {}).get("roots"), "EP-A12 the recipient has no source roots")
```

If `source.roots` is renamed or moves, `.get` returns `None`, the check passes, and EP-A12 silently stops being
tested. The other 18 checks assert values and are sound — `applies is True`, `records == 10`,
`len(caveats) == 1`, the sha comparisons — which is better than I predicted.

**Regression:** a positive control in the same run — assert the key exists and is empty, not merely falsy.

### F5 — ADVISORY. Trailing data after the zip is accepted silently

4,096 bytes appended to a valid bundle: `verified: 2 members`. Correct by the letter (the identity covers the
manifest, not the container) but it means attacker-chosen bytes ride inside a file the recipient has been told
is verified, and two "verified" bundles can differ byte for byte. Either refuse trailing data or say plainly
that verification covers the members, not the file.

## 4. What I could not check

`project.unsavedEdits` and rule 9 (P5); the moved-generation refusal path (P8); the continuing-walk rule (P9);
the skills followed literally (P10); the `eb-*` mutation controls; the display gate; `--default-window`'s two
reported failures; `mkdocs --strict`; the screenshots by eye. **None of these is a pass — they are unrun**, and
P8 in particular is the kind of cleanup path that fails quietly.

## 5. Verdict

**Merge after F1 and F2.** Both are in the trust surface — the verifier a recipient runs on an untrusted file,
and the check that decides what leaves the machine. Neither is deep: F1 wants a size cap and a streaming digest,
F2 wants an unanchored search and a decision about redaction versus refusal.

Everything else I attacked held. The duplicate-manifest, duplicate-member, case-collision, zip-slip and
verify-before-write behaviours were all correct, several of them untested but right, and RESULTS.md reproduced
on every number I checked. The disclosed gaps were real gaps in *testing*, not in the code — which is an
unusually good outcome for a first delivery, and worth saying as plainly as the defects.
