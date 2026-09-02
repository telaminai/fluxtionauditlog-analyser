#!/usr/bin/env bash
# Read the Spring bean file and write a shell Java class for every bean whose class does not exist.
# Purely mechanical: the XML is the design, this makes it compile.
set -euo pipefail
cd "$(dirname "$0")"
XML="${1:-src/main/fluxtion/designer/application-context.xml}"
# Resolve the dependency classpath so we can tell "this class does not exist yet" from
# "this class ships with the framework". Generating a stub over a framework class shadows the real
# one and produces baffling errors — FluxtionSpringConfig with no setters looks like a Fluxtion bug.
[ -f cp.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt 2>/dev/null || true
export FLUXTION_CP="$(cat cp.txt 2>/dev/null || true)"
rm -rf src/main/java/com/acme/app/generated
python3 - "$XML" <<'PY'
import sys,re,pathlib,os,subprocess
xml=pathlib.Path(sys.argv[1]).read_text()
beans=re.findall(r'<bean\s+id="([^"]+)"\s+class="([^"]+)"',xml)
# constructor-arg refs, per bean, in order
blocks=re.split(r'(?=<bean\s)',xml)
refs={}
for b in blocks:
    m=re.search(r'<bean\s+id="([^"]+)"\s+class="([^"]+)"',b)
    if m: refs[m.group(1)]=re.findall(r'<constructor-arg\s+ref="([^"]+)"',b)
byId={i:c for i,c in beans}
made=0
for bid,fqcn in beans:
    pkg,cls=fqcn.rsplit(".",1)
    path=pathlib.Path("src/main/java")/pkg.replace(".","/")/f"{cls}.java"
    if path.exists(): continue
    # never stub a class that already exists on the dependency classpath
    if os.environ.get("FLUXTION_CP") and subprocess.run(
            ["javap","-cp",os.environ["FLUXTION_CP"],fqcn],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        print(f"  skip {fqcn} — provided by a dependency")
        continue
    path.parent.mkdir(parents=True,exist_ok=True)
    parents=[(r,byId[r].rsplit(".",1)[1]) for r in refs.get(bid,[]) if r in byId]
    fields="".join(f"    private final {t} {r};\n" for r,t in parents)
    args=", ".join(f"{t} {r}" for r,t in parents)
    assign="".join(f"        this.{r} = {r};\n" for r,t in parents)
    ctor=(f"    public {cls}({args}) {{\n{assign}    }}\n\n" if parents else "")
    body=("    @OnTrigger\n    public boolean trigger() {\n"
          f"        auditLog.info(\"node\", \"{bid}\");\n"
          "        return true;   // SHELL: no logic yet\n    }\n") if parents else \
         ("    // SHELL: add @OnEventHandler methods for the events this node consumes\n")
    imports="import com.telamin.fluxtion.runtime.annotations.OnTrigger;\n" if parents else ""
    path.write_text(f"""package {pkg};

{imports}import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** SHELL generated from Spring bean id "{bid}". Replace the body; keep the fields. */
public class {cls} extends EventLogNode {{

{fields}{ctor}{body}}}
""")
    print(f"  created shell {path}")
    made+=1
print(f"  {made} shell(s) created, {len(beans)-made} already present")
PY
