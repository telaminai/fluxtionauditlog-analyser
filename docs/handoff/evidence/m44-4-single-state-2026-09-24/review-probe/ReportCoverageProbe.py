"""Constructed review: compare action and exported coverage on the same retained foreign pair.
Run from the repository root against the packaged subject. Uses an isolated home; no model or key.
"""
import importlib
import os
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path("tools").resolve()))
v = importlib.import_module("verify-m68-1-coverage")
Analyser = importlib.import_module("verify-m46-agent-api").Analyser
jar = str(Path("target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar").resolve())
with tempfile.TemporaryDirectory(prefix="review-report-") as scratch:
    root = Path(scratch)
    home = root / "home"
    exchange = root / "exchange"
    exchange.mkdir()
    cfg = home / ".fluxtion-analyser" / "config"
    cfg.parent.mkdir(parents=True)
    cfg.write_text(f"assistant.exports=true\nassistant.exportDir={exchange}\n")
    log = root / "constructed.yaml"
    v.constructed_log(str(log), [["foreignOnly"]])
    with Analyser(jar, str(home), "review-report") as app:
        context = v.open_pair(app, str(log))
        coverage = app.act("coverage")
        print("ACTION", coverage)
        pdf = exchange / "coverage.pdf"
        reply = app.act("report", name="constructed-review", title="Constructed coverage review",
                        sections=[{"kind":"table","call":{"verb":"coverage"}}], path=str(pdf))
        print("REPORT_OK", reply.get("ok"), "PDF_EXISTS", pdf.exists())
        text = pdf.read_bytes().decode("latin-1") if pdf.exists() else ""
        for line in text.splitlines():
            if any(term in line for term in ("declared", "covered", "ratio", "coverage", "Coverage", "foreignOnly", "REFUSED", "not declared")):
                print("PDF", line)
