package com.example.edgeguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edgeguard.R
import com.example.edgeguard.history.DetectionRecord
import com.example.edgeguard.history.DetectionType
import com.example.edgeguard.history.HistoryRepository
import com.example.edgeguard.history.fakeCount
import com.example.edgeguard.history.watchedCount
import com.example.edgeguard.settings.AppPrefs
import com.example.edgeguard.ui.theme.EdgeGuardColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * History tab (Figma "Video detection history").
 *
 *  Tabs: All | Fake | AI Label | Safe
 *   - All       : "last 3 months" numbers, then one row per month. Tap a month to see its days.
 *                 Tap a result to read the advice for that kind of result.
 *   - Fake / AI Label / Safe : the advice page for that kind of result (with Read aloud).
 */
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val records by HistoryRepository.get(ctx).records.collectAsState()

    var filter by rememberSaveable { mutableIntStateOf(0) }          // 0 All, 1 Fake, 2 AI Label, 3 Safe
    var openMonth by rememberSaveable { mutableIntStateOf(-1) }      // year*12+month, -1 = none
    var openType by rememberSaveable { mutableIntStateOf(-1) }       // DetectionType ordinal opened from a row

    // Android back button goes up one level
    BackHandler(enabled = openType >= 0) { openType = -1 }
    BackHandler(enabled = openType < 0 && openMonth >= 0 && filter == 0) { openMonth = -1 }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EdgeGuardLogo(size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.setting_video_detection_history),
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = EdgeGuardColors.TextPrimary
            )
        }
        Spacer(Modifier.height(16.dp))

        FilterTabs(selected = filter, onSelect = { filter = it; openType = -1; openMonth = -1 })
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                openType >= 0 -> AdvicePage(
                    type = DetectionType.entries[openType],
                    onBack = { openType = -1 }
                )
                filter == 1 -> AdvicePage(DetectionType.FAKE, onBack = null)
                filter == 2 -> AdvicePage(DetectionType.AI_LABEL, onBack = null)
                filter == 3 -> AdvicePage(DetectionType.SAFE, onBack = null)
                openMonth >= 0 -> MonthPage(
                    records = records,
                    monthKey = openMonth,
                    onBack = { openMonth = -1 },
                    onOpenType = { openType = it.ordinal }
                )
                else -> SummaryPage(records, onOpenMonth = { openMonth = it })
            }
        }
    }
}

// ------------------------------------------------------------------ tabs

