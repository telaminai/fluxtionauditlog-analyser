# Coinbase market-data connector — a reference connector and an optional live source

_Status: **PROPOSAL, 2026-09-20. Not implemented, not reviewed, not owner-approved.** Filed in the holding
pen because the work belongs to other repositories: the connector and its guide to the trading plugin set.
This repo's only interest is the audit log that comes out the far end. Companion:
[starter journey](../../specs/spec-project-starter-journey.md),
[template picker](../../specs/spec-template-from-analyser.md), [upstream asks](../upstream-asks.md)._

_**Two rules are not argued here.** Gap honesty and no-fabricated-values began as local decisions in earlier
drafts and should not have been: they bind every connector. They are now
[D-T8 and D-T9 in the trust-structure spec](../../specs/spec-trust-structure.md), indexed in ONBOARDING's
standing decisions. This document cites them and shows what compliance costs one feed._

## Why build this: it is the connector everyone will copy

An earlier draft said this adds engagement rather than capability. That was wrong, and conceding it gave
away the strongest reason to do the work.

Every customer who adopts this stack has to connect it to something: their venue, their message bus, their
device. Today the worked examples are a FIX feed and a mock. There is no public, non-trivial connector
against a live protocol that someone can read and imitate. The beta work established that people copy the
nearest example whatever the prose says, and that the imitation channel is the least controllable and most
decisive route by which a newcomer learns a shape. A connector that handles a real socket, a real
handshake, real reconnection and real malformed input is therefore a documentation asset first and a market
data source second.

That reframing changes its priority. As engagement it is decoration and can wait indefinitely. As the
canonical connector example it is the thing that makes the next ten integrations cheaper.

### What is actually delivered

The justification moved to documentation, so the scope has to move with it. A connector nobody finds and
nobody can read teaches nobody anything, and shipping only the feed would argue for a slot on grounds this
proposal then fails to deliver against. Five artefacts, and the last three are not optional extras:

1. **The feed**, written for reading rather than for production. See D-2, which is the decision this whole
   section rests on.
2. **A bundled capture**, paced per D-10, which is a by-product rather than the point.
3. **A stub exchange server.** Acceptance 6 induces connection loss, a refused subscribe and a heartbeat
   timeout against a disposable fixture, and this is that fixture. It is listed as a deliverable rather than
   buried in the test line because it has teaching value of its own: it is the worked answer to the guide's
   hardest section, which is how to test a transport you cannot run in CI.
4. **A "how to write a connector" guide** that the feed anchors: the event-source base and its threading
   contract, where the data mapper sits, what D-T8 and D-T9 oblige, how to record a capture, and how to test
   against a stub. The guide is the deliverable; the feed is its worked example. **It cannot be written
   before the base-class question below is answered**, because its central section describes the contract
   that question decides.
5. **Discoverability, with the documentation site as the primary surface.** Someone asking how to connect
   this to their venue has downloaded nothing and is reading the site, so the guide belongs with the other
   connector documentation first. The journey spec's routing is a second path, for a reader who already has
   a project in front of them. An earlier draft had these the wrong way round and would have routed only the
   people who least need it.

## The obvious question, asked and answered

If offline is the tester's default (D-7), the capture is the proof (D-8), and the mock already produces the
same domain events (D-1), then the only thing a live connector adds over a capture recorded once with a
throwaway script is that a user can point it at the exchange themselves. Real market data is available for
a fraction of this cost, with no port, no connector and no standing exposure to a third-party API.

That is true, and a reader will notice it, so it is answered here rather than left implicit. **The capture
is not the reason to build this.** If realism were the goal, a recorded capture wins on every axis and this
proposal should be declined. The reason is the reference implementation: a capture teaches nothing about
connectors at all.

## Relationship to the beta

Not a beta blocker, and no longer named for it. The [beta proposal](../beta-testing/README.md) has since
replaced its named presets with shipping templates and deferred the trading domain template. Nothing in the
beta arc requires a live exchange: a tester who never opens a socket sees the same graph, the same audit log
and the same evidence pack. D-3 removes the last structural thread between the two.

## The correction that shapes this proposal

An earlier draft treated the trading domain model as something the generated project would define. That was
wrong. A complete trading API already exists in `gregv12/fluxtion-server-plugins`, and the work here is one
more market-data feed alongside the ones already written.

