#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <chrono>
#include <algorithm>
#include <vector>
#include <new>
#include "QuotingCoreProcessor.h"

// ---------------------------------------------------------------------------------------------
// The C++ arm of the quoting core. Same graph, same arithmetic, same workload as the Java arm.
//
// WHERE THE STATE LIVES. A hand-written Java node IS your class - fields and callbacks together. The
// C++ emitter generates a STUB carrying parent pointers and (when audited) an auditLog, and nothing
// else, because it cannot know your fields. So per-symbol state sits at file scope here and in node
// fields there. Both are contiguous and neither allocates on the measured path, but they are not the
// same memory layout, and on a benchmark whose whole point includes a working-set sweep that
// difference is worth stating rather than burying.
//
// SYMBOLS is a compile-time constant for the same reason it is a build-time constant in the Java
// graph: the processor is generated for a fixed symbol count, so the sweep rebuilds rather than
// re-runs.
// ---------------------------------------------------------------------------------------------
#ifndef SYMBOLS
#define SYMBOLS 64
#endif
#ifndef RING_BITS
#define RING_BITS 16
#endif

namespace app::gen {

constexpr int kSymbols = SYMBOLS;
constexpr int kRing = 1 << RING_BITS;
constexpr int kRingMask = kRing - 1;

// Strategy constants - identical to GenQuotingCore.
constexpr int SUB_TICK_SHIFT = 4;
constexpr int BASE_HALF_SPREAD = 4 << SUB_TICK_SHIFT;
constexpr int VOL_SHIFT = 1;
constexpr int IMBALANCE_SHIFT = 5;
constexpr int INVENTORY_SHIFT = 2;
constexpr int MOMENTUM_SHIFT = 2;
constexpr int BASE_QTY = 10;
constexpr int MAX_QUOTE_QTY = 25;
constexpr int POSITION_LIMIT = 2000;
constexpr int QTY_THRESHOLD = 3;
constexpr int STALE_NANOS = 5000;

constexpr int ACK_NEW = 0, ACK_REPLACE = 1, ACK_CANCEL = 2, PARTIAL_FILL = 3, REJECT = 4;
constexpr int NONE = 0, NEW = 1, REPLACE = 2, CANCEL = 3;

// ---- node state, one block per node -------------------------------------------------------
struct CycleData { int32_t symbol = 0; int64_t now = 0; } cycleData;

struct MarketData {
    int32_t bidPx[kSymbols]{}, askPx[kSymbols]{}, bidQty[kSymbols]{}, askQty[kSymbols]{};
    int32_t mid[kSymbols]{}, spread[kSymbols]{}, imbalance[kSymbols]{};
    int64_t lastMarketTime[kSymbols]{};
    bool valid[kSymbols]{};
} marketData;

struct SignalData {
    int32_t vol[kSymbols]{}, momentum[kSymbols]{}, lastMid[kSymbols]{};
    bool seeded[kSymbols]{};
} signalData;

struct InventoryData { int32_t position[kSymbols]{}; } inventoryData;

struct WorkingData {
    int32_t livePx[kSymbols * 2]{}, liveQty[kSymbols * 2]{}, remainingQty[kSymbols * 2]{};
    bool live[kSymbols * 2]{}, pending[kSymbols * 2]{};
    int64_t generation[kSymbols * 2]{};
} workingData;

struct FreshnessData { int32_t sweepCursor = 0; int64_t now = 0; } freshnessData;
struct FairData { int32_t fair[kSymbols]{}; } fairData;

struct QuoteData {
    int32_t desiredBidPx[kSymbols]{}, desiredAskPx[kSymbols]{};
    int32_t desiredBidQty[kSymbols]{}, desiredAskQty[kSymbols]{};
} quoteData;

struct RiskData {
    bool bidAllowed[kSymbols]{}, askAllowed[kSymbols]{};
    int64_t staleSuppressed = 0, riskSuppressed = 0;
} riskData;

struct DiffData {
    int32_t action[kSymbols * 2]{};
    int64_t none = 0, neu = 0, replace = 0, cancel = 0;
    int64_t noneNotAllowed = 0, nonePending = 0, noneUnchanged = 0;
} diffData;

struct IntentData {
    int32_t intentSymbol[kRing]{}, intentSide[kRing]{}, intentAction[kRing]{};
    int32_t intentPx[kRing]{}, intentQty[kRing]{};
    int64_t intentGeneration[kRing]{};
    int32_t cursor = 0;
    int64_t emitted = 0;
} intentData;

// ---- lifecycle. Java's @Initialise allocates the per-symbol arrays; here they are file-scope and
// zero-initialised at load, so init() only has to reset them so a rebuilt processor starts clean.
// The DECLARATIONS come from the emitter - it emits `node.init()` call sites for @Initialise, and
// until 2026-09-10 did not declare them on the stub, so any graph using @Initialise on a hand-written
// node produced a processor that would not compile.
template <typename P0> void MarketState<P0>::init() { marketData = MarketData{}; }
template <typename P0, typename P1> void SignalState<P0, P1>::init() { signalData = SignalData{}; }
template <typename P0> void InventoryState<P0>::init() { inventoryData = InventoryData{}; }
template <typename P0> void WorkingOrders<P0>::init() { workingData = WorkingData{}; }
template <typename P0, typename P1, typename P2, typename P3>
void FairValue<P0, P1, P2, P3>::init() { fairData = FairData{}; }
template <typename P0, typename P1, typename P2, typename P3, typename P4>
void QuoteBuilder<P0, P1, P2, P3, P4>::init() { quoteData = QuoteData{}; }
template <typename P0, typename P1, typename P2, typename P3, typename P4>
void RiskLimits<P0, P1, P2, P3, P4>::init() { riskData = RiskData{}; }
template <typename P0, typename P1, typename P2, typename P3>
void OrderDiff<P0, P1, P2, P3>::init() { diffData = DiffData{}; }
template <typename P0, typename P1, typename P2, typename P3>
void IntentPublisher<P0, P1, P2, P3>::init() { intentData = IntentData{}; }

// ---- the callbacks, arithmetic identical to the Java arm ------------------------------------
template <typename P0>
void MarketState<P0>::onTick(MarketTick* t) {
    const int32_t s = t->symbol;
    cycleData.symbol = s;
    cycleData.now = t->timestamp;
    marketData.bidPx[s] = t->bidPx; marketData.askPx[s] = t->askPx;
    marketData.bidQty[s] = t->bidQty; marketData.askQty[s] = t->askQty;
    const bool ok = t->bidPx > 0 && t->askPx > t->bidPx && t->bidQty > 0 && t->askQty > 0;
    marketData.valid[s] = ok;
    if (!ok) { return; }
    marketData.mid[s] = ((t->bidPx + t->askPx) << SUB_TICK_SHIFT) >> 1;
    marketData.spread[s] = (t->askPx - t->bidPx) << SUB_TICK_SHIFT;
    const int32_t total = t->bidQty + t->askQty;
    marketData.imbalance[s] = total == 0 ? 0 : ((t->bidQty - t->askQty) * 64) / total;
    marketData.lastMarketTime[s] = t->timestamp;
}

template <typename P0, typename P1>
void SignalState<P0, P1>::onMarket() {
    const int32_t s = cycleData.symbol;
    if (!marketData.valid[s]) { return; }
    const int32_t m = marketData.mid[s];
    if (!signalData.seeded[s]) { signalData.seeded[s] = true; signalData.lastMid[s] = m; return; }
    const int32_t delta = m - signalData.lastMid[s];
    const int32_t absMove = delta < 0 ? -delta : delta;
    signalData.vol[s] += (absMove - signalData.vol[s]) >> 3;
    signalData.momentum[s] += (delta - signalData.momentum[s]) >> 2;
    signalData.lastMid[s] = m;
}

template <typename P0>
void InventoryState<P0>::onFill(Fill* f) {
    const int32_t s = f->symbol;
    cycleData.symbol = s;
    inventoryData.position[s] += f->side > 0 ? f->qty : -f->qty;
#ifdef HAS_AUDIT
    auditLog.info("sym", s);
    auditLog.info("pos", inventoryData.position[s]);
#endif
}

template <typename P0>
void WorkingOrders<P0>::onOrderUpdate(OrderUpdate* u) {
    cycleData.symbol = u->symbol;
    const int32_t i = (u->symbol << 1) + (u->side > 0 ? 0 : 1);
    switch (u->type) {
        case ACK_NEW:
        case ACK_REPLACE:
            workingData.live[i] = true; workingData.pending[i] = false;
            workingData.livePx[i] = u->px; workingData.liveQty[i] = u->qty;
            workingData.remainingQty[i] = u->qty;
            workingData.generation[i]++;
            break;
        case ACK_CANCEL:
            workingData.live[i] = false; workingData.pending[i] = false;
            workingData.livePx[i] = 0; workingData.liveQty[i] = 0; workingData.remainingQty[i] = 0;
            break;
        case PARTIAL_FILL:
            // A fill proves the order is at the venue, so it clears pending as surely as an ack.
            workingData.live[i] = true;
            workingData.pending[i] = false;
            workingData.remainingQty[i] -= u->qty;
            if (workingData.remainingQty[i] <= 0) {
                workingData.live[i] = false; workingData.remainingQty[i] = 0;
            }
            break;
        case REJECT:
            workingData.pending[i] = false; workingData.live[i] = false;
            break;
        default: break;
    }
}

template <typename P0>
void FreshnessState<P0>::onTimer(TimerTick* t) {
    freshnessData.now = t->now;
    cycleData.now = t->now;
    freshnessData.sweepCursor =
            freshnessData.sweepCursor + 1 == kSymbols ? 0 : freshnessData.sweepCursor + 1;
    cycleData.symbol = freshnessData.sweepCursor;
}

template <typename P0, typename P1, typename P2, typename P3>
void FairValue<P0, P1, P2, P3>::onInputs() {
    const int32_t s = cycleData.symbol;
    if (!marketData.valid[s]) { return; }
    const int32_t microPriceAdj = (marketData.imbalance[s] * marketData.spread[s]) >> 7;
    const int32_t momentumAdj = signalData.momentum[s] >> MOMENTUM_SHIFT;
    fairData.fair[s] = marketData.mid[s] + microPriceAdj + momentumAdj;
}

static inline int32_t clampQty(int32_t q) {
    return q < 1 ? 1 : (q > MAX_QUOTE_QTY ? MAX_QUOTE_QTY : q);
}

template <typename P0, typename P1, typename P2, typename P3, typename P4>
void QuoteBuilder<P0, P1, P2, P3, P4>::onFairValue() {
    const int32_t s = cycleData.symbol;
    if (!marketData.valid[s]) { return; }
    const int32_t position = inventoryData.position[s];
    const int32_t imb = marketData.imbalance[s];
    const int32_t pressure = imb < 0 ? -imb : imb;
    const int32_t halfSpread = BASE_HALF_SPREAD
            + (signalData.vol[s] >> VOL_SHIFT)
            + ((pressure << SUB_TICK_SHIFT) >> IMBALANCE_SHIFT);
    const int32_t centre = fairData.fair[s] - (position >> INVENTORY_SHIFT);
    quoteData.desiredBidPx[s] = (centre - halfSpread) >> SUB_TICK_SHIFT;
    quoteData.desiredAskPx[s] = ((centre + halfSpread) + ((1 << SUB_TICK_SHIFT) - 1)) >> SUB_TICK_SHIFT;
    const int32_t longPenalty = position > 0 ? position >> INVENTORY_SHIFT : 0;
    const int32_t shortPenalty = position < 0 ? (-position) >> INVENTORY_SHIFT : 0;
    quoteData.desiredBidQty[s] = clampQty(BASE_QTY - longPenalty);
    quoteData.desiredAskQty[s] = clampQty(BASE_QTY - shortPenalty);
}

template <typename P0, typename P1, typename P2, typename P3, typename P4>
void RiskLimits<P0, P1, P2, P3, P4>::onQuote() {
    const int32_t s = cycleData.symbol;
    const bool marketValid = marketData.valid[s];
    const bool stale = freshnessData.now - marketData.lastMarketTime[s] > STALE_NANOS;
    const int32_t position = inventoryData.position[s];
    const int32_t projectedLong = position + quoteData.desiredBidQty[s];
    const int32_t projectedShort = position - quoteData.desiredAskQty[s];
    const bool bidOk = marketValid && !stale && projectedLong <= POSITION_LIMIT;
    const bool askOk = marketValid && !stale && projectedShort >= -POSITION_LIMIT;
    riskData.bidAllowed[s] = bidOk;
    riskData.askAllowed[s] = askOk;
    if (stale) { riskData.staleSuppressed++; }
    else if (!bidOk || !askOk) { riskData.riskSuppressed++; }
}

static inline int32_t decide(int32_t symbol, int32_t side, bool allowed,
                             int32_t desiredPx, int32_t desiredQty) {
    const int32_t i = (symbol << 1) + side;
    int32_t a;
    if (!allowed) {
        if (workingData.live[i] && !workingData.pending[i]) { a = CANCEL; }
        else { a = NONE; diffData.noneNotAllowed++; }
    } else if (workingData.pending[i]) {
        a = NONE; diffData.nonePending++;
    } else if (!workingData.live[i]) {
        a = NEW;
    } else {
        const int32_t dq = desiredQty - workingData.liveQty[i];
        const int32_t adq = dq < 0 ? -dq : dq;
        if (desiredPx != workingData.livePx[i] || adq >= QTY_THRESHOLD) { a = REPLACE; }
        else { a = NONE; diffData.noneUnchanged++; }
    }
    switch (a) {
        case NEW: diffData.neu++; workingData.pending[i] = true; break;
        case REPLACE: diffData.replace++; workingData.pending[i] = true; break;
        case CANCEL: diffData.cancel++; workingData.pending[i] = true; break;
        default: diffData.none++; break;
    }
    return a;
}

template <typename P0, typename P1, typename P2, typename P3>
void OrderDiff<P0, P1, P2, P3>::onInputs() {
    const int32_t s = cycleData.symbol;
    diffData.action[s << 1] = decide(s, 0, riskData.bidAllowed[s],
            quoteData.desiredBidPx[s], quoteData.desiredBidQty[s]);
    diffData.action[(s << 1) + 1] = decide(s, 1, riskData.askAllowed[s],
            quoteData.desiredAskPx[s], quoteData.desiredAskQty[s]);
}

template <typename P0, typename P1, typename P2, typename P3>
void IntentPublisher<P0, P1, P2, P3>::onDiff() {
    const int32_t s = cycleData.symbol;
    // Inlined twice rather than routed through a helper: the audit call needs `auditLog`, which is a
    // member of this stub, so the publish has to happen inside the member function.
    for (int32_t side = 0; side < 2; side++) {
        const int32_t action = diffData.action[(s << 1) + side];
        if (action == NONE) { continue; }
        const int32_t px = side == 0 ? quoteData.desiredBidPx[s] : quoteData.desiredAskPx[s];
        const int32_t qty = side == 0 ? quoteData.desiredBidQty[s] : quoteData.desiredAskQty[s];
        const int32_t i = intentData.cursor++ & kRingMask;
        const int32_t slot = (s << 1) + side;
        intentData.intentSymbol[i] = s;
        intentData.intentSide[i] = side;
        intentData.intentAction[i] = action;
        intentData.intentPx[i] = action == CANCEL ? workingData.livePx[slot] : px;
        intentData.intentQty[i] = action == CANCEL ? 0 : qty;
        intentData.intentGeneration[i] = workingData.generation[slot];
        intentData.emitted++;
#ifdef HAS_AUDIT
        auditLog.info("sym", s);
        auditLog.info("act", action);
        auditLog.info("px", intentData.intentPx[i]);
#endif
    }
}
}  // namespace app::gen

