#include "brain.h"
#include <cmath>
#include <cstring>
#include <random>
#include <algorithm>
namespace nucleo {
namespace {
inline float relu(float x){ return x>0.f?x:0.f; }
constexpr float INT8_SCALE = 1.0f/127.0f;
inline int8_t q8(float v){ v*=127.f; return (int8_t)std::max(-127.f,std::min(127.f,v)); }
inline float  dq8(int8_t v){ return v*INT8_SCALE; }
std::mt19937 rng(42u);
float randf(){ std::uniform_real_distribution<float> d(-0.05f,0.05f); return d(rng); }
}
struct Brain::Impl {
    std::vector<float>  embW;
    std::vector<int8_t> embWq;
    std::vector<float>  ffUp, ffDown, clsW;
};
Brain::Brain(const BrainConfig& c):cfg_(c),p_(new Impl){
    auto& i=*p_;
    size_t v=cfg_.vocabSize,e=cfg_.embedDim,f=cfg_.ffDim,h=cfg_.hidden,n=cfg_.numClasses;
    i.embW.resize(v*e); i.embWq.resize(v*e);
    i.ffUp.resize(e*f); i.ffDown.resize(f*h); i.clsW.resize(h*n);
    for(auto&x:i.embW)x=randf();
    for(auto&x:i.ffUp)x=randf(); for(auto&x:i.ffDown)x=randf(); for(auto&x:i.clsW)x=randf();
    for(size_t k=0;k<i.embW.size();++k) i.embWq[k]=q8(i.embW[k]);
}
Brain::~Brain(){ delete p_; }
size_t Brain::weightBytes() const{
    size_t v=cfg_.vocabSize,e=cfg_.embedDim,f=cfg_.ffDim,h=cfg_.hidden,n=cfg_.numClasses;
    return v*e + e*f + f*h + h*n;
}
bool Brain::loadWeights(const uint8_t* d,size_t len){
    if(len<weightBytes()) return false;
    auto& i=*p_;
    size_t off=0,v=cfg_.vocabSize,e=cfg_.embedDim,f=cfg_.ffDim,h=cfg_.hidden,n=cfg_.numClasses;
    auto rd=[&](std::vector<float>&dst,size_t cnt){
        for(size_t k=0;k<cnt;++k) dst[k]=dq8((int8_t)d[off++]);
    };
    rd(i.embW,v*e);
    for(size_t k=0;k<v*e;++k) i.embWq[k]=q8(i.embW[k]);
    rd(i.ffUp,e*f); rd(i.ffDown,f*h); rd(i.clsW,h*n);
    return true;
}
std::vector<uint8_t> Brain::saveWeights() const{
    auto& i=*p_;
    size_t v=cfg_.vocabSize,e=cfg_.embedDim,f=cfg_.ffDim,h=cfg_.hidden,n=cfg_.numClasses;
    std::vector<uint8_t> out; out.reserve(weightBytes());
    auto wr=[&](const std::vector<float>&src,size_t cnt){
        for(size_t k=0;k<cnt;++k) out.push_back((uint8_t)(q8(src[k])&0xFF));
    };
    wr(i.embW,v*e); wr(i.ffUp,e*f); wr(i.ffDown,f*h); wr(i.clsW,h*n);
    return out;
}
void Brain::forward(const int32_t* ids,float* outScores) const{
    auto& i=*p_;
    const int S=cfg_.seqLen,E=cfg_.embedDim,F=cfg_.ffDim,H=cfg_.hidden,N=cfg_.numClasses;
    std::vector<float> emb(S*E,0.f);
    for(int t=0;t<S;++t){
        int id=ids[t]; if(id<0||id>=cfg_.vocabSize) id=0;
        const int8_t* row=&i.embWq[(size_t)id*E];
        for(int d=0;d<E;++d) emb[t*E+d]=dq8(row[d]);
    }
    std::vector<float> pool(E,0.f);
    for(int d=0;d<E;++d) for(int t=0;t<S;++t) pool[d]+=emb[t*E+d];
    for(int d=0;d<E;++d) pool[d]/=S;
    std::vector<float> h(F,0.f);
    for(int o=0;o<F;++o){ float x=0; for(int j=0;j<E;++j)x+=pool[j]*i.ffUp[o*E+j]; h[o]=relu(x); }
    std::vector<float> hid(H,0.f);
    for(int o=0;o<H;++o){ float x=0; for(int j=0;j<F;++j)x+=h[j]*i.ffDown[j*H+o]; hid[o]=relu(x); }
    float sc[N]; float mx=-1e9f;
    for(int c=0;c<N;++c){ float x=0; for(int j=0;j<H;++j)x+=hid[j]*i.clsW[c*H+j]; sc[c]=x; mx=std::max(mx,x); }
    float sum=0; for(int c=0;c<N;++c){ sc[c]=std::exp(sc[c]-mx); sum+=sc[c]; }
    for(int c=0;c<N;++c) outScores[c]=sc[c]/sum;
}
void Brain::trainStep(const int32_t* ids,int label,float lr){
    auto& i=*p_;
    const int S=cfg_.seqLen,E=cfg_.embedDim,F=cfg_.ffDim,H=cfg_.hidden,N=cfg_.numClasses;
    float scores[N]; forward(ids,scores);
    float g[N]; for(int c=0;c<N;++c) g[c]=scores[c]-(c==label?1.f:0.f);
    std::vector<float> emb(S*E,0.f);
    for(int t=0;t<S;++t){int id=ids[t];if(id<0||id>=cfg_.vocabSize)id=0;
        const int8_t*row=&i.embWq[(size_t)id*E];for(int d=0;d<E;++d)emb[t*E+d]=dq8(row[d]);}
    std::vector<float> pool(E,0.f);
    for(int d=0;d<E;++d)for(int t=0;t<S;++t)pool[d]+=emb[t*E+d];
    for(int d=0;d<E;++d)pool[d]/=S;
    std::vector<float> hh(F,0.f);
    for(int o=0;o<F;++o){float x=0;for(int j=0;j<E;++j)x+=pool[j]*i.ffUp[o*E+j];hh[o]=relu(x);}
    std::vector<float> hid(H,0.f);
    for(int o=0;o<H;++o){float x=0;for(int j=0;j<F;++j)x+=hh[j]*i.ffDown[j*H+o];hid[o]=relu(x);}
    for(int c=0;c<N;++c)for(int j=0;j<H;++j) i.clsW[c*H+j]-=lr*g[c]*hid[j];
    for(int t=0;t<S;++t){int id=ids[t];if(id<0||id>=cfg_.vocabSize)continue;
        for(int d=0;d<E;++d){
            i.embW[(size_t)id*E+d]+=lr*g[label]*0.5f*emb[t*E+d];
            i.embWq[(size_t)id*E+d]=q8(i.embW[(size_t)id*E+d]);
        }}
}
}