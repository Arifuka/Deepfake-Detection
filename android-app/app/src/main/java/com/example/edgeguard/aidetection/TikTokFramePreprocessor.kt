package com.example.edgeguard.aidetection

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Nisbah koordinat ternormal [0.0f .. 1.0f] untuk kawasan pemotongan skrin.
 * Menggunakan kelas data Kotlin tulen supaya logik pengiraan piksel boleh diuji
 * terus menggunakan JUnit di laptop tanpa bergantung pada kelas Android RectF.
 */
data class NormalizedRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Mod normalisasi piksel RGB mengikut spesifikasi pra-pemprosesan
 * semasa melatih model TFLite (cth. MobileNetV3).
 */
enum class RgbNormalizationMode {
    /** Julat [0.0f, 1.0f] — pembahagian pixel / 255.0f */
    ZERO_TO_ONE,

    /** Julat [-1.0f, 1.0f] — piawaian standard MobileNet / TensorFlow ((pixel / 127.5f) - 1.0f) */
    MINUS_ONE_TO_ONE,

    /** Julat [0.0f, 255.0f] — jika lapisan Rescaling sudah terbina di dalam fail .tflite */
    RAW_0_TO_255
}

/**
 * Stage 2 Preprocessor:
 * 1. Membuang elemen UI TikTok (status bar, butang sisi, kapsyen, bar bawah) supaya
 *    pinggir teks putih yang tajam tidak menghasilkan kebocoran spektrum palsu dalam 2D FFT.
 * 2. Memotong kawasan wajah dengan margin tambahan (untuk mengesan garisan cantuman deepfake).
 * 3. Menyediakan ByteBuffer RGB terus (direct buffer) untuk enjin inferens TFLite.
 */
class TikTokFramePreprocessor {

    companion object {
        /**
         * Kawasan video bersih TikTok:
         * - Buang 10% atas (bar status bateri/jam & tab Following/For You)
         * - Buang 17% kanan (ikon profil, like, komen, bookmark, share)
         * - Buang 22% bawah (nama pengguna, kapsyen video, audio ticker & bar navigasi)
         * - Buang 2% kiri (elak artifak lengkungan sisi skrin)
         */
        val TIKTOK_CLEAN_VIDEO_ROI = NormalizedRoi(
            left = 0.02f,
            top = 0.10f,
            right = 0.83f,
            bottom = 0.78f
        )

        private const val FLOAT_BYTE_SIZE = 4
        private const val RGB_CHANNELS = 3
    }

    /**
     * Mengira koordinat piksel mutlak [x, y, width, height] daripada nisbah ternormal.
     * Dilengkapi kawalan sempadan (clamping) supaya tidak pernah mencetuskan
     * IllegalArgumentException pada mana-mana nisbah skrin telefon.
     */
    fun calculateCropPixelBounds(
        frameWidth: Int,
        frameHeight: Int,
        roi: NormalizedRoi = TIKTOK_CLEAN_VIDEO_ROI
    ): IntArray {
        require(frameWidth > 0 && frameHeight > 0) {
            "Dimensi bingkai tidak sah: ${frameWidth}x${frameHeight}"
        }

        val safeLeft = roi.left.coerceIn(0f, 0.95f)
        val safeTop = roi.top.coerceIn(0f, 0.95f)
        val safeRight = roi.right.coerceIn(safeLeft + 0.05f, 1.0f)
        val safeBottom = roi.bottom.coerceIn(safeTop + 0.05f, 1.0f)

        val x = (frameWidth * safeLeft).roundToInt().coerceIn(0, frameWidth - 1)
        val y = (frameHeight * safeTop).roundToInt().coerceIn(0, frameHeight - 1)
        val width = max(1, (frameWidth * (safeRight - safeLeft)).roundToInt())
            .coerceAtMost(frameWidth - x)
        val height = max(1, (frameHeight * (safeBottom - safeTop)).roundToInt())
            .coerceAtMost(frameHeight - y)

        return intArrayOf(x, y, width, height)
    }

