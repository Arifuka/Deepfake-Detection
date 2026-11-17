package com.example.edgeguard.aidetection

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * Hasil analisis domain frekuensi 2D daripada bingkai video bersih.
 *
 * @property gridSize Dimensi matriks kuasa dua (cth. 128 atau 256)
 * @property normalizedMagnitude Spektrum magnitud logaritma selepas FFT Shift [0.0f .. 1.0f],
 *           bersaiz gridSize * gridSize (susunan baris-demi-baris)
 * @property highFrequencyEnergyRatio Nisbah tenaga pada jalur frekuensi tinggi (pinggir luar spektrum)
 *           berbanding jumlah tenaga keseluruhan; berguna untuk mengesan artifak upsampling GAN/Diffusion
 */
data class FftSpectrumResult(
    val gridSize: Int,
    val normalizedMagnitude: FloatArray,
    val highFrequencyEnergyRatio: Float
)

/**
 * Stage 2 Feature Extractor (Domain Frekuensi):
 * Menukar bingkai spatial (RGB) kepada peta spektrum frekuensi 2D menggunakan
 * algoritma Cooley-Tukey Radix-2 2D FFT untuk mendedahkan artifak grid berkala
 * yang ditinggalkan oleh penjana imej/video AI (GAN & Diffusion).
 */
