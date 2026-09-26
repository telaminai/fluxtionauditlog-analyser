from pathlib import Path
import subprocess,os,hashlib,json,xml.etree.ElementTree as E
site=Path('src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/GraphTabs.java');original=site.read_bytes();scratch=Path('target/m687-review');scratch.mkdir(parents=True,exist_ok=True);copy=scratch/'GraphTabs.copy';copy.write_bytes(original)
env=os.environ.copy()
results=[]
def run(label):
 for p in Path('target/surefire-reports').glob('TEST-*.xml'):p.unlink()
 with (scratch/('detached-'+label+'.log')).open('w') as out:
  r=subprocess.run(['mvn','-q','test','-Dtest=IdentityMarkFrameTest,IdentityMarkSurfacesTest','-Djava.awt.headless=false','-DargLine=-Djava.awt.headless=false'],env=env,stdout=out,stderr=subprocess.STDOUT)
 roots=[E.parse(p).getroot() for p in Path('target/surefire-reports').glob('TEST-*.xml')]
 row={'label':label,'exit':r.returncode,**{k:sum(int(x.get(k,0)) for x in roots) for k in ['tests','failures','errors','skipped']}}
 results.append(row);print(row,flush=True)
try:
 run('baseline')
 anchor=b'        north.add(identityBanner, BorderLayout.SOUTH);'
 assert original.count(anchor)==1
 site.write_bytes(original.replace(anchor,b'        // Review control: banner no longer belongs to the visible panel.'))
 run('detached')
finally:
 site.write_bytes(copy.read_bytes());assert site.read_bytes()==original
 subprocess.run(['cmp',str(copy),str(site)],check=True)
 run('restored')
 (scratch/'hand-control.json').write_text(json.dumps({'runs':results,'sha256':hashlib.sha256(original).hexdigest(),'restoredByteIdentical':site.read_bytes()==original},indent=2))
