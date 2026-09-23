#pragma once
#include <cstdint>
#include <cstddef>
#include <vector>

namespace nucleo {

constexpr int VOCAB_SIZE = 512;
constexpr int SEQ_LEN = 16;
constexpr int EMBED_DIM = 32;
constexpr int FF_DIM = 64;
constexpr int HIDDEN = 64;
constexpr int NUM_CLASSES = 8;

struct BrainConfig {
    int vocabSize = VOCAB_SIZE;
    int seqLen = SEQ_LEN;
    int embedDim = EMBED_DIM;
    int ffDim = FF_DIM;
    int hidden = HIDDEN;
    int numClasses = NUM_CLASSES;
};

class Brain {
public:
    explicit Brain(const BrainConfig& cfg = {});
    ~Brain();
    bool loadWeights(const uint8_t* data, size_t len);
    std::vector<uint8_t> saveWeights() const;
    void forward(const int32_t* ids, float* outScores) const;
    void trainStep(const int32_t* ids, int label, float lr);
    size_t weightBytes() const;

private:
    struct Impl;
    Impl* p_;
    BrainConfig cfg_;
};

}  // namespace nucleo