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

class SquadCacheMappingTest {
    @Test
    fun roomRoundTripKeepsSquadFieldsAndRole() {
        val dto = SquadDto(
            id = "squad-1",
            name = "Iron Guild",
            inviteCode = "ABC123",
            ownerId = "user-1",
            createdAt = 5_000L,
            role = "owner",
        )

        val entity = dto.toEntity()
        val restored = entity.toDto(role = "owner")

        assertEquals(dto, restored)
        assertEquals("ABC123", entity.inviteCode)
        assertEquals(5_000L, entity.createdAt)
    }

    @Test
    fun membershipFallsBackToAthleteWhenTheServerOmitsARole() {
        val dto = SquadDto(id = "squad-1", name = "Iron Guild")

        val membership = dto.toMembership(userId = "local-user", joinedAt = 9_000L)

        assertEquals("squad-1", membership.squadId)
        assertEquals("local-user", membership.userId)
        assertEquals("athlete", membership.role)
        assertEquals(9_000L, membership.joinedAt)
    }

    @Test
    fun groupQueueClampsNothingButKeepsTheRequestedRest() {
        val queue = SquadViewModel.defaultGroupQueue(restSeconds = 90)

        assertEquals(3, queue.size)
        assertTrue(queue.all { it.restSeconds == 90 })
        assertEquals("ex_back_squat", queue[0].exerciseId)
        assertEquals("ex_deadlift", queue[2].exerciseId)
    }
}
