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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Scaffold(
        containerColor = Color.White,
        bottomBar = { EdgeGuardBottomBar(selected = selected, onSelect = { selected = it }) }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (AppTab.entries[selected]) {
                AppTab.History -> PlaceholderPage(R.string.nav_history)   // step 2
                AppTab.Main -> MainPage(onStart = onStart, onStop = onStop)
                AppTab.Settings -> PlaceholderPage(R.string.nav_settings) // step 2
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
                .padding(horizontal = 18.dp, vertical = 8.dp),
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
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
