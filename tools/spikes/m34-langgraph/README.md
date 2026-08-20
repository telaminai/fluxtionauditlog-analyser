# M34.0 — the LangGraph spike

Throwaway. Not in the Maven build, not on any release path. It exists to answer one question:
**can a foreign engine's run be made legible by today's analyser, with no changes to it?**

Answer: yes. Findings: `docs/handoff/report_m34_0_spike.txt`.

The subject is the supermarket POC's cold-chain subsystem rebuilt on LangGraph — same domain, same
events, same derived quantities, different engine. A matched pair, because an arbitrary LangGraph app
would only show whether the analyser opens something; a pair shows what the instrument *loses* on the
way across.

```bash
python3 -m venv venv && ./venv/bin/pip install langgraph
./venv/bin/python lg_to_audit.py out
java -jar ../../../target/fluxtion-auditlog-analyser-*.jar out/langgraph-coldchain.yaml
```

Then in the analyser: `open {graphml: out/langgraph-coldchain.graphml}`, `coverage`, `series {expr:
"temperature_forecast.predicted_c", crossings: {above: 5}}`, `topology {step: 1}`.

Read the report before M34.1 — the ordering finding changes what the SPI has to carry.
