package com.example.edgeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.edgeguard.aidetection.AiLabelOcrScanner
import com.example.edgeguard.aidetection.EdgeGuardTfliteRunner
import com.example.edgeguard.aidetection.FftSpectrumExtractor
import com.example.edgeguard.aidetection.OverlayWarningManager
import com.example.edgeguard.history.DetectionType
import com.example.edgeguard.aidetection.RgbNormalizationMode
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import kotlinx.coroutines.runBlocking
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var detector: FaceDetector? = null
    private var interpreter: Interpreter? = null
    private var lastMs = 0L

    // Komponen Stage 1B (ML Kit OCR), Stage 2 (Clean-Frame TFLite + 2D FFT), dan Overlay UI
    private var ocrScanner: AiLabelOcrScanner? = null
    private var tfliteRunner: EdgeGuardTfliteRunner? = null
    private var overlayWarningManager: OverlayWarningManager? = null
    private val fftExtractor = FftSpectrumExtractor(gridSize = 128)
    private val mainHandler = Handler(Looper.getMainLooper())

    // model input: 1 x 224 x 224 x 3 floats (4 bytes each), RGB 0..255
    private val inputBuf: ByteBuffer =
        ByteBuffer.allocateDirect(224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(224 * 224)

    // alert logic
    private val scores = ArrayDeque<Float>()
    private var alerting = false
    private var noContentCount = 0
    private var latestDetectionSource = "Visual & Frequency Analysis"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EdgeGuard protection", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notif: Notification = builder
            .setContentTitle("EdgeGuard is protecting you")
            .setContentText("Checking videos on your screen")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notif)
        }
    }

    private fun makeDetector() {
        val opts = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setModelAssetPath("blaze_face_short_range.tflite").build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.5f)
            .build()
        detector = FaceDetector.createFromOptions(this, opts)
    }

    private fun makeInterpreter() {
        val bytes = assets.open("edgeguard_model.tflite").use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes)
        buf.rewind()
        interpreter = Interpreter(buf, Interpreter.Options().setNumThreads(4))
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
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        makeDetector()
        makeInterpreter()
        ocrScanner = AiLabelOcrScanner()
        overlayWarningManager = OverlayWarningManager(this)
        tfliteRunner = EdgeGuardTfliteRunner(
            context = this,
            aiThreshold = ALERT_THR,
            rgbNormalizationMode = RgbNormalizationMode.RAW_0_TO_255,
            invertSigmoidOutput = false
        )

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
            if (now - lastMs < 500) {                  // ~2 frames per second
                img.close()
                return@setOnImageAvailableListener
            }
            lastMs = now
            val bmp = toBitmap(img)
            img.close()                                // always close, or capture freezes
            processFrame(bmp)
            bmp.recycle()
        }, handler)
    }

    private fun processFrame(bmp: Bitmap) {
        val t0 = SystemClock.elapsedRealtime()

        // --- STAGE 1B: Fast-Pass ML Kit OCR (Label TikTok & Tera Air AI) ---
        val ocrResult = try {
            runBlocking { ocrScanner?.scanForAiLabels(bmp) }
        } catch (e: Exception) {
            Log.w("EdgeGuard", "OCR fast-pass ralat: ${e.message}")
            null
        }

        if (ocrResult?.isAiDetected == true) {
            val matchedLabel = ocrResult.matchedKeyword ?: "AI Label"
            noContentCount = 0
            alerting = true
            EdgeGuardState.set(ProtectionState.Alerting)
            overlayWarningManager?.show(DetectionType.AI_LABEL, "Stage 1 OCR ($matchedLabel)")

            val ms = SystemClock.elapsedRealtime() - t0
            Log.d(
                "EdgeGuard",
                "[STAGE 1 OCR] Label AI dikesan='$matchedLabel' | alert=true ($ms ms)"
            )
            return
        }

        // --- STAGE 2A: Pengesanan Wajah BlazeFace + MobileNetV3 + 2D FFT ---
        val faces = detector?.detect(BitmapImageBuilder(bmp).build())?.detections()
            ?: emptyList()
        val best = faces.maxByOrNull {
            it.boundingBox().width() * it.boundingBox().height()
        }

        if (best != null) {
            val face = cropFace(bmp, best.boundingBox()) ?: return
            val p = predict(face)
            val fftHfRatio = fftExtractor.extractFromBitmap(face).highFrequencyEnergyRatio
            face.recycle()

            val effectiveScore = if (p >= 0.62f && fftHfRatio >= 0.35f) max(p, 0.75f) else p
            latestDetectionSource = "Stage 2 Face Analysis (${(effectiveScore * 100).toInt()}%)"
            onScore(effectiveScore)

            val ms = SystemClock.elapsedRealtime() - t0
            val hi = scores.count { it > ALERT_THR }
            Log.d(
                "EdgeGuard",
                "[STAGE 2 FACE] P(fake)=%.2f  FFT_HF=%.2f  high=%d/%d  alert=%b  (%d ms)"
                    .format(p, fftHfRatio, hi, scores.size, alerting, ms)
            )
            return
        }

        // --- STAGE 2B: Fallback Video Tanpa Wajah (Potong UI TikTok + MobileNetV3 + 2D FFT) ---
        val stage2Clean = tfliteRunner?.analyzeFrame(bmp)
        if (stage2Clean != null) {
            val p = stage2Clean.aiProbability
            val fftHfRatio = stage2Clean.highFrequencyEnergyRatio
            val effectiveScore = if (p >= 0.65f && fftHfRatio >= 0.35f) max(p, 0.75f) else p

            if (effectiveScore > 0.15f) {
                latestDetectionSource = "Stage 2 Visual + FFT (${(effectiveScore * 100).toInt()}%)"
                onScore(effectiveScore)

                val ms = SystemClock.elapsedRealtime() - t0
                val hi = scores.count { it > ALERT_THR }
                Log.d(
                    "EdgeGuard",
                    "[STAGE 2 CLEAN-FRAME] P(fake)=%.2f  FFT_HF=%.2f  high=%d/%d  alert=%b  (%d ms)"
                        .format(p, fftHfRatio, hi, scores.size, alerting, ms)
                )
            } else {
                onNoContent()
            }
        } else {
            onNoContent()
        }
    }

    // same recipe as training: square box, side = longer edge * 1.4, resize to 224x224
    private fun cropFace(bmp: Bitmap, b: RectF): Bitmap? {
        val cx = (b.left + b.right) / 2f
        val cy = (b.top + b.bottom) / 2f
        val side = max(b.width(), b.height()) * 1.4f
        val x1 = max(cx - side / 2f, 0f).toInt()
        val y1 = max(cy - side / 2f, 0f).toInt()
        val x2 = min(cx + side / 2f, bmp.width.toFloat()).toInt()
        val y2 = min(cy + side / 2f, bmp.height.toFloat()).toInt()
        if (x2 - x1 < 20 || y2 - y1 < 20) return null
        val crop = Bitmap.createBitmap(bmp, x1, y1, x2 - x1, y2 - y1)
        var cur = crop
        while (cur.width >= 448 && cur.height >= 448) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== crop) cur.recycle()
            cur = next
        }
        val out = Bitmap.createScaledBitmap(cur, 224, 224, true)
        if (cur !== crop && cur !== out) cur.recycle()
        if (crop !== out) crop.recycle()
        return out
    }

    private fun predict(face: Bitmap): Float {
        face.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        inputBuf.rewind()
        for (px in pixels) {
            inputBuf.putFloat(((px shr 16) and 0xFF).toFloat())   // R
            inputBuf.putFloat(((px shr 8) and 0xFF).toFloat())    // G
            inputBuf.putFloat((px and 0xFF).toFloat())            // B
        }
        inputBuf.rewind()
        val out = Array(1) { FloatArray(1) }
        interpreter?.run(inputBuf, out)
        return out[0][0]                                          // P(fake)
    }

    private fun onNoContent() {
        noContentCount++
        if (noContentCount >= 6) {                  // ~3 seconds without suspicious content: reset
            scores.clear()
            if (alerting) {
                alerting = false
                dismissOverlaySafely()
            }
            EdgeGuardState.set(ProtectionState.On)
        }
        Log.d("EdgeGuard", "no suspicious face/frame")
    }

    private fun onScore(p: Float) {
        noContentCount = 0
        scores.addLast(p)
        if (scores.size > 5) scores.removeFirst()
        val hi = scores.count { it > ALERT_THR }
        val wasAlerting = alerting

        if (!alerting && hi >= 4) {
            alerting = true
        } else if (alerting && scores.size >= 5 && scores.average() < CLEAR_THR) {
            alerting = false
        }

        EdgeGuardState.set(if (alerting) ProtectionState.Alerting else ProtectionState.On)

        if (alerting) {
            overlayWarningManager?.show(DetectionType.FAKE, latestDetectionSource)
        } else if (wasAlerting) {
            dismissOverlaySafely()
        }
    }

    private fun dismissOverlaySafely() {
        mainHandler.post {
            overlayWarningManager?.dismissWarning()
        }
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * img.width
        val wide = Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888
        )
        wide.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return wide
        val cropped = Bitmap.createBitmap(wide, 0, 0, img.width, img.height)
        wide.recycle()
        return cropped
    }

    override fun onDestroy() {
        dismissOverlaySafely()
        display?.release()
        reader?.close()
        projection?.stop()
        detector?.close()
        interpreter?.close()
        tfliteRunner?.close()
        thread?.quitSafely()
        EdgeGuardState.set(ProtectionState.Off)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "edgeguard.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "edgeguard_capture"
        private const val ALERT_THR = 0.70f   // a frame counts as "fake" above this
        private const val CLEAR_THR = 0.40f   // alert clears when the average drops below this
    }
}