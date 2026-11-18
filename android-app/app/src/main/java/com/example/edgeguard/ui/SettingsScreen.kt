package com.example.edgeguard.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edgeguard.EdgeGuardState
import com.example.edgeguard.ProtectionState
import com.example.edgeguard.R
import com.example.edgeguard.settings.AppPrefs
import com.example.edgeguard.settings.AppSettings
import com.example.edgeguard.settings.findActivity
import com.example.edgeguard.ui.theme.EdgeGuardColors
import java.util.Locale

/**
 * TODO (Farah): put the real support contacts here.
 * Phone number in international format, e.g. "+60123456789" (WhatsApp wants digits only, no "+").
 */
private object SupportContact {
    const val PHONE = "+60000000000"
    const val WHATSAPP = "60000000000"
}

private enum class SPage { Main, Data, Learn, OnDevice, HistoryNote, Policy, Manual, Support, Appearance }


/** Settings tab: status + switch, "Data & privacy", "Learn & get help" and the pages under them. */
@Composable
fun SettingsScreen(onStart: () -> Unit, onStop: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(SPage.Main.ordinal) }
    val current = SPage.entries[page]

    fun go(p: SPage) { page = p.ordinal }
    fun parent(p: SPage): SPage = when (p) {
        SPage.OnDevice, SPage.HistoryNote, SPage.Policy -> SPage.Data
        SPage.Manual, SPage.Support -> SPage.Learn
        else -> SPage.Main
    }
    BackHandler(enabled = current != SPage.Main) { go(parent(current)) }

    // navy top bar on sub pages needs white clock/battery icons
    StatusBarIcons(darkIcons = current == SPage.Main && !AppSettings.dark)

    when (current) {
        SPage.Main -> SettingsMain(onStart, onStop, onData = { go(SPage.Data) }, onLearn = { go(SPage.Learn) },
            onAppearance = { go(SPage.Appearance) })

        SPage.Data -> SubPage(R.string.section_data_and_privacy, onBack = { go(SPage.Main) }) {
            NavRow(R.drawable.ic_phone_outline, R.string.setting_on_device_only) { go(SPage.OnDevice) }
            NavRow(R.drawable.ic_board, R.string.setting_video_detection_history) { go(SPage.HistoryNote) }
            NavRow(R.drawable.ic_clipboard, R.string.setting_privacy_and_policy) { go(SPage.Policy) }
        }

        SPage.Learn -> SubPage(R.string.section_learn_and_get_help, onBack = { go(SPage.Main) }) {
            NavRow(R.drawable.ic_book, R.string.setting_user_manual) { go(SPage.Manual) }
            NavRow(R.drawable.ic_headset, R.string.setting_support_team) { go(SPage.Support) }
        }

        SPage.OnDevice -> InfoPage(R.string.setting_on_device_only, onBack = { go(SPage.Data) }) { speak ->
            val a = stringResource(R.string.privacy_on_device_desc_1)
            val b = stringResource(R.string.privacy_on_device_desc_2)
            speak(listOf(a, b).joinToString(" "))
            Text(a, fontSize = 30.sp, color = EdgeGuardColors.TextPrimary)
            Text(b, fontSize = 26.sp, color = EdgeGuardColors.TextPrimary)
        }

        SPage.HistoryNote -> InfoPage(R.string.setting_video_detection_history, onBack = { go(SPage.Data) }) { speak ->
            val a = stringResource(R.string.privacy_history_auto_delete)
            speak(a)
            Text(a, fontSize = 30.sp, color = EdgeGuardColors.TextPrimary)
        }

        SPage.Policy -> InfoPage(R.string.setting_privacy_and_policy, onBack = { go(SPage.Data) }) { speak ->
            val header = stringResource(R.string.privacy_policy_header)
            val points = listOf(
                R.string.privacy_policy_point_1, R.string.privacy_policy_point_2, R.string.privacy_policy_point_3,
                R.string.privacy_policy_point_4, R.string.privacy_policy_point_5, R.string.privacy_policy_point_6
            ).map { stringResource(it) }
            speak((listOf(header) + points).joinToString(" "))
            Text(header, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = EdgeGuardColors.TextPrimary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            points.forEach {
                Row(Modifier.padding(top = 6.dp)) {
                    Text("•", fontSize = 17.sp, color = EdgeGuardColors.TextPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(it, fontSize = 17.sp, color = EdgeGuardColors.TextPrimary)
                }
            }
        }

        SPage.Manual -> InfoPage(R.string.setting_user_manual, onBack = { go(SPage.Learn) }) { speak ->
            val steps = listOf(
                R.string.manual_step_1, R.string.manual_step_2, R.string.manual_step_3, R.string.manual_step_4
            ).map { stringResource(it) }
            speak(steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString(" "))
            steps.forEachIndexed { i, s ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text("${i + 1}.", fontSize = 24.sp, color = EdgeGuardColors.TextPrimary, modifier = Modifier.width(36.dp))
                    Text(s, fontSize = 24.sp, color = EdgeGuardColors.TextPrimary)
                }
            }
        }

        SPage.Support -> SupportPage(onBack = { go(SPage.Learn) })

        SPage.Appearance -> AppearancePage(onBack = { go(SPage.Main) })
    }
}