@Composable
private fun FilterTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        R.string.filter_all, R.string.filter_fake, R.string.filter_ai_label, R.string.filter_safe
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(EdgeGuardColors.Surface)
            .padding(vertical = 4.dp)
    ) {
        labels.forEachIndexed { i, res ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(res), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = EdgeGuardColors.TextPrimary)
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(40.dp)
                        .background(if (i == selected) EdgeGuardColors.TextPrimary else Color.Transparent)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ All tab: summary + months

@Composable
private fun SummaryPage(records: List<DetectionRecord>, onOpenMonth: (Int) -> Unit) {
    val locale = currentLocale()
    val months = remember(records) { records.map { monthKey(it.timeMillis) }.distinct() }   // newest first

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.history_last_3_months), fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = EdgeGuardColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(EdgeGuardColors.TextPrimary))
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(records.watchedCount, R.string.stats_videos_watched, Modifier.weight(1f))
            Stat(records.fakeCount, R.string.stats_deepfakes_detected, Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))

        if (months.isEmpty()) {
            Text(stringResource(R.string.history_empty), fontSize = 17.sp, color = EdgeGuardColors.TextMuted)
        }
        months.forEach { key ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpenMonth(key) }
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(monthName(key, locale), fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = EdgeGuardColors.TextPrimary, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
                    tint = EdgeGuardColors.TextPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Stat(count: Int, formatRes: Int, modifier: Modifier) {
    // The string is "%1$d videos watched"; the design shows the number big and the words under it.
    val full = stringResource(formatRes, count)
    val words = full.removePrefix(count.toString()).trim()
    Column(modifier) {
        Text(count.toString(), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = EdgeGuardColors.TextPrimary)
        Text(words, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EdgeGuardColors.TextPrimary)
    }
}

// ------------------------------------------------------------------ month page

private sealed interface Row2 {
    data class Day(val label: String) : Row2
    data class Item(val record: DetectionRecord) : Row2
}

@Composable
private fun MonthPage(
    records: List<DetectionRecord>,
    monthKey: Int,
    onBack: () -> Unit,
    onOpenType: (DetectionType) -> Unit
) {
    val locale = currentLocale()
    val rows = remember(records, monthKey, locale) {
        val out = mutableListOf<Row2>()
        var lastDay = ""
        val dayFmt = SimpleDateFormat("d MMMM yyyy", locale)
        records.filter { monthKey(it.timeMillis) == monthKey }.forEach { r ->
            val day = dayFmt.format(Date(r.timeMillis))
            if (day != lastDay) { out.add(Row2.Day(day)); lastDay = day }
            out.add(Row2.Item(r))
        }
        out
    }

    Column(Modifier.fillMaxSize()) {
        BackRow(title = monthName(monthKey, locale), onBack = onBack)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { row ->
                when (row) {
                    is Row2.Day -> Text(row.label, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = EdgeGuardColors.TextPrimary, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                    is Row2.Item -> RecordRow(row.record, locale) { onOpenType(row.record.type) }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(r: DetectionRecord, locale: Locale, onClick: () -> Unit) {
    val (tagRes, titleRes, descRes, color) = when (r.type) {
        DetectionType.FAKE -> Quad(R.string.filter_fake, R.string.status_fake_title, R.string.status_fake_desc, EdgeGuardColors.Fake)
        DetectionType.AI_LABEL -> Quad(R.string.filter_ai_label, R.string.status_ai_label_title, R.string.status_ai_label_desc, Color(0xFFF07A1A))
        DetectionType.SAFE -> Quad(R.string.filter_safe, R.string.status_safe_title, R.string.status_safe_desc, Color(0xFF16A34A))
    }
    val time = SimpleDateFormat("h.mm a", locale).format(Date(r.timeMillis))
        .replace("AM", "A.M.").replace("PM", "P.M.")

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(stringResource(tagRes), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(76.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), fontSize = 17.sp, color = EdgeGuardColors.TextPrimary)
            Text(stringResource(descRes), fontSize = 13.sp, color = EdgeGuardColors.TextMuted)
        }
        Text(time, fontSize = 15.sp, color = EdgeGuardColors.TextPrimary, modifier = Modifier.padding(start = 8.dp))
    }
}

private data class Quad(val a: Int, val b: Int, val c: Int, val d: Color)

// ------------------------------------------------------------------ advice page (Fake / AI Label / Safe)

@Composable
private fun AdvicePage(type: DetectionType, onBack: (() -> Unit)?) {
    val ctx = LocalContext.current
    val speech = rememberSpeech()

    val titleRes: Int; val descRes: Int; val advice: List<Int>
    when (type) {
        DetectionType.FAKE -> {
            titleRes = R.string.details_fake_title; descRes = R.string.details_fake_desc
            advice = listOf(R.string.advice_avoid_trusting_content, R.string.advice_avoid_transferring_money,
                R.string.advice_consult_trusted_person)
        }
        DetectionType.AI_LABEL -> {
            titleRes = R.string.details_ai_label_title; descRes = R.string.details_ai_label_desc
            advice = listOf(R.string.advice_check_video_source, R.string.advice_treat_as_ad_or_animation,
                R.string.advice_be_careful_money_or_info)
        }
        DetectionType.SAFE -> {
            titleRes = R.string.details_safe_title; descRes = R.string.details_safe_desc
            advice = listOf(R.string.advice_check_video_source, R.string.advice_report_video)
        }
    }
    val title = stringResource(titleRes)
    val desc = stringResource(descRes)
    val adviceHeader = stringResource(R.string.section_you_are_advised_to)
    val adviceTexts = advice.map { stringResource(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = EdgeGuardColors.TextPrimary,
                    modifier = Modifier
                        .clickable(role = Role.Button) { onBack() }
                        .padding(8.dp)
                        .size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .clickable(role = Role.Button) {
                        val lang = AppPrefs.language(ctx) ?: "en"
                        val locale = if (lang == "ms") Locale.forLanguageTag("ms-MY") else Locale.ENGLISH
                        speech.speak((listOf(title, desc, adviceHeader) + adviceTexts).joinToString(". "), locale)
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(painterResource(R.drawable.ic_speaker), contentDescription = null, tint = EdgeGuardColors.TextMuted,
                    modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.btn_read_aloud), fontSize = 12.sp, color = EdgeGuardColors.TextMuted)
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (type) {
                DetectionType.FAKE -> Image(painterResource(R.drawable.ic_alert_triangle), null, Modifier.size(150.dp))
                DetectionType.AI_LABEL -> Image(painterResource(R.drawable.ic_robot), null, Modifier.size(140.dp))
                DetectionType.SAFE -> Box(
                    Modifier.size(120.dp).clip(CircleShape).background(Color(0xFF2EF26B)),
                    contentAlignment = Alignment.Center
                ) { Image(painterResource(R.drawable.ic_check), null, Modifier.size(70.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                color = EdgeGuardColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(desc, fontSize = 17.sp, textAlign = TextAlign.Center, color = EdgeGuardColors.TextPrimary)
        }

        Spacer(Modifier.height(22.dp))
        Text(adviceHeader, fontSize = 18.sp, color = EdgeGuardColors.TextPrimary)
        adviceTexts.forEach {
            Row(Modifier.padding(top = 4.dp, start = 8.dp)) {
                Text("•", fontSize = 17.sp, color = EdgeGuardColors.TextPrimary)
                Spacer(Modifier.width(10.dp))
                Text(it, fontSize = 17.sp, color = EdgeGuardColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ small helpers

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = EdgeGuardColors.TextPrimary,
            modifier = Modifier
                .clickable(role = Role.Button) { onBack() }
                .padding(8.dp)
                .size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = EdgeGuardColors.TextPrimary)
    }
}

@Composable
private fun currentLocale(): Locale {
    val ctx = LocalContext.current
    return ctx.resources.configuration.locales[0]
}

private fun monthKey(timeMillis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}

private fun monthName(key: Int, locale: Locale): String {
    val c = Calendar.getInstance().apply {
        clear(); set(Calendar.YEAR, key / 12); set(Calendar.MONTH, key % 12); set(Calendar.DAY_OF_MONTH, 1)
    }
    return SimpleDateFormat("MMMM yyyy", locale).format(c.time)
}
