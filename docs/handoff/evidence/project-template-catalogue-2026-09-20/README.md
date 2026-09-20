# Full-catalogue picker witness — 2026-09-20

The actual Swing picker was driven by `TemplateCatalogueFrameTest` against all 14 entries from
playground journey branch `0e7bd58` (`web/static/starter-templates/index.json`). The exact input is
`catalogue.json`; SHA-256 values are in `manifest.json`. No live website or download was used.

The test selects every entry, checks the displayed disclosure against the parsed entry, then captures
the first recommended entry. The screenshot was read: build and regeneration prerequisites, declared
agent entry files and the recommendation caveat are visible together. The list scrolls to reach the
last entry. Closing the dialog does not select a template or start a download.

Run with Java 21 and a display:

```sh
mvn -q -o -Dtest=TemplateCatalogueFrameTest -Djava.awt.headless=false \
  -DargLine=-Djava.awt.headless=false \
  -Djourney.catalogue=docs/handoff/evidence/project-template-catalogue-2026-09-20/catalogue.json test
```

Result: one test, zero failures/errors/skips. The default CI fixture separately exercises a smaller
catalogue containing absent, empty and populated bootstrap declarations. This witness establishes
picker rendering and selection disclosure only. End-to-end untagged download/open and independent
review remain open. Deploy default-on producer profiles before releasing the expanded picker.
