#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <chrono>
#include <algorithm>
#include "GroupByProcessor.h"

namespace app::gen {
int64_t Tick_getKey(const void* e)   { return static_cast<const Tick*>(e)->key; }
int32_t Tick_getPrice(const void* e) { return static_cast<const Tick*>(e)->price; }
// The author's side of the store contract: name the generated struct and read one key.
int32_t GenGroupBy_keyZeroTotal(const void* g) {
    return static_cast<const GROUPBY_STRUCT*>(g)->valueFor(0);
}
}

int main(int argc, char** argv) {
    using namespace app::gen;
    const int64_t iters   = argc > 1 ? atoll(argv[1]) : 20000000;
    const int64_t warm    = argc > 2 ? atoll(argv[2]) : 2000000;
    const int     batches = argc > 3 ? atoi(argv[3])  : 6;
    const int32_t keys    = argc > 4 ? atoi(argv[4])  : 4;
    // pattern 0 = round-robin, 1 = hammer key 0. Both warm ALL keys first, so the store size and the
    // hash probe are identical and only the dependency chain differs.
    const int     pattern = argc > 5 ? atoi(argv[5])  : 0;
    GroupByProcessor p;
    p.init();
    Tick t;
    for (int64_t i = 0; i < warm; i++) {
        t.price = (int32_t)((i & 15) - 8); t.key = (int32_t)(i % keys); p.handle_Tick(&t);
    }
    double best = 1e18;
    int32_t checksum = 0;
    for (int b = 0; b < batches; b++) {
        auto start = std::chrono::steady_clock::now();
        for (int64_t i = 0; i < iters; i++) {
            t.price = (int32_t)((i & 15) - 8);
            t.key = pattern == 1 ? 0 : (int32_t)(i % keys);
            p.handle_Tick(&t);
        }
        auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now() - start).count();
        best = std::min(best, (double) ns / (double) iters);
        checksum = p.total.getAsInt();
    }
    std::printf("cpp-groupby keys=%d pattern=%d ns=%.4f checksum=%d\n", keys, pattern, best, checksum);
    return 0;
}
