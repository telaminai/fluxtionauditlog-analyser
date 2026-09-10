#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <chrono>
#include <algorithm>
#include <vector>
#include <new>
#include "VenueCoreProcessor.h"

// The C++ arm of the venue-lifecycle quoting core. Same graph, same arithmetic, same deterministic
// workload and the same time-driven venue as the Java harness. Node state lives at file scope because
// the emitter generates stubs carrying parent pointers and an audit logger and nothing else.
#ifndef SYMBOLS
#define SYMBOLS 64
#endif

namespace app::gen {

constexpr int kSymbols = SYMBOLS;
constexpr int kRingBits = 16;
constexpr int kRing = 1 << kRingBits;
constexpr int kRingMask = kRing - 1;

constexpr int SUB_TICK_SHIFT = 4;
constexpr int BASE_HALF_SPREAD = 4 << SUB_TICK_SHIFT;
constexpr int VOL_SHIFT = 1, IMBALANCE_SHIFT = 5, INVENTORY_SHIFT = 2, MOMENTUM_SHIFT = 2;
constexpr int BASE_QTY = 10, MAX_QUOTE_QTY = 25, POSITION_LIMIT = 2000, QTY_THRESHOLD = 3;
constexpr int STALE_NANOS = 20000;
constexpr int BID = 0, ASK = 1;
constexpr int ACK_NEW = 0, ACK_REPLACE = 1, ACK_CANCEL = 2, REJECT = 3;
constexpr int NONE = 0, NEW = 1, REPLACE = 2, CANCEL = 3;

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
    int64_t generation[kSymbols * 2]{}, pendingGeneration[kSymbols * 2]{};
    int32_t lastFillSide = 0, lastFillQty = 0, lastFillSymbol = 0;
    int64_t rejectedExecutions = 0;
} workingData;
struct FreshnessData { int64_t now = 0, deadlinesFired = 0; } freshnessData;
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
    int64_t emitted = 0, nextGeneration = 1;
} intentData;

// ---- lifecycle ------------------------------------------------------------------------------
template <typename P0> void MarketState<P0>::init() { marketData = MarketData{}; }
template <typename P0, typename P1> void SignalState<P0, P1>::init() { signalData = SignalData{}; }
template <typename P0, typename P1> void InventoryState<P0, P1>::init() { inventoryData = InventoryData{}; }
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
void IntentPublisher<P0, P1, P2, P3>::init() { intentData = IntentData{}; intentData.nextGeneration = 1; }

