package com.example.edgeguard.detection

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetectionOrchestrator(
    private val scope: CoroutineScope,
    private val uiStateCallback: (Boolean, String) -> Unit // Callback to trigger your UI pop-up
) {
    private val ocrScanner = AiLabelOcrScanner()

    // Flag to drop incoming frames if the system is still busy processing the previous one
    @Volatile
    private var isProcessingFrame = false

    /**
     * Entry point for frames captured via MediaProjection.
     */
    fun onFrameCaptured(frame: Bitmap) {
        if (isProcessingFrame) {
            // Drop frame to prevent backpressure and OOM exceptions
            frame.recycle()
            return
        }

        isProcessingFrame = true

        scope.launch(Dispatchers.Default) {
            try {
                // Step 1: Fast-Pass OCR Check
                val ocrResult = ocrScanner.scanForAiLabels(frame)

                if (ocrResult.isAiDetected) {
                    // YES: Trigger bilingual pop-up and SKIP Deep Learning process
                    triggerWarningUi(ocrResult.matchedKeyword ?: "AI Label")
                    return@launch
                }

                // Step 2: NO -> Fallback to Deep Learning inference
                val dlResult = runDeepLearningDetection(frame)

                if (dlResult) {
                    triggerWarningUi("Visual Model (MobileNetV3)")
                }

            } finally {
                // Step 3: Absolute guarantee that the root frame is recycled
                if (!frame.isRecycled) {
                    frame.recycle()
                }
                isProcessingFrame = false
            }
        }
    }

    /**
     * Fallback inference process using your LiteRT/TensorFlow Lite model.
     */
    private suspend fun runDeepLearningDetection(bitmap: Bitmap): Boolean = withContext(Dispatchers.Default) {
        // TODO: Pass Bitmap to your LiteRT/TFLite MobileNetV3 model pipeline here.
        // Return 'true' if a visual deepfake is detected.

        // Example:
        // val tensorImage = TensorImage.fromBitmap(bitmap)
        // return model.process(tensorImage).probability > 0.85f
        false
    }

    /**
     * Dispatch signal to the Main Thread to trigger the UI.
     */
    private suspend fun triggerWarningUi(source: String) = withContext(Dispatchers.Main) {
        // Dispatch signal to trigger the Alert / Overlay specifically for elderly users
        uiStateCallback(true, source)
    }
}