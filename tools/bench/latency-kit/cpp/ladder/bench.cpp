// The PriceLadder benchmark, on the ACTUAL GENERATED C++ processor.
//
// Every C++ figure quoted for this target so far came from code hand-written to MODEL what a generator
// would emit. This measures the generator's real output: same graph, same 10,000 fixed-seed ladders,
// same driving sequence, and the same checksum the Java arms produce. If the checksum differs, this is
// a different program and the comparison is void.
#include <cstdio>
#include <cstdint>
#include <chrono>

namespace com::pl::gen {
struct PriceLadder {
    int32_t bidSizes[5]  = {0,0,0,0,0};
    int32_t bidPrices[5] = {0,0,0,0,0};
    int32_t askSizes[5]  = {0,0,0,0,0};
    int32_t askPrices[5] = {0,0,0,0,0};
};
struct PriceDistributor {
    PriceLadder* ladder = nullptr;
    void setPriceLadder(PriceLadder* l) { ladder = l; }
    PriceLadder* getPriceLadder() const { return ladder; }
};
}

#include "PlProcessor.h"

namespace com::pl::gen {

// ---- node bodies: the same four calculations the Java nodes perform ----

bool MidCalculator::newPriceLadder(PriceLadder* l) {
    mid_ = (l->askPrices[0] + l->bidPrices[0]) / 2;
    ladder_ = l;
    return true;
}

bool SkewCalculator::calculateSkewedLadder() {
    PriceLadder* l = midCalculator_4->ladder_;
    for (int i = 0; i < 5; i++) { l->bidPrices[i] += skew_; }
    for (int i = 0; i < 5; i++) { l->askPrices[i] += skew_; }
    return true;
}
void SkewCalculator::setSkew(int32_t skew) { skew_ = skew; }
void SkewCalculator::setLevels(int32_t) { }
void SkewCalculator::setPriceDistributor(PriceDistributor*) { }
bool SkewCalculator::newPriceLadder(PriceLadder*) { return false; }

bool LevelsCalculator::calculateLevelsForLadder() {
    PriceLadder* l = skewCalculator_3->midCalculator_4->ladder_;
    for (int i = maxLevels_; i < 5; i++) { l->bidPrices[i] = 0; l->bidSizes[i] = 0; }
    for (int i = maxLevels_; i < 5; i++) { l->askPrices[i] = 0; l->askSizes[i] = 0; }
    return true;
}
void LevelsCalculator::setLevels(int32_t levels) { maxLevels_ = levels; }
void LevelsCalculator::setSkew(int32_t) { }
void LevelsCalculator::setPriceDistributor(PriceDistributor*) { }
bool LevelsCalculator::newPriceLadder(PriceLadder*) { return false; }

bool PriceLadderPublisher::publishPriceLadder() {
    distributor_->setPriceLadder(levelsCalculator_2->skewCalculator_3->midCalculator_4->ladder_);
    return true;
}
void PriceLadderPublisher::setPriceDistributor(PriceDistributor* d) { distributor_ = d; }
void PriceLadderPublisher::setSkew(int32_t) { }
void PriceLadderPublisher::setLevels(int32_t) { }
bool PriceLadderPublisher::newPriceLadder(PriceLadder*) { return false; }

}  // namespace com::pl::gen

// java.util.Random reproduced exactly, so both languages see identical input.
struct JavaRandom {
    uint64_t seed;
    explicit JavaRandom(uint64_t s) : seed((s ^ 0x5DEECE66DULL) & ((1ULL << 48) - 1)) {}
    int32_t next(int bits) {
        seed = (seed * 0x5DEECE66DULL + 0xBULL) & ((1ULL << 48) - 1);
        return (int32_t)(seed >> (48 - bits));
    }
    int32_t nextInt(int32_t bound) {
        int32_t r = next(31), m = bound - 1;
        if ((bound & m) == 0) { return (int32_t)(((int64_t)bound * (int64_t)r) >> 31); }
        for (int32_t u = r; u - (r = u % bound) + m < 0; u = next(31)) {}
        return r;
    }
};

static com::pl::gen::PriceLadder ladders[10000];

static void buildLadders() {
    JavaRandom r(20260909ULL);
    for (int i = 0; i < 10000; i++) {
        auto& l = ladders[i];
        l.bidPrices[0] = 1240 - (r.nextInt(100) + 10);
        for (int k = 1; k < 5; k++) { l.bidPrices[k] = l.bidPrices[k-1] - (r.nextInt(100) + 10); }
        l.askPrices[0] = 1380 + (r.nextInt(100) + 10);
        for (int k = 1; k < 5; k++) { l.askPrices[k] = l.askPrices[k-1] + (r.nextInt(100) + 10); }
        for (int k = 0; k < 5; k++) { l.bidSizes[k] = 100 + r.nextInt(100); l.askSizes[k] = 100 + r.nextInt(100); }
    }
}

static int64_t run(int64_t n) {
    using namespace com::pl::gen;
    PlProcessor p;                       // constructed INSIDE the measured method, as the Java arms are
    PriceDistributor distributor;
    p.init();
    p.setPriceDistributor(&distributor);
    p.setSkew(10);
    p.setLevels(4);
    int64_t sum = 0;
    for (int64_t i = 0; i < n; i++) {
        PriceLadder* in = &ladders[i % 10000];
        p.newPriceLadder(in);
        PriceLadder* out = distributor.getPriceLadder();
        sum += out->bidPrices[0] + out->askPrices[4];
    }
    return sum;
}

int main(int argc, char** argv) {
    int64_t warm = 500000, iters = 3000000;
    for (int i = 1; i < argc; i++) {
        if (sscanf(argv[i], "-Dwarm=%lld", (long long*)&warm) == 1) continue;
        if (sscanf(argv[i], "-Diters=%lld", (long long*)&iters) == 1) continue;
    }
    buildLadders();
    run(warm);
    auto t0 = std::chrono::steady_clock::now();
    int64_t v = run(iters);
    double ns = std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - t0).count();
    printf("RESULT harness=h5 rt:cppgen graph=priceladder arm=cpp-generated audit=none clock=n/a "
           "%8.3f ns %7.2f Mmsg/s recPerEvent=0.000 avgRecBytes=n/a allocB=0.000 v=%lld\n",
           ns / iters, 1e9 / (ns / iters) / 1e6, (long long) v);
    return 0;
}
