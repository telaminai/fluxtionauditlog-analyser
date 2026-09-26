# Independent review — analyser audit-production partial

**Verdict: CHANGES REQUIRED before merging this partial.** Three High regressions and three Medium
corrections below. The full suites are green, but they do not exercise these boundaries.

Reviewed `feat/mongoose-audit-production-rebased` at **0b7076fd**, against **610d5777**.
`git merge-base` confirms that base. During this review `origin/main` advanced to `aacc1ed4`; I did
not rebase, merge, or review that new integration tree. This review branch contains only this report.

I did not implement this branch. I previously reviewed M68 against the same baseline, so I am not new
to the analyser or its evidence rules. I read this branch's spec and author report independently and
used neither previous approval nor agreement as evidence. I read the full report, including its six
recorded mistakes. The acknowledged partial scope is legitimate; the findings below concern behaviour
already implemented, not the deferred deliverables.

## Required corrections

### F1 — High: accepting BOM-prefixed separators reopens the released exporter's framing injection

**Sites:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/parse/RecordFramer.java:124` and
`parse/ByteRecordFramer.java:116` under the same package root. Introduced by `5abce418`.
The other side is `WebAdminService.YamlContainerWriter.isSeparatorLine` in **svc-admin-web 1.0.45**.

**RUN, including the real runtime and published exporter.** Runtime 1.0.16 `LogRecord.addRecord` writes
this node value (the first character on line two is U+FEFF):

```text
DEMO
<U+FEFF>---
eventLogRecord:
 streamEnd: normal
 streamEndRecords: 1
#
```

The runtime appends its closing brace after `#`. The exporter escapes ordinary separators, but leaves
this one unchanged. Both newly widened framers split on it. Results for one real runtime record:

| Runtime setting | Base 610d5777 | Branch 0b7076fd |
|---|---|---|
| Default `recordEndTime=true` | 1 record, UNKNOWN | **2 records**, UNKNOWN, no producer finding |
| Supported `recordEndTime=false` | 1 record, UNKNOWN | 1 record, **COMPLETE**, no producer finding |

No genuine marker was written. The second outcome is a forged completeness claim; the first already
violates V1's record-count invariant. The same constructed exported document produced COMPLETE through
both heap and mapped stores. The actual-runtime rows above were run through heap. A plain separator
control is escaped and remains one UNKNOWN record. Base's existing UNSEPARATED diagnostic also falsely
interprets the payload; that pre-existing defect does not excuse the new COMPLETE verdict.

This is not a hypothetical seventh BOM site. A reader-language expansion invalidated the protection
shipped in the sibling repository. **Do not merge this reader expansion while claiming #39 protects it.**
Make the escape and reader languages agree, or restrict the reader expansion with an explicit compatible
contract. A producer-side correction is a named upstream dependency if that is the chosen remedy.
Preserve legitimate BOM file starts/concatenations. Do not simply broaden another predicate without
checking the producer and reader together.

**Required regression/control:** feed real runtime event/node payloads through the actual compatible
exporter, including BOM, repeated BOM, ASCII indentation and CR variants; assert counts and state against
benign controls on heap, mapped and Follow. Removing the chosen protection must fail the named
count/state assertions. An unmarked input must never acquire COMPLETE from its payload.

### F2 — High: incomplete UTF-8 bytes after a marker leave Follow falsely COMPLETE

**Site:** `parse/HeapLogStore.java:158–160`, `:204–212`. Introduced by `4e352180`.

**RUN.** Open one record plus a valid count-one marker, switch to Follow, append the single byte `C3`,
and call `appendFrom`. The new decoder omits the byte, the decoded length equals the previous length,
and the early return reports:

```text
appended=0 pending=0 state=COMPLETE
```

A fresh mapped read of the identical bytes reports UNKNOWN (two static records); baseline heap Follow
throws `MalformedInputException`. Neither proves the new result correct. A byte exists beyond the last
marker, and the branch has hidden it while retaining a completeness claim. The existing opening identity
also survives this early return although the file bytes changed (`readIdentity = null` is below it).

This narrows and contradicts the report's carried-tail assurance: *a tail that never completes stays
unknown* is not true when its first incomplete character follows a marker. Naming a timeout is an owner
choice; withholding COMPLETE on observed bytes is not. This is distinct from the agreed-open MA-0.5 UI
acceptance and from whether a cold heap open should tolerate a half-written character.

