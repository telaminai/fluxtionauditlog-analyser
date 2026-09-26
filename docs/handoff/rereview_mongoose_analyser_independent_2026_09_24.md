# Re-review — analyser audit-production fixes

**Verdict: CHANGES REQUIRED before merging this partial.** The original probe and all fourteen
requested mutation controls reproduce the fixes. A further wording mutation also fails correctly.
There are four required corrections below: one High remaining Follow case, two Medium MA-8 interpretation
problems, and one Medium qualification problem. These do not invalidate the fixes demonstrated by the
original cases. They do prevent the broader claims about live completeness and applicable audit levels.

**Subject:** `feat/mongoose-audit-production-rebased` at **6998fcc8**, base **610d5777**. Reviewed the three
commits after **0b7076fd**, including the predictions committed as **dbc51ae9**. I did not rebase this tree
onto later main or test that future integration. This review is on its own branch and changes only this
report. All production/test mutations were temporary and restored byte-identical.

**Independence:** I am the reviewer of **2d7ac12f**, not a second independent reviewer of these fixes.
I reused that review's exact embedded probe but re-ran it; I did not reuse its outcomes as evidence.
I read the requested Independent review section and P5 first. The findings below distinguish executed
probes from source inspection. No key, client session, running Mongoose server, UI session, merge,
release or deployment was used.

## Required corrections

All abbreviated source paths below are under
`src/main/java/telamin/fluxtion/audit/analyser/analyser/`. Line numbers refer to **6998fcc8**.

### RR-1 — High: rejected bytes still leave the live store COMPLETE with its old identity

**Sites:** `parse/HeapLogStore.java:172–180`; `ui/MainFrame.java:4297–4301`.

**RUN, store; INSPECTED, UI propagation.** Start with one ordinary record and its valid count-one marker,
open through `HeapLogStore.fromFile(...).forFollow()`, append **C0**, and call `appendFrom`:

```text
before invalid append: COMPLETE identities=1
invalid append throws MalformedInputException
after invalid append: COMPLETE pending=0 identities=1
```

Rejecting C0 is correct: it cannot become valid UTF-8. Keeping the previous current-file verdict is not.
The byte-growth observation happens before decoding, but identity invalidation and completeness updates
happen after it. The exception bypasses both. `pollFollow` sets a status-bar error and returns; it does
not retire the stale completeness state. I did not drive that UI, so the measured result is the store
state, with the propagation path established by inspection.

This is the remaining malformed-input branch of F2, **not** the accepted cold-open UTF-8 limitation or
a demand to wait for invalid bytes. The failure path existed before these fixes; this review exposed it,
rather than these three commits necessarily introducing it. The new invalid-prefix test asserts the
exception only, leaving its post-error state unguarded.

**Required:** after observing changed bytes, retain readable prior rows if appropriate but retire the
current-file identity and completeness claim even when decoding fails. Expose failed/unverified live
reading consistently; do not present invalid bytes as a valid pending character. Add the COMPLETE →
append-invalid-byte → exception case, asserting state and identity after the exception. Its control must
fail when invalidation is moved back below the throwing read. Keep the existing valid-prefix recovery
and invalid-prefix rejection controls.

### RR-2 — Medium: the four-field check still changes real control-event addresses

**Sites:** `topology/PerNodeLevelChanges.java:131–146`, `:162–174`.

**RUN against the actual runtime 1.0.16 and through `CoverageService.assess`.** Register `riskMonitor`,
set it to INFO, then dispatch a WARN `EventLogControlEvent` with one of these source ids:

| Actual Java source id | Runtime `riskMonitor.canLog(INFO)` afterwards | Analyser explanation |
|---|---|---|
| `riskMonitor, DEMO` | true | riskMonitor was set to WARN |
| `riskMonitor}DEMO` | true | riskMonitor was set to WARN |
| ` riskMonitor ` | true | riskMonitor was set to WARN |
| empty string | true | every node was set to WARN |
| the non-null string `null` | true | every node was set to WARN |

These are **real runtime renderings**, not misspelt field names made up for the parser. The runtime uses
exact map lookup; none addresses `riskMonitor`. The analyser stops a value at comma/brace, trims it, and
maps both empty and `null` to Java null. Counting each key once cannot recover the lost distinction.
A second runtime probe used processor grouping `alpha` and addressed `groupId="alpha, DEMO"`: the
runtime ignored it, while the analyser truncated the address to `alpha` and asserted applicability.

The specific suggested attack — a listener whose `toString()` contains `, sourceId=other` — is safely
skipped because the source field occurs twice. I ran that too. It does not protect these single-occurrence
values. All coverage probes retained ratio **0.167** and uncovered membership: annotate-never-excuse
still holds, but the explanation is false.