    /**
     * Langkah 1: Potong bingkai penuh TikTok untuk mendapatkan kawasan kandungan video bersih sahaja.
     */
    fun extractCleanVideoRegion(
        fullFrame: Bitmap,
        roi: NormalizedRoi = TIKTOK_CLEAN_VIDEO_ROI
    ): Bitmap {
        val bounds = calculateCropPixelBounds(fullFrame.width, fullFrame.height, roi)
        return Bitmap.createBitmap(
            fullFrame,
            bounds[0], // x
            bounds[1], // y
            bounds[2], // width
            bounds[3]  // height
        )
    }

    /**
     * Langkah 2 (Jika wajah dikesan oleh BlazeFace):
     * Memotong kawasan wajah dengan margin tambahan (default 25%) di sekeliling kotak pengesanan.
     * Margin ini penting kerana artifak cantuman deepfake (blending seams) lazimnya muncul
     * di sepanjang garis rahang, telinga, dan garis rambut — bukan di tengah wajah semata-mata.
     */
    fun cropFaceWithMargin(
        sourceBitmap: Bitmap,
        normalizedFaceBox: NormalizedRoi,
        marginRatio: Float = 0.25f
    ): Bitmap {
        val boxWidth = (normalizedFaceBox.right - normalizedFaceBox.left).coerceAtLeast(0.01f)
        val boxHeight = (normalizedFaceBox.bottom - normalizedFaceBox.top).coerceAtLeast(0.01f)

        val padX = boxWidth * marginRatio
        val padY = boxHeight * marginRatio

        val expandedRoi = NormalizedRoi(
            left = max(0f, normalizedFaceBox.left - padX),
            top = max(0f, normalizedFaceBox.top - padY),
            right = min(1f, normalizedFaceBox.right + padX),
            bottom = min(1f, normalizedFaceBox.bottom + padY)
        )

        val bounds = calculateCropPixelBounds(sourceBitmap.width, sourceBitmap.height, expandedRoi)
        return Bitmap.createBitmap(
            sourceBitmap,
            bounds[0],
            bounds[1],
            bounds[2],
            bounds[3]
        )
    }

    /**
     * Langkah 3: Mengubah saiz Bitmap kepada dimensi input model (cth. 224x224)
     * dan menukarnya kepada Direct ByteBuffer format NHWC [1, height, width, 3].
     */
    fun bitmapToRgbFloatBuffer(
        bitmap: Bitmap,
        targetWidth: Int = 224,
        targetHeight: Int = 224,
        mode: RgbNormalizationMode = RgbNormalizationMode.MINUS_ONE_TO_ONE
    ): ByteBuffer {
        val scaledBitmap = if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }

        val bufferSize = 1 * targetHeight * targetWidth * RGB_CHANNELS * FLOAT_BYTE_SIZE
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixelCount = targetWidth * targetHeight
        val pixels = IntArray(pixelCount)
        scaledBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        for (i in 0 until pixelCount) {
            val argb = pixels[i]
            val r = ((argb shr 16) and 0xFF).toFloat()
            val g = ((argb shr 8) and 0xFF).toFloat()
            val b = (argb and 0xFF).toFloat()

            when (mode) {
                RgbNormalizationMode.ZERO_TO_ONE -> {
                    byteBuffer.putFloat(r / 255.0f)
                    byteBuffer.putFloat(g / 255.0f)
                    byteBuffer.putFloat(b / 255.0f)
                }
                RgbNormalizationMode.MINUS_ONE_TO_ONE -> {
                    byteBuffer.putFloat((r / 127.5f) - 1.0f)
                    byteBuffer.putFloat((g / 127.5f) - 1.0f)
                    byteBuffer.putFloat((b / 127.5f) - 1.0f)
                }
                RgbNormalizationMode.RAW_0_TO_255 -> {
                    byteBuffer.putFloat(r)
                    byteBuffer.putFloat(g)
                    byteBuffer.putFloat(b)
                }
            }
        }

        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        byteBuffer.rewind()
        return byteBuffer
    }
}