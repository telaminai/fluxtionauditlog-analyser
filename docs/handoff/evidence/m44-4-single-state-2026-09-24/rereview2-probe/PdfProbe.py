"""N2 only: two constructed counterexamples through the packaged app's report action."""
import importlib,json,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path('tools').resolve()))
v=importlib.import_module('verify-m68-1-coverage')
Analyser=importlib.import_module('verify-m46-agent-api').Analyser
jar=str(Path('target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar').resolve())
out=Path(sys.argv[1]).resolve();out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='m44-r2-pdf-') as scratch:
 home=Path(scratch)/'home';cfg=home/'.fluxtion-analyser'/'config';cfg.parent.mkdir(parents=True)
 cfg.write_text(f'assistant.exports=true\nassistant.exportDir={out}\n')
 log=out/'constructed-series.yaml'
 nodes=['    - nodeA: {x: 1}\n    - rootNode: {v: 1}\n','    - nodeB: {y: 2}\n    - rootNode: {v: 3}\n','    - nodeA: {x: 4}\n    - rootNode: {v: 2}\n']
 log.write_text(''.join(f'eventLogRecord:\n  event: Tick\n  logTime: {t}\n  nodeLogs:\n{n}---\n' for t,n in zip([1000,2000,3000],nodes)))
 with Analyser(jar,str(home),'pdf') as app:
  opened=app.act('open',log=str(log));v.settle(app);assert opened['ok'],opened
  # The view selects a different point: each report section must still honour its own call.
  view=app.act('filter',**{'from':3000,'to':3000});assert view['ok'],view
  print('VIEW_FILTER',json.dumps(view))
  calls=[{'expr':'nodeA.x + nodeB.y','resolve':'STRICT'},{'expr':'rootNode.v','filter':{'from':2000,'to':2000}}]
  for label,call in zip(['STRICT','FILTERED'],calls):
   result=app.act('series',**call);print(label,json.dumps(result))
  result=app.act('report',name='review-series',title='Constructed N2 review: the stored calls decide scope',sections=[{'kind':'series','call':c} for c in calls],path=str(out/'series-counterexamples.pdf'))
  assert result['ok'],result
  print('REPORT',json.dumps({k:x for k,x in result.items() if k!='report'}).replace(str(out),'<artifact-directory>'))
  assert (out/'series-counterexamples.pdf').exists()
  print('PDF_EXISTS true')