**Required:** do not infer an applied target/global change from an ambiguous or normalised address.
Validate the complete supported rendering and preserve significant address characters, or decline/qualify
an ambiguous explanation. Empty source id must not mean global. The literal string `null` and Java null
have identical runtime rendering, so no analyser-only parser can prove which was supplied; disclose that
limit rather than claiming a parser can solve it. Structured/escaped producer metadata can remove the
ambiguity upstream. Add real `EventLogControlEvent` counterexamples and runtime logger checks like these,
plus positive controls for ordinary per-node/global changes; removing the refusal/qualification must fail.

### RR-3 — Medium: applicability is checked against the control's processor, then applied to all processors

**Sites:** `topology/PerNodeLevelChanges.java:81–86`, `:103–104`, `:189–210`;
`topology/CoverageService.java:63–68`, `:95–98`.

**RUN through the annotation model and coverage; also RUN on both rolled-store backends.**

1. Record 1: `groupingId: alpha`, WARN control for `sourceId=riskMonitor, groupId=alpha`.
   Record 2: `groupingId: beta`, ordinary event. Select only record 2. Coverage says riskMonitor's
   lower-level output is suppressed by alpha's control, naming alpha as “this processor's”.
2. Record 1 as above; record 2 is a **beta** processor's global INFO addressed to beta; record 3 is an
   ordinary **alpha** event. Select record 3. The explanation is now **null**, because beta's restore
   closed alpha's interval. The runtime rule does not change another processor's logger.

`affects` tests whether the control applied to its own processor and whether its source matches the node.
It never establishes that an in-view record or the next control belongs to that same processor. Record
order is the right clock **within a processor's established stream**; concatenating rows does not establish
that stream. I split case 1 across two files, resolved their order, and reproduced it using
`RolledLogStore.open` with heap and mapped thresholds. Rolling does not fix the scope loss.

The missing-grouping assumption has the same missing-evidence problem. A record with **no** `groupingId:`
is read as Java null, and the output says “which applies because this processor states no grouping”.
Runtime 1.0.16 does explicitly render its grouping, including null. A missing field from an adapter or
an incomplete rendering does not state the default; the parser has lost that distinction.

**Required:** scope intervals, closing changes and selected rows to an established processor context.
Do not treat grouping alone as a globally unique identity where it is not declared to be one; when the
available record fields cannot establish identity/applicability, qualify or withhold that explanation.
Distinguish declared null from absent grouping, or disclose the assumption in the emitted explanation.
Keep the same-processor grouped/ungrouped tests, and add these two mixed-processor cases, absent-field
cases and a rolled equivalent. Controls should remove scope checking and turn those assertions red.
This is not a request to change the coverage denominator or filter out the control records.

### RR-4 — Medium: the run-boundary caveat follows a claim that already assumes survival

**Site:** `topology/PerNodeLevelChanges.java:221–238`.

**RUN through coverage.** A WARN control, a genuine stream-end marker, then a second marked run with one
ordinary record. Select only that second-run record. The same annotation says both:

> so riskMonitor's lines below that level are not in this log

and:

> the log does not say whether the level survived into the later run

The new boundary is found correctly. The second sentence is correct. It does not make the first
sentence true for a scope wholly after that boundary. `anIntervalThatCrossesARunBoundarySaysSo` guards
presence of the caveat, not absence of the unjustified conclusion.

**Required:** keep the historical level observation and the boundary qualification, as the spec asks;
do not drop the annotation or assume a restart. Make the statement about later selected rows conditional
on survival, and limit the definite suppression statement to the established same-run interval. Add a
wording assertion for a scope wholly after the boundary and one spanning it. Mutating conditional wording
back to definite suppression must fail. This is a correction to the new qualification, not a new demand
to infer process lifecycle from a marker.

## Original findings and the requested attack areas

| Item | Disposition and basis |
|---|---|
| F1 | Original injection **closed, RUN**. Both real-runtime payload cases now give one record, UNKNOWN. Heap/mapped/empty-start Follow matrix and separate string/byte mutations pass the required green/red/restored sequence. I additionally ran the real exported payload through `YamlAuditReader → SpiLogStore`: one UNKNOWN record with either runtime endTime setting. |
| F2 | Valid incomplete-prefix case **closed, RUN**: `C3` gives pending=1/UNKNOWN, identity discarded. Five controls reproduced. Invalid-prefix acceptance is incomplete at the post-exception boundary: RR-1. |
| F3 | **Closed, RUN**. Real binary writer → reader → SPI has no producer findings. Leading separators do not hide a headerless record or a later key mention in the committed controls; the boundary mutation fails the healthy-binary assertion. Skipping empty leading boundary lines is deliberate normalisation, not a general validation of plugin contents. |
| F4 | Same-processor global restore **closed, RUN**, including the runtime logger probe and global-transition mutation. Wider processor/run lifetime claims need RR-3/RR-4. |
| F5 | **Closed, RUN**. Exact restore boundary, empty scope and untimed future control give no annotation. Three separate controls reproduced; inside/outside assertions remain. |
| F6 | **Closed, RUN**. Actual unmarked/matching/mismatching cases use conditional wording. An additional mutation putting an unconditional COMPLETE claim back fails the named wording test. |
| O1 | **Closed for the reported literal, RUN**. Reinstating the octal detection bug fails on `0_177377`. The guard's stated expression/arithmetic limits remain, not a new semantic completeness guarantee. |
| O3 | Wrong/missing/duplicate-field cases **closed, RUN**, and the recognition mutation fails. Whole-rendering ambiguity remains: RR-2. |
| O4 | **Addressed by inspection**, with the existing whitespace/BOM examples also exercised by the original probe. The two scopes are documented; no claim that all consumers have one identical predicate is needed. |

