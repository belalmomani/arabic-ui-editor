package com.arabicuieditor.data

import com.arabicuieditor.data.model.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun blackOnWhiteIs21() = assertEquals(21.0, Contrast.ratio(black, white), 0.01)

    @Test fun sameColorIs1() = assertEquals(1.0, Contrast.ratio(white, white), 0.001)

    @Test fun symmetric() =
        assertEquals(Contrast.ratio(0xFF336699.toInt(), white), Contrast.ratio(white, 0xFF336699.toInt()), 1e-9)

    @Test fun grayOnWhiteKnownValue() {
        // #767676 على الأبيض ≈ 4.54 (أصغر رمادي يجتاز AA)
        val r = Contrast.ratio(0xFF767676.toInt(), white)
        assertTrue(r in 4.5..4.6)
        assertEquals(Contrast.Level.AA, Contrast.level(r))
    }

    @Test fun levels() {
        assertEquals(Contrast.Level.FAIL, Contrast.level(2.0))
        assertEquals(Contrast.Level.AA_LARGE_ONLY, Contrast.level(3.5))
        assertEquals(Contrast.Level.AA, Contrast.level(5.0))
        assertEquals(Contrast.Level.AAA, Contrast.level(8.0))
    }

    @Test fun transparentBackgroundBlendsOverBackdrop() {
        val clearBg = 0x00000000
        // خلفية شفافة تُعامل كالأبيض: الأسود عليها 21
        assertEquals(21.0, Contrast.ratio(black, clearBg), 0.01)
    }

    @Test fun halfTransparentTextLowersContrast() {
        val half = 0x80000000.toInt()
        assertTrue(Contrast.ratio(half, white) < 21.0)
    }

    @Test fun largeTextThreshold() {
        assertTrue(Contrast.isLargeText(18))
        assertTrue(!Contrast.isLargeText(14))
    }
}
