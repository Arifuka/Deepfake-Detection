package com.example.edgeguard

import android.Manifest
import android.app.Activity
import android.content.Context
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
import com.example.edgeguard.settings.AppPrefs
import com.example.edgeguard.settings.AppSettings
import com.example.edgeguard.settings.LocaleHelper
import com.example.edgeguard.ui.EdgeGuardRoot
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

    // Apply the language the user picked (Bahasa Melayu / English).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, AppPrefs.language(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load dark mode / text size / font the user chose
        AppSettings.load(this)

        // Delete history older than 3 months every time the app opens.
        HistoryRepository.get(this).purgeOld()

        setContent {
            // Light theme, no dynamic colours: the Figma design is white + navy for everyone.
            EdgeGuardTheme(darkTheme = AppSettings.dark, dynamicColor = false) {
                EdgeGuardRoot(onStart = { startProtection() }, onStop = { stopProtection() })
            }
        }
    }
}