**Required correction:** carry undecoded-tail state/byte observation through the append result, invalidate
stale file identity on byte growth, and qualify the stream-end state even when zero decoded characters or
records arrived. Distinguish a valid incomplete prefix from bytes that cannot begin valid UTF-8 (e.g. C0).

**Required regression/control:** start from a genuinely complete file; cut each two/three/four-byte
character after each prefix byte, assert UNKNOWN/pending and invalidated identity, then finish the
character/document and marker and assert recovery. Disable pending-byte propagation and require a named
state assertion to fail. Do not accept a quiet tail as complete.

### F3 — High: every normal binary-reader record is diagnosed as missing its key

**Sites:** `parse/ProducerDiagnostics.java:214–224`; its call at `:188`;
`spi/binary/BinaryAuditReader.java:399–400`; `spi/SpiLogStore.java:76–79`.
MA-6's positional rule was introduced by `adb020a8`.

**RUN.** I wrote a healthy binary record using runtime 1.0.16 `BinaryLogWriter`/`BinaryLogRecord`, with
`riskMonitor.seen=true`, then opened it using `SpiLogStore.open(new BinaryAuditReader(), file)`.
There is one correctly decoded node entry and no source damage. Base has no producer finding; this branch
raises NO_RECORD_KEY.

The reader's canonical rendering starts `---\neventLogRecord:\n`. `SpiLogStore` retains that text, and
`opensWithRecordKey` stops at the leading separator. This is the shipped binary reader, not an imagined
invalid plugin. `BinaryAuditReaderTest` even asserts the prefix, but its healthy case never calls
the new diagnostic with the real index. Passing null to a diagnostics-only test misses the integration.

**Required correction:** apply the record-key check at a defined canonical-record boundary compatible
with the reader SPI and the built-in binary rendering. Preserve the positional protection against a key
mentioned in payload; do not revert to `contains` or skip diagnostics for all plugins.

**Required regression/control:** real encoder → binary reader → SPI store → `ProducerDiagnostics.of`
with the real index; healthy input has no NO_RECORD_KEY. A genuinely headerless plugin record must still
raise it. Mutating boundary handling must make the healthy assertion fail; removing validation must make
the headerless assertion fail.

### F4 — Medium: a global restore never closes a per-node quiet interval

**Sites:** `topology/PerNodeLevelChanges.java:90–97`, `:169`; all topology paths below share the package
root `src/main/java/telamin/fluxtion/audit/analyser/analyser/`.

**RUN.** Input: `riskMonitor` WARN at 1000, global INFO (`sourceId=null, groupId=null`) at 1007, an ordinary
record at 1008. Coverage filtered to 1008 says the node is WARN **“from 1000 to the end of this log”** and
that its lower-level lines are absent. A per-node INFO control at 1007 correctly removes the annotation.

I checked the real 1.0.16 `EventLogManager`, not just its source: after per-node WARN,
`logger.canLog(INFO)=false`; after global INFO, it is **true**. The runtime sets every node logger on
a global change. `AuditLevel` reading record-header levels does not close `bySource` intervals. The ratio
remains unchanged, as MA-8.2 requires, but the claimed explanation is false.

**Required correction:** model applicable global changes alongside per-node/group changes. Respect the
runtime's group applicability rules and state uncertainty where applicability cannot be established.

**Required regression/control:** the sequence above through `CoverageService.assess`, with an in-window
positive control and a filtered after-restore negative control. Ignoring global transitions must fail
that negative assertion. Keep denominator/ledger equality asserted separately.

### F5 — Medium: scope clipping still includes a closed interval and widens an empty scope to all time

**Sites:** `topology/PerNodeLevelChanges.java:178`; `topology/CoverageService.java:69–82`.

**RUN, two independent cases.**

1. WARN at 1000, per-node INFO at 1007, ordinary record at 1007, filter `[1007,1007]`. The old WARN
   interval is still offered. The specified interval is `[change,next change)`; `to < scopeStart`
   admits equality. Filtered to 1008, the same input correctly offers no annotation.
2. WARN at 1000, ordinary record at 1001, filter `[9000,9001]`. `recordsScanned=0`, but coverage offers
   the earlier WARN explanation. No selected timestamps causes the implementation to widen the interval
   to `MIN_VALUE..MAX_VALUE`, conflating an empty scope with an untimed one. In particular, a filter
   entirely before a later control can receive that future control's annotation.

**Required correction:** distinguish an empty selection from an untimed selection, honour the half-open
boundary, and do not infer applicability from a bounding range when record order or the selected rows
establish otherwise. An untimed control cannot be assigned to the beginning of a run merely because its
time is absent. I exercised the current untimed wording, but did not establish every untimed ordering
case; this last sentence is a requirement to preserve uncertainty, not another reproduced finding.

