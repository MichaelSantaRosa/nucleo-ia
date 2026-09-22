package com.nucleo.ia.core

object NativeBridge {
    init { System.loadLibrary("nucleo_brain") }
    external fun nativeCreate(): Long
    external fun nativeDestroy(h: Long)
    external fun nativeLoadWeights(h: Long, arr: ByteArray): Boolean
    external fun nativeSaveWeights(h: Long): ByteArray
    external fun nativeForward(h: Long, ids: IntArray): FloatArray
    external fun nativeTrainStep(h: Long, ids: IntArray, label: Int, lr: Float)
    external fun nativeWeightBytes(h: Long): Long
}