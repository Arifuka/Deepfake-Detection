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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.edgeguard.R
import com.example.edgeguard.history.DetectionType
import com.example.edgeguard.history.HistoryRepository
import com.example.edgeguard.settings.AppPrefs
import com.example.edgeguard.settings.LocaleHelper

/**
 * The warning card that appears on top of TikTok (Figma: "Alert" and "Why am I seeing this?").
 *
 *  - DetectionType.FAKE      -> red card   "This video may be fake"
 *  - DetectionType.AI_LABEL  -> yellow card "This video is labeled as AI-generated"
 *  - Tap "Why am I seeing this?" for the explanation page, with 3 things to do.
 *  - Every card shown is also saved to the History list.
 *  - Texts follow the language the user chose (Bahasa Melayu / English).
 *
 * Built with plain Android views (not Compose) because it lives in a service window.
 */
class OverlayWarningManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentOverlayView: View? = null
    private var showingWhyPage = false

    companion object {
        // Shared by every manager (the screen-capture detector and the TikTok label reader),
        // so one video gives one card.
        // Set when a card is shown; cleared when the detectors report the suspicious content is gone.
        @Volatile private var shown = false
        @Volatile private var shownAtMs = 0L
        private const val MAX_HOLD_MS = 3 * 60_000L   // safety: allow a new card after 3 minutes at most
    }

    private class Theme(
        val iconRes: Int, val iconBg: Int,
        val title: Int, val subtitle: Int, val desc: Int, val explain: Int, val advice: List<Int>,
        val titleColor: Int, val subtitleColor: Int, val descColor: Int, val upperTitle: Boolean
    )

    private val fakeTheme = Theme(
        iconRes = R.drawable.ic_alert_triangle, iconBg = Color.parseColor("#F9C9CE"),
        title = R.string.alert_title, subtitle = R.string.alert_fake_subtitle,
        desc = R.string.alert_fake_desc, explain = R.string.explanation_fake_desc,
        advice = listOf(
            R.string.advice_avoid_trusting_video_content,
            R.string.advice_avoid_transferring_money_video,
            R.string.advice_consult_trusted_person_action
        ),
        titleColor = Color.parseColor("#F0525C"), subtitleColor = Color.parseColor("#F0525C"),
        descColor = Color.parseColor("#C25560"), upperTitle = true
    )

    private val labelTheme = Theme(
        iconRes = R.drawable.ic_robot, iconBg = Color.parseColor("#FFF3C4"),
        title = R.string.alert_title, subtitle = R.string.alert_ai_label_subtitle,
        desc = R.string.alert_ai_label_desc, explain = R.string.explanation_ai_label_desc,
        advice = listOf(
            R.string.advice_check_source,
            R.string.advice_treat_as_ad,
            R.string.advice_be_careful_money_info
        ),
        titleColor = Color.parseColor("#F5D76E"), subtitleColor = Color.parseColor("#F5D76E"),
        descColor = Color.parseColor("#B8A15A"), upperTitle = false
    )

    private val navy = Color.parseColor("#0A1A33")
    private val buttonBg = Color.parseColor("#172A47")
    private val link = Color.parseColor("#3B9AE1")

    /** Old name kept so older code still compiles. Treated as a face/frame detection (FAKE). */
    fun showAiWarning(detectionSource: String) = show(DetectionType.FAKE, detectionSource)

    fun show(type: DetectionType, source: String) {
        if (type == DetectionType.SAFE) return
        if (!Settings.canDrawOverlays(appContext)) return

        val now = SystemClock.elapsedRealtime()
        if (shown && (now - shownAtMs) < MAX_HOLD_MS) return   // already warned about this video
        shown = true
        shownAtMs = now

        // Save to History (the list the user sees in the History tab)
        try { HistoryRepository.get(appContext).add(type, source) } catch (_: Exception) {}

        mainHandler.post {
            removeView()
            showingWhyPage = false
            val theme = if (type == DetectionType.FAKE) fakeTheme else labelTheme
            render(theme, why = false)
        }
    }

    /** Called by the detectors when the suspicious content is gone. Ignored while the user is reading the explanation. */
    fun dismissWarning() {
        shown = false   // the suspicious content is gone, so the next one may warn again
        mainHandler.post { if (!showingWhyPage) removeView() }
    }

    // ---------------------------------------------------------------- drawing

    private fun render(theme: Theme, why: Boolean) {
        removeView()
        showingWhyPage = why
        val ctx = LocaleHelper.localized(appContext, AppPrefs.language(appContext) ?: "en")
        fun str(id: Int) = ctx.getString(id)

        val root = object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    this@OverlayWarningManager.removeView(); return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(if (why) 16 else 28), dp(24), dp(16))
            background = GradientDrawable().apply { setColor(navy); cornerRadius = dp(28).toFloat() }
        }

        if (why) {
            // top row: back arrow + title
            val top = FrameLayout(ctx)
            top.addView(TextView(ctx).apply {
                text = "←"; textSize = 26f; setTextColor(link)
                setOnClickListener { render(theme, why = false) }
                setPadding(dp(4), 0, dp(12), 0)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START))
            top.addView(TextView(ctx).apply {
                text = str(R.string.alert_why_title); textSize = 18f; setTextColor(link)
                setTypeface(null, Typeface.BOLD)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            card.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(space(ctx, 12))
        }

        card.addView(iconView(ctx, theme, if (why) 80 else 96))
        card.addView(space(ctx, if (why) 12 else 16))

        if (!why) {
            card.addView(text(ctx, if (theme.upperTitle) str(theme.title).uppercase() else str(theme.title), 26f, theme.titleColor, false))
            card.addView(text(ctx, str(theme.subtitle), 24f, theme.subtitleColor, true))
            card.addView(space(ctx, 8))
            card.addView(text(ctx, str(theme.desc), 18f, theme.descColor, false))
            card.addView(space(ctx, 20))
            card.addView(button(ctx, str(R.string.btn_ok), theme.titleColor) { removeView() })
            card.addView(space(ctx, 14))
            card.addView(TextView(ctx).apply {
                text = str(R.string.btn_why_am_i_seeing_this)
                textSize = 20f; setTextColor(link); setTypeface(null, Typeface.BOLD)
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { render(theme, why = true) }
            })
        } else {
            card.addView(text(ctx, str(theme.explain), 18f, theme.titleColor, false))
            card.addView(space(ctx, 16))
            card.addView(TextView(ctx).apply {
                text = str(R.string.section_what_you_should_do)
                textSize = 18f; setTextColor(theme.titleColor); setTypeface(null, Typeface.BOLD)
                gravity = Gravity.START
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            for (a in theme.advice) {
                card.addView(TextView(ctx).apply {
                    text = "•  " + str(a)
                    textSize = 17f; setTextColor(theme.titleColor)
                    setPadding(dp(8), dp(4), 0, dp(4))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            card.addView(space(ctx, 16))
            card.addView(button(ctx, str(R.string.btn_i_understand_return), theme.titleColor) { removeView() })
        }

        card.addView(space(ctx, 12))
        card.addView(text(ctx, str(R.string.alert_powered_by), 14f, Color.WHITE, false))

        val scroll = ScrollView(ctx).apply { isFillViewport = false; addView(card) }
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ).apply { setMargins(dp(16), dp(48), dp(16), dp(48)) })

        applyFont(ctx, root)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply { dimAmount = 0.6f }

        try {
            windowManager.addView(root, params)
            currentOverlayView = root
        } catch (_: Exception) {
            currentOverlayView = null
        }
    }

    /** Gives every text in the card the font and text size the user chose in Settings. */
    private fun applyFont(ctx: Context, v: View) {
        if (v is TextView) {
            val bold = v.typeface?.isBold == true
            val key = AppPrefs.fontKey(appContext)
            val face = try {
                when (key) {
                    "atkinson" -> ResourcesCompat.getFont(ctx, if (bold) R.font.atkinson_bold else R.font.atkinson_regular)
                    "system" -> if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    else -> ResourcesCompat.getFont(ctx, if (bold) R.font.inter_bold else R.font.inter_regular)
                }
            } catch (_: Exception) { null }
            if (face != null) v.typeface = face
            val scale = AppPrefs.FONT_SCALES[AppPrefs.fontStep(appContext)]
            v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, v.textSize * scale)
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) applyFont(ctx, v.getChildAt(i))
        }
    }

    private fun removeView() {
        currentOverlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        currentOverlayView = null
        showingWhyPage = false
    }

    // ---------------------------------------------------------------- small helpers

    private fun dp(v: Int) = (v * appContext.resources.displayMetrics.density).toInt()

    private fun space(ctx: Context, h: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun iconView(ctx: Context, t: Theme, sizeDp: Int) = ImageView(ctx).apply {
        setImageResource(t.iconRes)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(t.iconBg) }
        setPadding(dp(sizeDp / 4), dp(sizeDp / 4), dp(sizeDp / 4), dp(sizeDp / 4))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun text(ctx: Context, s: String, size: Float, color: Int, bold: Boolean) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun button(ctx: Context, label: String, color: Int, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 22f; setTextColor(color); gravity = Gravity.CENTER
        setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply { setColor(buttonBg); cornerRadius = dp(14).toFloat() }
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