// ---------------------------------------------------------------------------------------------
// Workload — the SAME deterministic stream as the Java harness.
//
// xorshift64 with identical operations: Java's `>>>` on a signed long and C++'s `>>` on uint64_t are
// the same logical shift, so both languages replay the same events and do the same work. Independent
// sub-streams for type, symbol, price, size and anomalies.
// ---------------------------------------------------------------------------------------------
namespace {

struct Rng {
    uint64_t s;
    explicit Rng(uint64_t seed) : s(seed == 0 ? 0x9E3779B97F4A7C15ULL : seed) {}
    uint64_t next() {
        s ^= s << 13;
        s ^= s >> 7;
        s ^= s << 17;
        return s;
    }
    int32_t nextInt(int32_t bound) { return (int32_t) ((next() >> 33) % (uint64_t) bound); }
};

constexpr uint8_t EV_MARKET = 0, EV_FILL = 1, EV_ORDER = 2, EV_TIMER = 3;

std::vector<uint8_t> evType;
std::vector<int32_t> evSymbol, evA, evB, evC, evD;
std::vector<int64_t> evTime;
bool g_skew = true;

void generate(int32_t count, int32_t symbols, bool active, uint64_t seed) {
    evType.assign((size_t) count, 0);
    evSymbol.assign((size_t) count, 0); evA.assign((size_t) count, 0);
    evB.assign((size_t) count, 0); evC.assign((size_t) count, 0); evD.assign((size_t) count, 0);
    evTime.assign((size_t) count, 0);

    Rng rType(seed), rSym(seed ^ 0x1111), rPx(seed ^ 0x2222),
        rQty(seed ^ 0x3333), rOdd(seed ^ 0x4444), rSide(seed ^ 0x5555);

    std::vector<int32_t> mid((size_t) symbols), spread((size_t) symbols),
                         lastBq((size_t) symbols, 100), lastAq((size_t) symbols, 100);
    for (int32_t i = 0; i < symbols; i++) {
        mid[(size_t) i] = 10000 + rPx.nextInt(200);
        spread[(size_t) i] = 1 + rPx.nextInt(4);
    }

    int64_t now = 1000000;
    for (int32_t i = 0; i < count; i++) {
        const int32_t roll = rType.nextInt(100);
        const uint8_t type = roll < 70 ? EV_MARKET : roll < 92 ? EV_ORDER : roll < 98 ? EV_FILL : EV_TIMER;
        int32_t sym;
        if (g_skew) {
            const int32_t u = rSym.nextInt(1 << 14);
            sym = (int32_t) (((uint64_t) ((int64_t) u * u) >> 28) % (uint64_t) symbols);
        } else {
            sym = rSym.nextInt(symbols);
        }
        now += 50 + rOdd.nextInt(200);
        evType[(size_t) i] = type;
        evSymbol[(size_t) i] = sym;
        evTime[(size_t) i] = now;

        if (type == EV_MARKET) {
            const int32_t r = rPx.nextInt(active ? 5 : 40);
            const int32_t step = r == 0 ? -1 : r == 1 ? 1 : 0;
            mid[(size_t) sym] += step;
            if (rOdd.nextInt(512) == 0) { mid[(size_t) sym] += rPx.nextInt(9) - 4; }
            if (rOdd.nextInt(256) == 0) { spread[(size_t) sym] = 1 + rPx.nextInt(4); }
            const int32_t half = spread[(size_t) sym] >> 1;
            int32_t bid = mid[(size_t) sym] - half - 1;
            const int32_t ask = mid[(size_t) sym] + half + 1;
            if (active || rOdd.nextInt(8) == 0) {
                lastBq[(size_t) sym] = 1 + rQty.nextInt(200);
                lastAq[(size_t) sym] = 1 + rQty.nextInt(200);
            }
            int32_t bq = lastBq[(size_t) sym];
            int32_t aq = lastAq[(size_t) sym];
            if (rOdd.nextInt(128) == 0) { bq = 1 + rQty.nextInt(20); aq = 200 + rQty.nextInt(200); }
            if (rOdd.nextInt(1024) == 0) { bid = 0; }
            evA[(size_t) i] = bid; evB[(size_t) i] = ask;
            evC[(size_t) i] = bq; evD[(size_t) i] = aq;
        } else if (type == EV_FILL) {
            evA[(size_t) i] = (rSide.next() & 1) == 0 ? 1 : -1;
            evB[(size_t) i] = 1 + rQty.nextInt(10);
            evC[(size_t) i] = mid[(size_t) sym];
        } else if (type == EV_ORDER) {
            evD[(size_t) i] = rOdd.nextInt(16);
            evA[(size_t) i] = (rSide.next() & 1) == 0 ? 1 : -1;
            const int32_t t = rOdd.nextInt(100);
            evB[(size_t) i] = t < 45 ? app::gen::ACK_NEW
                    : t < 80 ? app::gen::ACK_REPLACE
                    : t < 90 ? app::gen::ACK_CANCEL
                    : t < 97 ? app::gen::PARTIAL_FILL
                    : app::gen::REJECT;
            evC[(size_t) i] = 1 + rQty.nextInt(10);
        }
    }
}

app::gen::MarketTick TICK;
app::gen::Fill FILL;
app::gen::OrderUpdate ORDER;
app::gen::TimerTick TIMER;
int32_t ackCursor = 0;

/** Returns the intent cursor so the caller can create a read-after-write chain in serial mode. */
inline int32_t dispatch(app::gen::QuotingCoreProcessor& p, int32_t i, int32_t dep) {
    using namespace app::gen;
    switch (evType[(size_t) i]) {
        case EV_MARKET:
            TICK.symbol = evSymbol[(size_t) i];
            TICK.bidPx = evA[(size_t) i] + (dep & 1);
            TICK.askPx = evB[(size_t) i];
            TICK.bidQty = evC[(size_t) i];
            TICK.askQty = evD[(size_t) i];
            TICK.timestamp = evTime[(size_t) i];
            p.handle_MarketTick(&TICK);
            break;
        case EV_FILL:
            FILL.symbol = evSymbol[(size_t) i]; FILL.side = evA[(size_t) i];
            FILL.qty = evB[(size_t) i]; FILL.px = evC[(size_t) i];
            p.handle_Fill(&FILL);
            break;
        case EV_ORDER: {
            // A FIFO ack cursor trailing the intent cursor: every order sent is eventually acked,
            // roughly in order, as a venue does.
            if (ackCursor < intentData.cursor) {
                const int32_t slot = ackCursor++ & kRingMask;
                ORDER.symbol = intentData.intentSymbol[slot];
                ORDER.side = intentData.intentSide[slot] == 0 ? 1 : -1;
                ORDER.type = evB[(size_t) i];
                ORDER.qty = intentData.intentQty[slot] == 0 ? 1 : intentData.intentQty[slot];
                ORDER.px = intentData.intentPx[slot];
                p.handle_OrderUpdate(&ORDER);
            }
            break;
        }
        default:
            TIMER.now = evTime[(size_t) i];
            p.handle_TimerTick(&TIMER);
            break;
    }
    return intentData.cursor;
}
}  // namespace

