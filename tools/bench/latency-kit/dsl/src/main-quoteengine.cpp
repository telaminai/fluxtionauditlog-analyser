#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <chrono>
#include <algorithm>
#include <vector>
#include <new>
#include "QuoteEngineProcessor.h"

// ---------------------------------------------------------------------------------------------
// HEAP CALLS ON THE MEASURED PATH, counted rather than assumed.
//
// The Java arm proves zero allocation two ways - the JVM's own per-thread accounting reads 0 bytes,
// and both arms run 25M events under a NON-COLLECTING GC on a 32MB heap. C++ has no such accounting,
// so this counts global operator new directly. The counter is snapshotted after init and warmup, so
// what it reports is what the STEADY-STATE path did: construction and first-pass key interning are
// one-off costs and charging them per event would be a lie in the other direction.
// ---------------------------------------------------------------------------------------------
namespace { volatile uint64_t g_allocations = 0; volatile uint64_t g_allocBytes = 0; }
void* operator new(size_t n) {
    g_allocations++;
    g_allocBytes += n;
    void* p = std::malloc(n);
    if (p == nullptr) { throw std::bad_alloc(); }
    return p;
}
void operator delete(void* p) noexcept { std::free(p); }
void operator delete(void* p, size_t) noexcept { std::free(p); }

// ---------------------------------------------------------------------------------------------
// The C++ arm of the hand-written quote engine.
//
// WHERE THE NODE STATE LIVES, and it is not where Java puts it. A hand-written Java node IS your
// class: fields and callbacks together, one object per node. The C++ emitter generates a STUB
// carrying parent pointers and (when audited) an auditLog, and nothing else - it cannot know your
// fields. So the state below sits at file scope, one block per node, and the callbacks read it.
//
// That asymmetry is real and it is worth stating rather than hiding: the Java arm walks an object
// graph, this one walks statics. Both are contiguous and neither allocates on the measured path, so
// the comparison stands - but they are not the same memory layout, and a difference of a nanosecond
// or two on this path could be that and not the language.
// ---------------------------------------------------------------------------------------------
namespace app::gen {

constexpr int kWindow = 16;
constexpr int kSymbols = 64;
constexpr int kPositionLimit = 10000;

struct BookStateData {
    int32_t symbol = 0, mid = 0, imbalance = 0;
    bool valid = false;
} bookData;

struct VolData {
    int32_t ring[kWindow] = {0};
    int32_t cursor = 0, filled = 0, range = 0;
} volData;

struct InventoryData {
    int32_t positions[kSymbols] = {0};
    int32_t lastSymbol = 0, lastPosition = 0;
} inventoryData;

struct QuoteData { int32_t bidPx = 0, askPx = 0; } quoteData;
struct GateData  { bool quotable = false; } gateData;
struct PublisherData { int64_t published = 0, suppressed = 0; } publisherData;

// ---- the callbacks, arithmetic identical to the Java arm -------------------------------------
void BookState::onTick(MarketTick* e) {
    bookData.valid = e->bidPx > 0 && e->askPx > e->bidPx;
    if (!bookData.valid) { return; }
    bookData.symbol = e->symbol;
    bookData.mid = (e->bidPx + e->askPx) >> 1;
    const int32_t total = e->bidQty + e->askQty;
    bookData.imbalance = total == 0 ? 0 : ((e->bidQty - e->askQty) * 64) / total;
}

void InventoryBook::onFill(Fill* e) {
    const int32_t slot = e->symbol & (kSymbols - 1);
    inventoryData.positions[slot] += e->qty;
    inventoryData.lastSymbol = e->symbol;
    inventoryData.lastPosition = inventoryData.positions[slot];
#ifdef HAS_AUDIT
    auditLog.info("sym", e->symbol);
    auditLog.info("pos", inventoryData.lastPosition);
#endif
}

template <typename P0>
void VolatilityWindow<P0>::onBook() {
    if (!bookData.valid) { return; }
    volData.ring[volData.cursor] = bookData.mid;
    volData.cursor = volData.cursor + 1 == kWindow ? 0 : volData.cursor + 1;
    if (volData.filled < kWindow) { volData.filled++; }
    int32_t high = INT32_MIN, low = INT32_MAX;
    for (int32_t i = 0; i < volData.filled; i++) {
        const int32_t v = volData.ring[i];
        if (v > high) { high = v; }
        if (v < low) { low = v; }
    }
    volData.range = high - low;
}

template <typename P0, typename P1, typename P2>
void QuoteCalculator<P0, P1, P2>::onInputs() {
    if (!bookData.valid) { return; }
    const int32_t position = inventoryData.positions[bookData.symbol & (kSymbols - 1)];
    const int32_t pressure = bookData.imbalance < 0 ? -bookData.imbalance : bookData.imbalance;
    const int32_t half = 4 + (volData.range >> 1) + (pressure >> 3);
    const int32_t centre = bookData.mid - (position >> 2);
    quoteData.bidPx = centre - half;
    quoteData.askPx = centre + half;
}

template <typename P0, typename P1>
void RiskGate<P0, P1>::onQuote() {
    const int32_t position = inventoryData.lastPosition;
    const int32_t magnitude = position < 0 ? -position : position;
    gateData.quotable = quoteData.bidPx > 0 && quoteData.askPx > quoteData.bidPx
                        && magnitude < kPositionLimit;
}

template <typename P0, typename P1>
void QuotePublisher<P0, P1>::onGate() {
    if (!gateData.quotable) { publisherData.suppressed++; return; }
    publisherData.published++;
#ifdef HAS_AUDIT
    auditLog.info("bid", quoteData.bidPx);
    auditLog.info("ask", quoteData.askPx);
#endif
}
}  // namespace app::gen