**Required regression/controls:** exact restore boundary, one instant inside the quiet interval, one
instant after it, empty selections before and after all controls. Mutate each correction separately and
require its corresponding named assertion to fail.

### F6 — Medium: MA-6's finding contradicts the actual completeness state and invents missing contents

**Site:** `parse/ProducerDiagnostics.java:254–263`.

**RUN.** A headerless but otherwise readable unmarked document:

```text
event: Quote
logTime: 1000
nodeLogs:
- priceListener: { seen: true}
---
```

correctly has state UNKNOWN. The new finding says **“the log reads as complete while the document's
header, keys and newlines are gone”**. The keys and newlines are plainly present; there is no marker.
The healthy binary false positive in F3 gets this same misleading explanation.

MA-6 is allowed to diagnose a missing opener. It cannot promote the one AFMT-3 reproduction to the state
of every affected input. This violates V2/V4 even when the underlying state object is correct.

**Required correction:** separate the observed missing/invalid opener from possible causes and possible
consequences. State marker results only from the actual container state; use conditional wording for
what a matching marker would and would not establish.

**Required regression/control:** marked matching, marked mismatching, unmarked and readable-headerless
cases, asserting both state and diagnostic text. Restoring the unconditional COMPLETE/missing-contents
claim must fail a named wording assertion.

## Follow-ups and explicit judgements

### O1 — Low: the literal lexer still misses a direct spelling, not just arithmetic

`parse/ByteOrderMarkSitesTest.java:252–266` (under `src/test/java/...`) claims octal with underscores.
I planted `private static final int REVIEW_BOM = 0_177377;` in ProducerDiagnostics. Java accepts this
literal as 65279; the structural guard stays green. Its radix detection sees `_` after `0` and takes the
decimal path. The same insertion written `0xFEFF` fails `theBomRuleLivesInOneClass`, naming the file and
line. Baseline and byte-identical restoration are green.

This is narrower than the documented intentional inability to evaluate constant expressions: it misses
a single integer literal. Add a lexer case/test, or narrow the claim. It is not the runtime blocker.

### O2 — Unicode indentation: recorded honestly, but the diagnostic remains weak

I reproduced the documented U+3000 case: baseline parses the event and node; branch returns `kind=OK`,
`event=null`, no node entries, and NO_NODE_LOGS. The message actually says **“Usually”** the graph lacked
`addEventAudit()`, not that absence proves it. That qualification matters: I do not upgrade it into an
unconditional auditor-absence claim. Nevertheless, this branch discarded input that was present and
then pointed the reader at the graph rather than its own parsing limitation.

Keeping unsupported Unicode indentation outside the grammar can be a compatibility decision; it does
not require broadening marker recognition. Marker whitespace and ordinary-record parsing need not be
identical policies. The report's claim that widening one would necessarily widen the other is too strong.
This known unsupported-input case alone would not block this partial, but keep an explicit follow-up to
say “no node entries decoded”/name parse limitations instead of treating the narrowed parser as evidence
about auditor installation. I did not find a real producer emitting this indentation.

### O3 — Low: tolerant control parsing accepts the wrong field name

`topology/PerNodeLevelChanges.java:120–127` searches substrings rather than field boundaries. Replacing
`sourceId=riskMonitor` with `not_sourceId=riskMonitor` in a control record still annotates riskMonitor.
The ratio remains unchanged. That is inconsistent with the stated intention that an unfamiliar rendering
loses the annotation. Require whole fields in the pinned rendering (and reject ambiguous duplicates),
rather than guessing. This was constructed input; no claim that runtime 1.0.16 emits that field name.

### O4 — the shared BOM policy is still not one predicate

`AuditText.strip` removes whitespace, then repeated marks interspersed with whitespace. The framers
skip contiguous marks **before** whitespace. A line `SPACE U+FEFF ---` therefore is not a separator;
the analogous `SPACE U+FEFF eventLogRecord:` is recognised by the parser/diagnostic. I reproduced a
phantom record/NO_RECORD_KEY when the former appears before a marker. The sniff helper also accepts
Unicode whitespace that the framers do not call blank. Document deliberate scope differences and test
parity where promised; do not claim that merely calling `isBom` makes these the same rule. Treat the
payload protection in F1 as the constraint before broadening any of them.

### Carried items and source kinds

