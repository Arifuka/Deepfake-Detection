package com.example.edgeguard.aidetection

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayWarningManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentOverlayView: View? = null
    private var lastShownTimestampMs = 0L
    private val cooldownMs = 8000L // Elak pop-up muncul berulang kali dalam 8 saat

    private val autoDismissRunnable = Runnable { dismissWarning() }

    fun showAiWarning(detectionSource: String) {
        if (!Settings.canDrawOverlays(appContext)) return

        val now = SystemClock.elapsedRealtime()
        if ((now - lastShownTimestampMs) < cooldownMs) return
        lastShownTimestampMs = now

        mainHandler.post {
            dismissWarning() // Buang amaran lama jika masih terbuka

            val container = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 36, 48, 36)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B71C1C")) // Merah gelap kontras tinggi
                    cornerRadius = 32f
                    setStroke(4, Color.WHITE)
                }
            }

            val titleView = TextView(appContext).apply {
                text = "⚠️ AMARAN KANDUNGAN AI / AI WARNING"
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            }

            val bodyView = TextView(appContext).apply {
                text = "Video ini dikesan dijana oleh AI. Sila semak kesahihan sebelum percaya atau berkongsi.\n" +
                        "(This video appears AI-generated. Please verify before trusting.)\n\n" +
                        "Sumber / Source: $detectionSource"
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(0, 16, 0, 24)
            }

            val closeButton = Button(appContext).apply {
                text = "FAHAM / DISMISS"
                setTextColor(Color.parseColor("#B71C1C"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 20f
                }
                setOnClickListener { dismissWarning() }
            }

            container.addView(titleView)
            container.addView(bodyView)
            container.addView(closeButton)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 140 // Jarak sedikit dari status bar atas
            }

            try {
                windowManager.addView(container, params)
                currentOverlayView = container
                // Tutup automatik selepas 7 saat supaya tidak menghalang skrin terlalu lama
                mainHandler.postDelayed(autoDismissRunnable, 7000L)
            } catch (e: Exception) {
                currentOverlayView = null
            }
        }
    }

    fun dismissWarning() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        currentOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            } finally {
                currentOverlayView = null
            }
        }
    }
}