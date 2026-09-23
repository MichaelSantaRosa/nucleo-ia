package com.nucleo.ia

object NativeBridge {
    init {
        System.loadLibrary("nucleo")
    }

    external fun nativeCreateBrain(
        vocabSize: Int,
        seqLen: Int,
        embedDim: Int,
        ffDim: Int,
        hiddenDim: Int,
        numClasses: Int
    ): Long

    external fun nativeDestroyBrain(handle: Long)

    external fun nativeInfer(handle: Long, ids: IntArray, outScores: FloatArray)

    external fun nativeTrainStep(handle: Long, ids: IntArray, label: Int, lr: Float)

    external fun nativeSaveWeights(handle: Long): ByteArray

    external fun nativeLoadWeights(handle: Long, data: ByteArray): Boolean
}