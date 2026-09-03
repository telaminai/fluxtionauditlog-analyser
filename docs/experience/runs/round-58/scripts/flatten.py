#!/usr/bin/env python3
"""Rewrite the generated BenchProcessor into a single-method dispatch variant.

Inlines every guardCheck_*() body as a boolean expression, plus auditEvent() and afterEvent(),
into handleEvent(MarketTick) -- i.e. what a "one big method" code generator would emit.
Output is gitignored: it inherits the generator's vendor-domain copyright header (rule 1).

  usage: flatten.py <BenchProcessor.java> <BenchProcessorFlat.java>
"""
import re, sys, pathlib

src = pathlib.Path(sys.argv[1]).read_text()
guards = dict(re.findall(r'private boolean (guardCheck_\w+)\(\) \{\s*return ([^;]+);\s*\}', src))
def body(name):
    m = re.search(r'private void ' + name + r'\([^)]*\) \{\n(.*?)\n  \}', src, re.S)
    return m.group(1) if m else None
after = body('afterEvent')
audit = re.search(r'private void auditEvent\(Event typedEvent\) \{\n(.*?)\n  \}', src, re.S).group(1)

out = re.sub(r'\bBenchProcessor\b(?!Flat)', 'BenchProcessorFlat', src)
m = re.search(r'(  public void handleEvent\(MarketTick typedEvent\) \{\n)(.*?)(\n  \}\n)', out, re.S)
inner = m.group(2).replace('    auditEvent(typedEvent);', audit)
for g, expr in guards.items():
    inner = inner.replace(f'{g}()', f'({expr})')
inner = inner.replace('    afterEvent();', after)
out = out[:m.start(2)] + inner + out[m.end(2):]
pathlib.Path(sys.argv[2]).write_text(out)
print(f"inlined {len(guards)} guards + auditEvent + afterEvent -> {sys.argv[2]}")
