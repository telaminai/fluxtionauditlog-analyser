#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <mach/mach_time.h>
#include <chrono>
#include <algorithm>
#include <cstring>
#include <algorithm>
#include <vector>
#include "ShapeProcessor.h"

// ---------------------------------------------------------------------------------------------
// PER-EVENT LATENCY, which is a different measurement from everything else in this kit.
//
// Every other bench here does `t0 = now; loop N; (now - t0) / N`. That is steady-state reciprocal
// THROUGHPUT: a superscalar machine retires overlapping work from successive events, so a 0.62 ns
// figure can be entirely genuine while meaning nothing about how long one causally dependent event
// takes to traverse the graph. The two numbers answer different questions and only one of them has
// ever been measured here.
//
// The obstacle is that the timer costs more than the event. mach_absolute_time is 4.78 ns raw; an
// unaudited plain event is 0.62 ns. Timing each event individually would be measuring the clock
// EIGHT TIMES OVER and reporting the result as the graph. So this refuses rather than misleads: it
// measures the timer's own cost first, and declines to print percentiles for any arm where the
// measured event is not comfortably larger than the instrument.
// ---------------------------------------------------------------------------------------------
struct LatencyHistogram {
    // Fixed buckets, no allocation on the measured path: 1 ns resolution to 4 us, then saturating.
    static constexpr int kBuckets = 4096;
    // PARENTHESES, not braces. `std::vector<uint32_t> counts{kBuckets, 0}` selects the
    // initializer_list constructor and builds a vector of TWO elements - 4096 and 0 - so every
    // record() wrote out of bounds. One shape survived on luck and the next segfaulted.
    std::vector<uint32_t> counts = std::vector<uint32_t>(kBuckets, 0);
    uint64_t n = 0;
    int64_t worst = 0;

    inline void record(int64_t ns) {
        if (ns < 0) { ns = 0; }
        if (ns > worst) { worst = ns; }
        counts[ns < kBuckets ? (size_t) ns : (size_t) (kBuckets - 1)]++;
        ++n;
    }

    int64_t percentile(double p) const {
        uint64_t target = (uint64_t) (p * (double) n);
        uint64_t seen = 0;
        for (int i = 0; i < kBuckets; i++) {
            seen += counts[(size_t) i];
            if (seen >= target) { return i; }
        }
        return kBuckets - 1;
    }
};

namespace app::gen {
int32_t Tick_getPrice(const void* e) { return static_cast<const Tick*>(e)->price; }
// merge only; harmless where the shape does not declare them.
bool GenShapes_pos(const void* e) { return static_cast<const Tick*>(e)->price > 0; }
bool GenShapes_neg(const void* e) { return static_cast<const Tick*>(e)->price <= 0; }
bool GenShapes_always(const void* e) { return static_cast<const Tick*>(e)->price != INT32_MIN; }
bool GenShapes_alsoAlways(const void* e) { return static_cast<const Tick*>(e)->price != INT32_MIN; }
#ifdef HAS_FLATMAP
// The flatMap shape: three elements per event, so four graph cycles for one arrival.
static const char* const kParts[] = {"aa", "b", "ccc"};
void GenShapes_parts(const void* event, fluxtion::Emitter& emit) {
    (void) event;
    for (int i = 0; i < 3; i++) { emit(kParts[i]); }
}
int32_t String_length(const void* value) {
    return static_cast<int32_t>(std::strlen(static_cast<const char*>(value)));
}
#endif
#ifdef HAS_SINK
// The generated struct declares the trigger and nothing else - node state is the stub author's.
// The counter is printed at the end so the notification cannot be optimised away.
int64_t sinkFires = 0;
bool Sink::fired() { ++sinkFires; return true; }
#endif
}

