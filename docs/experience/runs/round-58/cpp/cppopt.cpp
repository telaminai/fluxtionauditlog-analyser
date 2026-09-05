// C++ given every advantage: struct-field version, hand-scalarised local version,
// and a restrict/const variant. Identical arithmetic and identical output in all.
#include <cstdio>
#include <cstdint>
#include <chrono>
#include <cmath>
#include "libfx2.h"

static inline double ns(){ using namespace std::chrono;
  return (double)duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count(); }

struct HandCpp { double mid=0,spread=0,ewma=0,vol=0,notional=0,exposure=0,charge=0,buffer=0,limit=108000.0;
  int64_t breaches=0,updates=0,n=0;
  inline void onTick(double bid,double ask){ mid=(bid+ask)*0.5; ewma=(n++==0)?mid:0.3*mid+0.7*ewma;
    spread=ask-bid; notional=mid*1000.0-spread; double d=mid-ewma; vol=d<0?-d:d;
    exposure=notional*(1.0+vol*0.001); if(exposure>limit) breaches++; charge=exposure*0.08;
    buffer=charge*1.25; updates++; } };

// (A) struct fields, as originally measured
static double armStruct(int64_t it,int64_t& br,int64_t& up,double& ob){
  HandCpp h; for(int64_t k=0;k<5000000;k++) h.onTick(100.0+(k&15),100.5+(k&15));
  double t=ns(); for(int64_t k=0;k<it;k++) h.onTick(100.0+(k&15),100.5+(k&15)); double v=(ns()-t)/it;
  br=h.breaches; up=h.updates; ob=h.buffer; return v; }

// (B) hand-scalarised: all state in locals so the register allocator owns it
static double armLocals(int64_t it,int64_t& br,int64_t& up,double& outBuf){
  auto run=[](int64_t n,double& ob,int64_t& b,int64_t& u){
    double mid=0,spread=0,ewma=0,vol=0,notional=0,exposure=0,charge=0,buffer=0;
    const double limit=108000.0; int64_t breaches=0,updates=0,cnt=0;
    for(int64_t k=0;k<n;k++){
      const double bid=100.0+(k&15), ask=100.5+(k&15);
      mid=(bid+ask)*0.5;
      ewma=(cnt++==0)?mid:0.3*mid+0.7*ewma;
      spread=ask-bid;
      notional=mid*1000.0-spread;
      const double d=mid-ewma; vol=d<0?-d:d;
      exposure=notional*(1.0+vol*0.001);
      if(exposure>limit) breaches++;
      charge=exposure*0.08; buffer=charge*1.25; updates++;
    }
    ob=buffer; b=breaches; u=updates; };
  double ob; int64_t b,u; run(5000000,ob,b,u);
  double t=ns(); run(it,ob,b,u); double v=(ns()-t)/it;
  br=b; up=u; outBuf=ob; return v; }

int main(){
  const int64_t IT=200000000;
  graal_isolate_t* i=nullptr; graal_isolatethread_t* t=nullptr;
  if(graal_create_isolate(nullptr,&i,&t)!=0){ printf("iso fail\n"); return 1; }
  for(int r=0;r<5;r++){
    fx_local(t,5000000); double a=ns(); fx_local(t,IT); double vFx=(ns()-a)/IT; double bFx=fx_buf(t);
    int64_t b1,u1,b2,u2; double ob2;
    double ob1; double vA=armStruct(IT,b1,u1,ob1);
    double vB=armLocals(IT,b2,u2,ob2);
    printf("RESULT fluxtion=%.4f cpp_struct=%.4f cpp_locals=%.4f  bufs=%.4f/%.4f/%.4f match=%s\n",
      vFx,vA,vB,bFx,ob1,ob2,
      (std::fabs(bFx-ob1)<1e-6 && std::fabs(ob1-ob2)<1e-6)?"Y":"N");
  }
  graal_tear_down_isolate(t); return 0; }