**1. Offset zero and concatenation.** Full-file heap/mapped framing and the shipped YAML plugin frame
whole containers; Follow rereads the whole file even if it started empty. A properly rendered runtime
record writes its header before its payload. Those are the concrete reasons the restriction closes F1,
not a universal property of any String handed to `RecordFramer`. The extra empty-file/BOM Follow probe
produced one UNKNOWN record. `frameForPlugin` has a whole-container contract and its shipped caller uses
`Files.readString`; an arbitrary plugin that frames isolated value fragments would violate that boundary.

`RollSetResolver` really does rebase tail chunks to zero (`:179–203`). Its chunk origin can be inside a
payload, so “offset zero cannot be payload” is not literally true of every caller. I inspected this path;
I did **not** reproduce a new tail-chunk verdict counterexample. It cannot feed an injected item into the
record count/completeness tracker, which is enough for the narrow F1 closure. “Only logTime, therefore
harmless” is nevertheless too broad: the resulting lastTime feeds FILE_OVERLAP at `:150–159`. Keep that
separate bounded-probe limitation visible; see optional improvements below.

The concatenated-BOM cost is acceptable for this partial: it restores the older separator language,
reports UNSEPARATED, and does not claim completeness. Format 1 defines `---`, not arbitrary embedded BOM
prefixes. This is a restriction with a tested compatibility cost, not a claim to preserve every BOM join
I originally requested. Any future widening needs writer/reader agreement first.

**2. Published test dependency.** Pinning the actual 1.0.45 exporter in test scope with all transitives
excluded is a sound compatibility test for that supported release; it adds no exporter to the runtime
application. It intentionally loads a nested writer reflectively. A nested-class API change should fail
loudly. Changing the exporter version/escape can break the explicit old-escape assertion before the
parity cases, requiring a deliberate update. This is not an automatic check of future releases: the pin
stays green when an unrelated newer release changes. Keep the old supported-version coverage and add
new versions deliberately; an optional cross-repo release gate would make that obligation mechanical.

**3. UTF-8.** I compared `decodeCompletePrefix` with an independent oracle on **315,793** byte arrays:
all lengths 0–2 plus 250,000 seeded arrays of lengths 3–8. Complete decoding uses the JDK; incomplete
prefix validity comes from prefixes enumerated from **every Unicode scalar's UTF-8 encoding**. Zero
mismatches. The JDK's incremental decoder alone is not a valid-prefix oracle: it reports underflow for
`ED A0` before the third byte, although no valid scalar can complete it. My first oracle attempt exposed
that difference; the corrected oracle tests the author's stricter rule, rather than calling it a defect.
This is broad differential testing, not exhaustive enumeration of all arbitrary-length byte strings.

The cold mapped C3 count versus Follow pending count is reproduced by the original probe. UNKNOWN is
preserved. I continue to accept cold-open decoding as the explicitly carried limitation; it does not
excuse RR-1's COMPLETE after observed invalid growth.

**4. Record-key boundary.** Plain leading separators are skipped only until content appears. The healthy
binary integration and plugin negative controls run against the real index. A leading separator alone
cannot make a later payload mention into an opening key. No additional F3 blocker found. A plugin still
owes complete canonical text; this diagnostic does not prove it met every duty.

**5. Runtime/group/order.** I read `EventLogManager.calculationLogConfig`, `EventLogControlEvent.toString`
and `LogRecord.triggerEvent/triggerObject` in the 1.0.16 source jar, inspected the class API, and ran the
real manager/logger probes. The author's single-processor rule is right: grouping gates the change,
then a null source sets every node. It does not follow that records from different processors share that
configuration, or that absent grouping explicitly declares null. RR-3 is the missing scope. Record order
avoids timestamp sentinels and equal-time ambiguity; it cannot prove identity or continuity across a
rolled set or a stream-end marker. RR-4 addresses the latter wording.

**6. Rewritten tests.** Inspected the exact diff and re-ran them in the full suite. The grouping rewrite
is justified: the old fixture omitted processor grouping, so its assertion about alpha surviving beta
was wrong for the runtime's ungrouped default. The replacement tests both grouped and explicitly-null
control records. It does **not** protect mixed-processor logs; its ordinary data helper even omits
grouping. That is a missing case, not grounds to reinstate the wrong assertion. The two wording changes
replace “between”/“until an untimed change” with named opening and closing record positions, preserving
the original historical-window and untimed-close intent. The later-quiet-window test still requires
continuing past an inapplicable/loud earlier window. None should be deleted to fix the findings here.

