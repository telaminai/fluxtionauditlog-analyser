// All arms in ONE process with ONE clock. Java arms cross the C ABI once per batch.
#include <cstdio>
#include <cstdint>
#include <chrono>
#include <cmath>
#include "libfluxtion.h"

struct HandCpp {
    double mid=0, spread=0, ewma=0, vol=0, notional=0, exposure=0, charge=0, buffer=0;
    double limit = 108000.0;
    int64_t breaches=0, updates=0, n=0;
    inline void onTick(double bid, double ask) {
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
static inline double now_ns(){ using namespace std::chrono;
    return (double)duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count(); }

int main() {
    const int64_t WARM = 5000000, ITERS = 200000000;
    graal_isolate_t* iso=nullptr; graal_isolatethread_t* thr=nullptr;
    if (graal_create_isolate(nullptr,&iso,&thr)!=0){ printf("isolate failed\n"); return 1; }

    fx_run_batch(thr, WARM);
    double t0=now_ns(); fx_run_batch(thr, ITERS); double javaFx=(now_ns()-t0)/ITERS;

    fx_hand_batch(thr, WARM);
    t0=now_ns(); fx_hand_batch(thr, ITERS); double javaHand=(now_ns()-t0)/ITERS;

    HandCpp h;
    for(int64_t i=0;i<WARM;i++) h.onTick(100.0+(i&15),100.5+(i&15));
    t0=now_ns(); for(int64_t i=0;i<ITERS;i++) h.onTick(100.0+(i&15),100.5+(i&15)); double cpp=(now_ns()-t0)/ITERS;

    printf("CORRECTNESS fx=%.4f javaHand=%.4f cpp=%.4f match=%s\n",
        fx_buffer(thr), fx_hand_buffer(thr), h.buffer,
        (std::fabs(fx_buffer(thr)-h.buffer)<1e-9 && std::fabs(fx_hand_buffer(thr)-h.buffer)<1e-9)?"YES":"NO");
    printf("RESULT java_fluxtion_generated %.4f\n", javaFx);
    printf("RESULT java_hand_rolled        %.4f\n", javaHand);
    printf("RESULT cpp_hand_rolled         %.4f\n", cpp);
    graal_tear_down_isolate(thr);
    return 0;
}
