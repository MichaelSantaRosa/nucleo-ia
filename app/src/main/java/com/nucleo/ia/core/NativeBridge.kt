package com.nucleo.ia.core

internal object NativeBridge {
    init {
        System.loadLibrary("nucleo_brain")
    }

    external fun nativeCreateBrain(
        vocabSize: Int,
        seqLen: Int,
        embedDim: Int,
        ffDim: Int,
        hidden: Int,
        numClasses: Int
    ): Long

    external fun nativeInfer(
        handle: Long,
        tokenIds: IntArray,
        scores: FloatArray
    )

    external fun nativeTrainStep(
        handle: Long,
        tokenIds: IntArray,
        target: Int,
        learningRate: Float
    )

    external fun nativeDestroyBrain(handle: Long)

    external fun nativeSaveWeights(handle: Long, path: String): Boolean

    external fun nativeLoadWeights(handle: Long, path: String): Boolean
}