# Work on this graph

The project is the state between sessions. Start by reading fluxtion-authoring.json, the Spring XML it names, and the node sources.

1. Run ./setup.sh once while online to fetch the tool and resolve the project classpath. Re-run it after dependency changes.
2. Edit the Spring XML, then run ./validate.sh. This checks XML structure and declarations only; it does not load your classes. Read target/fluxtion-validation.json and repair ERROR findings.
3. Run ./generate.sh. Reconciliation plans source edits before writing. A conflict leaves source and the authoring record unchanged; the change lists in target/fluxtion-reconciliation.json describe a plan, not edits applied. Implemented method bodies are kept.
4. Read target/fluxtion-run.json before consuming diagnostics. validate or regenerate starts a new attempt and clears previous stage entries; preflight and build continue it. The stage options name effective paths and per-run reconciliation flags; these do not rewrite persisted policy. Compare each stage's outputs (or inputs when outputs are absent) with the present XML, source and record hashes. Error reports can be current too. Compiler diagnostics at target/classes/fluxtion-diagnostics.json belong to this attempt only when build.compilerRan is true and those hashes still match. Authoring builds request this file even for a clean compiler result, whose diagnostics array is empty. A javac failure before generation leaves compilerRan false.
5. Read the generated processor and changed node sources. Check event dispatch, trigger ordering and service callbacks against your design. Then run ./run.sh.

The generated Maven command is package. The builder chooses local providers or HTTP; only the RapidAPI route probes its gateway. Custom HTTP authentication stays with the client. A local provider and all build dependencies must already be installed for air-gapped generation.

Keep fluxtion-authoring.json committed. It records ownership history; do not replace its stub hashes with implemented bodies. Without a record the project is treated as foreign and reconciliation defaults off. Disabled reconciliation is reported as skipped and leaves class checks to the compiler. A foreign project gains its first authoring record only after a successful regenerate --reconcile run; --reconcile opts in to adds and adoption, never ownership of existing code.

A new event or service type under the project base package may be created as an empty shell. Inspect and implement it before relying on it; these type files are not yet automatically removed when a declaration is withdrawn.

Use JAVA_TOOL_OPTIONS for Java system-property overrides that must reach both preflight and Maven. Never put keys in this project or transcripts. The compiler's class/model checks occur during generation, after the XML-only validation step.