// ------------------------------------------------------------------ main settings page

@Composable
private fun SettingsMain(
    onStart: () -> Unit, onStop: () -> Unit, onData: () -> Unit, onLearn: () -> Unit, onAppearance: () -> Unit
) {
    val state by EdgeGuardState.state.collectAsState()
    val active = state != ProtectionState.Off

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            EdgeGuardLogo(size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.settings_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = EdgeGuardColors.TextPrimary)
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(if (active) R.drawable.ic_eye else R.drawable.ic_eye_off),
                contentDescription = null, tint = EdgeGuardColors.TextPrimary, modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (active) R.string.settings_status_on_title else R.string.settings_status_off_title),
                    fontSize = 22.sp, color = EdgeGuardColors.TextPrimary
                )
                Text(
                    stringResource(if (active) R.string.settings_status_on_desc else R.string.settings_status_off_desc),
                    fontSize = 17.sp, color = EdgeGuardColors.TextPrimary
                )
            }
            Switch(
                checked = active,
                onCheckedChange = { if (it) onStart() else onStop() },
                modifier = Modifier.scale(1.2f),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = EdgeGuardColors.Accent, checkedThumbColor = Color.White,
                    uncheckedTrackColor = EdgeGuardColors.SwitchOffTrack, uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
        Spacer(Modifier.height(28.dp))

        BigCard(R.drawable.ic_lock, R.string.section_data_and_privacy, onData)
        Spacer(Modifier.height(24.dp))
        BigCard(R.drawable.ic_help_circle, R.string.section_learn_and_get_help, onLearn)
        Spacer(Modifier.height(24.dp))
        BigCard(R.drawable.ic_sun, R.string.settings_appearance_card, onAppearance)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BigCard(@DrawableRes icon: Int, labelRes: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth(0.82f)
            .align(Alignment.CenterHorizontally)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(EdgeGuardColors.CardWarm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = EdgeGuardColors.TextPrimary, modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(labelRes), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, color = EdgeGuardColors.TextPrimary, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

// ------------------------------------------------------------------ sub pages

@Composable
private fun TopBar(titleRes: Int, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(EdgeGuardColors.Navy)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = Color.White,
            modifier = Modifier
                .clickable(role = Role.Button) { onBack() }
                .padding(6.dp)
                .size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(titleRes), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SubPage(titleRes: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(EdgeGuardColors.Background)) {
        TopBar(titleRes, onBack)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 24.dp)) { content() }
    }
}

@Composable
private fun NavRow(@DrawableRes icon: Int, labelRes: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = EdgeGuardColors.TextPrimary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Text(stringResource(labelRes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EdgeGuardColors.TextPrimary,
                modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = EdgeGuardColors.TextPrimary,
                modifier = Modifier.size(22.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(EdgeGuardColors.Divider))
    }
}

/**
 * Page with logo + Read aloud on top, text in the middle, "Powered by EdgeGuard" at the bottom.
 * The content lambda gets a function to tell the Read aloud button what to read.
 */
@Composable
private fun InfoPage(titleRes: Int, onBack: () -> Unit, content: @Composable (speak: (String) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val speech = rememberSpeech()
    var toRead by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(EdgeGuardColors.Background)) {
        TopBar(titleRes, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EdgeGuardLogo(size = 64.dp)
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier
                        .clickable(role = Role.Button) {
                            val lang = AppPrefs.language(ctx) ?: "en"
                            val locale = if (lang == "ms") Locale.forLanguageTag("ms-MY") else Locale.ENGLISH
                            speech.speak(toRead, locale)
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(painterResource(R.drawable.ic_speaker), contentDescription = null,
                        tint = EdgeGuardColors.TextMuted, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.btn_read_aloud), fontSize = 12.sp, color = EdgeGuardColors.TextMuted)
                }
            }
            Spacer(Modifier.height(16.dp))
            content { toRead = it }
        }
        PoweredBy()
    }
}

