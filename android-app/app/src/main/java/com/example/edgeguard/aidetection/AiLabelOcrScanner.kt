package com.example.edgeguard.aidetection

import android.content.Context
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

class AiLabelOcrScanner(context: Context? = null) {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val logoDetector: AiLogoDetector? = context?.let { AiLogoDetector(it) }

    private val targetKeywords = listOf(
        // Label Pendedahan Rasmi TikTok (EN & BM + variasi bacaan OCR)
        "contains ai-generated",
        "ai-generated media",
        "ai-generated",
        "ai generated",
        "creator labeled as ai",
        "labeled as ai-generated",
        "dijana oleh ai",
        "dilabelkan sebagai dijana",
        "mengandungi media dijana ai",

        // Tera Air Penjana Video & Imej AI
        "sora",
        "veo",
        "runway",
        "pika.art",
        "kling",
        "luma",
        "dream machine",
        "hailuo",
        "minimax",
        "capcut ai",
        "heygen",
        "synthesia",
        "invideo ai",
        "midjourney",
        "dall-e",
        "gemini",
        "grok"
    )

    private val exactHashtagRegex = Regex(
        "(?i)#(ai|aigenerated|deepfake|soraai|klingai|veo|dijanaai)\\b"
    )
    private val strictWordRegex = Regex("(?i)\\b(pika|veo|sora|grok)\\b")

    suspend fun scanForAiLabels(fullScreenBitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val width = fullScreenBitmap.width
        val height = fullScreenBitmap.height

        // 1. Semak 42% Bahagian Bawah (Merangkumi pill "Contains AI-generated media", kapsyen & #AI)
        val bottomCropHeight = (height * 0.42).toInt().coerceAtLeast(1)
        val bottomCropY = height - bottomCropHeight
        val bottomCrop = Bitmap.createBitmap(fullScreenBitmap, 0, bottomCropY, width, bottomCropHeight)

        // Besarkan 2x supaya fon kecil 11sp pada pill label TikTok dapat dibaca jelas oleh ML Kit
        val upscaledBottom = Bitmap.createScaledBitmap(
            bottomCrop,
            (bottomCrop.width * 2).coerceAtMost(2160),
            (bottomCrop.height * 2).coerceAtMost(2160),
            true
        )
        if (upscaledBottom !== bottomCrop) {
            bottomCrop.recycle()
        }

        val bottomText = scanCropAndRecycle(upscaledBottom)
        findKeywordMatch(bottomText)?.let { match ->
            return@withContext OcrResult(isAiDetected = true, matchedKeyword = match)
        }

        // 2. Semak 4 Penjuru Skrin (22% x 22%) untuk ikon logo grafik & tera air penjuru
        val cornerW = (width * 0.22).toInt().coerceAtLeast(1)
        val cornerH = (height * 0.22).toInt().coerceAtLeast(1)

        val corners = listOf(
            Bitmap.createBitmap(fullScreenBitmap, 0, 0, cornerW, cornerH),
            Bitmap.createBitmap(fullScreenBitmap, width - cornerW, 0, cornerW, cornerH),
            Bitmap.createBitmap(fullScreenBitmap, 0, height - cornerH, cornerW, cornerH),
            Bitmap.createBitmap(fullScreenBitmap, width - cornerW, height - cornerH, cornerW, cornerH)
        )

        for (corner in corners) {
            val detectedLogo = logoDetector?.detectLogoInCorner(corner)
            if (detectedLogo != null) {
                corners.filter { !it.isRecycled }.forEach { it.recycle() }
                return@withContext OcrResult(isAiDetected = true, matchedKeyword = "Logo: $detectedLogo")
            }

            val cornerText = scanCropAndRecycle(corner)
            val cornerMatch = findKeywordMatch(cornerText)
            if (cornerMatch != null) {
                corners.filter { !it.isRecycled }.forEach { it.recycle() }
                return@withContext OcrResult(isAiDetected = true, matchedKeyword = cornerMatch)
            }
        }

        // 3. Semak Jalur Tengah (y = 30% hingga 60%) untuk tera air terapung
        val midY = (height * 0.30).toInt()
        val midH = (height * 0.30).toInt().coerceAtLeast(1)
        val midStrip = Bitmap.createBitmap(fullScreenBitmap, 0, midY, width, midH)

        val midText = scanCropAndRecycle(midStrip)
        findKeywordMatch(midText)?.let { match ->
            return@withContext OcrResult(isAiDetected = true, matchedKeyword = match)
        }

        return@withContext OcrResult(isAiDetected = false)
    }

    private suspend fun scanCropAndRecycle(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            visionText.text
        } catch (_: Exception) {
            ""
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun findKeywordMatch(extractedText: String): String? {
        if (extractedText.isBlank()) return null

        val normalized = extractedText.replace('\n', ' ')

        val phraseMatch = targetKeywords.firstOrNull { keyword ->
            normalized.contains(keyword, ignoreCase = true)
        }
        if (phraseMatch != null) return phraseMatch

        strictWordRegex.find(normalized)?.let { match ->
            return match.value
        }

        if (exactHashtagRegex.containsMatchIn(normalized)) {
            return "#ai"
        }

        return null
    }

    fun close() {
        logoDetector?.close()
    }
}