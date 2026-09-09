// The 30-node five-event-type converging graph, on the ACTUAL GENERATED C++ processor.
// Same arithmetic as DagNodesConverging, same driving sequence, same checksum as the Java arms.
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <chrono>
#include "ConvProcessor.h"

namespace com::bench::convc {
void R0::on(E0* e) { v = e->v + 0; }
void R1::on(E1* e) { v = e->v + 1; }
void R2::on(E2* e) { v = e->v + 2; }
void R3::on(E3* e) { v = e->v + 3; }
void R4::on(E4* e) { v = e->v + 4; }
template <typename P0> void D1<P0>::calc() { v = p0->v * 1.05 + 0.5; }
template <typename P0, typename P1, typename P2>
void D3<P0, P1, P2>::calc() { v = p0->v + p1->v + p2->v; }
template <typename P0> void Tail<P0>::calc() { v = p0->v * 1.05 + 0.5; }
}

static double out;

static void run(int64_t n) {
    using namespace com::bench::convc;
    ConvProcessor p;
    p.init();
    E0 e0; E1 e1; E2 e2; E3 e3; E4 e4;
    for (int64_t i = 0; i < n; i++) {
        int k = (int)(((uint64_t)(i * 0x9E3779B97F4A7C15ULL)) >> 61) % 5;
        double val = 1.0 + (double)(i & 15);
        switch (k) {
            case 0: e0.v = val; p.handle_E0(&e0); break;
            case 1: e1.v = val; p.handle_E1(&e1); break;
            case 2: e2.v = val; p.handle_E2(&e2); break;
            case 3: e3.v = val; p.handle_E3(&e3); break;
            default: e4.v = val; p.handle_E4(&e4); break;
        }
    }
    out = p.t5.v;
}

int main(int argc, char** argv) {
    int64_t warm = 500000, iters = 3000000;
    for (int i = 1; i < argc; i++) {
        if (sscanf(argv[i], "-Dwarm=%lld", (long long*)&warm) == 1) continue;
        if (sscanf(argv[i], "-Diters=%lld", (long long*)&iters) == 1) continue;
    }
    run(warm);
    auto t0 = std::chrono::steady_clock::now();
    run(iters);
    double ns = std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - t0).count();
    printf("RESULT harness=h5 rt:convcpp graph=conv arm=cpp-generated audit=none clock=n/a "
           "%8.3f ns %7.2f Mmsg/s recPerEvent=0.000 avgRecBytes=n/a allocB=0.000 v=%.4f\n",
           ns / iters, 1e9 / (ns / iters) / 1e6, out);
    return 0;
}