Read on 2026-09-20 through the GitHub API, not inferred:

| Piece | What it gives us |
|---|---|
| `serverlibrary-trading-api` | the domain model: market data, orders, executions, balances, and the node-side subscriber and strategy interfaces |
| `MarketDataFeed` | what a feed implements: `feedName()`, `subscribe(feedName, venueName, symbol)`, plus venue and aggregated-feed registration |
| `MarketDataListener` | what a feed publishes: `onMarketData`, `onMultilevelMarketData`, `marketDataVenueConnected`, `marketDataVenueDisconnected` |
| `MarketDataBook` | a flat value object carrying feed, venue, symbol, id, and bid and ask price, quantity and order count |
| `MultilevelMarketDataBook`, `PriceLevel`, `MultilevelBookConfig` | full depth, with a design note beside them |
| `AbstractMarketDataFeed`, `AbstractMarketDataFeedWorker`, `MarketListenerInvocationStrategy` | the base classes and dispatch strategy a new feed extends |
| `QuickFixMarketDataFeed` | a real feed implementation to mirror |
| `QuickFixMarketDataLogReplayFeed` and a `component/replay` package | replay of a captured session already exists in some form |
| `MockMarketDataFeed`, `MockMarketDataFeedWorker`, `MarketDataBookGenerator`, `MarketDataBookConfig` | a simulator already exists, with its own document |
| `serverlibrary-pnl` | positions, mark to market and a PnL calculator |

Three consequences follow, and they are why the feed itself is small.

**The simulator strategy is already realised.** Writing scenarios against domain objects rather than wire
formats is what `MockMarketDataFeed` does today, which is why nothing downstream waits on this connector.

**Top of book charts and depth does not**, which settles the channel question. See D-9.

**D-T8 is already expressible.** The listener declares venue connected and disconnected events, so this feed
satisfies the standing decision by publishing events that exist rather than inventing a channel.

## Prerequisite: the namespace port

The plugins named above are an earlier generation, under `com.fluxtion.server.plugin.trading`, built for the
predecessor of the current server. They are to be mapped across into the telamin namespace alongside the
existing connectors.

That port is a prerequisite. It carries one question this proposal cannot answer: whether
`AbstractMarketDataFeed` and its worker survive the move unchanged, or are re-expressed on the current
event-source base with its own agent thread and data-mapper hook. Whoever does the port decides, and both
this connector and the guide are written against whatever they land on.

### The port may be a quarter of what it looks like

An earlier draft put the whole port at weeks over 113 Java files and stopped there, without asking whether
this connector needs all of them. It does not, and the import graph says so. **Checked on 2026-09-20 through
the GitHub API, by reading the imports rather than assuming:**

- `AbstractMarketDataFeed` imports only `marketdata` types plus the predecessor server's dispatch and
  service interfaces. Nothing from `order`, `quotestream`, `balance` or `booking`.
- `MarketListenerInvocationStrategy` imports `marketdata` and the runtime dispatch base. Same story.
- `AbstractMarketDataFeedWorker` imports one agrona type and nothing else.
- `MarketDataBookSubscriber` imports one class, `MarketDataBook`.
- The only coupling found outside the market-data packages is `MarketDataBookNode`, which reaches into
  `node.TradeServiceListener` and an admin command registry. That is one file to look at, not a web.

Package counts in the API module: `quotestream` 18, `order` 17, `marketdata` 10, `node` 5, `common` 4, with
single files in `booking`, `balance` and elsewhere. A market-data-only port is therefore roughly
**the marketdata package, two or three node classes, and the plugin side's feed base, mock feed and replay
component** — on the order of twenty-five files rather than a hundred and thirteen. The order half, the
quote-stream half and the PnL library appear nowhere in this proposal's deliverables and can stay behind.

**What this check does not settle**, and it is the remaining hour of someone's time: whether a subset can be
*built*. Both are single Maven modules, so porting a subset means either splitting a module or porting it
and compiling part, and the market-data classes still import the predecessor server's dispatch and service
APIs, which is the base-class question in another guise. If the answer is that the module cannot be split,
say so here with the reason, because a negative answer changes nothing and a positive one changes the
schedule by an order of magnitude.

## Cost, as far as it can be stated

