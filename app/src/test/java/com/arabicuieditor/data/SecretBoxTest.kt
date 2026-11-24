package com.arabicuieditor.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** على JVM لا يوجد Android Keystore: القيمة تُحفظ كما هي دون انهيار، والقيم القديمة تُقرأ كما هي. */
class SecretBoxTest {
    @Test fun emptyStaysEmpty() {
        assertEquals("", SecretBox.seal(""))
        assertEquals("", SecretBox.open(""))
    }

    @Test fun legacyPlainValueIsReadAsIs() {
        assertEquals("sk-abc123", SecretBox.open("sk-abc123"))
    }

    @Test fun corruptedEncryptedValueGivesEmptyInsteadOfCrashing() {
        assertEquals("", SecretBox.open("enc1:!!!ليس-base64"))
    }
}