@Composable
private fun PoweredBy() {
    Text(
        stringResource(R.string.alert_powered_by), fontSize = 13.sp, color = EdgeGuardColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    )
}

@Composable
private fun SupportPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(EdgeGuardColors.Background)) {
        TopBar(R.string.setting_support_team, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeGuardLogo(size = 72.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.support_header), fontSize = 18.sp, color = EdgeGuardColors.TextPrimary)
                    Text(stringResource(R.string.support_description), fontSize = 18.sp, color = EdgeGuardColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(24.dp))
            SupportButton(R.drawable.ic_call, R.string.btn_call_us) {
                openSafely(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${SupportContact.PHONE}")))
            }
            Spacer(Modifier.height(24.dp))
            SupportButton(R.drawable.ic_chat, R.string.btn_message_whatsapp) {
                openSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${SupportContact.WHATSAPP}")))
            }
        }
        PoweredBy()
    }
}

@Composable
private fun SupportButton(@DrawableRes icon: Int, labelRes: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth(0.7f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(EdgeGuardColors.CardMint)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = EdgeGuardColors.TextPrimary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(labelRes), fontSize = 22.sp, color = EdgeGuardColors.TextPrimary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp))
    }
}

private fun openSafely(ctx: android.content.Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // no dialer / browser installed: do nothing rather than crash
    }
}

// ------------------------------------------------------------------ display & language page

@Composable
private fun AppearancePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(AppPrefs.language(ctx) ?: "ms") }

    Column(Modifier.fillMaxSize().background(EdgeGuardColors.Background)) {
        TopBar(R.string.settings_appearance, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ---- text size
            SectionTitle(R.string.appearance_text_size)
            listOf(
                R.string.size_small, R.string.size_normal, R.string.size_large, R.string.size_xlarge
            ).forEachIndexed { i, res ->
                OptionRow(stringResource(res), AppSettings.fontStep == i) { AppSettings.setFontStep(ctx, i) }
            }

            // ---- light / dark
            SectionTitle(R.string.appearance_mode)
            OptionRow(stringResource(R.string.mode_light), !AppSettings.dark) { AppSettings.setDark(ctx, false) }
            OptionRow(stringResource(R.string.mode_dark), AppSettings.dark) { AppSettings.setDark(ctx, true) }

            // ---- font
            SectionTitle(R.string.appearance_font)
            OptionRow(stringResource(R.string.font_inter), AppSettings.fontKey == "inter") { AppSettings.setFontKey(ctx, "inter") }
            OptionRow(stringResource(R.string.font_atkinson), AppSettings.fontKey == "atkinson") { AppSettings.setFontKey(ctx, "atkinson") }
            OptionRow(stringResource(R.string.font_system), AppSettings.fontKey == "system") { AppSettings.setFontKey(ctx, "system") }

            // ---- language
            SectionTitle(R.string.appearance_language)
            OptionRow(stringResource(R.string.lang_name_bm), lang == "ms") { lang = "ms"; changeLanguage(ctx, "ms") }
            OptionRow(stringResource(R.string.lang_name_en), lang == "en") { lang = "en"; changeLanguage(ctx, "en") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun changeLanguage(ctx: android.content.Context, lang: String) {
    if (AppPrefs.language(ctx) == lang) return
    AppPrefs.setLanguage(ctx, lang)
    ctx.findActivity()?.recreate()   // redraw every text in the new language
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
        color = EdgeGuardColors.TextPrimary, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(EdgeGuardColors.Surface)
            .then(if (selected) Modifier.border(2.dp, EdgeGuardColors.Accent, shape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 19.sp, color = EdgeGuardColors.TextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EdgeGuardColors.Accent)
        }
    }
}
