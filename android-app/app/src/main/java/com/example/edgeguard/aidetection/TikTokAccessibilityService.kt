package com.example.edgeguard.aidetection

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class TikTokAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayWarningManager
    private var targetPhrases: List<String> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayWarningManager(this)
        targetPhrases = loadTargetPhrases()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // Pantau aplikasi TikTok sahaja (SEA/Malaysia & Global)
        if (pkg != "com.ss.android.ugc.trill" && pkg != "com.zhiliaoapp.musically") return

        val rootNode = rootInActiveWindow ?: return
        val matchedLabel = findAiLabelInTree(rootNode, depth = 0)
        if (matchedLabel != null) {
            overlayManager.showAiWarning("Label Rasmi TikTok: \"$matchedLabel\"")
        }
    }

    private fun findAiLabelInTree(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 25) return null

        val nodeText = node.text?.toString()?.trim().orEmpty()
        val contentDesc = node.contentDescription?.toString()?.trim().orEmpty()
        val combinedText = "$nodeText $contentDesc".trim()

        if (combinedText.isNotEmpty() && !combinedText.startsWith("@") && combinedText.length <= 80) {
            for (phrase in targetPhrases) {
                if (combinedText.contains(phrase, ignoreCase = true)) {
                    return phrase
                }
            }
        }

        for (i in 0 until node.childCount) {
            val match = findAiLabelInTree(node.getChild(i), depth + 1)
            if (match != null) return match
        }
        return null
    }

    private fun loadTargetPhrases(): List<String> {
        val defaultPhrases = listOf(
            "contains ai-generated media",
            "contains ai-generated",
            "ai-generated",
            "creator labeled as ai-generated",
            "disclosed by creator as ai-generated",
            "dijana oleh ai",
            "dijana ai",
            "mengandungi media dijana ai"
        )
        return try {
            val jsonStr = assets.open("tiktok/phrases.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val exact = root.getJSONArray("exact_phrases")
            val fromJson = List(exact.length()) { idx -> exact.getString(idx) }
            (fromJson + defaultPhrases).distinct()
        } catch (e: Exception) {
            defaultPhrases
        }
    }

    override fun onInterrupt() {
        if (::overlayManager.isInitialized) {
            overlayManager.dismissWarning()
        }
    }

    override fun onDestroy() {
        if (::overlayManager.isInitialized) {
            overlayManager.dismissWarning()
        }
        super.onDestroy()
    }
}