// ---- burst latency, for the reason the Java harness states ------------------------------------
struct BurstHistogram {
    static constexpr int kBuckets = 65536;
    std::vector<uint32_t> counts = std::vector<uint32_t>(kBuckets, 0);   // parentheses, see main-shapes.cpp
    uint64_t n = 0, zero = 0;
    int64_t worst = 0;
    inline void record(int64_t ns) {
        if (ns < 0) { ns = 0; }
        if (ns == 0) { zero++; }
        if (ns > worst) { worst = ns; }
        counts[ns < kBuckets ? (size_t) ns : (size_t) (kBuckets - 1)]++;
        ++n;
    }
    int64_t percentile(double p) const {
        const uint64_t target = (uint64_t) (p * (double) n);
        uint64_t seen = 0;
        for (int i = 0; i < kBuckets; i++) {
            seen += counts[(size_t) i];
            if (seen >= target) { return i; }
        }
        return kBuckets - 1;
    }
};

namespace {
app::gen::MarketTick tick;
app::gen::Fill fill;

// The same mix as the Java harness, and the same two traps avoided: the fill quantity is indexed by
// the FILL number rather than the event number, and its cycle length is coprime with the symbol
// count. Either mistake pins every symbol to one quantity, drifts inventory, and hands the benchmark
// to the risk gate's early return.
inline void feed(app::gen::QuoteEngineProcessor& p, int64_t i, int symbols, int fillEvery) {
    if ((i % fillEvery) == 0) {
        const int64_t fillNo = i / fillEvery;
        fill.symbol = (int32_t) (fillNo % symbols);
        fill.qty = (int32_t) (((fillNo % 7) - 3) * 10);
        p.handle_Fill(&fill);
        return;
    }
    tick.symbol = (int32_t) (i % symbols);
    tick.bidPx = (int32_t) (10000 + (i & 63));
    tick.askPx = tick.bidPx + 2 + (int32_t) (i & 3);
    tick.bidQty = (int32_t) (100 + (i & 31));
    tick.askQty = (int32_t) (100 + ((i >> 3) & 31));
    p.handle_MarketTick(&tick);
}
}  // namespace

