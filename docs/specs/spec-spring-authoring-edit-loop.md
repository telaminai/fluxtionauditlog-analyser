# Spring authoring: make the second edit routine

Status: **PROPOSED v3**, 2026-09-25. No implementation or release acceptance is claimed.

Revision 3 adds the owner's design-first walkthrough, admin-console offer and M67 vendor
integration requirements (§I). These remain proposed work, not implemented product changes.

Revision 2 incorporates feedback 21–24 and the addition to 19, received during the first
review. These additions came primarily from documentation inspection, not further runtime
trials. Prior acceptance remains; §G now covers mapper composition, replay terminology,
loader choices and versioned plugin documentation.

This is the fix specification for a guided session that started from the analyser website's
prompt and `audit-analyser-bundle` download. The first run worked; changing existing nodes
needed expert intervention. The goal is a repeatable loop: intent → XML/Java edit → local
checks → generation → independent behavioural check → discuss the recorded result.

Read the [feedback review](../handoff/review_spring_authoring_feedback_2026_09_25.md) for
all twenty-four items, evidence limits and corrections to the participant's proposed remedies.
The [tracker](tracker.md#spring-authoring-edit-loop--session-intake-2026-09-25) owns new
analyser work. Existing items below retain their ownership and status; this document supplies
additional acceptance, not parallel completion boxes. Upstream work stays with its producer.

## Scope and evidence

The session reports starter/BOM 1.0.74, runtime 1.0.16, plugin 1.3.0, Mongoose 1.0.29,
plugins 1.0.44 and GraalVM 25.0.2. Its exact analyser version and original ZIP digest were
not recorded. Analyser source inspection for this proposal uses `710dc2ca` (1.20.1).
The current project has been edited: it is not a pristine downloaded fixture. These are
participant observations plus the source inspections named in the review, not a replay.

Do not count this guided session as an uncoached acquisition success or as closure of G14.
Do not claim that five failed generation attempts were charged. Invocation and billing
were not established. Preserve those uncertainties in receipts and documentation.

No runtime semantics, compiler ownership guarantees or read-authorisation boundary may be
weakened to shorten the workflow. Keep body-only keyless builds, implemented Java bodies,
read-only conflict refusal, vendor composition and source/run qualification intact.

## Delivery order and canonical ownership

| Order | Work | Feedback | Canonical owner / relationship |
|---|---|---|---|
| 1 | Repair structural-edit compilation; improve conflict guidance | 1, 4 | UP-FLX-21, compiler/starter + template producer; §A |
| 1 | Stop manufacturing valid events from malformed input | 6 | Hosted template producer; D-T9/D-T8 requirements; §B |
| 2 | Make ordinary Java navigation revision-aware | 3, 20 | New analyser work; reuse released source-spotlight machinery; §C |
| 3 | Support explicit ownership-preserving rename | 2 | Compiler/starter generated-member contract; §D |
| 4 | Clarify recovery provenance and authorise project inputs deliberately | 5, 11 | Existing journey recovery and M68.5 root diagnostics, plus scoped grant decision; §E |
| 5 | Separate XML validation, model preflight and provider accounting | 14–16 | Compiler/starter + public authoring contract; §F |
| Parallel | Repair downloaded guides, executable scripts and example tests | 8–10, 14, 18–24 | Existing authoring-route intake; compiler/starter + playground; §G |
| Existing queue | Run-scoped audit, fan-out, record-order charts, economical context | 7, 12, 13, 17 | MA-2/MA-5 and OD-5; existing chart item; new context projection; §H |

Order 1 includes a data-correctness fix, not just onboarding polish. Documentation repairs
can ship independently; a page explaining a workaround does not close the broken edit loop.
Do not start another client battery. Every slice first gets an executable cheap check.

## A. Compile the edited model without depending on the obsolete processor

**UP-FLX-21**, extended by this recurrence. Structural edits supported by the authoring
contract must work through the downloaded `generate.sh`, without manually moving generated
Java or permanently opting out of reconciliation.

The pipeline must compile the authored model needed for generation independently of the
old dispatcher; generate from that model; then compile the new dispatcher and its consumers.
Simply moving the scan earlier is insufficient if it needs compiled nodes. Simply excluding
the processor is insufficient if a supplier imports it. The implementing owner must choose
and document a complete dependency strategy before changing the Maven phases.

Use a bounded staging area or equivalent transactional preparation. Never delete the last
usable generated source as an undocumented prerequisite. On local preparation or generation
failure, retain prior usable generated outputs and implemented bodies, mark the current
attempt failed, and never reuse a stale success receipt. This is not a promise to roll back
all arbitrary Maven side effects; enumerate the authoring outputs protected by the operation.

**Acceptance A:** start from the real template's processor/supplier structure. Independently
add a constructor parent, rename a node class, add a setter-wired parent, and add a new node.
Each case uses the normal scripts and leaves a subsequent ordinary keyless build usable.
For §A's class-rename fixture, begin from mutually consistent edited XML/Java/ownership;
the operation that creates that state is §D's separate acceptance, not an opt-out workaround.
Isolate the compiler route with a deterministic local test provider for cheap regression;
label it a fixture, not a hosted-generation result. A public-download acceptance follows
publication. Test missing classpath, invalid constructor, provider failure and changed input
between planning and apply; no destructive repair and no success receipt on failure.

**Controls:** restore compile-before-generation and require the constructor-edit assertion
to fail; remove final consumer compilation and require a broken supplier to be rejected.
The setter case is an alternative wiring test, not a workaround that closes constructor support.

Conflict diagnostics must distinguish removed recorded source/member, edited owned member,
missing binding, ambiguous constructor and stale classpath. Include bean/XML location, Java
member/type, observed declaration, supported repair choices and whether anything was written.
Only print an exact suggested declaration when its binding is established. A bean id is not
universally a required Java field name; preserve existing valid alternate bindings. Test each
conflict against a fixture and mutate away its distinguishing diagnostic field.

## B. A bad input line must not become a plausible event

The shipped mapper must never synthesize zero price/quantity or another default business
value on parse failure. Proposed template policy: return the original input through a typed
rejection/unknown-event path with a visible reason; valid input still yields its typed event.
For this bundle, the existing unhandled-event route is a candidate, not assumed proof that
a rejection is visible in an audit record. Do not return null and silently drop the input.

**Acceptance B:** exercise the actual hosted feed adapter with valid, blank, short, trailing-empty,
non-numeric, non-finite and extra-field rows. Freeze the small CSV grammar (field count,
whitespace and blank-line policy) in the template contract. Invalid rows produce no business
event and no successful risk decision; each rejected row is accounted for on a named observable
surface. Test downstream state/output, not just the mapper's return value. Restore the
zero-valued fallback and require the invalid-row behavioural assertion to fail. Preserve
valid rows unchanged. A source-only assertion that the fallback text vanished is insufficient.

## C. Refresh Java snapshots without claiming they match the run

Ordinary source navigation currently reuses a pane for the same FQN; the selected processor
model is cached separately. A generic repaint is not a reread. Reuse the revision-bound source
lookup used by Java spotlights, extending its lifecycle rather than adding another resolver.

On explicit source navigation, showing a Java pane, or completion of log/graph replacement,
revalidate each affected visible document. Read, hash and parse off the EDT; install text,
origin and the selected processor model from one immutable snapshot on the EDT only while
its project/configuration/request ticket is current. Coalesce superseded requests, impose a
bounded preparation deadline, and never apply after cancellation. Avoid a full repository
scan on every Follow append. Keep an explicit refresh route for an already-visible pane.

While checking, disclose the displayed revision as unchecked for disk freshness. If the file
changed, either install the accepted current snapshot or clearly mark the retained snapshot
stale. If it disappeared or became unreadable, do not silently display its former body as
current. Disk freshness and source/run identity are separate: a fresh read still says
`Source/run: unverified`. Invalidate existing spotlight bindings when their revision changes;
never silently move an old highlight to different text. Preserve scroll where meaningful.

**Acceptance C:** same-FQN body change, constructor change, same-size/same-mtime replacement,
deleted class, renamed class, missing→present file and selected-model navigation after each.
Run a real-frame test through ordinary navigation, without using a spotlight to cause refresh.
Use a blocked-read test for EDT responsiveness and project switch/cancel/timeout supersession.
Controls independently disable reread, model replacement and stale-ticket rejection; each
must fail its own assertion. Retain the existing source-spotlight geometry/policy tests.

For vendor sources, first use the existing optional local Maven `*-sources.jar` resolver.
Show search enabled/disabled, selected origin and explicit absence; do not suggest adding an
archive as a directory root. Cover absent source, an attached matching source archive, cached
miss, replaced archive and new archive after resolver discovery. Preserve first-match
qualification and do not claim binary/source equivalence merely from a matching FQN. This
slice adds no automatic download or decompilation; dependency-bound source selection is
separate work if an exact correspondence is required.

## D. Rename must migrate ownership explicitly, not erase its history

Provide a plan/apply operation for explicit old→new mappings of class FQN, bean id and affected
owned members. The implementing owner must settle the command/schema in the generated-member
contract before code. A proposed rename operation is not the existing `link` command: `link`
creates a browser link to the design.

Support class-only, bean-only and combined rename, retaining developer bodies and unrelated
ownership. Prefer planning before the edit. An already-edited project may be adopted only
when the old baseline and explicit mapping establish which owned constructs survived; otherwise
refuse with the missing proof named. Do not reset stored hashes to current bodies, infer a
rename from deletion, or resolve collisions by overwriting. Refuse ambiguous mappings,
existing destinations and changed inputs since preview. Apply source/XML/record changes as
one recoverable operation; interrupted apply must not strand half-migrated ownership.

**Acceptance D:** rename, then default regenerate, then add a new owned node and default
regenerate again. Verify the new shell is created, old ownership is gone only for mapped
constructs, bodies remain byte-identical, and no `--no-reconcile` escape is required. Cover
renamed references and externally supplied types. Controls leave the old record entry,
overwrite an edited member and bypass a destination collision; each must fail separately.
Keep a no-write failure witness. Until this lands, docs must call the rename workflow unsupported
rather than prescribing repeated opt-out or hand-editing ownership hashes.

## E. Recovery provenance and project-file access

### Recovery: reproduce the capture, not just the offer

The analyser already keys recovery by project identity and rejects a mismatched stored key.
An external path is not proof of a foreign project: a person may deliberately inspect an
external log. The reported offer therefore needs its capture/switch sequence reproduced.

Extend the existing journey-recovery acceptance: open project A and a log outside its folder;
switch to B while I/O is pending; capture/close/reopen each; include no-project state and two
profiles with the same display name. B must never receive A's offer or late completion.
An offer must name the profile identity under which it was captured, input origin, and what
acceptance will restore. A deliberately selected external input under B remains eligible.
No automatic restore and no new blanket refusal based on directory ancestry.

A regression fixture must demonstrate the reported wrong capture/offer before attributing
a root cause. If it cannot, retain this as unverified intake and ship only independently
justified provenance disclosure. Mutate key/generation isolation for the eventual regression.

### Access: declarations are not grants

`DesignFiles` deliberately distinguishes a relative-path base from permission to read. Do not
silently authorise the project root or all of `target/`. The supplied current profile does
not establish the feedback's assertion that every requested file had an explicit grant.

Proposed improvement: a project-open preview offers one explicit, role-scoped grant for its
actual design, authoring record, run receipt and declared diagnostic outputs. Validate any new
profile role schema at import; do not infer authority from a runbook's prose. Persist accepted
grants with the project, not globally. Canonicalise and check every read, including replacement
symlinks, and retain size limits. Until this policy is approved, improve the existing refusal
and generated setup instructions rather than widening access.

**Acceptance E:** relocated project, ambiguous relative path, imported profile, missing input,
external/symlink escape, and changed symlink after approval. One approved normal setup opens
the declared roles without repeatedly adding broad source roots; an unapproved profile cannot
read them. Keep M68.5's project-anchor and unresolved-pointer diagnostics acceptance separate.

## F. Local model preflight and honest cost reporting

Keep `validate` an inert XML/declaration check. Its success must explicitly name that scope.
A separate, named local model-preflight stage may compile/load application classes with the
user's knowledge, using the supported model checks before contacting a provider. It is not
a sandbox: user constructors or initialisers can have effects. Integrate it with §A's staged
compile; do not validate stale bytecode or duplicate the generator's rules in another heuristic.
If a check cannot be established locally, report it as not checked and name the remaining stage.

**Acceptance F:** duplicate supplied names and unmappable constructor fields are rejected before
a fake provider's request counter increments; valid graphs reach it once. Include stale/missing
classpath, equality-based sharing and setter wiring. A mutation bypassing preflight must make
the request-count assertion fail. Preserve the provider's own validation for direct callers.

Record attempt identity, stage status, provider route, request attempted/accepted when known,
and response/cost evidence when supplied. Provider invocation is not billing. Absent billing
metadata must read unknown, never zero, and receipts must not contain credentials. Preserve
receipts for failed attempts before the next attempt overwrites them. Test local refusal,
remote refusal, interrupted request and success; none may inherit another attempt's status.

For duplicate/equal node instances, expose declared aliases and the selected canonical id in
model diagnostics. Test reversed XML declaration order and resulting generated/GraphML/audit
ids. **Owner decision:** retain current id precedence with an explicit diagnostic, or choose a
new stable naming rule with a compatibility change. Do not silently substitute `NamedNode`
as the identity contract. Do not teach referenced-but-unlisted nodes as a way around validation.

## G. Make the downloaded project teach the supported path

One versioned public authoring contract must be discoverable from the pinned tool/project,
either bundled or at an immutable versioned location. Do not direct a released starter only
to a moving contract. Keep transient/collection rules, callback auditing and ownership repair
consistent across the contract, generated guides and `add-a-node` skill.

Required changes and checks:

- **Archive permissions:** test the actual ZIP writer used by each supported download route,
  then extract a published archive on a POSIX host and invoke its entry points without chmod.
  `ProjectFile.executable=true` alone does not prove ZIP permissions. Removing the authoring
  scripts' ZIP mode must fail the archive check. Also verify the supplied Windows entry points.
- **CLI:** top-level and per-command help exit successfully without project mutation or key
  access. Explain `link` as a browser URL and list the supported workflow commands. Unknown
  options still refuse. A help-parser mutation must fail the relevant command test.
- **Guides:** repair the truncated sentence; distinguish runtime/BOM/starter/plugin versions;
  link every reconciliation conflict to a matching explanation. Smoke-check every prescribed
  command exists in the archive and uses its supported launcher. No invented fallback command.
- **Hosting:** link the actual descriptor. Remove the copied block's claim to be effective
  configuration; a deliberately historical example must be labelled as such. Change the
  descriptor in a generated fixture and prove the guide no longer asserts the old contents.
- **Patterns:** add references with setters versus constructors; naming versus equality;
  supported sharing; inherited constructor-field mapping; per-parent callbacks; vendor-neutral
  interfaces. Build small public examples with expected behaviour. Owner tips in this session
  are hypotheses until those examples run. Do not turn a diagnostic's field guess into a rule.
- **Starter test:** add a keyless test sending a small independent expected input/output table
  through the shipped processor. Cover a rejection and a boundary case; a wrong business
  threshold must fail. Assert actual state/sink output, with audit as additional evidence when
  available, not only construction or a successful build. Freeze expected results before code.
  Prefer the already-declared `mongoose-test-support` for the hosted test: wrap a server built
  from the real descriptor or customise the harness so the actual mapper and processor are
  exercised, not a substitute echo handler. Use isolated temporary inputs, condition-based
  awaits and guaranteed teardown. The direct processor test and hosted wiring test prove
  different things; keep both claims explicit.
- **Vendor source guide:** explain existing local source-archive lookup, attaching sources to a
  dependency and what a missing-source result means; verify it using a placeholder component.

### G1. Teach the mapper extension point with examples that run (feedback 21)

Document `EventFeedConfig.valueMapper` as a per-feed transformation, including the concrete
input/output types and wrapping mode used by each example. Show single-type mapping, a vendor
mapper configured without application glue, and composition of app/vendor mappers on one
ordered feed. Freeze the composition rule: which mapper receives the original versus mapped
value, how “not mine” differs from malformed input, what wins if two accept, and how a rejected
row is accounted for. Do not generalise the session's first-match helper into an existing
framework composition API. State ordering within this feed, not across unrelated feeds.

`lib-jsonserialiser` is an existing candidate, not a proven drop-in recipe. Inspection of
plugins 1.0.44 found a material documentation mismatch: the example uses `@type`/`typeMap`,
while `TypeSerialiser` reads `type` as a class name, returns a map without that key, and returns
null for some parsing/class-lookup failures. Do not promise a registered type allow-list that
this version does not implement, or recommend arbitrary input-selected classes for untrusted
feeds. Decide whether to document the supported version accurately or repair the plugin;
any incompatible discriminator change needs a versioned migration note.

**Acceptance G1:** extract the published JSONL and YAML example verbatim and run it with the
pinned artifact, asserting the actual typed outputs. Include two event types, absent/unknown
class discriminator, malformed JSON, mapper overlap, unchanged input and exceptions. Run
through the real feed route so null/silent drops cannot pass merely because the mapper returned.
Apply §B's visible-rejection requirement. Controls substitute the wrong discriminator and
disable rejection accounting; each must fail a named assertion. No new generic mapper or
analyser verb is needed merely to make this capability discoverable.

### G2. One replay statement for the selected configuration (feedback 22)

Remove the unconditional deterministic-replay promise from emitted capture comments as well
as the hosting guide; fixing only the copied block leaves the contradictory source intact.
Distinguish four capabilities: reread original input; replay a connector's input archive;
inspect exported execution records; reproduce a previous application run. A connector's
replay feature does not establish a replay service for an arbitrary bundle. A loader restoring
persisted processor configurations is also not event replay.

Publish a short capability table for the selected pins/configuration: source of replayable
inputs, exact supported command or unavailable status, ordering domain, start/end position,
initial application state, dependency/configuration identity, and external/time inputs that
remain uncontrolled. Captured audit records need not contain reconstructible input events.
No invented admin replay command; no claim that installing capture enables incident replay.
When reproduction is unsupported, say which evidence/input is missing and preserve the audit
inspection path. Link this to §H's run-scope work without closing MA-2 or OD-5.

**Acceptance G2:** build the chosen minimal example, preserve its inputs and expectations,
then replay through the documented route from a specified clean state and compare outputs.
Separately assert the documented refusal/unavailable result for the default bundle if it has
no such route. An audit-only export must not qualify as replayable input without a tested
reconstruction contract. Mutate the capability/configuration or remove the required input
and require the acceptance to fail rather than silently falling back to hand-built events.
A generated-doc check must reject contradictory replay claims across descriptor and runbook.

### G3. Compare three loading routes, not “runtime” versus “deterministic” (feedback 23)

The inspected Spring loader supports both a compile branch and an interpreter branch. Teach
three choices: deploy a processor generated during the build; compile a Spring graph while
loading it into a server; interpret a Spring graph while loading it. For each, identify when
classes are instantiated, when generation occurs, provider/key requirements for the selected
versions, emitted/persisted Java and GraphML, audit options, startup/reload behaviour and
failure boundaries. Retain the loader's preview qualification where applicable. A UI or
service loading something at runtime does not by itself establish keyless operation.

**Acceptance G3:** a small common graph and frozen event table must exercise each advertised
route on the named versions, with the actual provider boundary observed or explicitly marked
unverified. Assert outputs, relevant dispatch/audit observations and failure handling. Scope
any equivalence claim to those tested properties and graph; do not promise universal identical
dispatch, regeneration reproducibility or pricing from a shared XML input. Do not switch the
starter's default route as a documentation fix. Fake-provider tests may establish request
routing, not public provisioning or charging. No key use is authorised by this spec.

### G4. State what version the plugin documentation describes (feedback 24)

Separate the documented version, the latest published artifact and the project's pin. The
live overview inspected for this revision advertised 1.0.37, while the participant project's
plugin pin is 1.0.44. This establishes a mismatch, not which later version is currently latest.
Serve immutable version-compatible documentation (or explicitly state its absence), generate
version labels/snippets from one release metadata source, and link the generated project's
hosting guide to its matching contract. Do not fix this by merely hard-coding 1.0.44 as latest.

**Acceptance G4:** inspect built site output and dependency snippets against the documentation
build's declared version; check the matching-version links in an actual generated bundle.
A synthetic newer release/version input must update the labels, while older versioned docs
remain unchanged. A stale “latest” label or broken matching-version route must fail the
release/docs check. A version label alone does not prove that its examples work: G1/G3 supply
that behavioural acceptance. Preserve an explicit unsupported-version outcome.

Update screenshots only if their depicted behaviour changes, using isolated demo data. These
checks run before a new hosted client trial. Plain `mvn package` remains keyless for the bundle.

## H. Existing audit/chart work and a smaller context response

**Feedback 7:** persistence across restarts is not itself a defect. MA-2/OD-5 own run boundaries
and Chronicle's contract. Capture/export must disclose cumulative versus run-specific scope.
A convenience per-run export needs a producer-issued boundary/id or captured start position,
including restarts, multiple processors and rolls; never infer it from a guessed timestamp.
If no trustworthy boundary exists, refuse run-only export while offering labelled cumulative
export. Do not delete or move a customer's prior capture automatically. Test two starts,
rollover and export counts against preserved inputs; disabling boundary selection must fail.

**Feedback 13:** route to MA-5, not a new analyser diagnostic. Test the original configured
listener and capture together, then stop/restart and verify restoration. The session's older
pins do not prove whether newer upstream work fixes it. Acceptance must run against a released,
consumed version. No stdout output alone is never evidence of no execution.

**Feedback 12:** use the existing record-order x-axis item. Freeze equal-timestamp input in a
cheap fixture; require distinct selectable points, stable original record identities under
filtering, persistence, report/export and note/marker consistency. The axis means record order,
not elapsed time or cross-feed causality. Removing the axis mode must fail an assertion without
inserting sleeps into the producer.

**Feedback 17:** add opt-in `context` section projection with the full response as the compatible
default. Define and publish the allowed section names; unknown names refuse before mutation.
Return a scope description; selected fields must equal those of a full context from the same
captured state. Include the qualification/basis with any selected verdict. Do not silently
omit a warning that qualifies a selected fact. Skip unrequested expensive reads/serialisation.

Test menu-only, pairing-with-basis, empty/unknown selection, project transition and full-default
compatibility. Record bytes for a fixed fixture, not an invented token saving. Mutate projection
or drop a verdict's qualification and require separate assertions to fail. Update schemas,
assistant guidance and portable-context tests together; no new verb is required.

## I. Design-first market-data tour and existing vendor jars

Owner additions, 2026-09-25. This extends the current demo/journey and **M67.1–M67.6**;
it does not open a second vendor catalogue. The existing D-X8 dedicated extension-template
entry may share the market-data base and assets. These are proposed implementation requirements.

### I1. Open the design before collecting evidence

The inspected template profile supplies only `src/main/java`; its Spring XML lives under
`src/main/fluxtion/designer`. The participant's profile now includes that directory and
`target`, but those later additions do not fix the template. Add the actual emitted design
directory to the Spring template's explicit source roots, with a visible project-load summary.
This is a narrow generated-profile default, not an implicit grant to the entire project.
Generic producer-result grants remain the separate §E policy decision.

Give the generated guide a direct design-open step and, if supported by the profile contract,
a typed design pointer. Do not invent an ignored profile key. The UI must distinguish authored
XML (**Source → Design**) from compiled **Topology** (GraphML). XML is not automatically proof
of the compiled graph. Offer navigation between the design and the loaded topology with the
existing relationship qualification; explain the distinct views at the point of use.

The first tour step needs no audit log: inspect XML, shipped GraphML and generated Java,
then a node's Java beside the topology. No artificial empty log, background application run
or restored old session is needed. Current open-design and open-GraphML paths already permit
no-log operation; improve discovery and test the complete journey instead of inventing a new
mode. With no log, pairing/coverage must say unavailable/not compared, never matched or executed.

**Acceptance I1:** extract the real Spring download under an isolated home, open its profile
through the normal UI, then use the actual Sources actions to open design and GraphML and
show Java beside topology. Assert no log is loaded, readable design/source, the visible
no-comparison qualification, and no unexpected recovery. Removing the emitted design root
must fail the design-read assertion; requiring a log must fail the no-log UI assertion.
Use real-frame button/menu checks for visibility, not just a socket echo. Preserve the
no-log frame and matching generated guides as the first tour screenshot.

### I2. Offer the matching, reachable Mongoose console

After the caller's normal start command, offer **Open Mongoose admin console** if the selected
project's server is identified and its console is reachable. Prefer the existing server-registry
record; a fixed `localhost:8181` guess or an existing registry file alone is insufficient.
The plugin writes a registry record before binding and can leave it after a crash. Probe
read-only, off the UI thread, with a short deadline. Establish matching server identity using
the supported server metadata. Multiple candidates require selection; absent/ambiguous
project association is disclosed, not guessed. Do not browse arbitrary registry URLs silently.

A successful HTTP response from an unrelated service is not a successful match. Distinguish
starting, unavailable, authentication required and identified/reachable. Offer a refresh/retry,
not a green link on stale state. Use the supported browser login route; never put registry
tokens in a URL, screenshot, context response or shareable profile. The offer opens only on
user action, does not restart/configure a server, and disappears or becomes unavailable when
its identity is no longer current. The assistant may offer the same route using existing
runbook discovery; this adds no server-mutating analyser verb. A native analyser affordance
is a journey enhancement, not a change to M67's no-new-surface constraint for its initial slices.

**Acceptance I2:** fake local services/registry fixtures for success, refused connection,
pre-bind record, stale process, unrelated responder, authentication and ambiguous servers.
Switch projects while the probe is blocked: no old offer may appear. Click the offer with a
browser-opener seam and assert the verified destination and no token. Mutate identity and
reachability checks independently; each must fail. A live console check is additional evidence,
not a substitute for these tests. Nothing in this spec authorises controlling the live session.

### I3. Improve the existing collection before making it the guided default

Inspect and extend [fluxtion-vendor-jars](https://github.com/telaminai/fluxtion-vendor-jars),
source head `3a89391` at intake. Its public source is available; its README/catalogue explicitly
say binary publication is still pending. Do not emit a customer POM pointing at an empty
`main/libs` path or treat source availability as successful dependency resolution.

Concrete compatibility work:

- **Shared market-event contract:** the demo currently handles concrete `PriceEvent` with
  symbol/price/volume, whereas `QuoteView` exposes symbol/size. Define units and meaning first:
  traded volume is not automatically order size or position. Prefer an additive shared market
  event interface in `component-api`, implemented by the demo event and any vendor DTO, with
  handlers typed to that interface. Keep the original API or version an intentional break.
  No casts to demo classes in vendor jars and no duplicate same-FQN API classes.
- **A Mongoose-compatible feed mapper:** the existing `CsvFeedAdapter` accepts a callback;
  it is not a `Function` for `valueMapper`. Supply a compatible mapper alongside it, preserving
  callback users. Return the shared market-event shape that the processor actually handles.
  Define malformed/unmatched input and rejection accounting under §B/G1. Do not claim that
  implementing an interface alone makes concrete-class handlers receive new DTOs.
- **One useful first component:** a clearly named per-event volume check or another explicitly
  agreed market-data rule, with caller-set threshold, public wiring, audit state and a named
  breach sink. Keep this rule distinct from the existing quote-size contract and the one-day
  risk calculation. Boundary inputs below/equal/above the threshold get frozen expected values.
- **Notifier:** demonstrate the actual supported exported-service or subscription connection.
  The current notifier's existence does not prove it receives a limit component's sink events;
  provide and test that wiring, or leave it as a separate exercise.
- **Inspectable packaging:** deterministic binaries, POM dependencies and matching source jars,
  immutable versioned catalogue entries, source revision/licence and digests. Include the
  sources in the configured local Maven layout so the analyser's existing resolver can show
  vendor Java; do not promise binary/source equivalence from a matching FQN alone.
- **Keep the authentic wrong component:** risk A deliberately violates the consuming one-day
  contract while risk B meets it. Preserve A's logic, original fixtures and independent oracle.
  Use the pair in a labelled advanced comparison, never silently “fix” A or make it the default.
  Load only one of their shared-FQN alternatives at a time; isolate build/classloader outputs.

Freeze an additive compatibility plan and expected results before editing jars. Existing
callback tests remain useful but must be joined by generated-dispatch and real hosted-feed
integration. Exercise app and vendor inputs on the same supported ordered route; compare
business outputs and audit facts independently. Include unhandled/malformed rows, duplicate
node names, missing transitive dependency, wrong interface wiring and absent source archives.
A mutation dropping the vendor handler call must fail the output assertion; a wrong threshold
must fail the independent table; a broken mapper must fail input accounting. Publication
checks use an empty dependency cache against the actual chosen public binary route.

### I4. Tour sequence and release boundary

1. Open the downloaded project; inspect design, topology and Java **without a log** (§I1).
2. State expected baseline results, run the existing keyless bundle, and offer the identified
   admin console (§I2). Inspect its inputs/processor/capture without changing them implicitly.
3. Add the compatible vendor component through the supported dependency/XML route. Declare
   any one-time shared-interface adaptation explicitly; do not promise “XML only” until the
   shipped base already implements that contract. Run local checks before generation.
4. Re-run the same preserved inputs plus boundary cases; inspect the new vendor node/source,
   sink output and audit values, then save a comparison report. No rerun-for-neater-numbers.
5. Optional advanced exercise: switch risk A/B in separate builds and compare each with the
   specification-derived oracle. A matching jar digest never becomes a correctness verdict.

M67.1 owns collection/packaging; M67.2 owns the downloadable project; M67.3–M67.6 own skill,
verification and the witnessed tour beats. Preserve existing declaration/refusal spotlights
and D-X9's still-open historical-component provenance. Release the compatible jars first,
then pin and verify the customer archive, then publish the tour and current screenshots.
An improved local collection is not a published integrated demo. Actual graph regeneration
may need a provider; no key use or publication is authorised by this proposal alone.

## Acceptance and release discipline

Before each implementation trial, commit its prediction and label fixtures as constructed or
preserved. Every fix needs a green baseline, a named failing assertion when the relevant fix is
disabled, byte-identical restore and green rerun. These are **planned checks**, not tests run by
this specification. Preserve failures rather than rewriting the fixture to obtain a pass.

Use the owning repository's full named gates. Analyser: JDK 21, actual XML counts with skips
separate; real display for pane/recovery behaviours; current spotlight and public-data guards.
Template: unit checks plus actual archive extraction and keyless build/run/verify/stop from
its public customer route. Compiler/starter: whole affected modules and coverage guard, not
an old test-name filter. Match source, binary, contract, profile and template versions.

Release producer fixes before bumping consumer pins. Record branch-fixture acceptance separately
from published-download acceptance. One bounded edited-graph trial may follow the cheap checks
and publication; any paid/keyed route needs its own authorisation. This proposal grants none.
No new LLM battery, no claim of G14 completion, no changes to the running participant workspace.

## Decisions and remaining uncertainties

1. Approve the rejection surface and blank-line policy for the bundle (§B); no fabricated event
   is the invariant regardless of the chosen surface.
2. Approve the rename command/ownership migration contract (§D) before implementation.
3. Approve role-scoped project grants (§E), or keep explicit per-root authorisation and improve
   its guidance. A declaration alone is never permission.
4. Choose the canonical-id compatibility policy for equality-shared nodes (§F).
5. Run-boundary format/Chronicle choice remains **OD-5**, not a new decision here.

Still needed: the session's exact analyser build; pristine archive and acquisition route; the
recovery capture sequence; pre-edit class/XML/record snapshots for rename; immutable receipts
for the failed attempts; and vendor sources availability at the time of failure. These limit
reproduction claims, not the value of the inspected defects or the ability to add constructed
regressions. Obtain only relevant sanitised copies; do not publish the active project wholesale.
