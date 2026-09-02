#!/usr/bin/env python3
"""Fingerprint a subsystem inside a composed Fluxtion graph.

The supplier publishes this hash for their subsystem. The consumer computes it from THEIR composed
graph and compares. It covers the subsystem's own nodes, the edges among them, and the edges crossing
its boundary — so it changes if the supplier's internals change, if a node is dropped, or if the
consumer wires an input to something other than what the supplier declared.
"""
import sys, hashlib, xml.etree.ElementTree as ET

NS = "{http://graphml.graphdrawing.org/xmlns}"

def graph(path):
    r = ET.parse(path).getroot()
    nodes = {n.get("id") for n in r.iter(NS + "node")}
    edges = {(e.get("source"), e.get("target")) for e in r.iter(NS + "edge")}
    return nodes, edges

def fingerprint(path, members):
    nodes, edges = graph(path)
    members = set(members)
    missing = members - nodes
    if missing:
        return None, f"absent from the graph: {sorted(missing)}"
    internal = sorted((s, d) for s, d in edges if s in members and d in members)
    inbound  = sorted((s, d) for s, d in edges if d in members and s not in members)
    outbound = sorted((s, d) for s, d in edges if s in members and d not in members)
    payload = repr((sorted(members), internal, inbound, outbound))
    return hashlib.sha256(payload.encode()).hexdigest()[:16], None

if __name__ == "__main__":
    fp, err = fingerprint(sys.argv[1], sys.argv[2].split(","))
    print(err if err else fp)
