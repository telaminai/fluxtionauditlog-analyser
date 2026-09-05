#include <cstdio>
#include <cstdint>
#include <chrono>
#include <cmath>
#include "libfx2.h"
struct HandCpp { double mid=0,spread=0,ewma=0,vol=0,notional=0,exposure=0,charge=0,buffer=0,limit=108000.0;
  int64_t breaches=0,updates=0,n=0;
  inline void onTick(double bid,double ask){ mid=(bid+ask)*0.5; ewma=(n++==0)?mid:0.3*mid+0.7*ewma;
    spread=ask-bid; notional=mid*1000.0-spread; double d=mid-ewma; vol=d<0?-d:d;
    exposure=notional*(1.0+vol*0.001); if(exposure>limit) breaches++; charge=exposure*0.08;
    buffer=charge*1.25; updates++; } };
static inline double ns(){ using namespace std::chrono;
  return (double)duration_cast<nanoseconds>(steady_clock::now().time_since_epoch()).count(); }
int main(){
  const int64_t W=5000000, IT=200000000;
  graal_isolate_t* i=nullptr; graal_isolatethread_t* t=nullptr;
  if(graal_create_isolate(nullptr,&i,&t)!=0){ printf("iso fail\n"); return 1; }
  // interleave all four arms, 5 rounds, so drift cannot favour any one
  for(int r=0;r<5;r++){
    fx_local(t,W);  double a=ns(); fx_local(t,IT);  double vL=(ns()-a)/IT;  double bL=fx_buf(t);
    fx_static(t,W); a=ns(); fx_static(t,IT);        double vS=(ns()-a)/IT;
    fx_hand(t,W);   a=ns(); fx_hand(t,IT);          double vJ=(ns()-a)/IT;  double bJ=fx_buf(t);
    HandCpp h; for(int64_t k=0;k<W;k++) h.onTick(100.0+(k&15),100.5+(k&15));
    a=ns(); for(int64_t k=0;k<IT;k++) h.onTick(100.0+(k&15),100.5+(k&15)); double vC=(ns()-a)/IT;
    printf("RESULT fx_local=%.4f fx_static=%.4f java_hand=%.4f cpp_hand=%.4f  bufFx=%.4f bufC=%.4f match=%s\n",
           vL,vS,vJ,vC,bL,h.buffer,(std::fabs(bL-h.buffer)<1e-9?"Y":"N"));
  }
  graal_tear_down_isolate(t); return 0; }
