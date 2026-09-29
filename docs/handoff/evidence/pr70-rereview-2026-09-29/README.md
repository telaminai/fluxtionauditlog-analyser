# PR #70 re-review evidence (2026-09-29)

Subject: `0624fe30` on `feat/evidence-bundle-replay`; delta `3330f9f2..0624fe30`.
See [the re-review](../../review_pr70_rereview_2026_09_29_claude.md). DEMO data only; no local path is recorded.

| File | What it is |
|---|---|
| `tracing-build-probe.sh` | Correction C1: replays the committed bundle into the DEMO build and into the same build with tracing on, then compares. Run from the repository root after `mvn -o -q package -DskipTests` and the previous review's `reproduce.py --out <dir>`; takes that dir and a new scratch dir. Writes only to the scratch dir. |
| `tracing-probe-output.txt` | Its output at `0624fe30`: the plain build AGREES 8/8; the tracing build writes 9 records, one `EventLogControlEvent`, and DIVERGES at record 0. |
| `reproduce-results-at-0624fe30.json` | The previous review's `reproduce.py`, unchanged, at this head (paths already replaced by placeholders by that script). |
| `controls-summary.json` | All 51 requested fast-engine controls: requested and caught names, baseline, the mutant's failing test and message, byte-identical restore. |
| `ci-36492046613-attempt1-shard3.json` | Extract of the attempt-1 `mutation-shard-3` artifact: `mouse-loss-column-adjustment`'s mutant red by name, its restored run **skipped** (native press assumption). |
| `native-bundle-conv/*.png` | Correction C2: the four bundle-conversation images, captured natively by `tools/capture-bundle-conversations.py` under the isolated DEMO home, 3360×2100, window title bar present. Each was opened and read. |
| `native-bundle-conv/with-an-assistant.regenerated.md.txt` | The page that run wrote. Adopt it with the images: the received image's title bar shows the identity the page quotes. Stored as `.txt` so the site build and link checks ignore it here. |

Findings 1, 2, 4 and 7 were attacked through the real CLI and runner with scratch DEMO inputs under `-Xmx64m` and
timeouts; the minimal counterexamples are written out in the review text. Failing-first checks put the pre-fix
source in place from a byte copy, ran the named test, and restored the fixed file from a byte copy with a sha256
check; no `git checkout` was used and no tracked file was left changed.
