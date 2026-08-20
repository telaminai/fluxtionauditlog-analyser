"""Re-derive §9-§11's numbers from the POC on disk, so they can be checked rather than argued.

    python3 measure.py /path/to/supermarket-poc
"""
import collections, pathlib, re, sys

poc = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                   "/Users/greg/IdeaProjects/telamin/supermarket-poc")
gml = poc / "src/main/resources/com/acme/store/generated/StoreProcessor.graphml"
log = poc / "run/store-audit.yaml"

g = gml.read_text()
edges = re.findall(r'<edge[^>]*source="([^"]+)"[^>]*target="([^"]+)"', g)
nodes = set(re.findall(r'<node id="([^"]+)"', g))
pred = collections.defaultdict(list)
for s, t in edges:
    pred[t].append(s)

# longest-path depth == the super-step a node lands in under Pregel semantics
depth = {n: 0 for n in nodes}
for _ in range(len(nodes)):
    changed = False
    for s, t in edges:
        if depth[s] + 1 > depth[t]:
            depth[t] = depth[s] + 1
            changed = True
    if not changed:
        break

joins = [n for n in nodes if len(pred[n]) > 1]
unbalanced = [n for n in joins if len({depth[p] for p in pred[n]}) > 1]
extra = sum(len({depth[p] for p in pred[n]}) - 1 for n in unbalanced)

observed, dup_records, total = set(), 0, 0
cur = []
for line in log.read_text().splitlines():
    if line.startswith("---"):
        if cur:
            total += 1
            if any(v > 1 for v in collections.Counter(cur).values()):
                dup_records += 1
        cur = []
    m = re.match(r'\s*-\s+([A-Za-z_][\w]*):\s*\{', line)
    if m:
        cur.append(m.group(1))
        observed.add(m.group(1))

print(f"  nodes / edges                    {len(nodes)} / {len(edges)}")
print(f"  max depth (super-steps)          {max(depth.values())}")
print(f"  join nodes (>1 predecessor)      {len(joins)}")
print(f"  UNBALANCED joins (would glitch)  {len(unbalanced)}")
print(f"    on paths that ran              {len([n for n in unbalanced if n in observed])}")
print(f"    never ran -> ship undetected   {len([n for n in unbalanced if n not in observed])}")
print(f"  extra firings per event          {extra}")
print(f"  FLUXTION baseline: records where a node fired twice: {dup_records} of {total}")
