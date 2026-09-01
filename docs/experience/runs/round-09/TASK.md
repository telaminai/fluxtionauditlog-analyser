# Build: an intraday margin engine for a clearing house

Package `com.acme.clearing`. **This specification describes BEHAVIOUR. It does not tell you what the
nodes are, how many there should be, or how they depend on each other — that is your design.**

## What arrives

Four event types, in any order and any number of times:

- a **trade**: member, symbol, signed quantity, price
- a **mark price**: symbol, price
- a **margin parameter**: symbol, initial margin percentage
- a **collateral deposit**: member, amount

## What must be true

**M1 — the requirement.** A member's margin requirement is, summed over every symbol they hold:
`|net position| × mark price × initial margin percentage`. Net position is the signed sum of their trades
in that symbol.

**M2 — the call.** When a member's requirement exceeds their posted collateral, a margin call is raised
naming the member and the shortfall. When it does not, no call exists for that member.

**M3 — paperwork is not a market event.** Re-publishing a margin parameter whose value has not changed
must **not**, by itself, raise a margin call or re-issue an existing one. Only a change in positions,
prices or collateral may do that.

**M4 — one report per cycle.** Each incoming event produces **exactly one** clearing-report record,
however many intermediate values changed inside that cycle.

**M5 — the report is last.** In every cycle, the clearing report must be the **final** node recorded in
that cycle's `nodeLogs`.

**M6 — no mixed generations.** Every value in a single report record must describe the same event. A
report may not pair a requirement computed from this event with a collateral figure from a previous one.

## What you must deliver

1. The graph, built with a `FluxtionGraphBuilder`, class `ClearingProcessor`, package
   `com.acme.clearing.generated`, output `src/main/java`, resources `src/main/resources`.
2. **Audit logging enabled**, and a `Main` you can run that feeds a scenario through the processor and
   writes the audit log to `logs/clearing-audit.yaml`.
3. **Evidence.** For each of M1–M6, quote the audit-log lines that demonstrate it. M3, M4, M5 and M6
   cannot be shown by reading your source — they are claims about what actually happened, so show the
   records.

## Tools available to you

- `mvn process-classes` — compiles and regenerates. Compiler diagnostics carry a code, a rule, a reason
  and a suggested fix; read them rather than guessing.
- The **generated processor** under `src/main/java/com/acme/clearing/generated/` — readable Java showing
  the exact dispatch order and the guard for every node. A green build is not a correct graph.
- The **audit log** you produce — the record of what actually ran, in order, per event.
- The **Fluxtion audit log analyser**, if you want it:
  `java -jar /Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser/target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar --rest`
  starts it with a localhost REST endpoint; it can open your YAML plus the generated `.graphml` and answer
  `context`, `coverage` and `topology` queries about the run.

## Done when

The build is green, the scenario has been run, and you have quoted audit-log evidence for M1–M6.
