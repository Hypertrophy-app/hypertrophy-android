package app.hypertrophy.feature.today

import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ChallengeEntity
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayInsightsTest {
    @Test
    fun epochDayIsStableAcrossTimeZones() {
        val utc = TodayInsights.epochDayOf(0L, ZoneOffset.UTC)

        assertEquals(0L, utc)
        assertEquals(1L, TodayInsights.epochDayOf(86_400_000L, ZoneOffset.UTC))
    }

    @Test
    fun streakCountsTodayAndPreviousDays() {
        val today = 20_000L
        val days = listOf(today, today - 1, today - 2, today - 4)

        assertEquals(3, TodayInsights.streakDays(days, today))
    }

    @Test
    fun streakSurvivesBeforeTodaysWorkout() {
        val today = 20_000L

        assertEquals(2, TodayInsights.streakDays(listOf(today - 1, today - 2), today))
    }

    @Test
    fun streakIsZeroAfterADayOff() {
        val today = 20_000L

        assertEquals(0, TodayInsights.streakDays(listOf(today - 2, today - 3), today))
        assertEquals(0, TodayInsights.streakDays(emptyList(), today))
    }

    @Test
    fun weekStatsOnlyCountsTheLastSevenDays() {
        val today = LocalDate.of(2026, 11, 21).toEpochDay()
        val sessions = listOf(
            session(daysAgo = today, volume = 1_000.0),
            session(daysAgo = today - 6, volume = 500.0),
            session(daysAgo = today - 7, volume = 9_000.0),
        )

        val stats = TodayInsights.weekStats(sessions, today)

        assertEquals(2, stats.sessions)
        assertEquals(1_500.0, stats.volumeKg, 0.001)
    }

    @Test
    fun sessionsInWindowIncludesTheBoundaryDay() {
        val today = 20_000L
        val sessions = listOf(
            session(daysAgo = today, volume = 1.0),
            session(daysAgo = today - 29, volume = 1.0),
            session(daysAgo = today - 30, volume = 1.0),
        )

        assertEquals(2, TodayInsights.sessionsInWindow(sessions, days = 30, todayEpochDay = today))
    }

    @Test
    fun recoveryLevelUses24And72HourBoundaries() {
        val now = 1_000_000_000L

        assertEquals(
            RecoveryLevel.FATIGUED,
            TodayInsights.recoveryLevel(now - 1_000L, now),
        )
        assertEquals(
            RecoveryLevel.RECOVERING,
            TodayInsights.recoveryLevel(now - 48L * 3_600_000L, now),
        )
        assertEquals(
            RecoveryLevel.FRESH,
            TodayInsights.recoveryLevel(now - 100L * 3_600_000L, now),
        )
        assertEquals(RecoveryLevel.FRESH, TodayInsights.recoveryLevel(null, now))
    }

    @Test
    fun lastTrainedByMuscleIgnoresMalformedJsonAndUsesLatestSession() {
        val squat = ExerciseCatalog.exercises.first { it.id == "ex_back_squat" }
        val bench = ExerciseCatalog.exercises.first { it.id == "ex_bench_press" }
        val exerciseById = mapOf(squat.id to squat, bench.id to bench)
        val sessions = listOf(
            sessionWithSets(
                daysAgo = 10L,
                createdAt = 1_000L,
                setsJson = """[{"exerciseId":"ex_back_squat","reps":5,"weightKg":100}]""",
            ),
            sessionWithSets(
                daysAgo = 11L,
                createdAt = 2_000L,
                setsJson = """[{"exerciseId":"ex_back_squat","reps":8,"weightKg":100}]""",
            ),
            sessionWithSets(
                daysAgo = 12L,
                createdAt = 3_000L,
                setsJson = """[{"exerciseId":"ex_bench_press","reps":8,"weightKg":80}]""",
            ),
            sessionWithSets(
                daysAgo = 13L,
                createdAt = 4_000L,
                setsJson = "not-json",
            ),
        )

        val training = TodayInsights.lastTrainedByMuscle(sessions, exerciseById)

        assertEquals(listOf("Chest", "Quads"), training.map { it.label })
        assertEquals(2_000L, training.first { it.key == "Quads" }.lastTrainedAtMillis)
        assertEquals(3_000L, training.first { it.key == "Chest" }.lastTrainedAtMillis)
    }

    @Test
    fun defaultWeekSeedsSevenDaysWithTwoRestDays() {
        val week = TodayInsights.defaultWeek(
            todayEpochDay = 20_000L,
            nowMillis = 5L,
            userId = "local-user",
        )

        assertEquals(7, week.size)
        assertEquals(2, week.count { it.isRestDay })
        assertEquals(5, week.count { it.isKeyCompound })
        assertEquals(20_000L, week.first().scheduledDay)
        assertEquals(20_006L, week.last().scheduledDay)
    }

    @Test
    fun challengeSummaryFormatsTargetAndDeadline() {
        val now = 1_000_000_000L
        val challenge = ChallengeEntity(
            id = "challenge",
            squadId = "squad",
            title = "November volume",
            metricType = "total_volume",
            targetValue = 50_000,
            startsAt = now,
            endsAt = now + 3L * 86_400_000L,
            createdAt = now,
        )

        assertTrue(TodayInsights.challengeSummary(challenge, now).contains("50,000 kg"))
        assertTrue(TodayInsights.challengeSummary(challenge, now).contains("3 days left"))
    }

    @Test
    fun volumeUsesThousandsSeparators() {
        assertEquals("12,450", TodayInsights.formatVolume(12_450.4))
        assertEquals("0", TodayInsights.formatVolume(0.0))
    }

    private fun session(daysAgo: Long, volume: Double): WorkoutSessionEntity {
        val createdAt = LocalDate.ofEpochDay(daysAgo)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return WorkoutSessionEntity(
            id = "session-$daysAgo",
            userId = "local-user",
            squadId = null,
            title = "Session",
            startedAt = createdAt,
            endedAt = createdAt + 1_000L,
            totalVolumeKg = volume,
            avgFormScore = 100.0,
            aiRecapText = null,
            setsJson = "[]",
            createdAt = createdAt,
        )
    }

    private fun sessionWithSets(daysAgo: Long, createdAt: Long, setsJson: String): WorkoutSessionEntity =
        WorkoutSessionEntity(
            id = "session-$daysAgo",
            userId = "local-user",
            squadId = null,
            title = "Session",
            startedAt = createdAt,
            endedAt = createdAt + 1_000L,
            totalVolumeKg = 0.0,
            avgFormScore = 100.0,
            aiRecapText = null,
            setsJson = setsJson,
            createdAt = createdAt,
        )
}
