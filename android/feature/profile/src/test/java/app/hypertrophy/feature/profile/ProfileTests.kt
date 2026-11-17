package app.hypertrophy.feature.profile

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RecoveryMapTest {
    @Test
    fun nullLastTrainedIsPrime() {
        assertEquals(RecoveryStatus.PRIME, recoveryStatus(null, 1_000_000L))
    }

    @Test
    fun under24HoursIsFatigued() {
        val now = 10_000_000L
        val trained = now - TimeUnit.HOURS.toMillis(12)
        assertEquals(RecoveryStatus.FATIGUED, recoveryStatus(trained, now))
    }

    @Test
    fun between24And72HoursIsRecovering() {
        val now = 10_000_000L
        val trained = now - TimeUnit.HOURS.toMillis(48)
        assertEquals(RecoveryStatus.RECOVERING, recoveryStatus(trained, now))
    }

    @Test
    fun over72HoursIsPrime() {
        val now = 10_000_000L
        val trained = now - TimeUnit.HOURS.toMillis(100)
        assertEquals(RecoveryStatus.PRIME, recoveryStatus(trained, now))
    }

    @Test
    fun recoveryColorMapsToPhoenixTokens() {
        assertEquals(Color(0xFFFF2A4B), recoveryColor(RecoveryStatus.FATIGUED))
        assertEquals(Color(0xFFFF6B1A), recoveryColor(RecoveryStatus.RECOVERING))
        assertEquals(Color(0xFFFFB800), recoveryColor(RecoveryStatus.PRIME))
    }
}

class HeatmapTest {
    @Test
    fun buildHeatmapHasRequestedLength() {
        val nowDay = 1000L
        val days = buildHeatmap(emptyList(), 90, nowDay)
        assertEquals(90, days.size)
        assertEquals(nowDay - 89, days.first().epochDay)
        assertEquals(nowDay, days.last().epochDay)
    }

    @Test
    fun buildHeatmapCountsSessions() {
        val nowDay = 1000L
        val days = buildHeatmap(listOf(1000L, 1000L, 999L), 90, nowDay)
        val last = days.last()
        val prev = days[days.size - 2]
        assertEquals(2, last.sessionCount)
        assertEquals(1, prev.sessionCount)
    }

    @Test
    fun heatmapIntensityScales() {
        assertEquals(0f, heatmapIntensity(0), 0.001f)
        assertTrue(heatmapIntensity(3) > heatmapIntensity(1))
    }
}
