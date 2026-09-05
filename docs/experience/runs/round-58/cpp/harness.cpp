// Fluxtion native shared library embedded in C++, against hand-optimised C++.
// Identical arithmetic, identical semantics, identical output asserted before timing.
#include <cstdio>
#include <cstdint>
#include <chrono>
#include <cmath>
#include "libfluxtion.h"

// ---- hand-rolled C++, what a seasoned developer writes: one struct, one method, no objects ----
struct HandCpp {
    double mid=0, spread=0, ewma=0, vol=0, notional=0, exposure=0, charge=0, buffer=0;
    double limit = 108000.0;
    int64_t breaches=0, updates=0, n=0;
    inline void onTick(double bid, double ask, int64_t) {
        mid      = (bid + ask) * 0.5;
        ewma     = (n++ == 0) ? mid : 0.3 * mid + 0.7 * ewma;
        spread   = ask - bid;
        notional = mid * 1000.0 - spread;
        double d = mid - ewma;
        vol      = d < 0 ? -d : d;
        exposure = notional * (1.0 + vol * 0.001);
        if (exposure > limit) breaches++;
        charge   = exposure * 0.08;
        buffer   = charge * 1.25;
        updates++;
    }
};

static inline double now_ns() {
    using namespace std::chrono;
    return (double)duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count();
}

int main(int argc, char** argv) {
    const int64_t WARM = 5000000, ITERS = 200000000;

    graal_isolate_t* iso = nullptr; graal_isolatethread_t* thr = nullptr;
    if (graal_create_isolate(nullptr, &iso, &thr) != 0) { printf("isolate failed\n"); return 1; }

    // ---- arm 1: per-event across the C boundary (what an integrator actually pays) ----
    for (int64_t i = 0; i < WARM; i++) fx_on_tick(thr, 100.0 + (i & 15), 100.5 + (i & 15), i);
    double t0 = now_ns();
    for (int64_t i = 0; i < ITERS; i++) fx_on_tick(thr, 100.0 + (i & 15), 100.5 + (i & 15), i);
    double perEvent = (now_ns() - t0) / ITERS;

    // ---- arm 2: batch, loop inside the library (boundary amortised away) ----
    fx_run_batch(thr, WARM);
    t0 = now_ns();
    fx_run_batch(thr, ITERS);
    double batch = (now_ns() - t0) / ITERS;
    double fxBuf = fx_buffer(thr); int64_t fxBre = fx_breaches(thr), fxUpd = fx_updates(thr);

    // ---- arm 3: hand-optimised C++ ----
    HandCpp h;
    for (int64_t i = 0; i < WARM; i++) h.onTick(100.0 + (i & 15), 100.5 + (i & 15), i);
    t0 = now_ns();
    for (int64_t i = 0; i < ITERS; i++) h.onTick(100.0 + (i & 15), 100.5 + (i & 15), i);
    double cpp = (now_ns() - t0) / ITERS;

    printf("CORRECTNESS  fluxtion buffer=%.4f  cpp buffer=%.4f  match=%s\n",
           fxBuf, h.buffer, (std::fabs(fxBuf - h.buffer) < 1e-9 ? "YES" : "NO"));
    printf("             fluxtion breaches=%lld updates=%lld | cpp breaches=%lld updates=%lld\n",
           (long long)fxBre, (long long)fxUpd, (long long)h.breaches, (long long)h.updates);
    printf("RESULT per_event_boundary %.4f\n", perEvent);
    printf("RESULT batch_in_library   %.4f\n", batch);
    printf("RESULT hand_cpp           %.4f\n", cpp);
    graal_tear_down_isolate(thr);
    return 0;
}
