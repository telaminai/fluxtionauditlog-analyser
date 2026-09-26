"""Constructed review: real exported focus and series pages, without a model or key.
Run from the repository root. The artifact directory is supplied as argv[1].
"""
import importlib
import json
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path('tools').resolve()))
v = importlib.import_module('verify-m68-1-coverage')
Analyser = importlib.import_module('verify-m46-agent-api').Analyser
jar = str(Path('target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar').resolve())
out = Path(sys.argv[1]).resolve()
out.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='m44-review-pdf-') as scratch:
    home = Path(scratch) / 'home'
    cfg = home / '.fluxtion-analyser' / 'config'
    cfg.parent.mkdir(parents=True)
    cfg.write_text(f'assistant.exports=true\nassistant.exportDir={out}\n')
    log = out / 'constructed-series.yaml'
    log.write_text(''.join('eventLogRecord:\n  event: Tick\n  logTime: %d\n  nodeLogs:\n    - rootNode: {v: %d}\n---\n' % (t,x)
                          for t,x in ((1000,1),(2000,3),(3000,2))))
    with Analyser(jar, str(home), 'pdf') as app:
        app.act('open', log=str(log), graphml=v.GRAPH)
        v.settle(app)
        focus = app.act('topology', select='rootNode', scope='node', focus=True, saveFocusAs='review-focus')
        print('FOCUS', json.dumps(focus))
        direct = app.act('series', expr='rootNode.v', filter={'from':2000,'to':2000})
        print('DIRECT_SERIES', json.dumps(direct))
        result = app.act('report', name='review-series', title='Constructed review: focus and requested series',
                         sections=[{'kind':'topology','focus':'review-focus'},
                                   {'kind':'series','call':{'expr':'rootNode.v','filter':{'from':2000,'to':2000}}}],
                         path=str(out / 'focus-and-series.pdf'))
        # Avoid putting the scratch/home path or endpoint credentials in public evidence.
        print('REPORT', json.dumps({k:v for k,v in result.items() if k != 'report'}))
        print('PDF_EXISTS', (out / 'focus-and-series.pdf').exists())
