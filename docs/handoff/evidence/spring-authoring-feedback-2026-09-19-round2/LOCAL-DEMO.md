# Local Spring authoring rehearsal

This is a worked example, provisioned with branch artifacts and a local generation provider.
It is not the post-publication fresh-download acceptance run. The cached dependency BOM is
retained from the acceptance fixture; the builder and starter use a distinct local demo version.

Read RUNBOOK.md and authoring-docs/contract.md. Use JDK 21 (the scripts set it).
From this directory run `./validate.sh`, `./generate.sh`, then `./run-scenario.sh`.
After dependency changes run `./setup.sh` again. No paid generation key is used.
The scripts source .demo-env.sh, a local-only wrapper around the downloaded workflow.

The scenario checks seven states and exactly two outputs: Checked(10,1), Checked(40,1).
Filter 9 is ignored; pause stops processing; exported reset clears state and resumes it.
Results: evidence/latest/audit.yaml, state.csv, checked.csv. Repeat with
`./run-scenario.sh evidence/another-run` to retain a comparison run.

Open an MCP-capable client in this directory and enable the project .mcp.json entry.
The client must support that configuration format; it is not a global client registration.
Ask it to read this file, call analyser_context, predict a scenario and show evidence
in the analyser. Its project shell tools perform builds; the analyser renders results.
The demo analyser has its own home and recent-project list. Reports/screenshots may
be written under evidence/. Your regular analyser preferences are separate.
The profile authorises this sample's whole project directory for source, XML,
ownership-record and diagnostic reads so the analyser can check producer freshness.

Playground: http://127.0.0.1:5174/start
Local contract: http://127.0.0.1:5174/spring-authoring/contract.md
Docs: http://127.0.0.1:8000/fluxtionauditlog-analyser/spring-authoring/
For the browser preview import src/main/fluxtion/designer/application-context.xml.
A new browser download retains the unpublished release pin; it is not automatically
provisioned like this sample. That installation path remains a release gate.

Start again: /Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser/tools/start-spring-demo.sh --root /private/tmp/fluxtion-spring-demo
Stop services: /Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser/tools/stop-spring-demo.sh --root /private/tmp/fluxtion-spring-demo
Restart preserves project edits, the original evidence and analyser preferences.
Teardown stops owned services only; it retains files and local Maven test artifacts.