**7. Four rendered fields.** Missing/duplicate whole fields are now refused, including the actual custom
listener rendering I tried. The single-occurrence address counterexamples are RR-2. A count of field
names is not validation of the runtime's unescaped values.

## Optional improvements and unchanged scope

- Carry the exporter pin in an explicit supported-writer matrix or release gate. Do not replace this
  pinned reproduction with a moving latest dependency or remove old compatibility cases.
- Clarify `RecordFramer`'s origin precondition and give `RollSetResolver` a chunk-origin-aware boundary
  test. Do not describe a derived time-order finding as harmless simply because it cannot change count.
  This is inspection-based follow-up, not a reproduced new framing blocker.
- Keep the cold heap/mapped/Follow UTF-8 disagreement and Unicode NO_NODE_LOGS wording visible. I did
  not turn either accepted limitation into another required correction.
- D-MA0c, MA-0.5, MA-0.7/MA-6.3, MA-5.7, the MA-8 report path and OD-5 remain explicitly open. No
  Mongoose server or core attach-default implementation was re-reviewed. No stuck-tail timeout is
  invented. The pre-existing UNSEPARATED payload false positive remains and appears in my probe output.

## Checks actually run

- Initial **and final** `JAVA_HOME=<JDK 21> mvn -q test`: **1961 tests, 0 failures, 0 errors, 62 skipped**.
  Counts were summed from Surefire XML, not copied from the report. This is 22 more than the previous
  review's 1939; I did not rerun 0b7076fd or current main this round.
- Exact embedded `MAIndependentProbe.java` from review **2d7ac12f**, against the branch classes and the
  released **svc-admin-web 1.0.45** and **fluxtion-runtime 1.0.16** jars. The exporter was the cached
  published jar; its SHA-256 was independently recomputed as
  `6839817621a57fcb284b2570e6a80cfbf9ecd0b32422fcdc296a72b3fa39273d`, matching the public artifact verified
  in the original review. I did not claim a new empty-cache download in this round.
- New executable probe below: actual runtime logger changes, coverage-selected rows, heap/mapped rolled
  stores, real exporter through YAML SPI, empty-start Follow, and the UTF-8 differential check.
- **14 requested mutation controls + 1 additional F6 control**. Each had its own green focused baseline,
  a Surefire assertion failure at the specified test, byte-identical source restoration, and a green
  rerun. A compiler failure or a test name printed in a code frame did not count as a witness. The harness
  reads the XML failure element. It normalises JUnit's `(Path)` parameter suffix when matching names.
- `git diff --check` and the exact tracked-file rule-one sweep, plus a separate scan of this new report.
  Only this report is committed. No product fixes or permanent test changes are included.
- No new package, real-display or strict-site-build claim: this commit changes a handoff report only.
  The 62 headless skips remain disclosed; I did not turn them into display passes.

## Mutation results

Every row below restored green and byte-identical. `F2-*`'s first four use the same test, but independent
edits hit respectively its verdict, verdict, identity and pending-count assertions; they are not one
combined mutation standing in for five protections.

