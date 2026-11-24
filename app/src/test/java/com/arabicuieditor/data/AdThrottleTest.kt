package com.arabicuieditor.data

import com.arabicuieditor.ads.AdThrottle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdThrottleTest {
    @Test fun firstShowIsAllowedThenBlockedUntilGapPasses() {
        val t = AdThrottle(minGapMs = 1000)
        assertTrue(t.canShow(0))
        t.markShown(0)
        assertFalse(t.canShow(999))
        assertTrue(t.canShow(1000))
    }

    @Test fun markingResetsTheWindow() {
        val t = AdThrottle(minGapMs = 1000)
        t.markShown(5000)
        assertFalse(t.canShow(5500))
        t.markShown(6000)
        assertFalse(t.canShow(6500))
        assertTrue(t.canShow(7000))
    }
}
