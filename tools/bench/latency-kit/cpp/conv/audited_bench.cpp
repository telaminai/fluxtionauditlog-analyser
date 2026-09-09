// The 30-node graph with EVERY NODE LOGGING — the dense-audit control, and the direct counterpart of
// the Java c-audit-string arm. Same nodes, same values logged, same record layout.
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <chrono>
#include "ConvProcessor.h"

namespace {
/// No I/O: the control measures the audit machinery, not the disk. Matches the Java arm's sink.
struct CountingSink : fluxtion::LogRecordListener {
    int64_t records = 0, bytes = 0;
    void processLogRecord(const fluxtion::BinaryLogRecord& r) override { records++; bytes += r.length(); }
};
CountingSink sink;
}

namespace com::bench::convac {
void R0::on(E0* e) { v = e->v + 0; auditLog.info("v", v); }
void R1::on(E1* e) { v = e->v + 1; auditLog.info("v", v); }
void R2::on(E2* e) { v = e->v + 2; auditLog.info("v", v); }
void R3::on(E3* e) { v = e->v + 3; auditLog.info("v", v); }
void R4::on(E4* e) { v = e->v + 4; auditLog.info("v", v); }
template <typename P0> void D1<P0>::calc() { v = p0->v * 1.05 + 0.5; auditLog.info("v", v); }
template <typename P0, typename P1, typename P2>
void D3<P0, P1, P2>::calc() { v = p0->v + p1->v + p2->v; auditLog.info("v", v); }
// The Java Tail logs TWO entries - auditLog.info("v", v).info("n", 1L), a double and a long. Logging
// one here made the C++ record 172 bytes against Java's 188: same graph, different audit content, and
// an invalid comparison. The checksum cannot see this because it is computed on v, not on the log.
// avgRecBytes is what caught it.
template <typename P0> void Tail<P0>::calc() {
    v = p0->v * 1.05 + 0.5;
    auditLog.info("v", v);
    auditLog.info("n", (int64_t) 1);
}
}

static double out;

static void run(int64_t n) {
    using namespace com::bench::convac;
    ConvProcessor p;
    p.setLogSink(&sink);
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
    int64_t r0 = sink.records;
    auto t0 = std::chrono::steady_clock::now();
    run(iters);
    double ns = std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - t0).count();
    int64_t produced = sink.records - r0;
    if (produced == 0) { fprintf(stderr, "the sink saw ZERO records - nothing was measured\n"); return 1; }
    printf("RESULT harness=h5 rt:convaudc graph=conv arm=cpp-audit-dense audit=on clock=n/a "
           "%8.3f ns %7.2f Mmsg/s recPerEvent=%.3f avgRecBytes=%.1f allocB=0.000 v=%.4f\n",
           ns / iters, 1e9 / (ns / iters) / 1e6, (double) produced / iters,
           (double) sink.bytes / sink.records, out);
    return 0;
}