class FftSpectrumExtractor(
    val gridSize: Int = DEFAULT_FFT_SIZE,
    private val applyHannWindow: Boolean = true
) {
    companion object {
        const val DEFAULT_FFT_SIZE = 128
        private const val FLOAT_BYTE_SIZE = 4
    }

    private val cosTable: FloatArray
    private val sinTable: FloatArray
    private val hannWindow1D: FloatArray

    init {
        require(gridSize >= 16 && (gridSize and (gridSize - 1)) == 0) {
            "gridSize mesti kuasa dua >= 16 (cth. 64, 128, 256), diterima: $gridSize"
        }

        val halfSize = gridSize shr 1
        cosTable = FloatArray(halfSize)
        sinTable = FloatArray(halfSize)
        for (k in 0 until halfSize) {
            val angle = -2.0 * PI * k / gridSize
            cosTable[k] = cos(angle).toFloat()
            sinTable[k] = sin(angle).toFloat()
        }

        hannWindow1D = FloatArray(gridSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (gridSize - 1)))).toFloat()
        }
    }

    /**
     * Titik masuk utama untuk aplikasi Android:
     * Mengambil Bitmap video bersih (hasil daripada TikTokFramePreprocessor),
     * menukarnya kepada skala kelabu, dan mengekstrak spektrum 2D FFT.
     */
    fun extractFromBitmap(cleanVideoBitmap: Bitmap): FftSpectrumResult {
        val scaled = if (cleanVideoBitmap.width == gridSize && cleanVideoBitmap.height == gridSize) {
            cleanVideoBitmap
        } else {
            Bitmap.createScaledBitmap(cleanVideoBitmap, gridSize, gridSize, true)
        }

        val totalPixels = gridSize * gridSize
        val pixels = IntArray(totalPixels)
        scaled.getPixels(pixels, 0, gridSize, 0, 0, gridSize, gridSize)

        val luminance = FloatArray(totalPixels)
        for (i in 0 until totalPixels) {
            val argb = pixels[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            // Piawaian ITU-R BT.601 luminance ternormal [0.0 .. 1.0]
            luminance[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
        }

        if (scaled !== cleanVideoBitmap) {
            scaled.recycle()
        }

        return extractFromLuminanceGrid(luminance)
    }

    /**
     * Teras matematik 2D FFT yang beroperasi pada tatasusunan Float tulen.
     * Boleh diuji terus melalui JUnit pada JVM laptop tanpa kelas Android Bitmap.
     */
    fun extractFromLuminanceGrid(luminanceGrid: FloatArray): FftSpectrumResult {
        val totalSize = gridSize * gridSize
        require(luminanceGrid.size == totalSize) {
            "Saiz input mestilah $totalSize piksel (${gridSize}x$gridSize), diterima: ${luminanceGrid.size}"
        }

        val real = FloatArray(totalSize)
        val imag = FloatArray(totalSize)

        // 1. Guna tetingkap Hann 2D untuk mengelak kebocoran spektrum pada sempadan potongan bingkai
        for (r in 0 until gridSize) {
            val rowOffset = r * gridSize
            val wy = if (applyHannWindow) hannWindow1D[r] else 1.0f
            for (c in 0 until gridSize) {
                val wx = if (applyHannWindow) hannWindow1D[c] else 1.0f
                real[rowOffset + c] = luminanceGrid[rowOffset + c] * wy * wx
            }
        }

        // 2. Jalankan 1D FFT pada setiap baris (horizontal pass)
        val rowRe = FloatArray(gridSize)
        val rowIm = FloatArray(gridSize)
        for (r in 0 until gridSize) {
            val offset = r * gridSize
            System.arraycopy(real, offset, rowRe, 0, gridSize)
            rowIm.fill(0f)

            fft1DInPlace(rowRe, rowIm)

            System.arraycopy(rowRe, 0, real, offset, gridSize)
            System.arraycopy(rowIm, 0, imag, offset, gridSize)
        }

        // 3. Jalankan 1D FFT pada setiap lajur (vertical pass)
        val colRe = FloatArray(gridSize)
        val colIm = FloatArray(gridSize)
        for (c in 0 until gridSize) {
            for (r in 0 until gridSize) {
                val idx = r * gridSize + c
                colRe[r] = real[idx]
                colIm[r] = imag[idx]
            }

            fft1DInPlace(colRe, colIm)

            for (r in 0 until gridSize) {
                val idx = r * gridSize + c
                real[idx] = colRe[r]
                imag[idx] = colIm[r]
            }
        }

        // 4. Kira magnitud logaritma: log(1 + sqrt(Re^2 + Im^2))
        val magnitude = FloatArray(totalSize)
        var maxMag = 1e-6f
        for (i in 0 until totalSize) {
            val mag = ln(1.0f + hypot(real[i], imag[i]))
            magnitude[i] = mag
            if (mag > maxMag) {
                maxMag = mag
            }
        }

        // 5. FFT Shift: alihkan komponen frekuensi rendah (DC) ke tengah spektrum
        fftShift2DInPlace(magnitude)

        // 6. Normalisasi ke julat [0.0f .. 1.0f] dan kira nisbah tenaga frekuensi tinggi
        val center = gridSize / 2.0f
        val highFreqCutoffRadius = center * 0.50f
        var totalEnergy = 0.0
        var highFreqEnergy = 0.0

        for (r in 0 until gridSize) {
            val dy = r - center
            val rowOffset = r * gridSize
            for (c in 0 until gridSize) {
                val idx = rowOffset + c
                val normVal = (magnitude[idx] / maxMag).coerceIn(0f, 1f)
                magnitude[idx] = normVal

                val dx = c - center
                val dist = hypot(dx, dy)
                val energy = (normVal * normVal).toDouble()
                totalEnergy += energy
                if (dist >= highFreqCutoffRadius) {
                    highFreqEnergy += energy
                }
            }
        }

        val hfRatio = if (totalEnergy > 0.0) {
            (highFreqEnergy / totalEnergy).toFloat()
        } else {
            0f
        }

        return FftSpectrumResult(
            gridSize = gridSize,
            normalizedMagnitude = magnitude,
            highFrequencyEnergyRatio = hfRatio
        )
    }

    /**
     * Menukar spektrum ternormal kepada Direct ByteBuffer format NHWC [1, gridSize, gridSize, 1]
     * untuk disuapkan ke cabang spektrum dalam model TFLite.
     */
    fun spectrumToByteBuffer(spectrumResult: FftSpectrumResult): ByteBuffer {
        val data = spectrumResult.normalizedMagnitude
        val buffer = ByteBuffer.allocateDirect(data.size * FLOAT_BYTE_SIZE).apply {
            order(ByteOrder.nativeOrder())
        }
        for (value in data) {
            buffer.putFloat(value)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Algoritma Cooley-Tukey Radix-2 1D FFT (in-place).
     */
    private fun fft1DInPlace(re: FloatArray, im: FloatArray) {
        val n = gridSize

        // Susun semula indeks mengikut bit-reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpRe = re[i]
                re[i] = re[j]
                re[j] = tmpRe

                val tmpIm = im[i]
                im[i] = im[j]
                im[j] = tmpIm
            }
        }

        // Pengiraan rama-rama (butterfly stages)
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val tableStep = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until halfLen) {
                    val tableIdx = k * tableStep
                    val cosVal = cosTable[tableIdx]
                    val sinVal = sinTable[tableIdx]

                    val evenIdx = i + k
                    val oddIdx = evenIdx + halfLen

                    val tRe = re[oddIdx] * cosVal - im[oddIdx] * sinVal
                    val tIm = re[oddIdx] * sinVal + im[oddIdx] * cosVal

                    re[oddIdx] = re[evenIdx] - tRe
                    im[oddIdx] = im[evenIdx] - tIm
                    re[evenIdx] += tRe
                    im[evenIdx] += tIm
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Menukar kedudukan empat sukuan (quadrants) matriks 2D supaya frekuensi 0 Hz
     * berada tepat di pusat imej spektrum.
     */
    private fun fftShift2DInPlace(matrix: FloatArray) {
        val half = gridSize shr 1
        for (r in 0 until half) {
            val topRow = r * gridSize
            val bottomRow = (r + half) * gridSize
            for (c in 0 until half) {
                // Tukar sukuan atas-kiri <-> bawah-kanan
                val idxTL = topRow + c
                val idxBR = bottomRow + (c + half)
                val tmp1 = matrix[idxTL]
                matrix[idxTL] = matrix[idxBR]
                matrix[idxBR] = tmp1

                // Tukar sukuan atas-kanan <-> bawah-kiri
                val idxTR = topRow + (c + half)
                val idxBL = bottomRow + c
                val tmp2 = matrix[idxTR]
                matrix[idxTR] = matrix[idxBL]
                matrix[idxBL] = tmp2
            }
        }
    }
}