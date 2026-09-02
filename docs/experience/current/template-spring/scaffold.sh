#!/usr/bin/env bash
# Read the Spring bean file and write a shell Java class for every bean whose class does not exist.
# Purely mechanical: the XML is the design, this makes it compile.
set -euo pipefail
cd "$(dirname "$0")"
XML="${1:-src/main/fluxtion/designer/application-context.xml}"
rm -rf src/main/java/com/acme/app/generated
python3 - "$XML" <<'PY'
import sys,re,pathlib
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
