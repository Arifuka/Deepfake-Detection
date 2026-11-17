package com.example.edgeguard.aidetection

import com.example.edgeguard.aidetection.FftSpectrumExtractor
import com.example.edgeguard.aidetection.TikTokFramePreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage2AlgorithmVerificationTest {

    @Test
    fun verifyTikTokCleanVideoCropBounds_acrossMultipleResolutions() {
        val preprocessor = TikTokFramePreprocessor()

        // Ujian 1: Skrin FHD+ standard (1080 x 2400)
        val boundsFhd = preprocessor.calculateCropPixelBounds(1080, 2400)
        val x = boundsFhd[0]
        val y = boundsFhd[1]
        val w = boundsFhd[2]
        val h = boundsFhd[3]

        assertTrue("X mesti >= 0", x >= 0)
        assertTrue("Y mesti >= 0", y >= 0)
        assertTrue("X + Width tidak boleh melebihi lebar skrin", x + w <= 1080)
        assertTrue("Y + Height tidak boleh melebihi tinggi skrin", y + h <= 2400)
        assertEquals(22, x)    // 2% daripada 1080
        assertEquals(240, y)   // 10% daripada 2400

        // Ujian 2: Skrin HD+ (720 x 1600)
        val boundsHd = preprocessor.calculateCropPixelBounds(720, 1600)
        assertTrue(boundsHd[0] + boundsHd[2] <= 720)
        assertTrue(boundsHd[1] + boundsHd[3] <= 1600)
    }

    @Test
    fun verify2dFftDetectsHighFrequencyPeriodicGridArtifacts() {
        val size = 64
        val extractor = FftSpectrumExtractor(gridSize = size, applyHannWindow = false)

        // 1. Imej semula jadi licin (kecerunan lembut tanpa artifak grid)
        val smoothNaturalGrid = FloatArray(size * size) { idx ->
            val r = idx / size
            val c = idx % size
            ((r + c).toFloat() / (2f * size))
        }
        val smoothResult = extractor.extractFromLuminanceGrid(smoothNaturalGrid)

        // 2. Imej sintetik dengan corak grid berkala (simulasi artifak upsampling GAN/Diffusion)
        val checkerboardArtifactGrid = FloatArray(size * size) { idx ->
            val r = idx / size
            val c = idx % size
            if ((r + c) % 2 == 0) 0.9f else 0.1f
        }
        val artifactResult = extractor.extractFromLuminanceGrid(checkerboardArtifactGrid)

        // Sahkan semua nilai spektrum ternormal berada dalam julat sah [0.0f .. 1.0f]
        assertTrue(artifactResult.normalizedMagnitude.all { it in 0.0f..1.0f })

        // Sahkan tenaga frekuensi tinggi meningkat mendadak bagi corak artifak grid berkala
        println("Smooth Natural HF Ratio: ${smoothResult.highFrequencyEnergyRatio}")
        println("Grid Artifact HF Ratio:  ${artifactResult.highFrequencyEnergyRatio}")

        assertTrue(
            "Artifak grid berkala mesti menghasilkan nisbah tenaga frekuensi tinggi lebih besar daripada imej licin",
            artifactResult.highFrequencyEnergyRatio > smoothResult.highFrequencyEnergyRatio
        )
    }
}