"""Reads of generated code or other avoidable sources, from a transcript. Prints JSON.

A tool call counts as a generated-code read when its command or path names the generated processor or GraphML
(`/generated/`), the builder (`/builder/`), or the source of setup/validate/generate scripts read with cat/sed/
head/less. Bytes are those of the tool result returned to the model."""
import json, re, sys
GEN = re.compile(r'/generated/|MarketProcessor\.(java|graphml)|/builder/|(cat|sed|head|less|tail)\b[^|;&]*\b(setup|validate|generate)\.sh')
names, out = {}, {'generatedReads': 0, 'generatedBytes': 0, 'toolResultBytes': 0, 'calls': 0}
for line in open(sys.argv[1], errors='replace'):
    try:
        d = json.loads(line)
    except ValueError:
        continue
    if d.get('type') == 'assistant':
        for c in d['message'].get('content', []):
            if c.get('type') == 'tool_use':
                i = c.get('input', {})
                names[c['id']] = str(i.get('command') or i.get('file_path') or i.get('path') or i.get('pattern') or '')
                out['calls'] += 1
    elif d.get('type') == 'user' and isinstance(d['message'].get('content'), list):
        for c in d['message']['content']:
            if c.get('type') == 'tool_result':
                t = c.get('content'); t = t if isinstance(t, str) else json.dumps(t)
                out['toolResultBytes'] += len(t)
                if GEN.search(names.get(c['tool_use_id'], '')):
                    out['generatedReads'] += 1; out['generatedBytes'] += len(t)
print(json.dumps(out))
