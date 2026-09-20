import importlib.util,json,os,shutil,hashlib,tempfile
from pathlib import Path

repo=Path('/Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser')
src=Path('/private/tmp/fluxtion-spring-demo/project')
work=Path(tempfile.mkdtemp(prefix='spring-reopen-ui-',dir='/private/tmp'))
project=work/'project'; home=work/'home'; home.mkdir()
shutil.copytree(src,project,ignore=shutil.ignore_patterns('.mcp.json','.demo-env.sh','.git'))
profile=project/'.analyser/project.fluxtion-settings'
profile.write_text(profile.read_text().replace(str(src),str(project)).replace('/tmp/fluxtion-spring-demo/project',str(project)))
cfg=home/'.fluxtion-analyser'; cfg.mkdir()
(cfg/'config').write_text(f'assistant.rest=true\nassistant.exports=true\nassistant.exportDir={work}\nwindowW=1440\nwindowH=950\n')
spec=importlib.util.spec_from_file_location('probe_base',repo/'tools/verify-m46-agent-api.py')
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
os.environ['PATH']='/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home/bin:'+os.environ['PATH']
original=hashlib.sha256((src/'.analyser/project.fluxtion-settings').read_bytes()).hexdigest()
print('Isolated evidence:',work,flush=True)
def saved(label,value):
 (work/(label+'.json')).write_text(json.dumps(value,indent=2))
 return value
def summary(c):
 return {'project':c.get('project'),'runbooks':c.get('runbooks'),
 'log':c.get('log'), 'design':c.get('design'), 'graphs':c.get('graphs'),
 'source':c.get('source'), 'graphPairing':c.get('graphPairing')}
with m.Analyser('/private/tmp/fluxtion-spring-demo/analyser.jar',str(home),'reopen') as a:
 def act(label,verb,**params):
  r=saved(label,a.act(verb,**params))
  if not r.get('ok'):print(label,json.dumps(r)[:600],flush=True)
  return r
 act('01-open-project','open',project=str(project))
 act('02-open-log','open',log=str(project/'evidence/latest/audit.yaml'))
 a.settled_context()
 act('03-open-topology','open',graphml=str(project/'src/main/resources/com/example/myapp/generated/MyProcessor.graphml'),processor='com.example.myapp.generated.MyProcessor')
 act('04-open-design','open',design=str(project/'src/main/fluxtion/designer/application-context.xml'))
 act('05-open-result','open',diagnostics=str(project/'target/fluxtion-validation.json'))
 before=saved('06-before',a.context())
 act('07-before-image','screenshot',path=str(work/'before.png'))
 act('08-close','open',close='project')
 act('09-reopen','open',project=str(project))
 after=saved('10-after',a.context())
 act('11-after-image','screenshot',path=str(work/'after.png'))
 act('12-rebind-log','open',log=str(project/'evidence/latest/audit.yaml'))
 rebound=saved('13-rebound',a.settled_context())
 for tag,c in [('before',before),('after',after),('rebound',rebound)]:
  saved(tag+'-summary',summary(c))
  print(tag,'project=',c.get('project',{}).get('active'),'runbooks=',len(c.get('runbooks',[])),
        'log=',bool(c.get('log')),'graphs=',len(c.get('graphs',[])),
        'design=',json.dumps(c.get('design',{}))[:200],flush=True)
 print('Project declaration keys before/after:',{k:before.get(k)==after.get(k) for k in ['project','runbooks','source']},flush=True)
assert original==hashlib.sha256((src/'.analyser/project.fluxtion-settings').read_bytes()).hexdigest(),'live profile changed during probe'
print('Live profile unchanged. Probe instance stopped.',flush=True)