The standing risk on this project is surface area outrunning one person's attention, so a proposal arguing
for a slot owes a number. Rough orders, not estimates, and each is stated with what would sharpen it.

| Piece | Rough order | What would sharpen it |
|---|---|---|
| The feed, written for reading | days | whether the ported base class handles the transport loop or the feed does |
| Its tests, fixtures, stub server, capture and smoke test | comparable to the feed, possibly more | how a transport that cannot run in CI is normally tested here |
| The connector guide and its routing | days | whether it slots into existing connector documentation or starts a new shape |
| The stub server | small, and reusable by every later connector | nothing; it is understood work |
| **The port, full** | **weeks, and I cannot narrow it** | the base-class question. 113 Java files across two modules |
| **The port, market-data subset** | **plausibly days** | whether the module can be split at all. Roughly 25 files, import-clean of the order and quote-stream halves |

The honest summary: the connector, its stub and its guide are a small piece of work sitting behind a
prerequisite whose size is not yet known but may be far smaller than first stated. **Two questions decide
everything else, and the subset one should be asked first**, because a negative answer costs an hour and a
positive answer converts this from blocked to schedulable. The base-class question then decides both the
port's shape and whether the guide can be written at all.

## Decisions to record

**D-1 · The connector maps to the existing domain model and defines nothing of its own.** The trading API is
the contract; a venue-specific event type would fragment it and break every consumer that already reads
`MarketDataBook`.

**D-2 · Written for reading, not for production.** These are different artefacts and the difference is not
cosmetic. A production connector earns its keep with machinery that makes it a worse teaching object,
because the shape a reader should copy disappears into it. This one is plain on purpose: obvious control
flow, error paths visible rather than abstracted behind a framework, comments that say why rather than what,
and no optimisation that has not been demonstrated necessary. Its header says so, so nobody mistakes it for
a template for a high-volume venue. If a production-grade variant is wanted later it forks; it does not
accrete here.

**The line, because "plain" and "handles real reconnection" pull against each other**, and under this
document's own imitation argument a naive reconnect loop teaches a naive reconnect loop. Two sets:

- **Present, and written to be seen** — connect, subscribe, heartbeat response, bounded connect deadline,
  reconnect with resubscribe, a simple bounded backoff, gap reporting under D-T8, counted unmappable input,
  and clean shutdown. These are the behaviours a correct connector must have. They are also each small
  enough to read, which is why omitting them would teach the wrong thing rather than a simpler thing.
- **Omitted deliberately** — adaptive or jittered backoff curves, connection pooling, adaptive batching,
  buffer reuse, retry budgets and circuit breakers. Each is a tuning decision that depends on a venue this
  connector does not have, and each hides the control flow underneath it.

The test for the boundary: if leaving it out would make the connector *wrong*, it is in the first set. If
leaving it out would only make it *slower or less tuned*, it is in the second, and the guide names it as
something a production fork would add.

**D-3 · Ships as a plugin plus a documented example, not a catalogue entry.** The reference-implementation
framing answers this: if the value is the example, then a plugin and a guide deliver it, and a catalogue
entry adds template maintenance for no teaching benefit. The entry is deferred rather than refused, and
would be reconsidered only if the trading domain template is revived.

**D-4 · Gap honesty is inherited, not decided here.** The feed complies with
[D-T8](../../specs/spec-trust-structure.md) by naming its discontinuities — connection loss, a subscribe
that is refused or partially filled, and a heartbeat timeout — and publishing each through the listener
events that already exist. It carries the outage window, counts unmappable messages, and never interpolates
or re-emits a stale book. **Caveat, and it is load-bearing:** D-T8's strongest form names *what* was missed,
and whether that is possible depends on the exchange's sequence semantics, which nobody has read. Until then
this feed reports the window and states the missed range as unknown. Promoting it to a known range is a
change that must cite the exchange's documentation.

**D-5 · No fabricated fields, also inherited.** [D-T9](../../specs/spec-trust-structure.md) is the reason
this feed must not reach for `MarketDataBook`'s convenience constructors, which set order count to one.
Those exist for hand-built events. A connector using them publishes a count nobody reported, and a
fabricated number is indistinguishable downstream from a measured one.

**D-6 · Public channels only, and no credential path.** The template must run from an empty directory with
no account. Order entry and authenticated channels are out of scope; the order half of the API is exercised
by the mock venue.

