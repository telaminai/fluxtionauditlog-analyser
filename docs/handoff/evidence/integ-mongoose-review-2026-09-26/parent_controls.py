import subprocess,sys,os,hashlib,json,shutil,re,time
from pathlib import Path
import xml.etree.ElementTree as E
root=Path.cwd();out=Path(sys.argv[1]);out.mkdir(exist_ok=True)
P='src/main/java/telamin/fluxtion/audit/analyser/analyser/'
def parent(ref,p):return subprocess.check_output(['git','-C',os.environ.get('SOURCE_REPO','.'),'show',ref+':'+p],text=True)
def method(text,anchor):
 start=text.index(anchor);end=text.index('\n    }',start)+6
 return text[start:end]
def rep(text,a,b):
 assert text.count(a)==1,(a[:80],text.count(a));return text.replace(a,b)
def parent_method(p,anchor,ref):
 text=Path(p).read_text();return rep(text,method(text,anchor),method(parent(ref,p),anchor))
heap=P+'parse/HeapLogStore.java';diag=P+'parse/ProducerDiagnostics.java';cov=P+'topology/CoverageService.java';frame=P+'ui/MainFrame.java'
parse_tests='HeapLogStoreFollowIdentityTest,FollowPendingBytesTest,ProducerFramingTest,EmptyLogAndRecordKeyDiagnosticsTest,ProducerDiagnosticsTest,ByteOrderMarkSitesTest'
cov_tests='CoveragePerNodeLevelTest,CoverageServiceTest,CoverageScopeTest,CoverageCaptureTest,ControlAddressAndScopeTest,PerNodeLevelChangesTest'
frame_tests='StatusLineTest,ProducerFramingTest,NoLogDesignJourneyFrameTest,PairingDuringLoadFrameTest,StatusExplanationSurvivesFrameTest'
def noempty():
 text=Path(diag).read_text();start=text.index('        if (idx.size() == 0 && !pending) {');end=text.index('        boolean noRecords',start)
 return text[:start]+text[end:]
cases=[
 ('heap-ma-parent',heap,lambda:parent_method(heap,'    public int appendFrom(Path path)', 'a5bd6e0c'),parse_tests),
 ('heap-main-parent',heap,lambda:parent_method(heap,'    public int appendFrom(Path path)', 'aa268458'),parse_tests),
 ('diagnostics-ma-empty-return',diag,lambda:rep(Path(diag).read_text(),'if (idx.size() == 0 && !pending) {','if (idx.size() == 0) {'),parse_tests),
 ('diagnostics-main-no-empty',diag,noempty,parse_tests),
 ('coverage-ma-unbounded',cov,lambda:rep(Path(cov).read_text(),'PerNodeLevelChanges.of(store, rows)','PerNodeLevelChanges.of(store)'),cov_tests),
 ('coverage-main-no-annotation',cov,lambda:rep(Path(cov).read_text(),'PerNodeLevelChanges.of(store, rows)','PerNodeLevelChanges.of(null, 0)'),cov_tests),
 ('frame-ma-parent',frame,lambda:parent_method(frame,'    private void refreshFollowDiagnostics(', 'a5bd6e0c'),frame_tests),
]
def mainrefresh():
 text=Path(frame).read_text(); old=method(text,'    private void refreshFollowDiagnostics(')
 # main's inline successful-poll policy, put in the integration seam without a signature change.
 par=parent('aa268458',frame);start=par.index('        String pending = store.pendingFrameText();',par.index('private void pollFollow()'))
 end=par.index('        if (added == 0)',start)
 body=par[start:end]
 return rep(text,old,'    private void refreshFollowDiagnostics(telamin.fluxtion.audit.analyser.analyser.parse.StreamEnd end, int added, boolean readFailed) {\n'+body+'    }')
cases.append(('frame-main-parent-policy',frame,mainrefresh,frame_tests))
env=dict(os.environ)
assert env.get('JAVA_HOME'), 'Set JAVA_HOME to JDK 21'
def run(label,tests):
 for f in Path('target/surefire-reports').glob('TEST-*.xml'):f.unlink()
 cmd=['mvn','-q','test','-Dtest='+tests,'-Dsurefire.failIfNoSpecifiedTests=false']
 if 'FrameTest' in tests:cmd+=['-Djava.awt.headless=false','-DargLine=-Djava.awt.headless=false']
 t=time.monotonic()
 with open(out/(label+'.log'),'w') as o:r=subprocess.run(cmd,env=env,stdout=o,stderr=subprocess.STDOUT,timeout=300)
 counts=dict.fromkeys(['tests','failures','errors','skipped'],0);fail=[]
 for f in Path('target/surefire-reports').glob('TEST-*.xml'):
  e=E.parse(f).getroot()
  for k in counts:counts[k]+=int(e.get(k,0))
  for c in e.findall('testcase'):
   for kind in ['failure','error','skipped']:
    v=c.find(kind)
    if v is not None:fail.append({'test':e.get('name').split('.')[-1]+'#'+c.get('name'),'kind':kind,'message':v.get('message','')[:600]})
 result={'exit':r.returncode,'counts':counts,'assertions':fail,'seconds':round(time.monotonic()-t,2)}
 (out/(label+'.json')).write_text(json.dumps(result,indent=2));return result
results=[]
for name,p,mutate,tests in cases:
 if len(sys.argv)>2 and name not in sys.argv[2:]:continue
 base=run(name+'-baseline',tests)
 if base['exit'] or base['counts']['skipped']:
  print(name,'baseline not green',base,flush=True);results.append({'name':name,'baseline':base});continue
 path=Path(p);original=path.read_bytes();backup=out/(name+'.original');backup.write_bytes(original)
 try:
  path.write_text(mutate());red=run(name+'-mutated',tests)
 finally:
  path.write_bytes(backup.read_bytes());subprocess.run(['cmp',str(path),str(backup)],check=True)
 restored=run(name+'-restored',tests)
 entry={'name':name,'baseline':base,'mutated':red,'restored':restored,'sha256':hashlib.sha256(original).hexdigest(),'byteIdentical':path.read_bytes()==original}
 results.append(entry);(out/'summary.json').write_text(json.dumps(results,indent=2))
 print(name,red['counts'],'exit',red['exit'],'restore',restored['counts'],flush=True)
print('completed',len(results),flush=True)
