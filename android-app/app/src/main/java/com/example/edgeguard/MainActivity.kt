package com.example.edgeguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.edgeguard.ui.theme.EdgeGuardTheme

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            // check if user betul2 tekan "start now", not cancel.
            // r.data = token from android untuk baca screen
            if (r.resultCode == Activity.RESULT_OK && r.data != null) {
                // envelope, result code, truth token inside it
                val i = Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.EXTRA_CODE, r.resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, r.data)
                // foreground service so bila user exit from edgeguard and bukak tiktok,
                // android tak terminate service screen detection
                ContextCompat.startForegroundService(this, i)
            }
        }

    private val notifLauncher =
        //box receiver answer after user click allow/deny on popup notif
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { askProjection() }

    private fun askProjection() {
        // manager system android for recording/screen record @ shot
        val mpm = getSystemService(MediaProjectionManager::class.java)
        // works on dialogue system Android yang tanya start record/casting with edgeguard?
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun startProtection() {
        //Android 13/API 33 must ask permission POST_NOTIF before app
        //can show notif foreground service
        //if/else: if belum allow notif permission, buka popup dulu, else
        // terus jump ke askProjection()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else askProjection()
    }

    // send Intent to CaptService dgn arahan ACTION STOP so service stop screen record
    // then set semula edgeguard state to protectionstate.off
    private fun stopProtection() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
    }
//first function that android call when app is opened
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    // panggil dua remote control (onstart and onstop) so UI screen can call func
    // startProtection and stopProtect dalam MainActivity
        setContent {
            EdgeGuardTheme {
                MainScreen(onStart = { startProtection() }, onStop = { stopProtection() })
            }
        }
    }
}

@Composable
fun MainScreen(onStart: () -> Unit, onStop: () -> Unit) {
    // MainScreen listens to change on EdgeGuardState.state
    val state by EdgeGuardState.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // ambil text dari strings.xml, automatic tukar dual lang based on phone's lang
                stringResource(R.string.section_protection),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state != ProtectionState.Off,
                onCheckedChange = { if (it) onStart() else onStop() }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                if (state == ProtectionState.Off) R.string.status_inactive else R.string.status_active
            )
        )
    }
}