int main(int argc, char** argv) {
    using namespace app::gen;
    const int64_t iters = argc > 1 ? atoll(argv[1]) : 3000000;
    const int64_t warm = argc > 2 ? atoll(argv[2]) : 1500000;
    const int batches = argc > 3 ? atoi(argv[3]) : 3;
    const int32_t bufferBits = 20;
    const int32_t bufferSize = 1 << bufferBits;
    const int32_t bufferMask = bufferSize - 1;
    const bool active = getenv("PROFILE") == nullptr || std::strcmp(getenv("PROFILE"), "selective") != 0;
    g_skew = getenv("SKEW") == nullptr || std::strcmp(getenv("SKEW"), "false") != 0;

    generate(bufferSize, kSymbols, active, 0xC0FFEEULL);

    QuotingCoreProcessor p;
#ifdef HAS_AUDIT
    struct CountingSink : fluxtion::LogRecordListener {
        long long records = 0;
        void processLogRecord(const fluxtion::BinaryLogRecord&) override { ++records; }
    } sink;
    p.setLogSink(&sink);
#endif
    p.init();

    for (int64_t i = 0; i < warm; i++) { dispatch(p, (int32_t) (i & bufferMask), 0); }
#ifdef HAS_AUDIT
    if (sink.records == 0) {
        std::printf("REFUSED %s %s: audit build but the sink saw no records\n", HARNESS_TAG, RUNTIME_TAG);
        return 3;
    }
#endif

    if (getenv("MIX") != nullptr) {
        int64_t m = 0, f = 0, o = 0, t = 0;
        for (int32_t i = 0; i < bufferSize; i++) {
            switch (evType[(size_t) i]) {
                case EV_MARKET: m++; break; case EV_FILL: f++; break;
                case EV_ORDER: o++; break; default: t++; break;
            }
        }
        const int64_t decisions = diffData.none + diffData.neu + diffData.replace + diffData.cancel;
        const double pct = 100.0 / bufferSize;
        const double dpct = decisions == 0 ? 0 : 100.0 / (double) decisions;
        std::printf("MIX cpp profile=%s symbols=%d skew=%s\n",
                    active ? "active" : "selective", kSymbols, g_skew ? "true" : "false");
        std::printf("  events   market=%.1f%%  order=%.1f%%  fill=%.1f%%  timer=%.1f%%\n",
                    (double) m * pct, (double) o * pct, (double) f * pct, (double) t * pct);
        std::printf("  decisions NONE=%.1f%% (%lld)  NEW=%.1f%% (%lld)  REPLACE=%.1f%% (%lld)  "
                    "CANCEL=%.2f%% (%lld)  n=%lld\n",
                    (double) diffData.none * dpct, (long long) diffData.none,
                    (double) diffData.neu * dpct, (long long) diffData.neu,
                    (double) diffData.replace * dpct, (long long) diffData.replace,
                    (double) diffData.cancel * dpct, (long long) diffData.cancel,
                    (long long) decisions);
        std::printf("  suppressed  risk=%lld  stale=%lld   intents emitted=%lld\n",
                    (long long) riskData.riskSuppressed, (long long) riskData.staleSuppressed,
                    (long long) intentData.emitted);
        const double n = diffData.none == 0 ? 1.0 : (double) diffData.none;
        std::printf("  NONE breakdown  notAllowed=%.1f%%  pending=%.1f%%  unchanged=%.1f%%\n",
                    (double) diffData.noneNotAllowed * 100.0 / n,
                    (double) diffData.nonePending * 100.0 / n,
                    (double) diffData.noneUnchanged * 100.0 / n);
        return 0;
    }

    const char* mode = getenv("DEPENDENT");
    if (mode != nullptr) {
        const bool control = std::strcmp(mode, "control") == 0;
        int32_t dep = 0;
        for (int64_t i = 0; i < warm; i++) {
            dep = dispatch(p, (int32_t) (i & bufferMask), control ? 0 : dep);
        }
        double best = 1e18;
        for (int b = 0; b < batches; b++) {
            const auto start = std::chrono::steady_clock::now();
            if (control) {
                int32_t sink2 = 0;
                for (int64_t i = 0; i < iters; i++) { sink2 += dispatch(p, (int32_t) (i & bufferMask), 0); }
                dep = sink2;
            } else {
                for (int64_t i = 0; i < iters; i++) { dep = dispatch(p, (int32_t) (i & bufferMask), dep); }
            }
            const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::steady_clock::now() - start).count();
            best = std::min(best, (double) ns / (double) iters);
        }
        std::printf("RESULT harness=%s %s cpp-quotingcore-%s audit=%s symbols=%d profile=%s skew=%s "
                    "%.4f ns intents=%lld sink=%d\n",
                    HARNESS_TAG, RUNTIME_TAG, control ? "readcontrol" : "serial",
#ifdef HAS_AUDIT
                    "true",
#else
                    "false",
#endif
                    kSymbols, active ? "active" : "selective", g_skew ? "true" : "false",
                    best, (long long) intentData.emitted, dep);
        return 0;
    }

    double best = 1e18;
    for (int b = 0; b < batches; b++) {
        const auto start = std::chrono::steady_clock::now();
        for (int64_t i = 0; i < iters; i++) { dispatch(p, (int32_t) (i & bufferMask), 0); }
        const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now() - start).count();
        best = std::min(best, (double) ns / (double) iters);
    }
    // Validated OUTSIDE the timed region so the work cannot be optimised away for free.
    int64_t checksum = 0;
    for (int32_t i = 0; i < kRing; i++) {
        checksum = checksum * 31 + intentData.intentSymbol[i] + intentData.intentAction[i] * 7LL
                + intentData.intentPx[i] * 13LL + intentData.intentQty[i] * 17LL;
    }
    std::printf("RESULT harness=%s %s cpp-quotingcore audit=%s symbols=%d profile=%s skew=%s %.4f ns "
                "intents=%lld checksum=%lld",
                HARNESS_TAG, RUNTIME_TAG,
#ifdef HAS_AUDIT
                "true",
#else
                "false",
#endif
                kSymbols, active ? "active" : "selective", g_skew ? "true" : "false",
                best, (long long) intentData.emitted, (long long) checksum);
#ifdef HAS_AUDIT
    std::printf(" records=%lld", sink.records);
#endif
    std::printf("\n   decisions NONE=%lld NEW=%lld REPLACE=%lld CANCEL=%lld\n",
                (long long) diffData.none, (long long) diffData.neu,
                (long long) diffData.replace, (long long) diffData.cancel);
    return 0;
}