// ---- callbacks ------------------------------------------------------------------------------
template <typename P0>
void MarketState<P0>::onTick(MarketTick* t) {
    const int32_t s = t->symbol;
    cycleData.symbol = s; cycleData.now = t->timestamp;
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
void WorkingOrders<P0>::onOrderUpdate(OrderUpdate* u) {
    cycleData.symbol = u->symbol; cycleData.now = u->timestamp;
    const int32_t i = (u->symbol << 1) + u->side;
    if (u->generation != workingData.pendingGeneration[i]) { return; }
    switch (u->type) {
        case ACK_NEW:
        case ACK_REPLACE:
            workingData.live[i] = true; workingData.pending[i] = false;
            workingData.livePx[i] = u->px; workingData.liveQty[i] = u->qty;
            workingData.remainingQty[i] = u->qty;
            workingData.generation[i] = u->generation;
            break;
        case ACK_CANCEL:
        case REJECT:
            workingData.live[i] = false; workingData.pending[i] = false;
            workingData.livePx[i] = 0; workingData.liveQty[i] = 0; workingData.remainingQty[i] = 0;
            workingData.generation[i] = u->generation;
            break;
        default: break;
    }
}

template <typename P0>
void WorkingOrders<P0>::onExecution(Execution* e) {
    cycleData.symbol = e->symbol; cycleData.now = e->timestamp;
    const int32_t i = (e->symbol << 1) + e->side;
    workingData.lastFillQty = 0;
    workingData.lastFillSymbol = e->symbol;
    workingData.lastFillSide = e->side;
    if (!workingData.live[i] || workingData.generation[i] != e->generation
            || workingData.remainingQty[i] <= 0) {
        workingData.rejectedExecutions++;
        return;
    }
    const int32_t applied = e->qty < workingData.remainingQty[i] ? e->qty : workingData.remainingQty[i];
    workingData.remainingQty[i] -= applied;
    workingData.lastFillQty = applied;
    if (workingData.remainingQty[i] == 0) { workingData.live[i] = false; }
}

template <typename P0, typename P1>
void InventoryState<P0, P1>::onExecution(Execution* e) {
    const int32_t applied = workingData.lastFillQty;
    if (applied == 0) { return; }
    const int32_t s = e->symbol;
    inventoryData.position[s] += e->side == BID ? applied : -applied;
#ifdef HAS_AUDIT
    auditLog.info("sym", s);
    auditLog.info("pos", inventoryData.position[s]);
    auditLog.info("fill", applied);
#endif
}

template <typename P0>
void FreshnessState<P0>::onTimer(TimerTick* t) {
    freshnessData.now = t->now;
    cycleData.now = t->now;
    cycleData.symbol = t->symbol;
    freshnessData.deadlinesFired++;
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
static inline int32_t floorDiv16(int32_t v) { return v >> SUB_TICK_SHIFT; }
static inline int32_t ceilDiv16(int32_t v) { return -((-v) >> SUB_TICK_SHIFT); }

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
    quoteData.desiredBidPx[s] = floorDiv16(centre - halfSpread);
    quoteData.desiredAskPx[s] = ceilDiv16(centre + halfSpread);
    const int32_t longPenalty = position > 0 ? position >> INVENTORY_SHIFT : 0;
    const int32_t shortPenalty = position < 0 ? (-position) >> INVENTORY_SHIFT : 0;
    quoteData.desiredBidQty[s] = clampQty(BASE_QTY - longPenalty);
    quoteData.desiredAskQty[s] = clampQty(BASE_QTY - shortPenalty);
}

template <typename P0, typename P1, typename P2, typename P3, typename P4>
void RiskLimits<P0, P1, P2, P3, P4>::onQuote() {
    const int32_t s = cycleData.symbol;
    const bool marketValid = marketData.valid[s];
    const bool stale = cycleData.now - marketData.lastMarketTime[s] > STALE_NANOS;
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
        case NEW: diffData.neu++; break;
        case REPLACE: diffData.replace++; break;
        case CANCEL: diffData.cancel++; break;
        default: diffData.none++; break;
    }
    return a;
}

template <typename P0, typename P1, typename P2, typename P3>
void OrderDiff<P0, P1, P2, P3>::onInputs() {
    const int32_t s = cycleData.symbol;
    diffData.action[s << 1] = decide(s, BID, riskData.bidAllowed[s],
            quoteData.desiredBidPx[s], quoteData.desiredBidQty[s]);
    diffData.action[(s << 1) + 1] = decide(s, ASK, riskData.askAllowed[s],
            quoteData.desiredAskPx[s], quoteData.desiredAskQty[s]);
}

template <typename P0, typename P1, typename P2, typename P3>
void IntentPublisher<P0, P1, P2, P3>::onDiff() {
    const int32_t s = cycleData.symbol;
    for (int32_t side = 0; side < 2; side++) {
        const int32_t action = diffData.action[(s << 1) + side];
        if (action == NONE) { continue; }
        const int32_t px = side == BID ? quoteData.desiredBidPx[s] : quoteData.desiredAskPx[s];
        const int32_t qty = side == BID ? quoteData.desiredBidQty[s] : quoteData.desiredAskQty[s];
        const int32_t slot = (s << 1) + side;
        const int64_t gen = intentData.nextGeneration++;
        workingData.pending[slot] = true;
        workingData.pendingGeneration[slot] = gen;
        const int32_t i = intentData.cursor++ & kRingMask;
        intentData.intentSymbol[i] = s;
        intentData.intentSide[i] = side;
        intentData.intentAction[i] = action;
        intentData.intentPx[i] = action == CANCEL ? workingData.livePx[slot] : px;
        intentData.intentQty[i] = action == CANCEL ? 0 : qty;
        intentData.intentGeneration[i] = gen;
        intentData.emitted++;
#ifdef HAS_AUDIT
        auditLog.info("sym", s);
        auditLog.info("side", side);
        auditLog.info("act", action);
        auditLog.info("px", intentData.intentPx[i]);
        auditLog.info("qty", intentData.intentQty[i]);
        auditLog.info("gen", (int64_t) gen);
#endif
    }
}
}  // namespace app::gen