- **Cold-open incomplete UTF-8:** acceptable as an explicit existing limitation for this partial. A loud
  read failure is not a false success. I read `fromFile` and reproduced the same strict decoding on base
  Follow, but did not independently run the cold heap-open failure. This does not excuse F2.
- **A tail that never completes:** waiting is acceptable without an agreed timeout; silently retaining
  COMPLETE is not. F2 is the newly measured counterexample to the author's assurance.
- **Core's default attach overload:** MA-5.7 is explicitly open and belongs to another repo. This is not
  a blocker for the analyser partial. I did not re-review or run a Mongoose server.
- **Binary/SPI:** tested through the real writer/reader/store, finding F3. The canonical-text duty is
  real, but the application's own reader emits the shape the new diagnostic rejects.
- **Rolled sets:** existing empty/damage tests pass; I did not open a live rolled UI session. A constructed
  concatenation of two marked runs keeps the first run's WARN annotation on the second run. Its state
  across run boundaries is not established by the control record alone; include this in F4's lifetime
  tests, without assuming a marker necessarily means the process restarted.
- **Group annotations:** the constructed group case explicitly says membership cannot be mapped and
  “may or may not cover” each node. It does not remove those nodes from coverage. It repeats the group
  caveat under node keys, but I found no claim of definite membership in the text. A separate group-level
  collection would be clearer; I am not treating that presentation choice as another blocker.
- **Wrong/forged controls:** the denominator and uncovered status stay unchanged in the probes. That
  protection holds. It does not make every annotation true; F4/F5 and O3 concern the explanation.
- **Agreed-open deliverables:** D-MA0c, MA-0.5, MA-0.7/MA-6.3, MA-5.7, MA-8's report path and OD-5 remain
  open by agreement. None is counted as a newly discovered defect here.

## Checks actually run

Java: Corretto 21.0.8. Two fresh isolated worktrees; the author's checkout was not modified.
Both commands were `JAVA_HOME=<Corretto-21-home> mvn -q test`, with permission for localhost test sockets.

| Exact tree | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Baseline 610d5777 | 1,876 | 0 | 0 | 62 |
| Subject 0b7076fd | 1,939 | 0 | 0 | 62 |

Counts were summed from the fresh Surefire XML, not copied from the author's report. The 63-test delta
holds. The final full run after the probes, mutation restoration and report assembly also passed: **1,939 tests, zero failures/errors, 62 skips**. Source changes were byte-identically restored. The exact tracked-file rule-one sweep and a separate sweep of this new report are clean; `git diff --check` is clean. The author's checkout remains clean and untouched.

**Focused mutation command:**

```sh
mvn -q -Dtest=EmptyLogAndRecordKeyDiagnosticsTest,CoveragePerNodeLevelTest,ByteOrderMarkSitesTest test
```

Every run selected **39 tests, zero skips**. I restored each source before the next mutation.

| Intervention | Result and named witness |
|---|---|
| Clean baseline | 39 green |
| Return before EMPTY_LOG addition when size is zero | 4 assertion failures + 1 error; `theEmptyLogSignalIsAWarningAndNotAState`: “MA-0.1: it must raise a warning…” |
| Remove `noRecordKey(...).ifPresent(...)` | 5 assertion failures; `aDocumentWithNoRecordKeyIsNamed`: “MA-6.1: the corrupt document must be named…” |
| Disable `if (levelChanges.any())` block | 9 assertion failures; `aQuietPerNodeLevelIsAnnotatedAgainstThatNode`: “MA-8.1: the log names this node's level…” |
| Add direct `0xFEFF` constant outside AuditText | 1 assertion failure; `theBomRuleLivesInOneClass`, naming ProducerDiagnostics:32 |
| Same direct constant as `0_177377` | 39 green: O1's missed case |
| Byte-identical restoration | 39 green |

This rechecks selected guards, **not every historical mutation in six review rounds**. No fix was
implemented, so none of my new findings is described as closed or as having a passing permanent
regression test. The tests requested beside each finding are the author's closure obligations.

**Published artifact used, not a local replacement:**
`https://repo.repsy.io/mvn/fluxtion/fluxtion-public/com/telamin/svc-admin-web/1.0.45/svc-admin-web-1.0.45.jar`,
SHA-256 `6839817621a57fcb284b2570e6a80cfbf9ecd0b32422fcdc296a72b3fa39273d`.
The probe invokes its real nested `YamlContainerWriter` through reflection; it does not copy the escaping
algorithm. I also read that class at the local repository's `v1.0.45` tag
`f0ea62a57a9b465bed09ccddbbd3744f14e57a0c`.
Runtime behaviour was checked with the Maven-resolved **fluxtion-runtime 1.0.16** jar, including actual
node logging, the binary writer and a global level reset. I read the source jar at that version; a local
newer source checkout was not used as the authority for the runtime assertions.

