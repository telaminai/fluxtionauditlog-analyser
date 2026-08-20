"""M34.0 spike — the throwaway translator: a LangGraph run -> the analyser's audit-log format.

No SPI, no refactor, nothing added to the analyser. This is deliberately a hand-fed file, because
the question M34.0 asks is whether a foreign run can be made legible AT ALL by today's tool. If it
can't, no amount of SPI fixes that.

THE MAPPING, and where it is honest vs where it strains:

  Fluxtion                      LangGraph                     verdict
  --------------------------    --------------------------    ----------------------------------
  one event -> one dispatch     one input -> N super-steps     one .stream() call = one RECORD
    cycle = one record            each with >=1 task             (super-steps are within-record)
  nodeLogs in DISPATCH order    tasks within a super-step      *** THE PROBLEM (D-A1a) ***
                                  are CONCURRENT                 stream arrival order is not
                                                                 dispatch order and nothing on
                                                                 screen would say so
  what each node logged         payload.result = "channel       clean: LangGraph reports per-task
    (auditLog.info(...))          names -> values written        writes, so D-A3's attribution
                                  by this task"                  rule is satisfied by the source
                                                                 rather than invented by us
  declared graph (GraphML       graph.get_graph()              clean: DECLARED, so coverage and
    from the AOT compiler)                                       "did not run" shading can exist

Run:  python lg_to_audit.py <out-dir>
"""
import json
import pathlib
import sys
from datetime import datetime, timezone

import lg_store

# The analyser addresses values as "instanceId.key". A LangGraph channel name is the key; the task
# that wrote it is the instanceId — which is exactly the pairing the format wants.
GRAPH = lg_store.build()


def iso_to_millis(ts: str) -> int:
    return int(datetime.fromisoformat(ts).replace(tzinfo=timezone.utc).timestamp() * 1000)


def fmt_value(v):
    """Render a channel value the way the audit log renders one: scalars bare, lists in brackets."""
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, float):
        return f"{v:g}"
    if isinstance(v, list):
        return "[" + ", ".join(str(x) for x in v) + "]"
    return str(v)


def run_one(event: dict):
    """One input -> the ordered tasks it caused, grouped by super-step.

    Returns (steps, wall_span_millis) where steps is [[(node, result_dict), ...], ...] in super-step
    order. Within a step the list is STREAM ARRIVAL order, which is NOT a dispatch order — see the
    header. Keeping the grouping is what lets the translator be honest about that later.
    """
    steps: dict[int, list] = {}
    first = last = None
    for ev in GRAPH.stream(event, stream_mode="debug"):
        if ev.get("type") != "task_result":
            if ev.get("type") == "task":
                t = iso_to_millis(ev["timestamp"])
                first = t if first is None else first
            continue
        p = ev["payload"]
        if p.get("error"):
            steps.setdefault(ev["step"], []).append((p["name"], {"error": p["error"]}))
            continue
        steps.setdefault(ev["step"], []).append((p["name"], p.get("result") or {}))
        last = iso_to_millis(ev["timestamp"])
    return [steps[k] for k in sorted(steps)], max(0, (last or 0) - (first or 0))


def record_text(event: dict, steps, span_millis: int) -> str:
    """One canonical eventLogRecord. Concurrency is DECLARED IN THE RECORD rather than flattened
    away: a super-step holding more than one task gets a marker line naming the nodes that ran
    together, because the format has no field for it and silently emitting them in arrival order
    would present an invented order as an observed one."""
    t = event["at_millis"]
    lines = [
        "---",
        "eventLogRecord: ",
        f"    eventTime: {t}",
        f"    logTime: {t}",
        "    groupingId: null",
        "    event: TemperatureReading",
        f"    eventToString: TemperatureReading[unitId={event['unit_id']}, "
        f"celsius={event['celsius']:g}, vibrationRms={event['vibration_rms']:g}, "
        f"currentAmps={event['current_amps']:g}]",
        "    thread: langgraph.pregel",
        "    nodeLogs: ",
    ]
    for step in steps:
        if len(step) > 1:
            names = ", ".join(n for n, _ in step)
            lines.append(f"        - _concurrent: {{ nodes: {len(step)}, ran: {names}, "
                         f"ordered: false}}")
        for node, result in step:
            kv = ", ".join(f"{k}: {fmt_value(v)}" for k, v in result.items()) if result else ""
            lines.append(f"        - {node}: {{ {kv}}}")
    lines.append(f"    endTime: {t + max(1, span_millis)}")
    return "\n".join(lines)


def graphml(path: pathlib.Path):
    """The DECLARED graph, in the shape the analyser's topology view reads. get_graph() gives it
    directly — this is GraphSupport.DECLARED, so coverage ("declared minus observed") can exist for
    this source, unlike an engine that only reports what it saw."""
    g = GRAPH.get_graph()
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<graphml xmlns="http://graphml.graphdrawing.org/xmlns" '
           'xmlns:jGraph="http://www.jgraph.com/" '
           'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">',
           '    <key id="vertex_label" for="node" attr.name="nodeData" attr.type="string"/>',
           '    <key id="edge_label" for="edge" attr.name="edgeData" attr.type="string"/>',
           '    <graph edgedefault="undirected">']
    for n in g.nodes:
        style = "EVENTHANDLER" if n == "__start__" else "NODE"
        label = (f"id:{n}&#10;class:langgraph.node.{n}" if not n.startswith("__")
                 else f"&lt;&lt;EventHandle&gt;&gt;&#10;id:{n}&#10;class:langgraph.pregel.{n}")
        out += [f'        <node id="{n}">', '            <data key="vertex_label">',
                '                <jGraph:ShapeNode>',
                '                    <jGraph:Geometry height="70" width="160" x="20" y="20"/>',
                f'                    <jGraph:label text="{label}"/>',
                f'                    <jGraph:Style properties="{style}"/>',
                '                </jGraph:ShapeNode>', '            </data>', '        </node>']
    for i, e in enumerate(g.edges):
        out += [f'        <edge id="e{i}" source="{e.source}" target="{e.target}">',
                '            <data key="edge_label">',
                '                <jGraph:ShapeEdge><jGraph:Style properties="EDGE"/>'
                '</jGraph:ShapeEdge>',
                '            </data>', '        </edge>']
    out += ['    </graph>', '</graphml>']
    path.write_text("\n".join(out))


def main():
    out_dir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out_dir.mkdir(parents=True, exist_ok=True)

    events = lg_store.readings()
    records, concurrent_steps, total_tasks = [], 0, 0
    observed = set()
    for e in events:
        steps, span = run_one(dict(e))
        for st in steps:
            total_tasks += len(st)
            if len(st) > 1:
                concurrent_steps += 1
            observed.update(n for n, _ in st)
        records.append(record_text(e, steps, span))

    log = out_dir / "langgraph-coldchain.yaml"
    log.write_text("\n".join(records) + "\n")
    graphml(out_dir / "langgraph-coldchain.graphml")

    declared = {n for n in GRAPH.get_graph().nodes if not n.startswith("__")}
    print(json.dumps({
        "records": len(records),
        "tasks": total_tasks,
        "concurrent_super_steps": concurrent_steps,
        "declared_nodes": sorted(declared),
        "observed_nodes": sorted(observed),
        "never_ran": sorted(declared - observed),
        "log": str(log),
        "bytes": log.stat().st_size,
    }, indent=2))


if __name__ == "__main__":
    main()