// ---------------------------------------------------------------------------------------------
// The venue: time-driven, on the same data-driven clock the engine sees. Identical structure and
// identical PRNG to the Java harness, so both languages replay the same stream.
// ---------------------------------------------------------------------------------------------
namespace {

struct Rng {
    uint64_t s;
    explicit Rng(uint64_t seed) : s(seed == 0 ? 0x9E3779B97F4A7C15ULL : seed) {}
    uint64_t next() { s ^= s << 13; s ^= s >> 7; s ^= s << 17; return s; }
    int32_t nextInt(int32_t bound) { return (int32_t) ((next() >> 33) % (uint64_t) bound); }
};

struct Regime {
    const char* name; int ackMin, ackSpan, fillMin, fillSpan, fillPercent;
};
const Regime LOW    {"LOW",      800,   700,  3000, 12000, 12};
const Regime NORMAL {"NORMAL",  3000,  5000, 10000, 30000, 12};
const Regime SLOW   {"SLOW",   18000, 22000, 25000, 60000, 12};
Regime regime = NORMAL;

std::vector<int32_t> evSymbol, evBid, evAsk, evBidQty, evAskQty;
std::vector<int64_t> evTime;
bool g_skew = true;
int freshnessDeadlineNs = app::gen::STALE_NANOS / 2;
std::vector<char> deadlinePending;
std::vector<int64_t> lastBookUpdate;
int64_t deadlinesScheduled = 0, deadlinesFiredH = 0, deadlinesRearmed = 0;

constexpr int WHEEL_SLOTS = 4096, WHEEL_GRAN_NS = 64, POOL = 1 << 16;
int32_t slotHead[WHEEL_SLOTS], poolNext[POOL], poolKind[POOL], poolSymbol[POOL],
        poolSide[POOL], poolType[POOL], poolPx[POOL], poolQty[POOL];
int64_t poolDue[POOL], poolGen[POOL];
int32_t freeHead = 0, inFlight = 0, maxInFlight = 0;
int64_t wheelNow = 0;
int64_t acksSent = 0, execsSent = 0, intentsConsumed = 0;
int32_t consumedIntent = 0;
Rng venueRng(0);

app::gen::MarketTick TICK;
app::gen::OrderUpdate ACKEV;
app::gen::Execution EXEC;
app::gen::TimerTick TIMER;
app::gen::VenueCoreProcessor* procp = nullptr;

void wheelInit(int64_t now) {
    for (int i = 0; i < WHEEL_SLOTS; i++) { slotHead[i] = -1; }
    for (int i = 0; i < POOL - 1; i++) { poolNext[i] = i + 1; }
    poolNext[POOL - 1] = -1;
    freeHead = 0; wheelNow = now; inFlight = 0; maxInFlight = 0;
}

void schedule(int64_t due, int kind, int symbol, int side, int type, int px, int qty, int64_t gen) {
    if (freeHead < 0) { std::printf("REFUSED: venue pool exhausted\n"); std::exit(4); }
    if (due - wheelNow >= (int64_t) WHEEL_SLOTS * WHEEL_GRAN_NS) {
        std::printf("REFUSED: scheduled delay %lld exceeds the wheel span\n",
                    (long long) (due - wheelNow));
        std::exit(4);
    }
    const int32_t n = freeHead;
    freeHead = poolNext[n];
    poolDue[n] = due; poolKind[n] = kind; poolSymbol[n] = symbol; poolSide[n] = side;
    poolType[n] = type; poolPx[n] = px; poolQty[n] = qty; poolGen[n] = gen;
    const int32_t slot = (int32_t) ((due / WHEEL_GRAN_NS) & (WHEEL_SLOTS - 1));
    poolNext[n] = slotHead[slot];
    slotHead[slot] = n;
    if (++inFlight > maxInFlight) { maxInFlight = inFlight; }
}

void harvestIntents(int64_t now);

void dispatchVenue(int32_t n) {
    using namespace app::gen;
    if (poolKind[n] == 2) {
        const int32_t sym = poolSymbol[n];
        deadlinePending[(size_t) sym] = 0;
        const int64_t due = lastBookUpdate[(size_t) sym] + freshnessDeadlineNs;
        if (due > poolDue[n]) {
            deadlinePending[(size_t) sym] = 1;
            deadlinesRearmed++;
            schedule(due, 2, sym, 0, 0, 0, 0, 0);
            return;
        }
        TIMER.now = poolDue[n]; TIMER.symbol = sym;
        procp->handle_TimerTick(&TIMER);
        deadlinesFiredH++;
        harvestIntents(poolDue[n]);
        return;
    }
    if (poolKind[n] == 0) {
        ACKEV.symbol = poolSymbol[n]; ACKEV.side = poolSide[n]; ACKEV.type = poolType[n];
        ACKEV.px = poolPx[n]; ACKEV.qty = poolQty[n];
        ACKEV.generation = poolGen[n]; ACKEV.timestamp = poolDue[n];
        procp->handle_OrderUpdate(&ACKEV);
        acksSent++;
        harvestIntents(poolDue[n]);
        if ((poolType[n] == ACK_NEW || poolType[n] == ACK_REPLACE)
                && venueRng.nextInt(100) < regime.fillPercent) {
            const int32_t slot = (poolSymbol[n] << 1) + poolSide[n];
            const int32_t q = poolQty[n] > 0 ? poolQty[n] : 1;
            schedule(poolDue[n] + regime.fillMin + venueRng.nextInt(regime.fillSpan), 1,
                     poolSymbol[n], poolSide[n], 0, poolPx[n],
                     1 + venueRng.nextInt(q), workingData.generation[slot]);
        }
    } else {
        EXEC.symbol = poolSymbol[n]; EXEC.side = poolSide[n]; EXEC.px = poolPx[n];
        EXEC.qty = poolQty[n]; EXEC.generation = poolGen[n]; EXEC.timestamp = poolDue[n];
        procp->handle_Execution(&EXEC);
        execsSent++;
        harvestIntents(poolDue[n]);
    }
}

void drainVenue(int64_t upto) {
    while (wheelNow <= upto) {
        const int32_t slot = (int32_t) ((wheelNow / WHEEL_GRAN_NS) & (WHEEL_SLOTS - 1));
        int32_t n = slotHead[slot], keep = -1;
        slotHead[slot] = -1;
        while (n >= 0) {
            const int32_t next = poolNext[n];
            if (poolDue[n] <= upto) {
                dispatchVenue(n);
                poolNext[n] = freeHead; freeHead = n; inFlight--;
            } else {
                poolNext[n] = keep; keep = n;
            }
            n = next;
        }
        slotHead[slot] = keep;
        wheelNow += WHEEL_GRAN_NS;
    }
}

void harvestIntents(int64_t now) {
    using namespace app::gen;
    while (consumedIntent != intentData.cursor) {
        const int32_t i = consumedIntent++ & kRingMask;
        const int32_t action = intentData.intentAction[i];
        const int type = action == NEW ? ACK_NEW : action == REPLACE ? ACK_REPLACE : ACK_CANCEL;
        schedule(now + regime.ackMin + venueRng.nextInt(regime.ackSpan), 0,
                 intentData.intentSymbol[i], intentData.intentSide[i], type,
                 intentData.intentPx[i], intentData.intentQty[i], intentData.intentGeneration[i]);
        intentsConsumed++;
    }
}

int32_t dispatchMarket(int32_t i, int32_t dep) {
    const int64_t t = evTime[(size_t) i];
    drainVenue(t);
    const int32_t sym = evSymbol[(size_t) i];
    TICK.symbol = sym; TICK.bidPx = evBid[(size_t) i] + (dep & 1); TICK.askPx = evAsk[(size_t) i];
    TICK.bidQty = evBidQty[(size_t) i]; TICK.askQty = evAskQty[(size_t) i]; TICK.timestamp = t;
    procp->handle_MarketTick(&TICK);
    lastBookUpdate[(size_t) sym] = t;
    if (!deadlinePending[(size_t) sym]) {
        deadlinePending[(size_t) sym] = 1;
        deadlinesScheduled++;
        schedule(t + freshnessDeadlineNs, 2, sym, 0, 0, 0, 0, 0);
    }
    harvestIntents(t);
    return app::gen::intentData.cursor;
}

void generate(int32_t count, int32_t symbols, uint64_t seed) {
    evSymbol.assign((size_t) count, 0); evBid.assign((size_t) count, 0);
    evAsk.assign((size_t) count, 0); evBidQty.assign((size_t) count, 0);
    evAskQty.assign((size_t) count, 0); evTime.assign((size_t) count, 0);
    deadlinePending.assign((size_t) symbols, 0);
    lastBookUpdate.assign((size_t) symbols, 0);

    Rng rSym(seed ^ 0x1111), rPx(seed ^ 0x2222), rQty(seed ^ 0x3333),
        rOdd(seed ^ 0x4444), rPhase(seed ^ 0x6666);
    std::vector<int32_t> mid((size_t) symbols), spread((size_t) symbols),
                         lastBq((size_t) symbols, 100), lastAq((size_t) symbols, 100);
    for (int32_t i = 0; i < symbols; i++) {
        mid[(size_t) i] = 10000 + rPx.nextInt(200);
        spread[(size_t) i] = 1 + rPx.nextInt(4);
    }
    int64_t now = 1000000;
    int phase = 1, phaseLeft = 0;
    for (int32_t i = 0; i < count; i++) {
        if (phaseLeft == 0) {
            const int32_t r = rPhase.nextInt(100);
            phase = r < 25 ? 0 : r < 90 ? 1 : 2;
            phaseLeft = phase == 2 ? 200 + rPhase.nextInt(800)
                    : phase == 0 ? 2000 + rPhase.nextInt(4000)
                    : 1000 + rPhase.nextInt(4000);
        }
        phaseLeft--;
        now += phase == 2 ? 40 + rOdd.nextInt(60)
                : phase == 1 ? 300 + rOdd.nextInt(700)
                : 3000 + rOdd.nextInt(9000);
        int32_t sym;
        if (g_skew) {
            const int32_t u = rSym.nextInt(1 << 14);
            sym = (int32_t) (((uint64_t) ((int64_t) u * u) >> 28) % (uint64_t) symbols);
        } else {
            sym = rSym.nextInt(symbols);
        }
        const int32_t r = rPx.nextInt(phase == 2 ? 3 : 8);
        mid[(size_t) sym] += r == 0 ? -1 : r == 1 ? 1 : 0;
        if (rOdd.nextInt(512) == 0) { mid[(size_t) sym] += rPx.nextInt(9) - 4; }
        if (rOdd.nextInt(256) == 0) { spread[(size_t) sym] = 1 + rPx.nextInt(4); }
        if (rOdd.nextInt(6) == 0) {
            lastBq[(size_t) sym] = 1 + rQty.nextInt(200);
            lastAq[(size_t) sym] = 1 + rQty.nextInt(200);
        }
        int32_t bid = mid[(size_t) sym] - (spread[(size_t) sym] >> 1) - 1;
        const int32_t ask = mid[(size_t) sym] + (spread[(size_t) sym] >> 1) + 1;
        int32_t bq = lastBq[(size_t) sym], aq = lastAq[(size_t) sym];
        if (rOdd.nextInt(128) == 0) { bq = 1 + rQty.nextInt(20); aq = 200 + rQty.nextInt(200); }
        if (rOdd.nextInt(1024) == 0) { bid = 0; }
        evSymbol[(size_t) i] = sym; evBid[(size_t) i] = bid; evAsk[(size_t) i] = ask;
        evBidQty[(size_t) i] = bq; evAskQty[(size_t) i] = aq; evTime[(size_t) i] = now;
    }
}

int violations = 0;
void check(bool ok, const char* what) {
    if (!ok) { std::printf("   * %s\n", what); violations++; }
}
}  // namespace

