# Round 07 environment

- **Date:** 2026-09-01
- **Builder:** fluxtion-builder 1.0.66 (released; PARALLEL is the emission default)
- **Runtime:** fluxtion-runtime 1.0.14
- **API key:** present (`~/.fluxtion/fluxtion.apiKeyFile`), so remote source generation is reachable
- **Analyser:** NOT in the loop this round. Round 01–06's recorded design defect was that the analyser
  was never reachable; this round does not fix that, because the variable under test is the doc set and
  adding a second change would confound it. Named so it is not mistaken for an oversight.
- **Agents:** 6 fresh-context general-purpose agents, 3 per arm, launched in parallel, each confined to
  its own project directory and told not to look at any other project.
- **Scaffold:** identical `pom.xml` and empty `src/main/java/com/acme/depot` in all six.
- **Doc sets:** arm A 157 lines, arm B 190 lines. `DOC-DELTA.diff` is the exact difference.
