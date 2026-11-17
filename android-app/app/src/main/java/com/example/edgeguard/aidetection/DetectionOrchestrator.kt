package com.example.edgeguard.aidetection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Laporan keputusan pengesanan bersepadu (Stage 1 + Stage 2) untuk paparan UI / Overlay.
 */
data class DetectionVerdict(
    val isAiContent: Boolean,
    val detectionStage: String,
    val confidenceScore: Float,
    val reasonDetail: String
)

/**
 * Pengawal Aliran Pengesanan Hibrid EdgeGuard:
 * - Stage 1A: Nod teks Accessibility TikTok (0ms - pantas, tanpa OCR)
 * - Stage 1B: Google ML Kit OCR pada kawasan label & tera air (Sora, Veo, Kling, dll.)
 * - Stage 2:  MobileNetV3 (TFLite) + Analisis Spektrum 2D FFT jika Stage 1 tiada label eksplisit
 */
class DetectionOrchestrator(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onDetectionResult: (DetectionVerdict) -> Unit
) : AutoCloseable {

    companion object {
        private const val TAG = "DetectionOrchestrator"
        private const val MIN_SCAN_INTERVAL_MS = 1500L
        private const val HYBRID_VISUAL_THRESHOLD = 0.72f
        private const val HYBRID_FFT_HF_THRESHOLD = 0.35f
    }

    private val ocrScanner = AiLabelOcrScanner()

    // Lazy init supaya fail .tflite hanya dimuatkan apabila Stage 2 diperlukan
    private val tfliteRunner: EdgeGuardTfliteRunner by lazy {
        EdgeGuardTfliteRunner(
            context = context,
            rgbNormalizationMode = RgbNormalizationMode.RAW_0_TO_255,
            invertSigmoidOutput = false
        )
    }

    @Volatile
    private var isProcessing = false
    private var lastProcessedTimestamp = 0L
    private var activeJob: Job? = null

    /**
     * Dipanggil setiap kali bingkai skrin baharu ditangkap oleh CaptureService.
     */
    fun processScreenFrame(fullScreenBitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (isProcessing || (now - lastProcessedTimestamp) < MIN_SCAN_INTERVAL_MS) {
            return
        }

        isProcessing = true
        lastProcessedTimestamp = now

        activeJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                // --- STAGE 1B: Imbasan OCR Pantas (Label TikTok & Tera Air AI) ---
                val ocrResult = ocrScanner.scanForAiLabels(fullScreenBitmap)
                if (ocrResult.isAiDetected) {
                    val keyword = ocrResult.matchedKeyword ?: "AI Label"
                    val verdict = DetectionVerdict(
                        isAiContent = true,
                        detectionStage = "STAGE_1_OCR",
                        confidenceScore = 0.99f,
                        reasonDetail = "Label/Tera Air AI dikesan: '$keyword'"
                    )
                    withContext(Dispatchers.Main) {
                        onDetectionResult(verdict)
                    }
                    return@launch
                }

                // --- STAGE 2: Inferens Visual MobileNetV3 + Spektrum Frekuensi 2D FFT ---
                val stage2Result = tfliteRunner.analyzeFrame(fullScreenBitmap)
                val isHybridTriggered = stage2Result.aiProbability >= HYBRID_VISUAL_THRESHOLD &&
                        stage2Result.highFrequencyEnergyRatio >= HYBRID_FFT_HF_THRESHOLD

                val isFinalAi = stage2Result.isAiDetected || isHybridTriggered

                Log.d(
                    TAG,
                    "Stage 2 selesai dalam ${stage2Result.inferenceTimeMs}ms | " +
                            "Prob=${"%.3f".format(stage2Result.aiProbability)} | " +
                            "FFT_HF=${"%.3f".format(stage2Result.highFrequencyEnergyRatio)} | " +
                            "AI=$isFinalAi"
                )

                val verdict = DetectionVerdict(
                    isAiContent = isFinalAi,
                    detectionStage = if (isHybridTriggered && !stage2Result.isAiDetected) {
                        "STAGE_2_HYBRID_FFT"
                    } else {
                        "STAGE_2_MOBILENET"
                    },
                    confidenceScore = stage2Result.aiProbability,
                    reasonDetail = "Skor Visual: ${(stage2Result.aiProbability * 100).toInt()}%, " +
                            "Tenaga Frekuensi Tinggi FFT: ${(stage2Result.highFrequencyEnergyRatio * 100).toInt()}%"
                )

                withContext(Dispatchers.Main) {
                    onDetectionResult(verdict)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ralat semasa memproses bingkai: ${e.message}", e)
            } finally {
                isProcessing = false
            }
        }
    }

    override fun close() {
        activeJob?.cancel()
        try {
            tfliteRunner.close()
        } catch (e: Exception) {
            Log.w(TAG, "Ralat menutup TFLite interpreter: ${e.message}")
        }
    }
}