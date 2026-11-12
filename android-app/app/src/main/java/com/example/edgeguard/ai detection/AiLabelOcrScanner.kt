package com.example.edgeguard.detection

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class OcrResult(
    val isAiDetected: Boolean,
    val matchedKeyword: String? = null
)

class AiLabelOcrScanner {

    // Reuse a single recognizer instance to prevent memory leaks and instantiation overhead
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val targetKeywords = listOf(
        "ai-generated",
        "creator labeled as ai-generated",
        "contains ai-generated media",
        "dijana oleh ai", // Malay localization target
        "sora", "runway", "kling", "luma", "veo", "capcut ai"
    )

    // Regex ensures #ai is followed by a word boundary (space, punctuation, or end of string)
    // Prevents EdgeGuard from falsely detecting hashtags like #airpods or #airbnb
    private val exactHashtagAiRegex = Regex("(?i)#ai\\b")

    /**
     * Executes the OCR pre-check on specific ROIs on a background thread.
     */
    suspend fun scanForAiLabels(fullScreenBitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val width = fullScreenBitmap.width
        val height = fullScreenBitmap.height

        // 1. Check Bottom 25% First (Highest probability for TikTok/Instagram native labels)
        val bottomCropHeight = (height * 0.25).toInt()
        val bottomCropY = height - bottomCropHeight
        val bottomCrop = Bitmap.createBitmap(fullScreenBitmap, 0, bottomCropY, width, bottomCropHeight)

        val bottomText = scanCropAndRecycle(bottomCrop)
        val bottomMatch = findKeywordMatch(bottomText)
        if (bottomMatch != null) {
            return@withContext OcrResult(isAiDetected = true, matchedKeyword = bottomMatch)
        }

        // 2. If bottom fails, check the 4 corners for tool watermarks (20% of screen size)
        val cornerW = (width * 0.20).toInt()
        val cornerH = (height * 0.20).toInt()

        val corners = listOf(
            Bitmap.createBitmap(fullScreenBitmap, 0, 0, cornerW, cornerH), // Top-Left
            Bitmap.createBitmap(fullScreenBitmap, width - cornerW, 0, cornerW, cornerH), // Top-Right
            Bitmap.createBitmap(fullScreenBitmap, 0, height - cornerH, cornerW, cornerH), // Bottom-Left
            Bitmap.createBitmap(fullScreenBitmap, width - cornerW, height - cornerH, cornerW, cornerH) // Bottom-Right
        )

        // Process corners sequentially. Fast-fail and stop immediately if a match is found.
        for (corner in corners) {
            val cornerText = scanCropAndRecycle(corner)
            val cornerMatch = findKeywordMatch(cornerText)
            if (cornerMatch != null) {
                // Recycle any remaining un-scanned corner bitmaps before returning early
                corners.filter { !it.isRecycled }.forEach { it.recycle() }
                return@withContext OcrResult(isAiDetected = true, matchedKeyword = cornerMatch)
            }
        }

        // No AI labels detected in this frame
        return@withContext OcrResult(isAiDetected = false)
    }

    /**
     * Helper to process the bitmap through ML Kit and guarantee cleanup.
     */
    private suspend fun scanCropAndRecycle(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            visionText.text
        } catch (e: Exception) {
            ""
        } finally {
            // CRITICAL: Prevent memory leaks (OOM) during continuous scrolling
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun findKeywordMatch(extractedText: String): String? {
        if (extractedText.isBlank()) return null

        // 1. Check standard phrases (case-insensitive substring match)
        val phraseMatch = targetKeywords.firstOrNull { keyword ->
            extractedText.contains(keyword, ignoreCase = true)
        }
        if (phraseMatch != null) return phraseMatch

        // 2. Safely check for the #ai hashtag using Regex
        if (exactHashtagAiRegex.containsMatchIn(extractedText)) {
            return "#ai"
        }

        return null
    }
}