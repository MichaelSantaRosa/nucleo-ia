#include "brain.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <random>

namespace nucleo {
namespace {

constexpr float INT8_SCALE = 1.0f / 127.0f;

inline int8_t q8(float v) {
    v *= 127.0f;
    if (v > 127.0f) v = 127.0f;
    if (v < -127.0f) v = -127.0f;
    return (int8_t)std::lround(v);
}

inline float dq8(int8_t v) { return (float)v * INT8_SCALE; }

inline float relu(float x) { return x > 0.0f ? x : 0.0f; }

std::mt19937 rng(42u);
float randf() {
    static std::uniform_real_distribution<float> d(-0.05f, 0.05f);
    return d(rng);
}

}  // namespace

struct Brain::Impl {
    std::vector<float> embW;
    std::vector<float> ffUp;
    std::vector<float> ffDown;
    std::vector<float> clsW;
    std::vector<int8_t> embWq;
    std::vector<int8_t> ffUpq;
    std::vector<int8_t> ffDownq;
    std::vector<int8_t> clsWq;
    mutable std::vector<float> actUp;
    mutable std::vector<float> actHid;
    mutable std::vector<float> logits;

    void requantize() {
        embWq.resize(embW.size());
        ffUpq.resize(ffUp.size());
        ffDownq.resize(ffDown.size());
        clsWq.resize(clsW.size());
        for (size_t k = 0; k < embW.size(); k++) embWq[k] = q8(embW[k]);
        for (size_t k = 0; k < ffUp.size(); k++) ffUpq[k] = q8(ffUp[k]);
        for (size_t k = 0; k < ffDown.size(); k++) ffDownq[k] = q8(ffDown[k]);
        for (size_t k = 0; k < clsW.size(); k++) clsWq[k] = q8(clsW[k]);
    }
};

Brain::Brain(const BrainConfig& c) : cfg_(c), p_(new Impl) {
    auto& i = *p_;
    const size_t v = cfg_.vocabSize, e = cfg_.embedDim, f = cfg_.ffDim, h = cfg_.hidden, n = cfg_.numClasses;
    i.embW.resize(v * e);
    i.ffUp.resize(e * f);
    i.ffDown.resize(f * h);
    i.clsW.resize(h * n);
    for (auto& x : i.embW) x = randf();
    for (auto& x : i.ffUp) x = randf();
    for (auto& x : i.ffDown) x = randf();
    for (auto& x : i.clsW) x = randf();
    i.actUp.resize(f);
    i.actHid.resize(h);
    i.logits.resize(n);
    i.requantize();
}

Brain::~Brain() { delete p_; }

size_t Brain::weightBytes() const {
    const auto& i = *p_;
    return 28 + i.embW.size() + i.ffUp.size() + i.ffDown.size() + i.clsW.size();
}

std::vector<uint8_t> Brain::saveWeights() const {
    const auto& i = *p_;
    std::vector<uint8_t> out;
    out.reserve(weightBytes());
    const uint8_t magic[4] = {'N', 'C', 'L', 'R'};
    out.insert(out.end(), magic, magic + 4);
    uint32_t ver = 1;
    out.insert(out.end(), (const uint8_t*)&ver, (const uint8_t*)&ver + 4);
    int32_t dims[5] = {(int32_t)cfg_.vocabSize, (int32_t)cfg_.embedDim, (int32_t)cfg_.ffDim,
                       (int32_t)cfg_.hidden, (int32_t)cfg_.numClasses};
    out.insert(out.end(), (const uint8_t*)dims, (const uint8_t*)dims + 20);
    for (auto& x : i.embWq) out.push_back((uint8_t)x);
    for (auto& x : i.ffUpq) out.push_back((uint8_t)x);
    for (auto& x : i.ffDownq) out.push_back((uint8_t)x);
    for (auto& x : i.clsWq) out.push_back((uint8_t)x);
    return out;
}

bool Brain::loadWeights(const uint8_t* data, size_t len) {
    auto& i = *p_;
    if (!data || len < 28) return false;
    const uint8_t magic[4] = {'N', 'C', 'L', 'R'};
    for (int k = 0; k < 4; k++) if (data[k] != magic[k]) return false;
    uint32_t ver;
    std::memcpy(&ver, data + 4, 4);
    if (ver != 1) return false;
    int32_t dims[5];
    std::memcpy(dims, data + 8, 20);
    if (dims[0] != cfg_.vocabSize || dims[1] != cfg_.embedDim ||
        dims[2] != cfg_.ffDim || dims[3] != cfg_.hidden ||
        dims[4] != cfg_.numClasses) return false;
    const size_t szEmb = (size_t)dims[0] * dims[1];
    const size_t szUp = (size_t)dims[1] * dims[2];
    const size_t szDown = (size_t)dims[2] * dims[3];
    const size_t szCls = (size_t)dims[3] * dims[4];
    if (len < 28 + szEmb + szUp + szDown + szCls) return false;
    size_t off = 28;
    for (size_t k = 0; k < szEmb; k++, off++) i.embWq[k] = (int8_t)data[off];
    for (size_t k = 0; k < szUp; k++, off++) i.ffUpq[k] = (int8_t)data[off];
    for (size_t k = 0; k < szDown; k++, off++) i.ffDownq[k] = (int8_t)data[off];
    for (size_t k = 0; k < szCls; k++, off++) i.clsWq[k] = (int8_t)data[off];
    for (size_t k = 0; k < szEmb; k++) i.embW[k] = dq8(i.embWq[k]);
    for (size_t k = 0; k < szUp; k++) i.ffUp[k] = dq8(i.ffUpq[k]);
    for (size_t k = 0; k < szDown; k++) i.ffDown[k] = dq8(i.ffDownq[k]);
    for (size_t k = 0; k < szCls; k++) i.clsW[k] = dq8(i.clsWq[k]);
    return true;
}

void Brain::forward(const int32_t* ids, float* outScores) const {
    const auto& i = *p_;
    const int E = cfg_.embedDim, F = cfg_.ffDim, H = cfg_.hidden, N = cfg_.numClasses, S = cfg_.seqLen;
    std::vector<float> pool(E, 0.0f);
    int used = 0;
    for (int t = 0; t < S; t++) {
        int id = ids ? ids[t] : 0;
        if (id < 0 || id >= cfg_.vocabSize) id = 0;
        const int8_t* row = &i.embWq[(size_t)id * E];
        for (int d = 0; d < E; d++) pool[d] += dq8(row[d]);
        used++;
    }
    const float invUsed = 1.0f / (float)(used > 0 ? used : 1);
    for (int d = 0; d < E; d++) pool[d] *= invUsed;
    for (int o = 0; o < F; o++) {
        float s = 0.0f;
        const int8_t* row = &i.ffUpq[(size_t)o * E];
        for (int d = 0; d < E; d++) s += dq8(row[d]) * pool[d];
        i.actUp[o] = relu(s);
    }
    for (int o = 0; o < H; o++) {
        float s = 0.0f;
        const int8_t* row = &i.ffDownq[(size_t)o * F];
        for (int d = 0; d < F; d++) s += dq8(row[d]) * i.actUp[d];
        i.actHid[o] = relu(s);
    }
    float mx = -1e9f;
    for (int c = 0; c < N; c++) {
        float s = 0.0f;
        const int8_t* row = &i.clsWq[(size_t)c * H];
        for (int d = 0; d < H; d++) s += dq8(row[d]) * i.actHid[d];
        i.logits[c] = s;
        if (s > mx) mx = s;
    }
    float sum = 0.0f;
    for (int c = 0; c < N; c++) { i.logits[c] = std::exp(i.logits[c] - mx); sum += i.logits[c]; }
    for (int c = 0; c < N; c++) outScores[c] = i.logits[c] / sum;
}

void Brain::trainStep(const int32_t* ids, int label, float lr) {
    auto& i = *p_;
    const int E = cfg_.embedDim, F = cfg_.ffDim, H = cfg_.hidden, N = cfg_.numClasses, S = cfg_.seqLen;
    if (label < 0 || label >= N) label = N - 1;

    std::vector<float> pool(E, 0.f), up(F, 0.f), hid(H, 0.f), lg(N, 0.f);
    std::vector<float> preUp(F, 0.f), preHid(H, 0.f);
    int used = 0;
    for (int t = 0; t < S; t++) {
        int id = ids ? ids[t] : 0;
        if (id < 0 || id >= cfg_.vocabSize) id = 0;
        const float* row = &i.embW[(size_t)id * E];
        for (int d = 0; d < E; d++) pool[d] += row[d];
        used++;
    }
    const float invUsed = 1.0f / (float)(used > 0 ? used : 1);
    for (int d = 0; d < E; d++) pool[d] *= invUsed;

    for (int o = 0; o < F; o++) {
        float s = 0.f;
        const float* row = &i.ffUp[(size_t)o * E];
        for (int d = 0; d < E; d++) s += row[d] * pool[d];
        preUp[o] = s; up[o] = relu(s);
    }
    for (int o = 0; o < H; o++) {
        float s = 0.f;
        const float* row = &i.ffDown[(size_t)o * F];
        for (int d = 0; d < F; d++) s += row[d] * up[d];
        preHid[o] = s; hid[o] = relu(s);
    }
    float mx = -1e9f;
    for (int c = 0; c < N; c++) {
        float s = 0.f;
        const float* row = &i.clsW[(size_t)c * H];
        for (int d = 0; d < H; d++) s += row[d] * hid[d];
        lg[c] = s;
        if (s > mx) mx = s;
    }
    float sum = 0.f;
    for (int c = 0; c < N; c++) { lg[c] = std::exp(lg[c] - mx); sum += lg[c]; }
    for (int c = 0; c < N; c++) lg[c] /= sum;

    std::vector<float> dLg(N, 0.f);
    for (int c = 0; c < N; c++) dLg[c] = lg[c] - (c == label ? 1.f : 0.f);
    std::vector<float> dCls((size_t)H * N, 0.f), dHid(H, 0.f);
    for (int c = 0; c < N; c++)
        for (int h = 0; h < H; h++) {
            dCls[(size_t)c * H + h] = dLg[c] * hid[h];
            dHid[h] += dLg[c] * i.clsW[(size_t)c * H + h];
        }
    std::vector<float> dDown((size_t)H * F, 0.f), dUp(F, 0.f);
    for (int h = 0; h < H; h++) {
        dHid[h] *= (preHid[h] > 0.f ? 1.f : 0.f);
        for (int f = 0; f < F; f++) {
            dDown[(size_t)h * F + f] = dHid[h] * up[f];
            dUp[f] += dHid[h] * i.ffDown[(size_t)h * F + f];
        }
    }
    std::vector<float> dUpW((size_t)F * E, 0.f), dPool(E, 0.f);
    for (int o = 0; o < F; o++) {
        dUp[o] *= (preUp[o] > 0.f ? 1.f : 0.f);
        for (int d = 0; d < E; d++) {
            dUpW[(size_t)o * E + d] = dUp[o] * pool[d];
            dPool[d] += dUp[o] * i.ffUp[(size_t)o * E + d];
        }
    }
    for (size_t k = 0; k < i.clsW.size(); k++) i.clsW[k] -= lr * dCls[k];
    for (size_t k = 0; k < i.ffDown.size(); k++) i.ffDown[k] -= lr * dDown[k];
    for (size_t k = 0; k < i.ffUp.size(); k++) i.ffUp[k] -= lr * dUpW[k];
    for (int t = 0; t < S; t++) {
        int id = ids ? ids[t] : 0;
        if (id < 0 || id >= cfg_.vocabSize) id = 0;
        float* row = &i.embW[(size_t)id * E];
        for (int d = 0; d < E; d++) row[d] -= lr * invUsed * dPool[d];
    }
    i.requantize();
}

}  // namespace nucleo