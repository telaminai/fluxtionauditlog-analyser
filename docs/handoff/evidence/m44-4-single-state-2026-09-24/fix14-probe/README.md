# The re-review's probes, re-run on the N1/N2 fix (set 14)

The re-review's probes in `../rereview-probe/` are preserved unchanged. They were first re-run on `93046a48`, and
reproduced there (RESULTS, set 14). These are their runs on the fixed branch, built with `mvn -q package -DskipTests`,
JDK 21.

| File | What it shows |
|---|---|
| `src/…/RereviewProbeAfterN2.java`, `output.txt` | The re-review's `RereviewProbe`, with ONE change: its two `ReportSeriesPicture.of` calls drop the view-filter argument the N2 fix removed. Nothing else differs. **N1:** `ATOMIC_AFTER` keeps `focus=true, contextDepth=1, visibleNodes=1`, and the refusal says nothing was changed. **N2:** the report draws 0 STRICT points, where the verb finds 0, and 1 point in the call's `2000–2000` scope, where the verb finds 1; each caption states the resolution and scope. |
| `pdf-output.txt` | The re-review's unchanged `PdfProbe.py`, run on the fixed jar. The export directory is replaced with `<artifact-directory>`. |
| `focus-and-series.pdf`, `focus-and-series-page1.png`, `focus-and-series-page2.png`, `render-pages.swift` | The exported PDF, unedited, and both pages rendered with macOS PDFKit (`render-pages.swift`; Poppler is not installed here), then inspected. **Page 1:** the one-node `review-focus`, captioned "1 node". **Page 2:** one point at value 3, a time axis of 00:00:02.000–00:00:02.001, captioned `rootNode.v · 1 point · STRICT · scope: the call's filter: logTime 2000–2000; the view filter does not apply`. **Seen while inspecting, not changed here:** a lone sample is drawn as a small dot on the plot's left edge, so it is easy to miss. That is how `ChartPanel` draws one sample, independent of N2. |
