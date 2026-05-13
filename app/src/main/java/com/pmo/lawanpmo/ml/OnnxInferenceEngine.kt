package com.pmo.lawanpmo.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.LongBuffer

/**
 * Wrapper ONNX Runtime untuk inferensi model IndoBERTweet INT8.
 *
 * Cara pakai:
 *   val engine = OnnxInferenceEngine(context, ModelType.PTQ)
 *   val result = engine.predict(inputIds, attentionMask)
 *   engine.close()  // panggil saat Activity onDestroy
 *
 * Model diload dari assets/ saat pertama kali dibuat.
 * Load model memakan waktu ~1-3 detik, jangan lakukan di Main thread.
 */
class OnnxInferenceEngine(
    context   : Context,
    modelType : ModelType,
) {
    enum class ModelType(val assetFileName: String, val displayName: String) {
        PTQ("ptq_int8.onnx", "PTQ INT8"),
        QAT("qat_int8.onnx", "QAT INT8"),
        FP32("model_fp32.onnx", "Baseline FP32")
    }

    private val env     : OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session : OrtSession

    val modelName = modelType.displayName

    // Label sesuai CONFIG["id2label"] di penelitian
    private val id2label = mapOf(
        0 to "Cyber-Aggression",
        1 to "Non Cyber-Aggression",
    )

    init {
        val bytes = context.assets.open(modelType.assetFileName).readBytes()
        val opts  = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)    // pakai 4 thread CPU untuk inferensi
            setInterOpNumThreads(1)
        }
        session = env.createSession(bytes, opts)
    }

    data class InferenceResult(
        val label         : String,
        val labelId       : Int,
        val confidence    : Float,   // probabilitas kelas pemenang [0.0 - 1.0]
        val latencyMs     : Float,   // waktu inferensi murni (ms)
    )

    /**
     * Jalankan satu kali inferensi dan ukur latensinya.
     * Panggil dari Dispatchers.Default — jangan dari Main thread.
     */
    fun predict(inputIds: LongArray, attentionMask: LongArray): InferenceResult {
        val shape = longArrayOf(1L, inputIds.size.toLong())

        val tIds  = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds),       shape)
        val tMask = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask),  shape)

        val inputs = mapOf("input_ids" to tIds, "attention_mask" to tMask)

        val t0     = System.nanoTime()
        val output = session.run(inputs)
        val latMs  = (System.nanoTime() - t0) / 1_000_000f

        @Suppress("UNCHECKED_CAST")
        val logits  = (output[0].value as Array<FloatArray>)[0]
        val probs   = softmax(logits)
        val labelId = probs.indices.maxByOrNull { probs[it] } ?: 0

        tIds.close(); tMask.close(); output.close()

        return InferenceResult(
            label      = id2label[labelId] ?: "Unknown",
            labelId    = labelId,
            confidence = probs[labelId],
            latencyMs  = latMs,
        )
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max  = logits.max()
        val exps = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum  = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    fun close() {
        session.close()
        env.close()
    }
}
