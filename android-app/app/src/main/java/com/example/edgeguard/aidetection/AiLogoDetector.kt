package com.example.edgeguard.aidetection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Mengesan ikon logo penjana AI tanpa teks pada 4 penjuru video TikTok
 * (bintang Gemini, ikon Grok, Sora, Kling, CapCut AI).
 */
class AiLogoDetector(
    context: Context,
    modelAssetName: String = DEFAULT_LOGO_MODEL,
    private val confidenceThreshold: Float = 0.88f
) : AutoCloseable {

    companion object {
        private const val TAG = "AiLogoDetector"
        const val DEFAULT_LOGO_MODEL = "ai_logo_classifier.tflite"
        private const val INPUT_SIZE = 64

        val LOGO_LABELS = listOf(
            "capcut_ai_icon",
            "clean",
            "gemini_icon",
            "grok_icon",
            "kling_icon",
            "sora_icon"
        )
    }

    private var interpreter: Interpreter? = null
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

    val isModelLoaded: Boolean
        get() = interpreter != null

    init {
        interpreter = tryLoadModel(context, modelAssetName)
    }

    fun detectLogoInCorner(cornerBitmap: Bitmap): String? {
        val tflite = interpreter ?: return null
        if (cornerBitmap.isRecycled) return null

        val scaled = Bitmap.createScaledBitmap(cornerBitmap, INPUT_SIZE, INPUT_SIZE, true)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== cornerBitmap) {
            scaled.recycle()
        }

        inputBuffer.rewind()
        for (px in pixels) {
            inputBuffer.putFloat(((px shr 16) and 0xFF) / 255.0f)
            inputBuffer.putFloat(((px shr 8) and 0xFF) / 255.0f)
            inputBuffer.putFloat((px and 0xFF) / 255.0f)
        }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(LOGO_LABELS.size) }
        tflite.run(inputBuffer, output)

        val scores = output[0]
        var bestIdx = 0
        var bestScore = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIdx = i
            }
        }

        val label = LOGO_LABELS.getOrElse(bestIdx) { "clean" }
        return if (label != "clean" && bestScore >= confidenceThreshold) {
            "$label (${(bestScore * 100).toInt()}%)"
        } else {
            null
        }
    }

    private fun tryLoadModel(context: Context, assetName: String): Interpreter? {
        return try {
            val buffer = try {
                context.assets.openFd(assetName).use { fd ->
                    FileInputStream(fd.fileDescriptor).use { input ->
                        input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                    }
                }
            } catch (_: Exception) {
                val bytes = context.assets.open(assetName).use { it.readBytes() }
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
            }
            Log.i(TAG, "Model pengesan logo ($assetName) berjaya dimuatkan.")
            Interpreter(buffer, Interpreter.Options().setNumThreads(2))
        } catch (_: Exception) {
            Log.i(TAG, "Model $assetName belum ditambah ke assets; imbasan ikon grafik dilangkau.")
            null
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}