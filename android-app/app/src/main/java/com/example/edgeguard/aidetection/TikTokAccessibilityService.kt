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
        if (node == null || depth > 15) return null

        val nodeText = node.text?.toString()?.trim().orEmpty()
        val contentDesc = node.contentDescription?.toString()?.trim().orEmpty()

        for (phrase in targetPhrases) {
            if (nodeText.equals(phrase, ignoreCase = true) ||
                nodeText.contains(phrase, ignoreCase = true) ||
                contentDesc.contains(phrase, ignoreCase = true)
            ) {
                // Abaikan jika teks datang daripada kapsyen panjang atau @username
                if (!nodeText.startsWith("@") && nodeText.length <= 60) {
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
        return try {
            val jsonStr = assets.open("tiktok/phrases.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val exact = root.getJSONArray("exact_phrases")
            List(exact.length()) { idx -> exact.getString(idx) }
        } catch (e: Exception) {
            listOf("ai-generated", "creator labeled as ai-generated", "disclosed by creator as ai-generated", "dijana ai")
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