| Mutation | Named failing test | Failure witnessed |
|---|---|---|
| F1-string | `ExporterFramingAgreementTest#everyHostileSeparatorReadsExactlyAsItsBenignControl` | F1 (heap, bom, recordEndTime=true): a payload changed the record count or the verdict ==> expected: <Read[records=1, state=UNKNOWN]> but was: <Read[records=2, state=UNKNOWN]> |
| F1-bytes | `ExporterFramingAgreementTest#everyHostileSeparatorReadsExactlyAsItsBenignControl` | F1 (mapped, bom, recordEndTime=true): a payload changed the record count or the verdict ==> expected: <Read[records=1, state=UNKNOWN]> but was: <Read[records=2, state=UNKNOWN]> |
| F2-growth | `FollowPendingBytesTest#aCharacterCutAfterEveryPrefixByteWithholdsTheVerdictAndThenRecovers` | F2 (233 cut after byte 1 of 2): bytes exist past the last marker; the file cannot vouch for itself ==> expected: not equal but was: <COMPLETE> |
| F2-verdict | `FollowPendingBytesTest#aCharacterCutAfterEveryPrefixByteWithholdsTheVerdictAndThenRecovers` | F2 (233 cut after byte 1 of 2): bytes exist past the last marker; the file cannot vouch for itself ==> expected: not equal but was: <COMPLETE> |
| F2-identity | `FollowPendingBytesTest#aCharacterCutAfterEveryPrefixByteWithholdsTheVerdictAndThenRecovers` | F2 (233 cut after byte 1 of 2): the file's bytes changed, so the opening identity must not survive ==> expected: <true> but was: <false> |
| F2-pending | `FollowPendingBytesTest#aCharacterCutAfterEveryPrefixByteWithholdsTheVerdictAndThenRecovers` | 233 cut after byte 1 of 2: and it is said to be pending ==> expected: <1> but was: <0> |
| F2-validity | `FollowPendingBytesTest#bytesThatCanNeverBeginACharacterFailRatherThanWait` | F2: [192] can never complete, so waiting for it is a silent lie ==> Expected java.nio.charset.MalformedInputException to be thrown, but nothing was thrown. |
| F3-boundary | `RecordKeyBoundaryTest#aHealthyBinaryRecordThroughTheRealReaderHasItsKey` | F3: a healthy record from the analyser's own binary reader was diagnosed as keyless ==> expected: <false> but was: <true> |
| F4-global | `CoveragePerNodeLevelTest#aGlobalRestoreClosesAPerNodeInterval` | F4: after a global INFO the node is not WARN any more, so nothing explains its silence: {spreadCalculator=this log sets spreadCalculator's audit level to WARN at record 1 (logTime 1000), and nothing later in this log changes it… |
| F5-restore | `CoveragePerNodeLevelTest#theRestoreBoundaryIsHalfOpen` | F5: nothing in view lies inside [WARN, INFO), so nothing is explained: {spreadCalculator=this log sets spreadCalculator's audit level to WARN at record 1 (logTime 1000), until record 2 (logTime 1007) sets it to INFO, so spreadC… |
| F5-empty | `CoveragePerNodeLevelTest#anEmptySelectionIsExplainedByNothing` | F5: no record is in view, so no level explains one [1,2]: {spreadCalculator=this log sets spreadCalculator's audit level to WARN at record 2 (logTime 1000), and nothing later in this log changes it, so spreadCalculator's lines … |
| F5-future | `CoveragePerNodeLevelTest#aControlAfterTheRecordsInViewExplainsNothing_evenUntimed` | an untimed control after the record was assigned to the start of the log: {spreadCalculator=this log sets spreadCalculator's audit level to WARN at record 2 (untimed), and nothing later in this log changes it, so spreadCalculat… |
| O1-octal | `ByteOrderMarkSitesTest#theLexerReadsEveryClaimedSpellingAsItsValue` | 0_177377 must read as 0xFEFF ==> expected: <true> but was: <false> |
| O3-rendering | `CoveragePerNodeLevelTest#aFieldThatIsNotTheFieldIsNotReadAsItOrAsAbsent` | not_sourceId is not sourceId — and a missing sourceId must not become 'every node': {spreadCalculator=this log sets every node's audit level to WARN at record 1 (logTime 1000), and nothing later in this log changes it, so sprea… |
| F6-wording | `RecordKeyBoundaryTest#theWordingNeverStatesAVerdictItCannotSeeOrInventsMissingContent` | F6 (unmarked, readable, headerless): the finding asserted a verdict it cannot see: Record 1 does not open with the 'eventLogRecord:' key — its first line is 'event: Quote'. The format requires each document to begin with that k… |

## Reproduction appendix — new probe

Save this Java block as `MARereviewProbe.java` outside the checkout. From an **unmutated 6998fcc8**
worktree after `mvn test`, run with JDK 21, branch `target/classes`, and the two published jars:

```sh
java -cp "target/classes:$HOME/.m2/repository/com/telamin/svc-admin-web/1.0.45/svc-admin-web-1.0.45.jar:$HOME/.m2/repository/com/telamin/fluxtion/fluxtion-runtime/1.0.16/fluxtion-runtime-1.0.16.jar" /tmp/MARereviewProbe.java
```

It prints the wrong-result witnesses rather than pretending these are passing regression assertions.
`stillINFO=true` is measured in the real runtime, not inferred from the same analyser parser under test.
The coverage cases are labelled constructed regression inputs; they are not another client session.

```java
import java.nio.file.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.topology.*;
import com.telamin.fluxtion.runtime.audit.*;
public class MARereviewProbe {
 static class Node implements EventLogSource {EventLogger logger; public void setLogger(EventLogger x){logger=x;}}
 static final Set<Integer> validPrefixes=new HashSet<>();
 static int key(byte[] b,int start,int end){int key=1;for(int i=start;i<end;i++)key=(key<<8)|(b[i]&255);return key;}
 static final String END="eventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n---\n";
 static String row(int t,String group){return "eventLogRecord:\n logTime: "+t+"\n"+(group==null?"":" groupingId: "+group+"\n")+" event: Tick\n nodeLogs:\n  - priceListener: { seen: true}\n---\n";}
 static String control(int t,String grouping,String source,String addressed,String level){return "eventLogRecord:\n logTime: "+t+"\n"+(grouping==null?"":" groupingId: "+grouping+"\n")+" event: EventLogControlEvent\n eventToString: "+new EventLogControlEvent(source,addressed,EventLogControlEvent.LogLevel.valueOf(level))+"\n---\n";}
 static void annotation(String name,String text,int selected)throws Exception{var s=new HeapLogStore(text);System.out.println(name+" => "+PerNodeLevelChanges.of(s).annotationFor("riskMonitor",new int[]{selected}));
  var topology=GraphMlParser.parse(Files.readString(Path.of("src/test/resources/topology/demo-quote-processor-noaudit.graphml")));
  var filter=new telamin.fluxtion.audit.analyser.analyser.filter.FilterState();filter.setTimeRange(s.record(selected).logTime(),s.record(selected).logTime());
  var result=CoverageService.assess(s,true,filter,new CoverageService.Input(topology,Scaffolding.authoredNodes(topology),null));
  System.out.println("  CoverageService selected="+result.echo().get("recordsScanned")+" ratio="+result.echo().get("ratio")+" annotations="+result.echo().get("levelAnnotations"));
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("ma-rereview-input-");
  Path p=dir.resolve("bad.yaml");Files.writeString(p,row(1,null)+END);var live=HeapLogStore.fromFile(p).forFollow();
  System.out.println("before invalid append: "+live.streamEnd().state()+" identities="+live.readIdentities().size());
  Files.write(p,new byte[]{(byte)0xc0},StandardOpenOption.APPEND);try{live.appendFrom(p);}catch(Exception e){System.out.println("invalid append throws "+e.getClass().getSimpleName());}
  System.out.println("after invalid append: "+live.streamEnd().state()+" pending="+live.trailingRecordsPending()+" identities="+live.readIdentities().size());
  for(String source:new String[]{"riskMonitor, DEMO","riskMonitor}DEMO"," riskMonitor ","","null"}){
   EventLogManager m=new EventLogManager();m.clock=new com.telamin.fluxtion.runtime.time.Clock();m.init();var node=new Node();m.nodeRegistered(node,"riskMonitor");
   m.calculationLogConfig(new EventLogControlEvent(null,null,EventLogControlEvent.LogLevel.INFO));
   var event=new EventLogControlEvent(source,null,EventLogControlEvent.LogLevel.WARN);m.calculationLogConfig(event);
   System.out.println("REAL runtime source='"+source+"' stillINFO="+node.logger.canLog(EventLogControlEvent.LogLevel.INFO));
   annotation("source='"+source+"'",control(1,null,source,null,"WARN")+row(2,null),1);
  }
  EventLogManager m=new EventLogManager();m.clock=new com.telamin.fluxtion.runtime.time.Clock();m.init();m.setLogGroupId("alpha");var node=new Node();m.nodeRegistered(node,"riskMonitor");
  m.calculationLogConfig(new EventLogControlEvent(null,"alpha",EventLogControlEvent.LogLevel.INFO));
  m.calculationLogConfig(new EventLogControlEvent("riskMonitor","alpha, DEMO",EventLogControlEvent.LogLevel.WARN));
  System.out.println("REAL runtime group='alpha, DEMO' stillINFO="+node.logger.canLog(EventLogControlEvent.LogLevel.INFO));
  annotation("truncated group",control(1,"alpha","riskMonitor","alpha, DEMO","WARN")+row(2,"alpha"),1);
  annotation("alpha WARN applied to beta row",control(1,"alpha","riskMonitor","alpha","WARN")+row(2,"beta"),1);
  annotation("beta INFO closes alpha WARN",control(1,"alpha","riskMonitor","alpha","WARN")+control(2,"beta",null,"beta","INFO")+row(3,"alpha"),2);
  annotation("missing grouping accepted as ungrouped",control(1,null,"riskMonitor","alpha","WARN")+row(2,"beta"),1);
  annotation("only later run selected",control(1,null,"riskMonitor",null,"WARN")+END+row(2,null)+END,1);
  Path empty=dir.resolve("empty.yaml");Files.writeString(empty,"");var follow=HeapLogStore.fromFile(empty).forFollow();
  Files.writeString(empty,"\uFEFF---\n"+row(1,null));follow.appendFrom(empty);System.out.println("empty-start Follow file BOM count="+follow.size()+" state="+follow.streamEnd().state());
  LogRecordListener listener=new LogRecordListener(){public void processLogRecord(LogRecord r){} public String toString(){return "DEMO, sourceId=other";}};
  var nested=new EventLogControlEvent("riskMonitor",null,EventLogControlEvent.LogLevel.WARN,listener);
  annotation("duplicate field inside real listener rendering",control(1,null,"riskMonitor",null,"WARN").replace(new EventLogControlEvent("riskMonitor",null,EventLogControlEvent.LogLevel.WARN).toString(),nested.toString())+row(2,null),1);
  Path a=dir.resolve("demo.log.1"),rolledB=dir.resolve("demo.log.2");Files.writeString(a,control(1,"alpha","riskMonitor","alpha","WARN"));Files.writeString(rolledB,row(2,"beta"));
  var ordered=RollSetResolver.resolve(List.of(rolledB,a)).ordered().stream().map(RollSetResolver.Sibling::file).toList();
  for(int threshold:new int[]{10,0})try(var rolls=RolledLogStore.open(ordered,threshold)){System.out.println("rolled threshold="+threshold+" alpha control beta selected => "+PerNodeLevelChanges.of(rolls).annotationFor("riskMonitor",new int[]{1}));}
  for(boolean recordEndTime:new boolean[]{true,false}){
   var clock=new com.telamin.fluxtion.runtime.time.Clock();clock.init();LogRecord runtime=new LogRecord(clock);runtime.setRecordEndTime(recordEndTime);runtime.triggerObject("DEMO");runtime.addRecord("quoteHandler","value","DEMO\n\uFEFF---\neventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n#");runtime.terminateRecord();
   String exported=export(runtime.toString());Path f=dir.resolve("spi-"+recordEndTime+".yaml");Files.writeString(f,exported);
   try(var spi=telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore.open(new telamin.fluxtion.audit.analyser.analyser.spi.YamlAuditReader(),f)){System.out.println("SPI runtime payload endTime="+recordEndTime+" count="+spi.size()+" state="+spi.streamEnd().state());}
  }
  Method decode=HeapLogStore.class.getDeclaredMethod("decodeCompletePrefix",byte[].class);decode.setAccessible(true);
  for(int cp=0;cp<=0x10ffff;cp++){if(cp>=0xd800&&cp<=0xdfff)continue;byte[] b=new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);for(int end=1;end<b.length;end++)validPrefixes.add(key(b,0,end));}
  int checked=0;for(int n=0;n<=2;n++){int size=1<<(n*8);for(int j=0;j<size;j++){byte[] b=new byte[n];for(int i=0;i<n;i++)b[i]=(byte)(j>>>(8*i));compare(decode,b);checked++;}}
  Random random=new Random(20260924);for(int j=0;j<250000;j++){byte[] b=new byte[3+random.nextInt(6)];random.nextBytes(b);compare(decode,b);checked++;}
  System.out.println("JDK decoder plus exhaustive Unicode-scalar prefix oracle cases="+checked+" mismatches=0");
 }
 static String export(String doc)throws Exception{
  Class<?> c=Class.forName("com.telamin.mongoose.plugin.svc.adminweb.WebAdminService$YamlContainerWriter");var ctor=c.getDeclaredConstructor(java.io.Writer.class);ctor.setAccessible(true);var document=c.getDeclaredMethod("document",String.class);document.setAccessible(true);var end=c.getDeclaredMethod("end");end.setAccessible(true);var out=new java.io.StringWriter();Object writer=ctor.newInstance(out);document.invoke(writer,doc);end.invoke(writer);return out.toString();
 }
 static void compare(Method decode,byte[] b)throws Exception{
  ByteBuffer input=ByteBuffer.wrap(b);CharBuffer output=CharBuffer.allocate(b.length+1);CoderResult r=StandardCharsets.UTF_8.newDecoder().decode(input,output,false);output.flip();
  boolean bad=false;Object got=null;try{got=decode.invoke(null,(Object)b);}catch(InvocationTargetException e){if(!(e.getCause() instanceof CharacterCodingException))throw e;bad=true;}
  boolean expectedBad=r.isError() || (input.remaining()>0&&!validPrefixes.contains(key(b,input.position(),b.length)));
  if(bad!=expectedBad)throw new AssertionError("validity mismatch "+HexFormat.of().formatHex(b));
  if(!bad){Method text=got.getClass().getDeclaredMethod("text"),pending=got.getClass().getDeclaredMethod("pendingBytes");text.setAccessible(true);pending.setAccessible(true);
   if(!output.toString().equals(text.invoke(got)) || input.remaining()!=(int)pending.invoke(got))throw new AssertionError("decode mismatch "+HexFormat.of().formatHex(b));}
 }
}
```

## Reproduction appendix — mutation runner

Run only in a **disposable, otherwise idle 6998fcc8 worktree** with `JAVA_HOME` set to JDK 21. Save the
following as a Python file outside it. It writes logs/results under a temporary directory, restores
source in `finally`, and stops on a missing assertion or failed restoration. The final `mvn test` above
was run separately after all controls. The F6 case is included here alongside the fourteen; in my run it
was an additional invocation of the same harness. Do not run probes concurrently against its temporarily
mutated classes.

```python
from pathlib import Path
import subprocess,os,hashlib,json,xml.etree.ElementTree as ET
root=Path.cwd()
base=root/'src/main/java/telamin/fluxtion/audit/analyser/analyser'
test=root/'src/test/java/telamin/fluxtion/audit/analyser/analyser'
out=Path('/private/tmp/ma-rereview-mutations');out.mkdir(exist_ok=True)
env=dict(os.environ)  # Set JAVA_HOME to JDK 21 before invoking this runner.
cases=[]
def add(id,path,old,new,suite,method):cases.append((id,path,old,new,suite,method))
p=base/'parse/HeapLogStore.java';q=base/'topology/PerNodeLevelChanges.java'
cut='aCharacterCutAfterEveryPrefixByteWithholdsTheVerdictAndThenRecovers'
add('F1-string',base/'parse/RecordFramer.java','if (start == 0) while','if (true) while','ExporterFramingAgreementTest','everyHostileSeparatorReadsExactlyAsItsBenignControl')
add('F1-bytes',base/'parse/ByteRecordFramer.java','lineStart == 0 ? skipBom(b) : 0','skipBom(b)','ExporterFramingAgreementTest','everyHostileSeparatorReadsExactlyAsItsBenignControl')
add('F2-growth',p,'String full = decoded.text();','String full = decoded.text();\n        if (full.length() == file.length()) return 0;','FollowPendingBytesTest',cut)
add('F2-verdict',p,'if (trailingPending || pendingBytes > 0) streamEnd =','if (trailingPending) streamEnd =','FollowPendingBytesTest',cut)
add('F2-identity',p,'this.readIdentity = null;','// mutant: opening identity retained','FollowPendingBytesTest',cut)
add('F2-pending',p,'return trailingPending || pendingBytes > 0 ? 1 : 0;','return trailingPending ? 1 : 0;','FollowPendingBytesTest',cut)
add('F2-validity',p,'!canBegin(v) || (lead + 1 < n && !secondByteAllowed(v, b[lead + 1] & 0xFF))','false','FollowPendingBytesTest','bytesThatCanNeverBeginACharacterFailRatherThanWait')
add('F3-boundary',base/'parse/ProducerDiagnostics.java','&& !plainSeparator','&& true','RecordKeyBoundaryTest','aHealthyBinaryRecordThroughTheRealReaderHasItsKey')
add('F4-global',q,'(sourceId == null || sourceId.equals(nodeId))','(sourceId != null && sourceId.equals(nodeId))','CoveragePerNodeLevelTest','aGlobalRestoreClosesAPerNodeInterval')
add('F5-restore',q,'if (r >= until) break;','if (r > until) break;','CoveragePerNodeLevelTest','theRestoreBoundaryIsHalfOpen')
add('F5-empty',q,'if (inView == null || inView.length == 0) return null;','if (inView == null || inView.length == 0) inView = new int[]{1000000};','CoveragePerNodeLevelTest','anEmptySelectionIsExplainedByNothing')
add('F5-future',q,'if (r <= from) continue;','// mutant: no lower bound','CoveragePerNodeLevelTest','aControlAfterTheRecordsInViewExplainsNothing_evenUntimed')
add('O1-octal',test/'parse/ByteOrderMarkSitesTest.java',"(Character.digit(line.charAt(i + 1), 8) >= 0 || line.charAt(i + 1) == '_')","(Character.digit(line.charAt(i + 1), 8) >= 0)",'ByteOrderMarkSitesTest','theLexerReadsEveryClaimedSpellingAsItsValue')
add('O3-rendering',q,'text == null || !recognised(text)','text == null','CoveragePerNodeLevelTest','aFieldThatIsNotTheFieldIsNotReadAsItOrAsAbsent')
add('F6-wording',base/'parse/ProducerDiagnostics.java','If a stream-end marker covers it, the marker counts it too: a matching marker ','This log reads as complete. If a stream-end marker covers it, the marker counts it too: a matching marker ','RecordKeyBoundaryTest','theWordingNeverStatesAVerdictItCannotSeeOrInventsMissingContent')
def run(label,suite,method):
 cmd=['mvn','-q','-Dtest='+suite+'#'+method,'test']
 with (out/(label+'.log')).open('w') as f:r=subprocess.run(cmd,cwd=root,env=env,stdout=f,stderr=subprocess.STDOUT)
 paths=list((root/'target/surefire-reports').glob('TEST-*.'+suite+'.xml'))
 assert len(paths)==1,paths
 tree=ET.parse(paths[0]);fails=[]
 for t in tree.findall('.//testcase'):
  for tag in ('failure','error'):
   for e in t.findall(tag):fails.append({'test':t.get('name'),'kind':tag,'message':e.get('message'),'text':e.text})
 return r.returncode,fails
results=[]
for id,path,old,new,suite,method in cases:
 original=path.read_bytes();s=original.decode();assert s.count(old)==1,(id,s.count(old))
 pre,fail=run(id+'-baseline',suite,method);assert pre==0 and not fail,(id,'baseline')
 try:
  path.write_text(s.replace(old,new));rc,fail=run(id+'-mutated',suite,method)
 finally:path.write_bytes(original)
 assert path.read_bytes()==original
 restored,restoredFail=run(id+'-restored',suite,method)
 result=dict(id=id,source=str(path.relative_to(root)),old=old,new=new,suite=suite,method=method,baselineGreen=pre==0,mutantExit=rc,failures=fail,restoredGreen=restored==0 and not restoredFail,sourceRestored=True,sha256=hashlib.sha256(original).hexdigest())
 results.append(result);(out/'results.json').write_text(json.dumps(results,indent=2))
 print(id, 'named assertion RED' if rc!=0 and any(f['test'].split('(')[0]==method and f['kind']=='failure' for f in fail) else 'WITNESS FAILED', 'restored',restored,flush=True)
 assert rc!=0 and any(f['test'].split('(')[0]==method and f['kind']=='failure' for f in fail),(id,fail)
 assert restored==0 and not restoredFail
```
