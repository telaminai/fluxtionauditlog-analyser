# Complete staged-session feedback, including both addenda

Read [ANALYSER-FEEDBACK.md](ANALYSER-FEEDBACK.md) to its end: issues 1–21, the correction to the
event counts, successful same-path reopen, and additional marker evidence. Also read
[AUTHORING-DOCS-FEEDBACK.md](AUTHORING-DOCS-FEEDBACK.md). Both are preserved byte-for-byte.

This is a second snapshot. The earlier ten-issue packet remains unchanged beside it. The
[review and decisions](../../review_staged_spring_feedback_2026_09_19.md) distinguish reported
observations, source-confirmed behaviour, independent probes and proposed changes.

`manifest.json` records hashes and original locations for 60 files, including all nine screenshots,
the original and hosted audit runs, CSV inputs, application source, host configuration and scripts,
ownership record, and the documentation the participant actually had. `producer-results/` contains
files from `target/` so the evidence is not silently ignored as build output. The original report's
absolute paths identify the staged project; use the relative copies here for durable inspection.
The participant's original project README is preserved as `project-README.md`.

All nine screenshots have been visually inspected; six are new since the earlier snapshot. Text
artifacts pass the public-repo sweep. Private provider jars, credentials, classpath, `.mcp.json`,
compiled classes and the full generated processor Java are omitted. These files are an evidence
snapshot, not a portable runnable project; the copied scripts still depend on local provisioning.

## Independent probes

`review-probes/` contains two small Java probes run against the staged analyser jar, their results,
and the response summary from the playground's existing headless scaffold endpoint. No paid key
was used, no host was rebooted, and the participant's running analyser was not changed.

Compile the probes with JDK 21 and the analyser jar, then run `MarkerProbe` with paths to each of
`evidence/{trades,mongoose-run1,mongoose-run2,mongoose-paced}/audit.yaml`. Run `LogCompare` with
the first three paths in that order. Its normalization removes only top-level `logTime`, `eventTime`,
`endTime` fields and thread labels, preserving the remaining record text and node order. The format
is legacy audit text, not strict YAML; generic YAML parsing is not a valid comparison method here.

The marker probe shows every firing record index and event type, making extra markers traceable.
The log comparison confirms 19 identical business records after the stated normalization and only
two differing control records between the unpaced hosted runs. This does not establish general replay
correctness or prove completion from the sample's two-second idle heuristic.

The scaffold probe requested `GET /start/scaffold?template=fluxtion-spring-mongoose` from the staged
playground and received a 22-entry ZIP. Only its digest, entry list and relevant listener/boot lines
are retained. The ZIP itself is not included and this probe did not build the downloaded project.
