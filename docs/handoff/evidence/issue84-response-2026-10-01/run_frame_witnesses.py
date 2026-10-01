#!/usr/bin/env python3
"""Maintained issue #84 frame witnesses against an explicitly named shaded jar, JDK 21, DEMO only.
Display invocations must be wrapped in lockf -k /tmp/fluxtion-analyser-display.lock.
Production source is untouched. Results, compiled probes and synthetic homes go under target.
"""
import argparse, hashlib, json, os, re, subprocess, sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument("kind",choices=["bundle","walk"])
parser.add_argument("--jar", required=True)
parser.add_argument("--output", required=True)
parser.add_argument("--method",action="append",default=[])
args=parser.parse_args()
work=Path.cwd(); dest=Path(args.output).resolve(); dest.mkdir(parents=True,exist_ok=True)
cpfile=dest/"dependencies.txt"
subprocess.run(["mvn","-o","-q","dependency:build-classpath","-Dmdep.includeScope=test","-Dmdep.outputFile="+str(cpfile)],check=True)
deps=cpfile.read_text().strip()
version=re.search(r"junit-platform-engine-([0-9][^/:]*)\.jar",deps).group(1)
launcher=Path.home()/".m2/repository/org/junit/platform/junit-platform-launcher"/version/("junit-platform-launcher-"+version+".jar")
assert launcher.is_file(),"matching cached JUnit launcher required: "+version
jar=Path(args.jar).resolve()
assert jar.is_file(),"build the selected jar with mvn -o -q package -DskipTests first"
classes=dest/"classes"; classes.mkdir(exist_ok=True)
# Test helpers first; application classes come only from the explicitly selected shaded jar.
cp=":".join([str(classes),str(work/"target/test-classes"),str(jar),deps,str(launcher)])
name="Issue84BundleFrameTest" if args.kind=="bundle" else "Issue84JavaWalkFrameTest"
sources=[str(work/"src/test/java/telamin/fluxtion/audit/analyser/analyser/ui"/(name+".java")),"tools/gate/GateLauncher.java"]
subprocess.run(["javac","--release","21","-cp",cp,"-d",str(classes),*sources],check=True)
selectors=["telamin.fluxtion.audit.analyser.analyser.ui."+name+("#"+m if m else "") for m in (args.method or [""])]
rowsfile=dest/(args.kind+"-results.jsonl"); home=dest/"bootstrap-home"; home.mkdir(exist_ok=True)
cmd=["java","-Djava.awt.headless=false","-Duser.home="+str(home),"-Dissue84.captureDir="+str(dest),"-cp",cp,"GateLauncher",str(rowsfile),*selectors]
proc=subprocess.run(cmd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180)
(dest/(args.kind+"-output.log")).write_text(proc.stdout)
rows=[json.loads(line) for line in rowsfile.read_text().splitlines()] if rowsfile.exists() else [{"class":name,"method":None,"kind":"error","message":proc.stdout}]
tests=[r for r in rows if r["method"] is not None]
counts={"total":len(tests),"failures":sum(r["kind"]=="failure" for r in rows),"errors":sum(r["kind"]=="error" for r in rows),"skips":sum(r["kind"]=="skipped" for r in rows)}
print(proc.stdout)
print(json.dumps({"mode":"RAN","sourceHead":subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip(),"jarSha256":hashlib.sha256(jar.read_bytes()).hexdigest(),"command":"lockf -k /tmp/fluxtion-analyser-display.lock python3 EVIDENCE/run_frame_witnesses.py "+" ".join(sys.argv[1:]),"counts":counts,"results":rows},indent=2))
# Deliberately retain failed witnesses; failures are review evidence, never converted to passes.
sys.exit(1 if counts["failures"] or counts["errors"] else 0)