int main(int argc, char** argv) {
    using namespace app::gen;
    const int64_t iters = argc > 1 ? atoll(argv[1]) : 2000000;
    const int64_t warm  = argc > 2 ? atoll(argv[2]) : 1000000;
    const int batches   = argc > 3 ? atoi(argv[3])  : 3;
    const int32_t bufferSize = 1 << 20, bufferMask = bufferSize - 1;
    const char* rn = getenv("REGIME");
    regime = rn == nullptr ? NORMAL
            : std::strcmp(rn, "LOW") == 0 ? LOW
            : std::strcmp(rn, "SLOW") == 0 ? SLOW : NORMAL;
    g_skew = getenv("SKEW") == nullptr || std::strcmp(getenv("SKEW"), "false") != 0;

    generate(bufferSize, kSymbols, 0xC0FFEEULL);
    venueRng = Rng(0xC0FFEEULL ^ 0x7777);

    VenueCoreProcessor p;
    procp = &p;
#ifdef HAS_AUDIT
    struct CountingSink : fluxtion::LogRecordListener {
        long long records = 0;
        void processLogRecord(const fluxtion::BinaryLogRecord&) override { ++records; }
    } sink;
    p.setLogSink(&sink);
#endif
    p.init();
    wheelInit(evTime[0] - WHEEL_GRAN_NS);

    for (int64_t i = 0; i < warm; i++) { dispatchMarket((int32_t) (i & bufferMask), 0); }
#ifdef HAS_AUDIT
    if (sink.records == 0) { std::printf("REFUSED: audit build, sink saw no records\n"); return 4; }
#endif

    const char* mode = getenv("DEPENDENT");
    const bool dependent = mode != nullptr;
    const bool control = dependent && std::strcmp(mode, "control") == 0;
    const bool mixOnly = getenv("MIX") != nullptr;

    double best = 1e18;
    int32_t dep = 0;
    if (!mixOnly) {
        for (int b = 0; b < batches; b++) {
            const auto start = std::chrono::steady_clock::now();
            if (!dependent) {
                for (int64_t i = 0; i < iters; i++) { dispatchMarket((int32_t) (i & bufferMask), 0); }
            } else if (control) {
                int32_t s2 = 0;
                for (int64_t i = 0; i < iters; i++) { s2 += dispatchMarket((int32_t) (i & bufferMask), 0); }
                dep = s2;
            } else {
                for (int64_t i = 0; i < iters; i++) { dep = dispatchMarket((int32_t) (i & bufferMask), dep); }
            }
            const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::steady_clock::now() - start).count();
            best = std::min(best, (double) ns / (double) iters);
        }
    }

    // ---- invariants: refuse rather than print an attractive number ---------------------------
    for (int32_t i = 0; i < kSymbols * 2; i++) {
        check(workingData.remainingQty[i] >= 0, "remaining quantity went negative");
        check(workingData.remainingQty[i] <= workingData.liveQty[i] || workingData.liveQty[i] == 0,
              "remaining exceeds live quantity");
    }
    for (int32_t s = 0; s < kSymbols; s++) {
        if (!marketData.valid[s]) { continue; }
        check(quoteData.desiredBidPx[s] < quoteData.desiredAskPx[s], "crossed quote");
        check(!(!riskData.bidAllowed[s] && diffData.action[s << 1] == NEW), "NEW on a disallowed bid");
        check(!(!riskData.askAllowed[s] && diffData.action[(s << 1) + 1] == NEW), "NEW on a disallowed ask");
    }
    check(floorDiv16(-33) == -3 && floorDiv16(33) == 2, "bid rounding must floor");
    check(ceilDiv16(-33) == -2 && ceilDiv16(33) == 3, "ask rounding must ceil");
    check(acksSent > 0, "no acknowledgements were delivered");
    check(intentsConsumed >= acksSent, "more acks delivered than intents issued");
    check(intentsConsumed - acksSent <= inFlight, "intents neither acked nor in flight");
    check(consumedIntent == intentData.cursor, "intents left unconsumed");
    check(freshnessDeadlineNs <= STALE_NANOS, "deadline horizon exceeds the stale bound");
    const int64_t decisions = diffData.none + diffData.neu + diffData.replace + diffData.cancel;
    check(decisions > 0, "no decisions were made");
    const double actionable = (double) (diffData.neu + diffData.replace + diffData.cancel) * 100.0
            / (double) (decisions > 0 ? decisions : 1);
    check(actionable >= 0.5, "actionable decisions below the 0.5% floor");
    if (violations > 0) { std::printf("REFUSED - %d invariant(s) violated (above)\n", violations); return 4; }

    int64_t checksum = 0;
    for (int32_t i = 0; i < kRing; i++) {
        checksum = checksum * 31 + intentData.intentSymbol[i] + intentData.intentAction[i] * 7LL
                + intentData.intentPx[i] * 13LL + intentData.intentQty[i] * 17LL
                + intentData.intentGeneration[i] * 19LL;
    }
    const double dp = decisions == 0 ? 0 : 100.0 / (double) decisions;
    const double np = diffData.none == 0 ? 0 : 100.0 / (double) diffData.none;
    if (mixOnly) {
        std::printf("MIX cpp-venuecore symbols=%d regime=%s skew=%s\n",
                    kSymbols, regime.name, g_skew ? "true" : "false");
    } else {
        std::printf("RESULT harness=%s %s cpp-venuecore%s audit=%s symbols=%d regime=%s skew=%s "
                    "%.4f ns intents=%lld checksum=%lld sink=%d\n",
                    HARNESS_TAG, RUNTIME_TAG,
                    !dependent ? "" : control ? "-readcontrol" : "-serial",
#ifdef HAS_AUDIT
                    "true",
#else
                    "false",
#endif
                    kSymbols, regime.name, g_skew ? "true" : "false",
                    best, (long long) intentData.emitted, (long long) checksum, dep);
    }
    std::printf("  decisions NONE=%.1f%% (%lld)  NEW=%.2f%% (%lld)  REPLACE=%.2f%% (%lld)  "
                "CANCEL=%.2f%% (%lld)  n=%lld\n",
                (double) diffData.none * dp, (long long) diffData.none,
                (double) diffData.neu * dp, (long long) diffData.neu,
                (double) diffData.replace * dp, (long long) diffData.replace,
                (double) diffData.cancel * dp, (long long) diffData.cancel, (long long) decisions);
    std::printf("  NONE why  pending=%.1f%%  unchanged=%.1f%%  notAllowed=%.1f%%\n",
                (double) diffData.nonePending * np, (double) diffData.noneUnchanged * np,
                (double) diffData.noneNotAllowed * np);
    std::printf("  venue    acks=%lld execs=%lld (refused=%lld) deadlines=%lld maxInFlight=%d\n",
                (long long) acksSent, (long long) execsSent,
                (long long) workingData.rejectedExecutions, (long long) deadlinesFiredH, maxInFlight);
    return 0;
}