int main(int argc, char** argv) {
    using namespace app::gen;
    const int64_t iters   = argc > 1 ? atoll(argv[1]) : 20000000;
    const int64_t warm    = argc > 2 ? atoll(argv[2]) : 2000000;
    const int     batches = argc > 3 ? atoi(argv[3])  : 6;
    ShapeProcessor p;
#ifdef HAS_AUDIT
    // The counterpart of the Java arm's counting sink - the audit PATH, not the disk.
    struct CountingSink : fluxtion::LogRecordListener {
        long long records = 0;
        void processLogRecord(const fluxtion::BinaryLogRecord&) override { ++records; }
    } sink;
    p.setLogSink(&sink);
#endif
    p.init();
    Tick t;
    for (int64_t i = 0; i < warm; i++) { t.price = (int32_t)((i & 15) - 8); p.handle_Tick(&t); }
    // -Dlatency: measure the INSTRUMENT first, then per-event deltas, then refuse if the instrument
    // is not small against the thing measured.
    if (getenv("LATENCY") != nullptr) {
        auto ticks = []() -> int64_t {
#if defined(__APPLE__)
            return (int64_t) mach_absolute_time();
#else
            return std::chrono::duration_cast<std::chrono::nanoseconds>(
                       std::chrono::steady_clock::now().time_since_epoch()).count();
#endif
        };
        mach_timebase_info_data_t tb; mach_timebase_info(&tb);
        auto toNs = [&](int64_t t) { return t * tb.numer / tb.denom; };

        // The instrument's RESOLUTION, not its call cost - they differ by an order of magnitude and
        // only one of them bounds what can be measured. mach_absolute_time COSTS about 4.8 ns per
        // call (pipelined), but it TICKS every 41.67 ns on Apple Silicon: 82% of consecutive reads
        // return the same value. So the smallest observable non-zero delta is one tick, and any event
        // shorter than that reads as either 0 or 41 - quantisation, not latency.
        //
        // Measuring the call cost instead, which is what this did first, reported a 0 ns timer and
        // happily printed p50=4 / p99=41 for a 14 ns event. Both numbers were the clock.
        int64_t resolution = INT64_MAX;
        for (int i = 0; i < 500000; i++) {
            int64_t a = ticks(); int64_t b2 = ticks();
            int64_t d = b2 - a;
            if (d > 0 && d < resolution) { resolution = d; }
        }
        const int64_t timerNs = toNs(resolution);

        for (int64_t i = 0; i < warm; i++) { t.price = (int32_t)((i & 15) - 8); p.handle_Tick(&t); }
        LatencyHistogram hist;
        for (int64_t i = 0; i < iters; i++) {
            t.price = (int32_t)((i & 15) - 8);
            const int64_t start = ticks();
            p.handle_Tick(&t);
            // NOT minus the resolution: a resolution is not an overhead to subtract, it is a floor
            // on what can be seen. Subtracting it turned every sub-tick event into 0 and every
            // one-tick event into 0 as well, which is how this first reported p50=0 for everything.
            hist.record(toNs(ticks() - start));
        }
        const int64_t p50 = hist.percentile(0.50);
        const double zeroShare = (double) hist.counts[0] / (double) hist.n;
        // If the median event does not even move the clock by one tick, every percentile below is
        // quantisation. Refuse rather than print it: a p99 of 41 ns for a 14 ns event is not a tail,
        // it is one tick of a counter that cannot see the event at all.
        // TWO ticks, not one. A p50 of exactly one tick is still quantisation: the plain audited graph
        // reported p50 = p99 = p99.9 = 41 ns, a degenerate distribution in which every event lands in
        // the same single bucket because the counter cannot resolve them apart. Percentiles only carry
        // information once an event spans several ticks - flatMap, at 83/125/166, does.
        if (p50 < timerNs * 2 || zeroShare > 0.5) {
            std::printf("REFUSED harness=%s %s %s: %.1f%% of events did not move the clock at all "
                        "(resolution %lld ns), and p50 is %lld ns. Every percentile here would be "
                        "quantisation - the counter cannot resolve events this small apart. Per-event "
                        "latency needs a graph costing several ticks.\n",
                        HARNESS_TAG, RUNTIME_TAG, SHAPE_NAME, zeroShare * 100.0,
                        (long long) timerNs, (long long) p50);
            return 2;
        }
        std::printf("RESULT harness=%s %s %s-latency p50=%lld p99=%lld p999=%lld max=%lld "
                    "timerResolution=%lld ns n=%llu\n",
                    HARNESS_TAG, RUNTIME_TAG, SHAPE_NAME,
                    (long long) p50, (long long) hist.percentile(0.99),
                    (long long) hist.percentile(0.999), (long long) hist.worst,
                    (long long) timerNs, (unsigned long long) hist.n);
        return 0;
    }

    double best = 1e18;
    int32_t checksum = 0;
    for (int b = 0; b < batches; b++) {
        auto start = std::chrono::steady_clock::now();
        for (int64_t i = 0; i < iters; i++) { t.price = (int32_t)((i & 15) - 8); p.handle_Tick(&t); }
        auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now() - start).count();
        best = std::min(best, (double) ns / (double) iters);
        checksum = p.total.getAsInt();
    }
#ifdef HAS_AUDIT
    std::printf("RESULT harness=%s %s cpp-%s audit=true %.4f ns checksum=%d records=%lld\n",
                HARNESS_TAG, RUNTIME_TAG, SHAPE_NAME, best, checksum, sink.records);
#else
    std::printf("RESULT harness=%s %s cpp-%s audit=false %.4f ns checksum=%d\n",
                HARNESS_TAG, RUNTIME_TAG, SHAPE_NAME, best, checksum);
#endif
    return 0;
}
