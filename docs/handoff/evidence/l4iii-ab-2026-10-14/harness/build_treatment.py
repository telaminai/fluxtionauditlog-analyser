"""Treatment fixtures for the M72 L4 (iii) A/B: each control fixture plus ONLY the fresh-context guidance.

Bundle rungs (3, 5): the vendored .claude/skills/load-audit-log/SKILL.md gets exactly two hunks from the canonical file
(analyser docs/skills/common/load-audit-log/SKILL.md, sha256 c5b7d908…, m19-skills/2): the step-1 routing paragraph
and the `## Diagnose in a fresh context` section, byte-identical. The bundle's substituted export command and its
5-line "Do not use analyser_context…" paragraph are KEPT (owner/coordinator decision 2026-10-14: the canonical file's
unsubstituted TODO(bundle) line and shortened paragraph are not the lever). Verified below: the patched file differs
from the vendored one only by those insertions, and from the canonical one only in the two kept vendored hunks.

Starter rungs (2, 4), as the spec states: (1) append the section, from `## Diagnose in a fresh context` up to the next
heading, byte-identical, to runbooks/analyser.md; (2) in RUNBOOK.md step 5, after "Then run ./run.sh.", insert the
spec's sentence.

Usage: build_treatment.py <canonical SKILL.md>   prints JSON (digests and checks); exits 1 if a check fails.
"""
import difflib, hashlib, json, shutil, sys
from pathlib import Path

B = Path('/private/tmp/fx-ab14')
CANON = Path(sys.argv[1])
CANON_SHA = 'c5b7d90867adfee693cfdf049bd3588d8618eb252981db08135d6c85fce59c62'
ROUTING = ('   If this conversation designed or changed the graph and the question needs more than one analyser\n'
           '   read after opening (a chart, several records or nodes, before/after, several processors), stop after\n'
           '   this step and follow *Diagnose in a fresh context* below. A single check stays here.\n\n')
SENTENCE = ('If checking the change needs more than one analyser read after opening the log, follow '
            '*Diagnose in a fresh context* in runbooks/analyser.md.')
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
out = {'canonical': {'path': 'analyser docs/skills/common/load-audit-log/SKILL.md', 'sha256': sha(CANON)}}
ok = out['canonical']['sha256'] == CANON_SHA
canon = CANON.read_text()
i = canon.index('## Diagnose in a fresh context\n'); j = canon.index('\n## ', i + 1) + 1
SECTION = canon[i:j]                      # from the heading up to (not including) the next heading
assert ROUTING in canon, 'routing paragraph not found in canonical'

for r in ('r3', 'r5'):
    src, dst = B / r / 'fixture', B / r / 'fixture-t'
    shutil.copytree(src, dst, symlinks=True)
    f = dst / '.claude/skills/load-audit-log/SKILL.md'
    v = f.read_text()
    # routing: canonical places it right before the blank line ending step 1's "Do not use analyser_context" paragraph,
    # i.e. before the next numbered step. Insert at the same logical place in the vendored file.
    # canonical: ROUTING follows the "Do not use `analyser_context` …" paragraph and its blank line. The vendored
    # paragraph is the longer 5-line version, so anchor on the END of the vendored paragraph (its next blank line).
    pc = canon.index('**Do not use `analyser_context`'); assert canon.index(ROUTING) > pc
    assert canon[canon.index('\n\n', pc) + 2:].startswith(ROUTING), 'canonical layout changed'
    pv = v.index('**Do not use `analyser_context`')
    k = v.index('\n\n', pv) + 2           # just after the paragraph's terminating blank line
    v2 = v[:k] + ROUTING + v[k:]
    # section: canonical places it before `## What to do when there is no log yet`
    nxt = canon[j:].splitlines()[0]
    m = v2.index(nxt + '\n')
    v2 = v2[:m] + SECTION + v2[m:]
    f.write_text(v2)
    vend_lines, pat_lines, can_lines = v.splitlines(True), v2.splitlines(True), canon.splitlines(True)
    d_vend = [l for l in difflib.unified_diff(vend_lines, pat_lines, n=0) if l[:1] in '+-' and l[:3] not in ('+++', '---')]
    only_inserts = all(l.startswith('+') for l in d_vend)
    inserted = ''.join(l[1:] for l in d_vend)
    expect = ROUTING + SECTION
    d_can = list(difflib.unified_diff(can_lines, pat_lines, n=0))
    hunks = sum(1 for l in d_can if l.startswith('@@'))
    out[r] = {'vendoredSha256': sha(src / '.claude/skills/load-audit-log/SKILL.md'), 'patchedSha256': sha(f),
              'vsVendored': {'onlyInsertions': only_inserts, 'insertedEqualsRoutingPlusSection': sorted(inserted) == sorted(expect),
                             'insertedLines': len(d_vend)},
              'vsCanonical': {'hunks': hunks, 'diff': ''.join(d_can[2:])}}
    ok &= only_inserts and sorted(inserted) == sorted(expect) and hunks == 2

for r in ('r2', 'r4'):
    src, dst = B / r / 'fixture', B / r / 'fixture-t'
    shutil.copytree(src, dst, symlinks=True)
    a = dst / 'runbooks/analyser.md'; t = a.read_text()
    a.write_text(t + ('' if t.endswith('\n\n') else '\n') + SECTION)
    rb = dst / 'RUNBOOK.md'; s = rb.read_text()
    assert s.count('Then run ./run.sh.') == 1
    rb.write_text(s.replace('Then run ./run.sh.', 'Then run ./run.sh. ' + SENTENCE, 1))
    appended = a.read_text()[len(t):]
    out[r] = {'analyserMdSha256': {'before': sha(src / 'runbooks/analyser.md'), 'after': sha(a)},
              'runbookSha256': {'before': sha(src / 'RUNBOOK.md'), 'after': sha(rb)},
              'sectionByteIdentical': appended.lstrip('\n') == SECTION,
              'runbookSentenceInserted': rb.read_text().count(SENTENCE) == 1}
    ok &= out[r]['sectionByteIdentical'] and out[r]['runbookSentenceInserted']
out['sectionSha256'] = hashlib.sha256(SECTION.encode()).hexdigest()
out['sectionChars'] = len(SECTION)
out['ok'] = bool(ok)
print(json.dumps(out, indent=1))
sys.exit(0 if ok else 1)
