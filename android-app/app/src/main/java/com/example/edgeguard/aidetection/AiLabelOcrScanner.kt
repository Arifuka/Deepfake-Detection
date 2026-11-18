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

    // Optional: Pengesan ikon grafik (bintang Gemini, Grok, Sora, Kling) jika model .tflite wujud
    private val logoDetector: AiLogoDetector? = context?.let { AiLogoDetector(it) }

    private val targetKeywords = listOf(
        // 1. Label Pendedahan Rasmi TikTok (EN & BM)
        "ai-generated",
        "creator labeled as ai-generated",
        "contains ai-generated media",
        "dijana oleh ai",
        "dilabelkan sebagai dijana oleh ai",
        "mengandungi media dijana ai",

        // 2. Tera Air Penjana Video & Imej AI (yang sering di-repost ke TikTok)
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

    // Regex ketat dengan sempadan perkataan (\b) supaya tidak tersalah kesan #airpods, #aina, #pakaian
    private val exactHashtagRegex = Regex(
        "(?i)#(ai|aigenerated|deepfake|soraai|klingai|veo|dijanaai)\\b"
    )

    // Perkataan pendek berisiko tinggi dipadankan sebagai perkataan penuh sahaja
    private val strictWordRegex = Regex("(?i)\\b(pika|veo|sora|grok)\\b")

    /**
     * Mengimbas zon label TikTok (bawah 25%), 4 penjuru (ikon logo & tera air),
     * serta jalur tepi tengah (tera air terapung) dalam video TikTok.
     */
    suspend fun scanForAiLabels(fullScreenBitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val width = fullScreenBitmap.width
        val height = fullScreenBitmap.height

        // 1. Semak 25% Bahagian Bawah (Kapsyen & label natif TikTok)
        val bottomCropHeight = (height * 0.25).toInt().coerceAtLeast(1)
        val bottomCropY = height - bottomCropHeight
        val bottomCrop = Bitmap.createBitmap(fullScreenBitmap, 0, bottomCropY, width, bottomCropHeight)

        val bottomText = scanCropAndRecycle(bottomCrop)
        findKeywordMatch(bottomText)?.let { match ->
            return@withContext OcrResult(isAiDetected = true, matchedKeyword = match)
        }

        // 2. Semak 4 Penjuru Skrin (22% x 22%) untuk tera air teks & ikon logo (Gemini, Grok, dll.)
        val cornerW = (width * 0.22).toInt().coerceAtLeast(1)
        val cornerH = (height * 0.22).toInt().coerceAtLeast(1)

        val corners = listOf(
            Bitmap.createBitmap(fullScreenBitmap, 0, 0, cornerW, cornerH), // Atas-Kiri
            Bitmap.createBitmap(fullScreenBitmap, width - cornerW, 0, cornerW, cornerH), // Atas-Kanan
            Bitmap.createBitmap(fullScreenBitmap, 0, height - cornerH, cornerW, cornerH), // Bawah-Kiri
            Bitmap.createBitmap(fullScreenBitmap, width - cornerW, height - cornerH, cornerW, cornerH) // Bawah-Kanan
        )

        for (corner in corners) {
            // 2A. Semak ikon logo grafik dahulu (jika ai_logo_classifier.tflite sudah ditambah)
            val detectedLogo = logoDetector?.detectLogoInCorner(corner)
            if (detectedLogo != null) {
                corners.filter { !it.isRecycled }.forEach { it.recycle() }
                return@withContext OcrResult(isAiDetected = true, matchedKeyword = "Logo: $detectedLogo")
            }

            // 2B. Semak teks OCR pada penjuru yang sama (scanCropAndRecycle mengitar semula bitmap)
            val cornerText = scanCropAndRecycle(corner)
            val cornerMatch = findKeywordMatch(cornerText)
            if (cornerMatch != null) {
                corners.filter { !it.isRecycled }.forEach { it.recycle() }
                return@withContext OcrResult(isAiDetected = true, matchedKeyword = cornerMatch)
            }
        }

        // 3. Semak Jalur Tengah (y = 35% hingga 65%) untuk tera air terapung dalam video TikTok
        val midY = (height * 0.35).toInt()
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

        val phraseMatch = targetKeywords.firstOrNull { keyword ->
            extractedText.contains(keyword, ignoreCase = true)
        }
        if (phraseMatch != null) return phraseMatch

        strictWordRegex.find(extractedText)?.let { match ->
            return match.value
        }

        if (exactHashtagRegex.containsMatchIn(extractedText)) {
            return "#ai"
        }

        return null
    }

    fun close() {
        logoDetector?.close()
    }
}