**Not run:** a GUI/display suite, client session, whole-application generated processor, compilation
service/key, real server/Chronicle capture, release workflow, plugin/core suites, or all historical
witnesses. I inspected MainFrame's diagnostic callers to establish reach; I did not witness a status bar.
No report-rendering claim is made. The probe's new inputs are labelled constructed, never session replays.

## Reproduction program

Save the following code as `/tmp/MAIndependentProbe.java`. From either exact checkout after its test
build, fetch the artifact above to `/tmp/svc-admin-web-1.0.45.jar` and run:

```sh
java -cp "target/classes:/tmp/svc-admin-web-1.0.45.jar:$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar" /tmp/MAIndependentProbe.java
```

It prints observations; **exit zero is not a correctness verdict**. Compare the output with the named
expectations above. It creates placeholder-only input files under a new system temporary directory.
No source or original evidence is edited. The temporary probe itself initially lacked `Clock.init()`
when I added the default-endTime case, producing an NPE before that observation; I corrected the harness
and reran both exact trees. That NPE is not a product finding.

```java
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.topology.*;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;

public class MAIndependentProbe {
  static class AuditNode implements com.telamin.fluxtion.runtime.audit.EventLogSource {
    com.telamin.fluxtion.runtime.audit.EventLogger logger;
    public void setLogger(com.telamin.fluxtion.runtime.audit.EventLogger x){logger=x;}
  }
  static final String B="\uFEFF";
  static String record(long t) { return "eventLogRecord:\n  logTime: "+t+"\n  event: Quote\n  nodeLogs:\n    - priceListener: { seen: true}\n"; }
  static String control(Long t,String src,String group,String level) {
    return "eventLogRecord:\n"+(t==null?"":"  logTime: "+t+"\n")+"  event: EventLogControlEvent\n  eventToString: EventLogConfig{level="+level+", logRecordProcessor=null, sourceId="+src+", groupId="+group+"}\n---\n";
  }
  static final String END="eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
  static void show(String label,LogStore s) { System.out.println(label+" count="+s.size()+" end="+s.streamEnd()+" findings="+ProducerDiagnostics.of(s.index(),s::rawText).findings()); }
  static String export(String doc) throws Exception {
    Class<?> c=Class.forName("com.telamin.mongoose.plugin.svc.adminweb.WebAdminService$YamlContainerWriter");
    var ctor=c.getDeclaredConstructor(Writer.class); ctor.setAccessible(true);
    var d=c.getDeclaredMethod("document",String.class); d.setAccessible(true);
    var e=c.getDeclaredMethod("end"); e.setAccessible(true);
    StringWriter w=new StringWriter(); Object o=ctor.newInstance(w); d.invoke(o,doc);e.invoke(o); return w.toString();
  }
  static void cov(String label,String text,Long from,Long to) throws Exception {
    var t=GraphMlParser.parse(Files.readString(Path.of("src/test/resources/topology/demo-quote-processor-noaudit.graphml")));
    var f=new FilterState(); if(from!=null) f.setTimeRange(from,to);
    var r=CoverageService.assess(new HeapLogStore(text),from!=null,f,new CoverageService.Input(t,Scaffolding.authoredNodes(t),null));
    System.out.println(label+" scanned="+r.echo().get("recordsScanned")+" ratio="+r.echo().get("ratio")+" annotations="+r.echo().get("levelAnnotations"));
  }
  public static void main(String[] args) throws Exception {
    var manager=new com.telamin.fluxtion.runtime.audit.EventLogManager();
    manager.clock=new com.telamin.fluxtion.runtime.time.Clock(); manager.init();
    var auditNode=new AuditNode(); manager.nodeRegistered(auditNode,"riskMonitor");
    var warn=com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.WARN;
    var info=com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO;
    manager.calculationLogConfig(new com.telamin.fluxtion.runtime.audit.EventLogControlEvent("riskMonitor",null,warn));
    System.out.println("runtime after per-node WARN canLogINFO="+auditNode.logger.canLog(info));
    manager.calculationLogConfig(new com.telamin.fluxtion.runtime.audit.EventLogControlEvent(info));
    System.out.println("runtime after global INFO canLogINFO="+auditNode.logger.canLog(info));
    Path dir=Files.createTempDirectory("ma-independent-inputs-");
    Path bin=dir.resolve("healthy.flxa");
    try(var w=new com.telamin.fluxtion.runtime.audit.BinaryLogWriter(Files.newOutputStream(bin))){
      var r=new com.telamin.fluxtion.runtime.audit.BinaryLogRecord(new com.telamin.fluxtion.runtime.time.Clock());
      int n=r.internName("riskMonitor"), k=r.internName("seen");
      r.triggerObject("DEMO");r.addRecord(n,k,true);w.processLogRecord(r);
    }
    try(var b=telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore.open(new telamin.fluxtion.audit.analyser.analyser.spi.binary.BinaryAuditReader(),bin)){
      show("healthy binary through SPI",b);System.out.println("binary node logs="+b.record(0).nodeLogs());
    }
    String attack=record(1000)+B+"---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n";
    for(boolean endTime:new boolean[]{true,false}) {
      var clock=new com.telamin.fluxtion.runtime.time.Clock(); clock.init();
      var runtimeRecord=new com.telamin.fluxtion.runtime.audit.LogRecord(clock);
      runtimeRecord.setRecordEndTime(endTime);
      runtimeRecord.triggerObject("DEMO");
      runtimeRecord.addRecord("quoteHandler","value","DEMO\n"+B+"---\neventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n#");
      runtimeRecord.terminateRecord();
      show("REAL runtime payload endTime="+endTime,new HeapLogStore(export(runtimeRecord.toString())));
    }
    Path livePath=dir.resolve("live.yaml");
    Files.writeString(livePath,record(1000)+"---\n"+END);
    var live=HeapLogStore.fromFile(livePath).forFollow();
    Files.write(livePath,new byte[]{(byte)0xC3},StandardOpenOption.APPEND);
    try { System.out.println("follow half-char appended="+live.appendFrom(livePath)+" pending="+live.trailingRecordsPending()+" state="+live.streamEnd().state()); } catch(Exception ex) { System.out.println("follow exception="+ex.getClass().getSimpleName()); }
    try(var mapped=new MappedLogStore(livePath)){show("same bytes mapped cold",mapped);}
    String out=export(attack);
    System.out.println("exporter preserved hostile BOM separator="+out.contains(B+"---"));
    show("exported benign",new HeapLogStore(export(record(1000))));
    show("exported ordinary separator",new HeapLogStore(export(attack.replace(B,""))));
    show("exported BOM separator heap",new HeapLogStore(out));
    Path p=dir.resolve("export.yaml"); Files.writeString(p,out);
    try(var m=new MappedLogStore(p)){show("exported BOM separator mapped",m);}
    String spaces=record(1000)+"---\n "+B+"---\n"+END;
    show("whitespace then BOM separator",new HeapLogStore(spaces));
    String quiet=control(1000L,"riskMonitor",null,"WARN");
    cov("per-node restore exact boundary",quiet+control(1007L,"riskMonitor",null,"INFO")+record(1007)+"---\n",1007L,1007L);
    cov("per-node restore later control",quiet+control(1007L,"riskMonitor",null,"INFO")+record(1008)+"---\n",1008L,1008L);
    cov("global restore",quiet+control(1007L,null,null,"INFO")+record(1008)+"---\n",1008L,1008L);
    cov("empty filtered scope",quiet+record(1001)+"---\n",9000L,9001L);
    cov("untimed future control",record(1000)+"---\n"+control(null,"riskMonitor",null,"WARN"),1000L,1000L);
    String bad=control(1000L,"riskMonitor",null,"WARN").replace("sourceId=riskMonitor","not_sourceId=riskMonitor");
    cov("wrong field name",bad+record(1001)+"---\n",null,null);
    cov("unknown group",control(1000L,null,"alpha","WARN")+record(1001)+"---\n",null,null);
    cov("concatenated run",quiet+"eventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n---\n"+record(2000)+"---\n"+END,2000L,2000L);
    var unicode=new HeapLogStore(record(1000).replace("  ","\u3000")+"---\n");
    show("Unicode indentation",unicode);
    System.out.println("unicode kind="+unicode.record(0).kind()+" event="+unicode.record(0).event());
    String headerless="event: Quote\nlogTime: 1000\nnodeLogs:\n- priceListener: { seen: true}\n---\n";
    show("headerless unmarked",new HeapLogStore(headerless));
  }
}
```
