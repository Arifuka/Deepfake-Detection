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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.edgeguard.history.HistoryRepository
import com.example.edgeguard.ui.EdgeGuardApp
import com.example.edgeguard.ui.theme.EdgeGuardTheme

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK && r.data != null) {
                val i = Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.EXTRA_CODE, r.resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, r.data)
                ContextCompat.startForegroundService(this, i)
            }
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { askProjection() }

    private fun askProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun startProtection() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else askProjection()
    }

    private fun stopProtection() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Delete history older than 3 months every time the app opens.
        HistoryRepository.get(this).purgeOld()

        setContent {
            // Light theme, no dynamic colours: the Figma design is white + navy for everyone.
            EdgeGuardTheme(darkTheme = false, dynamicColor = false) {
                EdgeGuardApp(onStart = { startProtection() }, onStop = { stopProtection() })
            }
        }
    }
}
