package app.hypertrophy.feature.squad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeTest {
    @Test
    fun validSixCharCodesPass() {
        assertTrue(InviteCode.isValid("ABC123"))
        assertTrue(InviteCode.isValid("  abc123  "))
    }

    @Test
    fun wrongLengthFails() {
        assertFalse(InviteCode.isValid("ABC12"))
        assertFalse(InviteCode.isValid("ABC1234"))
    }

    @Test
    fun nonAlphanumericFails() {
        assertFalse(InviteCode.isValid("ABC-12"))
        assertFalse(InviteCode.isValid("ABC 12"))
    }

    @Test
    fun normalizeUppercases() {
        assertEquals("ABC123", InviteCode.normalize("abc123"))
    }

    @Test
    fun deepLinkUsesCode() {
        assertTrue(InviteCode.deepLink("abc123").endsWith("/ABC123"))
    }
}

class PresenceStatusTest {
    @Test
    fun fromWireParsesKnown() {
        assertEquals(PresenceStatus.RESTING, PresenceStatus.fromWire("resting"))
        assertEquals(PresenceStatus.WORKING_OUT, PresenceStatus.fromWire("WORKING_OUT"))
    }

    @Test
    fun fromWireDefaultsToIdle() {
        assertEquals(PresenceStatus.IDLE, PresenceStatus.fromWire(null))
        assertEquals(PresenceStatus.IDLE, PresenceStatus.fromWire("garbage"))
    }
}
