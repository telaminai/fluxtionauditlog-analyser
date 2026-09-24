from pathlib import Path
import subprocess,json,sys,xml.etree.ElementTree as E
names=['PersonAtTheScreenFrameTest','NamedGraphAndMenuSpotlightFrameTest']
out=Path(sys.argv[1]); results=[]
for i in range(10):
 for n in names:
  for p in Path('target/surefire-reports').glob('TEST-*.'+n+'.xml'):p.unlink()
 r=subprocess.run(['mvn','-q','test','-Djava.awt.headless=false','-Dtest='+','.join(names)],capture_output=True,text=True)
 suites=[]
 for n in names:
  paths=list(Path('target/surefire-reports').glob('TEST-*.'+n+'.xml'));assert len(paths)==1,(n,r.stdout,r.stderr)
  root=E.parse(paths[0]).getroot();suites.append({'name':n,**{k:int(root.get(k,0)) for k in ['tests','failures','errors','skipped']},'problems':[{'test':t.get('name'),'kind':e.tag,'message':e.get('message')} for t in root.findall('testcase') for e in t if e.tag in ['failure','error','skipped']], 'focus':[e.text for e in root.iter('system-out') if e.text and 'focus attempt' in e.text]})
 results.append({'run':i+1,'exit':r.returncode,'suites':suites,'output':r.stdout+r.stderr});out.write_text(json.dumps(results,indent=2)+'\n');print(i+1,[(s['name'],s['tests'],s['failures'],s['errors'],s['skipped']) for s in suites],flush=True)