**D-7 · Offline is the default, not a fallback.** The audience this is aimed at works behind a corporate
firewall, and an outbound WebSocket to a crypto exchange is quite likely blocked. A first run that hangs on
a socket forms the impression at minute two. Therefore: the first run replays the bundled capture and opens
no socket. The live feed is a separate, explicitly chosen step. A blocked or failed connection produces a
named one-line diagnostic and the offline instruction immediately, never a timeout. The connect attempt is
bounded by a short deadline, and failing to reach the exchange is a supported outcome rather than an error.

**D-8 · The live feed is the hook; the capture is the proof.** A live feed is the one input that cannot be
checked against an oracle, so it can demonstrate but never evidence. Any evidence pack uses the capture.

**D-9 · Top of book is the default channel; depth is a later variant.** Read in source, and it decides this
rather than taste. `onMarketData` is abstract, so a node that forgets it does not compile;
`onMultilevelMarketData` is a default method returning false, so a node that forgets it silently ignores
every depth update. And `MultilevelMarketDataBook` holds its levels in collections whose `@ToString`
excludes them, so a node logging a depth book produces nothing chartable and nothing readable about the
levels. `MarketDataBook` exposes price and quantity on both sides as flat top-level numbers, which is the
shape the analyser charts. **Note the interaction with D-5:** an earlier draft counted order count among
this decision's reasons, and D-5 may remove it, because nobody has checked which fields the chosen channel
carries. The conclusion stands on price and quantity alone; order count is not part of the justification.

**D-10 · Bounded by volume and spread over span.** A liquid product publishes on the order of a hundred
updates in well under a second. Time is currently the analyser's only chart axis, so a dense capture renders
as a single vertical line and acceptance 7 fails for a reason unrelated to this connector. The shipped
capture is paced: roughly fifty to one hundred records spread over seconds. Removing the pacing requirement
depends on the record-order chart axis, tracked as staged-feedback item 17.

**D-11 · JSON is a real dependency and the answer is the existing one.** The JDK supplies the WebSocket and
no JSON parser, so "no new dependency" was overstated. The plugin set already depends on Jackson through its
JSON serialiser library, so the feed reuses that rather than adding one or hand-rolling a parser. The reason
to avoid a hand-rolled parser is maintenance and correctness on inputs nobody anticipated; acceptance 4
polices behaviour on bad input and a hand-rolled parser could pass it, so that acceptance is not the
argument here.

**D-12 · A named owner, an environment that can reach the exchange, and a named recipient.** Exchange
WebSocket interfaces move, and this one has been migrated and deprecated before. A connector inside public
documentation that breaks is worse than no connector, because the first report comes from a user. D-7 keeps
the blast radius small by making the live path opt-in and non-gating, and that cuts both ways: a break can
sit undetected for months. So the live path carries a scheduled smoke test that connects, takes a handful of
messages, asserts they still map, and fails without gating CI. Two things that decision left unmade, now
made: it **runs wherever egress to the exchange is actually permitted**, which is usually not the same
environment as CI, and the runner is named alongside the owner rather than assumed. Its failure **notifies
the named owner directly**, because an unwatched failing scheduled job is indistinguishable from no job.
If nobody will own it, decline the work rather than publish a liability.

**The guide has the same owner and its own rot detector.** Documentation rots faster than code and does so
silently: the smoke test catches the exchange changing its message format, and nothing catches a renamed base
class leaving the guide describing an API that no longer exists. Given the port is about to reshape exactly
those base classes, that is not hypothetical. The cheapest sufficient control is that **the guide's code
snippets compile against the current tree**, as part of the ordinary build, so a rename breaks the
documentation the same day it breaks the code.

**D-13 · The analyser never connects to the exchange.** It opens the audit log, the graph and the design as
it would for any project. No verb acquires network access to a venue.

**D-14 · The exchange is named plainly.** This is a public API and naming it is ordinary practice. It is
unrelated to the anonymisation rule, which governs real venue, vendor and account identifiers in log data,
fixtures and screenshots. New files must not inherit an SPDX header carrying an employer-domain author
address by copy-paste during the port.

## Acceptance

1. **The default first run opens no socket**, replays the bundled capture to completion and produces an
   audit log. This is the CI gate and the first experience.