int main(int argc, char** argv) {
    using namespace app::gen;
    const int64_t iters   = argc > 1 ? atoll(argv[1]) : 20000000;
    const int64_t warm    = argc > 2 ? atoll(argv[2]) : 2000000;
    const int     batches = argc > 3 ? atoi(argv[3])  : 6;
    const int     symbols = kSymbols;
    const int     fillEvery = 32;

    QuoteEngineProcessor p;
#ifdef HAS_AUDIT
    struct CountingSink : fluxtion::LogRecordListener {
        long long records = 0;
        void processLogRecord(const fluxtion::BinaryLogRecord&) override { ++records; }
    } sink;
    p.setLogSink(&sink);
#endif
    p.init();

    for (int64_t i = 0; i < warm; i++) { feed(p, i, symbols, fillEvery); }
#ifdef HAS_AUDIT
    if (sink.records == 0) {
        std::printf("REFUSED %s %s: audit build but the sink saw no records - the audit log is dead\n",
                    HARNESS_TAG, RUNTIME_TAG);
        return 3;
    }
#endif

    if (getenv("LATENCY") != nullptr) {
        const int burst = getenv("BURST") != nullptr ? atoi(getenv("BURST")) : 16;
        // The instrument's RESOLUTION, measured not assumed.
        int64_t resolution = INT64_MAX;
        for (int i = 0; i < 500000; i++) {
            const auto a = std::chrono::steady_clock::now();
            const auto b = std::chrono::steady_clock::now();
            const int64_t d = std::chrono::duration_cast<std::chrono::nanoseconds>(b - a).count();
            if (d > 0 && d < resolution) { resolution = d; }
        }
        BurstHistogram hist;
        const int64_t bursts = iters / burst;
        for (int64_t b = 0; b < bursts; b++) {
            const int64_t base = b * burst;
            const auto start = std::chrono::steady_clock::now();
            for (int k = 0; k < burst; k++) { feed(p, base + k, symbols, fillEvery); }
            hist.record(std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::steady_clock::now() - start).count());
        }
        const int64_t p50 = hist.percentile(0.50);
        const double zeroShare = (double) hist.zero / (double) hist.n;
        if (p50 < resolution * 2 || zeroShare > 0.5) {
            std::printf("REFUSED harness=%s %s cpp-quoteengine burst=%d: %.1f%% of bursts did not move "
                        "the clock (resolution %lld ns) and p50 is %lld ns - raise BURST.\n",
                        HARNESS_TAG, RUNTIME_TAG, burst, zeroShare * 100.0,
                        (long long) resolution, (long long) p50);
            return 2;
        }
        // The whole distribution - see the Java harness for why p99.99 is the interesting column.
        std::printf("RESULT harness=%s %s cpp-quoteengine-burstlatency audit=%s burst=%d p50=%lld "
                    "p90=%lld p99=%lld p999=%lld p9999=%lld max=%lld "
                    "perEventP50=%.2f perEventP99=%.2f perEventP9999=%.2f "
                    "timerResolution=%lld ns bursts=%lld published=%lld\n",
                    HARNESS_TAG, RUNTIME_TAG,
#ifdef HAS_AUDIT
                    "true",
#else
                    "false",
#endif
                    burst, (long long) p50, (long long) hist.percentile(0.90),
                    (long long) hist.percentile(0.99), (long long) hist.percentile(0.999),
                    (long long) hist.percentile(0.9999), (long long) hist.worst,
                    p50 / (double) burst, hist.percentile(0.99) / (double) burst,
                    hist.percentile(0.9999) / (double) burst,
                    (long long) resolution, (long long) bursts,
                    (long long) publisherData.published);
        if (getenv("CDF") != nullptr) {
            std::printf("CDF ");
            uint64_t seen = 0;
            for (int i = 0; i < BurstHistogram::kBuckets; i++) {
                if (hist.counts[(size_t) i] == 0) { continue; }
                seen += hist.counts[(size_t) i];
                std::printf("%d:%.6f ", i, (double) seen / (double) hist.n);
            }
            std::printf("\n");
        }
        return 0;
    }

    const uint64_t allocsBeforeMeasure = g_allocations;
    const uint64_t allocBytesBeforeMeasure = g_allocBytes;
    double best = 1e18;
    for (int b = 0; b < batches; b++) {
        const auto start = std::chrono::steady_clock::now();
        for (int64_t i = 0; i < iters; i++) { feed(p, i, symbols, fillEvery); }
        const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now() - start).count();
        best = std::min(best, (double) ns / (double) iters);
    }
    const uint64_t heapCalls = g_allocations - allocsBeforeMeasure;
    const uint64_t heapBytes = g_allocBytes - allocBytesBeforeMeasure;
#ifdef HAS_AUDIT
    std::printf("RESULT harness=%s %s cpp-quoteengine audit=true %.4f ns published=%lld records=%lld "
                "heapCalls=%llu heapBytes=%llu bytesPerEvent=%.4f\n",
                HARNESS_TAG, RUNTIME_TAG, best, (long long) publisherData.published, sink.records,
                (unsigned long long) heapCalls, (unsigned long long) heapBytes,
                (double) heapBytes / (double) (iters * batches));
#else
    std::printf("RESULT harness=%s %s cpp-quoteengine audit=false %.4f ns published=%lld "
                "heapCalls=%llu heapBytes=%llu bytesPerEvent=%.4f\n",
                HARNESS_TAG, RUNTIME_TAG, best, (long long) publisherData.published,
                (unsigned long long) heapCalls, (unsigned long long) heapBytes,
                (double) heapBytes / (double) (iters * batches));
#endif
    return 0;
}
