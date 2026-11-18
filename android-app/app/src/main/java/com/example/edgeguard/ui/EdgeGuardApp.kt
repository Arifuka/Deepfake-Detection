package com.example.edgeguard.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edgeguard.R
import com.example.edgeguard.settings.AppSettings
import com.example.edgeguard.ui.theme.EdgeGuardColors

/** Order matches the Figma bottom bar: History | Main Page | Settings. */
enum class AppTab(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    History(R.string.nav_history, R.drawable.ic_nav_history),
    Main(R.string.nav_main_page, R.drawable.ic_nav_home),
    Settings(R.string.nav_settings, R.drawable.ic_nav_settings)
}

/**
 * The whole app after first-run set-up: a bottom bar and one page per tab.
 * Main Page is the starting tab (as in the design).
 */
@Composable
fun EdgeGuardApp(onStart: () -> Unit, onStop: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(AppTab.Main.ordinal) }

    // white icons at the top in dark mode, dark icons in light mode
    StatusBarIcons(darkIcons = !AppSettings.dark)

    // plain Text() gets the right colour for light / dark mode
    CompositionLocalProvider(LocalContentColor provides EdgeGuardColors.TextPrimary) {
    Scaffold(
        containerColor = EdgeGuardColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),   // each page handles the status bar itself
        bottomBar = { EdgeGuardBottomBar(selected = selected, onSelect = { selected = it }) }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (AppTab.entries[selected]) {
                AppTab.History -> Box(Modifier.statusBarsPadding()) { HistoryScreen() }
                AppTab.Main -> Box(Modifier.statusBarsPadding()) { MainPage(onStart = onStart, onStop = onStop) }
                AppTab.Settings -> SettingsScreen(onStart = onStart, onStop = onStop)
            }
        }
    }
    }
}

@Composable
private fun EdgeGuardBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(EdgeGuardColors.Navy)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEachIndexed { i, tab ->
            NavItem(tab = tab, isSelected = i == selected, onClick = { onSelect(i) })
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: AppTab, isSelected: Boolean, onClick: () -> Unit) {
    val contentColor = if (isSelected) EdgeGuardColors.Navy else Color.White
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSelected) EdgeGuardColors.NavPill else Color.Transparent)
                .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = stringResource(tab.labelRes),
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Temporary page until step 2 builds History and Settings. */
@Composable
private fun PlaceholderPage(@StringRes titleRes: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(titleRes),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = EdgeGuardColors.TextMuted
        )
    }
}
