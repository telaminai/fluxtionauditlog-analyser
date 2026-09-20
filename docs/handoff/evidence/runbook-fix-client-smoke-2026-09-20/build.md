# Build and verify

From the project root:

```sh
./mvnw package
./run-server.sh
```

Use `./run-server.sh` from the project root; it carries this project's launch configuration.

Read README for the chosen compile mode and backend prerequisites. Successful compilation does not establish business correctness.

Write expected results from the scenario inputs before running. Compare actual audit values and sink outputs against an independent calculation. For a new report, test its arithmetic, grouping and boundaries separately from unchanged existing behaviour; producing a file is not a correctness check.

Review the generated source and the selected audit configuration. Absence of node log output is not proof a node did not execute. Capture must be configured before there can be evidence. Read and preserve outputs before cleaning build files.
