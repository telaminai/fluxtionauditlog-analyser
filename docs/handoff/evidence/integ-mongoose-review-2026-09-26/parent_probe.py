import os,subprocess,tempfile,shutil,json
from pathlib import Path
root=Path(os.environ.get("REVIEW_ROOT", ".")).resolve()
out=Path(os.environ.get("PROBE_OUTPUT", tempfile.mkdtemp(prefix="integration-parent-probe-")));out.mkdir(exist_ok=True)
java=Path(os.environ["JAVA_HOME"])/"bin"
deps=(root/"target/gate/test-classpath.txt").read_text().strip()
classes=out/"baseline-classes";shutil.copytree(root/"target/classes",classes,dirs_exist_ok=True)
prefix="src/main/java/telamin/fluxtion/audit/analyser/analyser/parse/"
probe=root/"docs/handoff/evidence/integ-mongoose-review-2026-09-26/IntegrationProbe.java"
results={}
for ref in ["5776e750","aa268458","a5bd6e0c"]:
 folder=out/ref;folder.mkdir(exist_ok=True);sources=[]
 for f in folder.rglob("*.class"):f.unlink()
 for name in ["HeapLogStore.java","RecordFramer.java"]:
  f=folder/name;f.write_bytes(subprocess.check_output(["git","-C",str(root),"show",ref+":"+prefix+name]));sources.append(str(f))
 cp=str(classes)+":"+deps
 compile=subprocess.run([str(java/"javac"),"-cp",cp,"-d",str(folder),*sources,str(probe)],text=True,capture_output=True)
 if compile.returncode:results[ref]="COMPILE ERROR\n"+compile.stderr
 else:
  run=subprocess.run([str(java/"java"),"-cp",str(folder)+":"+cp,"IntegrationProbe"],cwd=root,text=True,capture_output=True)
  results[ref]=run.stdout+run.stderr+"exit="+str(run.returncode)
 print(ref,results[ref],flush=True)
(out/"results.json").write_text(json.dumps(results,indent=2))
