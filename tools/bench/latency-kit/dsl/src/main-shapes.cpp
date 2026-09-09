#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <chrono>
#include <algorithm>
#include "ShapeProcessor.h"

namespace app::gen {
int32_t Tick_getPrice(const void* e) { return static_cast<const Tick*>(e)->price; }
// merge only; harmless where the shape does not declare them.
bool GenShapes_pos(const void* e) { return static_cast<const Tick*>(e)->price > 0; }
bool GenShapes_neg(const void* e) { return static_cast<const Tick*>(e)->price <= 0; }
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
#ifdef HAS_SINK
    std::printf("cpp-%s ns=%.4f checksum=%d fires=%lld\n", SHAPE_NAME, best, checksum,
                (long long) sinkFires);
#else
    std::printf("cpp-%s ns=%.4f checksum=%d\n", SHAPE_NAME, best, checksum);
#endif
    return 0;
}
