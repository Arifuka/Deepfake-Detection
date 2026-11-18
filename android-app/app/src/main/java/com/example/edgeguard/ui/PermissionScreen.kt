package com.example.edgeguard.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.edgeguard.R
import com.example.edgeguard.aidetection.TikTokAccessibilityService
import com.example.edgeguard.settings.findActivity
import com.example.edgeguard.ui.theme.EdgeGuardColors

/**
 * Welcome / permission screen from the design.
 * The button opens the NEXT missing Android setting:
 *   1. "Draw over other apps"   2. Accessibility
 * When the user comes back, the screen checks again. When both are on, [onAllDone] is called.
 *
 * Safety net: if the user has been to the Accessibility screen and came back, we continue
 * anyway, so nobody gets stuck (for example if a phone hides the switch).
 */
@Composable
fun PermissionScreen(onAllDone: () -> Unit) {
    val ctx = LocalContext.current
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var a11yOk by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }
    var openedA11y by rememberSaveable { mutableStateOf(false) }   // user pressed the button for accessibility
    var cameBackFromA11y by rememberSaveable { mutableStateOf(false) }

    val activity = ctx.findActivity()
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayOk = Settings.canDrawOverlays(ctx)
                a11yOk = isAccessibilityEnabled(ctx)
                if (openedA11y) cameBackFromA11y = true
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(overlayOk, a11yOk, cameBackFromA11y) {
        if (overlayOk && (a11yOk || cameBackFromA11y)) onAllDone()
    }

    val onAgree = {
        when {
            !overlayOk -> ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
            !a11yOk -> {
                openedA11y = true
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        EdgeGuardLogo(size = 96.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_title),
            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.permission_notice),
            fontSize = 18.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF7FAFD), Color(0xFFE6F3FB), Color(0xFFFDF2F2))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PermissionItem(
                stringResource(R.string.permission_draw_over_apps_title),
                stringResource(R.string.permission_draw_over_apps_desc),
                done = overlayOk
            )
            PermissionItem(
                stringResource(R.string.permission_accessibility_title),
                stringResource(R.string.permission_accessibility_desc),
                done = a11yOk
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.privacy_no_account_needed),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.privacy_no_data_leaves_phone),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Row(
            Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(EdgeGuardColors.Navy)
                .clickable(role = Role.Button) { onAgree() }
                .padding(horizontal = 32.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.btn_agree_and_continue),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_external_link),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionItem(title: String, description: String, done: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (done) "✓  $title" else title,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF0B1A33)
        )
        Spacer(Modifier.height(4.dp))
        Text(description, fontSize = 16.sp, color = Color(0xFF1E3A5F))
    }
}

/** True when EdgeGuard's accessibility service is switched on in Android settings. */
fun isAccessibilityEnabled(context: Context): Boolean {
    val me = ComponentName(context, TikTokAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}
