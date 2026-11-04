package com.example.edgeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

class CaptureService : Service() {

    private var saved = false
    private var frameCount = 0
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var lastMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 1) must be foreground BEFORE getMediaProjection
        startAsForeground()

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(code, data)
        EdgeGuardState.set(ProtectionState.On)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "EdgeGuard protection", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("EdgeGuard is protecting you")
            .setContentText("Checking videos on your screen")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun startCapture(code: Int, data: Intent) {
        thread = HandlerThread("edgeguard-capture").also { it.start() }
        val handler = Handler(thread!!.looper)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data)
        if (p == null) {
            stopSelf()
            return
        }
        projection = p
        // 2) required on Android 14+
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        display = p.createVirtualDisplay(
            "edgeguard", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )

        r.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = SystemClock.elapsedRealtime()
            if (now - lastMs >= 500) {          // ~2 frames per second
                lastMs = now
                Log.d("EdgeGuard", "frame ${img.width}x${img.height}")
                frameCount++
                if (frameCount == 10 && !saved) {
                    saved = true
                    val bmp = toBitmap(img)
                    val f = java.io.File(getExternalFilesDir(null), "test_frame.jpg")
                    java.io.FileOutputStream(f).use {
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
                    }
                    Log.d("EdgeGuard", "saved ${f.absolutePath}")
                }
            }
            img.close()                          // always close, or capture freezes
        }, handler)
    }

    private fun toBitmap(img: android.media.Image): android.graphics.Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * img.width
        val wide = android.graphics.Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        wide.copyPixelsFromBuffer(plane.buffer)
        return android.graphics.Bitmap.createBitmap(wide, 0, 0, img.width, img.height)
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        thread?.quitSafely()
        EdgeGuardState.set(ProtectionState.Off)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "edgeguard.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "edgeguard_capture"
    }
}