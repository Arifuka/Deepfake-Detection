package com.example.edgeguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edgeguard.R
import com.example.edgeguard.settings.LocaleHelper
import com.example.edgeguard.ui.theme.EdgeGuardColors
import java.util.Locale

/**
 * Language screen from the design.
 * Tap a card to choose; a Continue button appears, written in the chosen language.
 * The speaker icon reads the language name aloud.
 * [onChosen] gets "ms" or "en".
 */
@Composable
fun LanguageScreen(onChosen: (String) -> Unit) {
    val ctx = LocalContext.current
    val speech = rememberSpeech()
    StatusBarIcons(darkIcons = false)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(EdgeGuardColors.OnboardNavy)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Icon(
            painter = painterResource(R.drawable.ic_globe),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(90.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.language_select_title),
            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.language_select_subtitle),
            color = Color.White, fontSize = 24.sp
        )
        Spacer(Modifier.height(28.dp))

        LanguageOption(
            name = stringResource(R.string.lang_name_bm),
            note = LocaleHelper.localized(ctx, "ms").getString(R.string.lang_recommended),
            selected = selected == "ms",
            onSelect = { selected = "ms" },
            onSpeak = { speech.speak("Bahasa Melayu", Locale.forLanguageTag("ms-MY")) }
        )
        Spacer(Modifier.height(16.dp))
        LanguageOption(
            name = stringResource(R.string.lang_name_en),
            note = null,
            selected = selected == "en",
            onSelect = { selected = "en" },
            onSpeak = { speech.speak("English", Locale.ENGLISH) }
        )
        Spacer(Modifier.height(44.dp))

        selected?.let { lang ->
            Text(
                text = LocaleHelper.localized(ctx, lang).getString(R.string.btn_continue),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(EdgeGuardColors.OnboardCard)
                    .clickable(role = Role.Button) { onChosen(lang) }
                    .padding(horizontal = 44.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun LanguageOption(
    name: String,
    note: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    onSpeak: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EdgeGuardColors.OnboardCard)
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (note != null) {
                Text(note, color = EdgeGuardColors.OnboardNote, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onSpeak) {
            Icon(
                painter = painterResource(R.drawable.ic_speaker),
                contentDescription = stringResource(R.string.btn_read_aloud),
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
