package com.example.edgeguard.history
/**
 * The three result types in the Figma design:
 *  - FAKE      (red)    the model says "may be fake"
 *  - AI_LABEL  (yellow) TikTok / the creator marked the video as AI-generated
 *  - SAFE      (green)  no sign of AI found (this does NOT prove the video is real)
 */
enum class DetectionType { FAKE, AI_LABEL, SAFE }

/**
 * One row in "Video detection history".
 *
 * @param source short code for WHY, e.g. "tiktok_label", "ocr_label", "model".
 *               Used later for the "Why am I seeing this?" page and for the report.
 */
data class DetectionRecord(
    val id: Long,
    val timeMillis: Long,
    val type: DetectionType,
    val source: String
)