2. **A blocked exchange is a one-line named diagnostic within a short bounded deadline**, followed by the
   offline instruction. Verify behind a deny-all egress rule, not only with the endpoint unreachable.
3. **The same graph consumes the capture, the mock and the live feed** with no change to the processor, all
   three on the top-of-book channel per D-9. Two replays of one capture produce identical records under an
   **explicitly stated exclusion list**, published with the capture. Each entry names what is excluded and
   why; the identity hash rendered into a configuration record is on that list, and staged-feedback item 16
   is cited as the reason it is there. Silence about exclusions turns this from evidence into a claim.
4. **Malformed or unexpected messages are counted and reported**, never dropped silently, and the published
   count matches the number injected.
5. **No field is populated that the message did not carry** (D-5, D-T9). The fields the chosen channel
   supplies are recorded, and the test asserts the unsupplied ones are absent rather than defaulted, in the
   live path, the capture and the mock alike.
6. **D-T8 compliance, per named discontinuity.** Induce each case in D-4 against a disposable fixture and
   assert the event appears with its window, that the subscription is reissued, that no computed series
   spans the gap, and that the missed range is reported as unknown rather than guessed. Then replay a
   capture containing a gap and assert the gap survives the round trip.
7. **The analyser charts a price series and a derived measure** from the shipped capture with no edits, and
   the chart is readable rather than a single vertical line. Assert record span as well as record count.
8. **The scheduled smoke test exists, runs where egress is permitted, does not gate CI, and notifies its
   named owner on failure** (D-12). Verify it fails loudly by pointing it at a deliberately wrong message
   shape, and verify the notification arrives.
9. **The guide exists, is reachable, and does the work.** Reachable from the connector documentation site
   first and the journey routing second. Its snippets compile in the build. Then the real test: **a reader
   following the guide alone can write a trivial connector without opening this feed's source** — where
   *trivial connector* means a feed reading newline-delimited JSON from a local socket and publishing one
   flat domain event, and *a reader* means one person who has not worked on this stack, given an hour.
   That clause decides whether the guide or the example is carrying the deliverable. **Reuse the beta
   proposal's second-person instrument** for it rather than inventing a second one: it already has a
   recruitment, consent and scoring shape, and this is the same experiment with a different artefact.
10. **No credential appears anywhere** in the project, its scripts, its configuration or its documentation.

## Open questions for the owner

- Whether captured market data may be redistributed inside a publicly downloadable artefact. Exchange
  market-data terms frequently restrict redistribution, nobody has checked, and this surfaces after the
  thing is public. A synthetic capture from the mock feed is the fallback and costs nothing.
- Who owns the connector and receives the smoke-test alert under D-12. If the answer is nobody, that is a
  decision to decline rather than a gap to note.
- Whether the capture stores raw exchange messages or mapped domain events. Raw exercises the mapper on
  every replay; mapped is stable across a mapper change. The existing replay feed may already settle this.
- Carried over and owned outside this document: whether existing connectors are audited against D-T8 and
  D-T9 now or on next touch, and whether a gap is a per-feed domain event or one common type.

## What I read, and what I did not

Read through the GitHub API: the file trees of both trading modules with a file count, the top-level module
list, and the source of `MarketDataFeed`, `MarketDataListener`, `MarketDataBook` and
`MultilevelMarketDataBook`. Read locally: the current connector set and event-source base including the
data-mapper hook, the file source's tailing and once-strategies, and the JSON serialiser library's Jackson
dependency.

Not read, and worth reading before implementation: `AbstractMarketDataFeed` and its worker, the
`component/replay` package, and the four documents shipped beside the trading plugin. Two items are load
bearing rather than merely unread, because decisions depend on them:

- **the exchange's specification for sequencing and gap detection** on the public channels. D-4's
  unknown-range position stands until someone reads it.
- **which fields the chosen channel actually carries.** D-5 and D-9 both touch this, and acceptance 5
  cannot be written without it.

Not reproduced by me: the compressed-span chart rendering and the differing identity hash in acceptance 3.
Both are adopted from the session participant's measurements in the 2026-09-19/20 cold-start run, which the
[beta proposal](../beta-testing/README.md) records as a single biased source. Nothing in this document has
been run, built, or fetched from an exchange.
