package com.example.edgeguard.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edgeguard.EdgeGuardState
import com.example.edgeguard.ProtectionState
import com.example.edgeguard.R
import com.example.edgeguard.ui.theme.EdgeGuardColors

/**
 * "Main Page" from the Figma design:
 *   logo + "EDGEGUARD:" / "Deepfake detection"
 *   shield + PROTECTION + description + switch
 *   centred grey status text
 */
@Composable
fun MainPage(onStart: () -> Unit, onStop: () -> Unit) {
    val state by EdgeGuardState.state.collectAsState()
    val active = state != ProtectionState.Off

    // The string is one line "EDGEGUARD: Deepfake detection"; the design shows it on two lines.
    val header = stringResource(R.string.main_header_title)
    val title = header.substringBefore(":") + ":"
    val subtitle = header.substringAfter(":").trim()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoPlaceholder()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = EdgeGuardColors.TextPrimary)
                Text(subtitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EdgeGuardColors.TextPrimary)
            }
        }

        Spacer(Modifier.height(40.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = EdgeGuardColors.TextPrimary,
                modifier = Modifier.size(34.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    stringResource(R.string.section_protection),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EdgeGuardColors.TextPrimary
                )
                Text(
                    stringResource(R.string.protection_description),
                    fontSize = 20.sp,
                    color = EdgeGuardColors.TextPrimary
                )
            }
            Switch(
                checked = active,
                onCheckedChange = { if (it) onStart() else onStop() },
                modifier = Modifier.scale(1.3f),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = EdgeGuardColors.Navy,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = EdgeGuardColors.SwitchOffTrack,
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(if (active) R.string.status_active else R.string.status_inactive),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = EdgeGuardColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * TODO: replace with the real logo.
 * In Figma: select the logo > Export (right panel) > PNG 2x > save as
 * app/src/main/res/drawable/edgeguard_logo.png, then use:
 *   Image(painterResource(R.drawable.edgeguard_logo), contentDescription = null, modifier = Modifier.size(56.dp))
 */
@Composable
private fun LogoPlaceholder() {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(EdgeGuardColors.Navy),
        contentAlignment = Alignment.Center
    ) {
        Text("EG", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}
