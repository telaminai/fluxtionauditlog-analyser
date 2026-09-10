#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <chrono>
#include <algorithm>
#include <cstring>
#include "ShapeProcessor.h"

namespace app::gen {
int32_t Tick_getPrice(const void* e) { return static_cast<const Tick*>(e)->price; }
// merge only; harmless where the shape does not declare them.
bool GenShapes_pos(const void* e) { return static_cast<const Tick*>(e)->price > 0; }
bool GenShapes_neg(const void* e) { return static_cast<const Tick*>(e)->price <= 0; }
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
