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
 * Keputusan inferens Stage 2 (Visual + Domain Frekuensi).
 *
 * @property aiProbability Skor kebarangkalian kandungan dijana AI [0.0f .. 1.0f]
 * @property isAiDetected True jika aiProbability melepasi ambang (default >= 0.85f)
 * @property highFrequencyEnergyRatio Nisbah tenaga frekuensi tinggi daripada spektrum 2D FFT
 * @property inferenceTimeMs Masa pemprosesan keseluruhan Stage 2 dalam milisaat
 */
data class Stage2DetectionResult(
    val aiProbability: Float,
    val isAiDetected: Boolean,
    val highFrequencyEnergyRatio: Float,
    val inferenceTimeMs: Long
)

/**
 * Stage 2 Inference Engine:
 * Menguruskan pemuatan dan inferens model TensorFlow Lite (edgeguard_model.tflite)
 * dengan pengesanan dinamik bagi bilangan serta dimensi tensor input (RGB dan/atau 2D FFT).
 */
class EdgeGuardTfliteRunner(
    context: Context,
    private val modelAssetName: String = DEFAULT_MODEL_ASSET,
    private val aiThreshold: Float = DEFAULT_AI_THRESHOLD,
    private val rgbNormalizationMode: RgbNormalizationMode = RgbNormalizationMode.RAW_0_TO_255,
    private val fakeClassIndex: Int = 1,
    private val invertSigmoidOutput: Boolean = false
) : AutoCloseable {

    companion object {
        private const val TAG = "EdgeGuardTfliteRunner"
        const val DEFAULT_MODEL_ASSET = "edgeguard_model.tflite"
        const val DEFAULT_AI_THRESHOLD = 0.85f
    }

    private val preprocessor = TikTokFramePreprocessor()
    private val interpreter: Interpreter
    private val inputTensorCount: Int
    private val inputShapes: List<IntArray>
    private val outputShape: IntArray

    private var cachedFftExtractor: FftSpectrumExtractor? = null

    init {
        val modelBuffer = loadAssetModelBuffer(context, modelAssetName)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelBuffer, options)

        inputTensorCount = interpreter.inputTensorCount
        inputShapes = (0 until inputTensorCount).map { idx ->
            interpreter.getInputTensor(idx).shape()
        }
        outputShape = interpreter.getOutputTensor(0).shape()

        Log.i(
            TAG,
            "Model dimuatkan ($modelAssetName) | Bilangan Input: $inputTensorCount | " +
                    "Bentuk Input: ${inputShapes.joinToString { it.contentToString() }} | " +
                    "Bentuk Output: ${outputShape.contentToString()}"
        )
    }

    /**
     * Menjalankan keseluruhan saluran paip Stage 2 pada bingkai skrin TikTok:
     * 1. Memotong kawasan UI TikTok untuk mendapatkan kawasan video bersih.
     * 2. Membina input tensor RGB dan/atau spektrum 2D FFT mengikut seni bina model.
     * 3. Menjalankan inferens TFLite dan memulangkan skor kebarangkalian AI.
     */
    fun analyzeFrame(fullScreenBitmap: Bitmap): Stage2DetectionResult {
        val startTime = System.currentTimeMillis()

        val cleanVideoBitmap = preprocessor.extractCleanVideoRegion(fullScreenBitmap)

        var latestHfRatio = 0f
        val inputBuffers = Array<Any>(inputTensorCount) { index ->
            val shape = inputShapes[index]
            val height = if (shape.size >= 4) shape[1] else 224
            val width = if (shape.size >= 4) shape[2] else 224
            val channels = if (shape.size >= 4) shape[3] else 3

            when (channels) {
                3 -> {
                    preprocessor.bitmapToRgbFloatBuffer(
                        bitmap = cleanVideoBitmap,
                        targetWidth = width,
                        targetHeight = height,
                        mode = rgbNormalizationMode
                    )
                }
                1 -> {
                    val extractor = getOrCreateFftExtractor(width)
                    val fftResult = extractor.extractFromBitmap(cleanVideoBitmap)
                    latestHfRatio = fftResult.highFrequencyEnergyRatio
                    extractor.spectrumToByteBuffer(fftResult)
                }
                else -> {
                    throw IllegalStateException(
                        "Saluran input tidak disokong pada tensor[$index]: ${shape.contentToString()}"
                    )
                }
            }
        }

        if (inputShapes.none { it.size >= 4 && it[3] == 1 }) {
            val fallbackExtractor = getOrCreateFftExtractor(FftSpectrumExtractor.DEFAULT_FFT_SIZE)
            latestHfRatio = fallbackExtractor.extractFromBitmap(cleanVideoBitmap).highFrequencyEnergyRatio
        }

        if (cleanVideoBitmap !== fullScreenBitmap) {
            cleanVideoBitmap.recycle()
        }

        val numClasses = if (outputShape.size >= 2) outputShape[1] else 1
        val outputArray = Array(1) { FloatArray(numClasses) }
        val outputs = mutableMapOf<Int, Any>(0 to outputArray)

        interpreter.runForMultipleInputsOutputs(inputBuffers, outputs)

        val rawScores = outputArray[0]
        val aiProbability = when {
            numClasses == 1 -> {
                val p = rawScores[0].coerceIn(0f, 1f)
                if (invertSigmoidOutput) 1.0f - p else p
            }
            fakeClassIndex in rawScores.indices -> rawScores[fakeClassIndex].coerceIn(0f, 1f)
            else -> rawScores.last().coerceIn(0f, 1f)
        }

        val elapsedMs = System.currentTimeMillis() - startTime

        return Stage2DetectionResult(
            aiProbability = aiProbability,
            isAiDetected = aiProbability >= aiThreshold,
            highFrequencyEnergyRatio = latestHfRatio,
            inferenceTimeMs = elapsedMs
        )
    }

    fun getModelSignatureSummary(): String {
        val inputsDesc = inputShapes.mapIndexed { idx, shape ->
            "input[$idx]=${shape.contentToString()}"
        }.joinToString(", ")
        return "$modelAssetName -> Inputs: [$inputsDesc], Output: ${outputShape.contentToString()}"
    }

    private fun getOrCreateFftExtractor(gridSize: Int): FftSpectrumExtractor {
        val current = cachedFftExtractor
        if (current != null && current.gridSize == gridSize) {
            return current
        }
        return FftSpectrumExtractor(gridSize = gridSize).also {
            cachedFftExtractor = it
        }
    }

    private fun loadAssetModelBuffer(context: Context, assetName: String): ByteBuffer {
        return try {
            context.assets.openFd(assetName).use { fd ->
                FileInputStream(fd.fileDescriptor).use { inputStream ->
                    inputStream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aset $assetName dimampatkan dalam APK, menggunakan penimbal terus fallback.")
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }
    }

    override fun close() {
        interpreter.close()
    }
}