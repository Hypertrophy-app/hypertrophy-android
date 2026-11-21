package app.hypertrophy.feature.profile

import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.PersonalRecordEntity
import app.hypertrophy.core.data.local.PersonalRecordWithExercise
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileInsightsTest {
    @Test
    fun epleyEstimateGuardsInvalidInput() {
        assertEquals(0.0, ProfileInsights.estimatedOneRepMax(100.0, 0), 0.000001)
        assertEquals(0.0, ProfileInsights.estimatedOneRepMax(0.0, 5), 0.000001)
        assertEquals(0.0, ProfileInsights.estimatedOneRepMax(-50.0, 5), 0.000001)
        assertEquals(110.0, ProfileInsights.estimatedOneRepMax(100.0, 3), 0.000001)
    }

    @Test
    fun parseSetsAppliesLegacyDefaultsForPartialRows() {
        val sets = ProfileInsights.parseSets(
            """[{"exerciseId":"ex_bench_press","reps":5,"weightKg":100}]""",
        )

        assertEquals(1, sets.size)
        assertEquals("ex_bench_press", sets.first().exerciseId)
        assertEquals(0f, sets.first().romPercent, 0.0001f)
        assertEquals("", sets.first().tempoLabel)
        assertEquals(100, sets.first().formScore)
    }

    @Test
    fun parseSetsReturnsEmptyForMalformedJson() {
        assertTrue(ProfileInsights.parseSets("").isEmpty())
        assertTrue(ProfileInsights.parseSets("not-json").isEmpty())
        assertTrue(ProfileInsights.parseSets("{}").isEmpty())
    }

    @Test
    fun progressionUsesTheBestSetPerSessionAndSortsByTime() {
        val sessions = listOf(
            session(
                id = "later",
                createdAt = 2_000L,
                setsJson = """
                    [
                      {"exerciseId":"ex_bench_press","reps":5,"weightKg":90},
                      {"exerciseId":"ex_bench_press","reps":3,"weightKg":105},
                      {"exerciseId":"ex_back_squat","reps":5,"weightKg":140}
                    ]
                """,
            ),
            session(
                id = "earlier",
                createdAt = 1_000L,
                setsJson = """[{"exerciseId":"ex_bench_press","reps":5,"weightKg":100}]""",
            ),
        )

        val points = ProfileInsights.progressionForExercise(sessions, "ex_bench_press")

        assertEquals(2, points.size)
        assertEquals(1_000L, points[0].createdAtMillis)
        assertEquals(100.0 * (1.0 + 5.0 / 30.0), points[0].estimated1RmKg, 0.000001)
        assertEquals(2_000L, points[1].createdAtMillis)
        assertEquals(105.0 * (1.0 + 3.0 / 30.0), points[1].estimated1RmKg, 0.000001)
    }

    @Test
    fun progressionIgnoresSessionsWithoutTheExercise() {
        val sessions = listOf(
            session(id = "squat", createdAt = 1_000L, setsJson = """[{"exerciseId":"ex_back_squat","reps":5,"weightKg":100}]"""),
        )

        val points = ProfileInsights.progressionForExercise(sessions, "ex_bench_press")

        assertTrue(points.isEmpty())
    }

    @Test
    fun exerciseOptionsAreSortedByBestEstimatedOneRepMax() {
        val exercises = ExerciseCatalog.exercises.associateBy { it.id }
        val sessions = listOf(
            session(
                id = "session",
                createdAt = 1_000L,
                setsJson = """
                    [
                      {"exerciseId":"ex_back_squat","reps":5,"weightKg":140},
                      {"exerciseId":"ex_bench_press","reps":5,"weightKg":100}
                    ]
                """,
            ),
        )

        val options = ProfileInsights.exerciseOptions(sessions, exercises)

        assertEquals(2, options.size)
        assertEquals("ex_back_squat", options[0].exerciseId)
        assertEquals("Back Squat", options[0].name)
        assertEquals("ex_bench_press", options[1].exerciseId)
        assertTrue(options[0].bestEstimated1RmKg > options[1].bestEstimated1RmKg)
    }

    @Test
    fun totalsUseTheLastThirtyDaysAndCurrentStreak() {
        val today = LocalDate.now().toEpochDay()
        val sessions = listOf(
            session(id = "today", createdAt = midday(today), setsJson = "[]", volume = 1_000.0),
            session(id = "yesterday", createdAt = midday(today - 1L), setsJson = "[]", volume = 500.0),
            session(id = "old", createdAt = midday(today - 45L), setsJson = "[]", volume = 9_000.0),
        )

        val totals = ProfileInsights.totals(sessions, today)

        assertEquals(3, totals.sessions)
        assertEquals(10_500.0, totals.volumeKg, 0.001)
        assertEquals(2, totals.streakDays)
        assertEquals(2, totals.sessionsLast30Days)
    }

    @Test
    fun muscleRecoveryMapsCatalogueKeysAndKeepsTheLatestSession() {
        val exercises = ExerciseCatalog.exercises.associateBy { it.id }
        val sessions = listOf(
            session(
                id = "older",
                createdAt = 1_000L,
                setsJson = """[{"exerciseId":"ex_back_squat","reps":5,"weightKg":100}]""",
            ),
            session(
                id = "newer",
                createdAt = 2_000L,
                setsJson = """
                    [
                      {"exerciseId":"ex_bench_press","reps":5,"weightKg":80},
                      {"exerciseId":"ex_back_squat","reps":3,"weightKg":120}
                    ]
                """,
            ),
        )

        val recovery = ProfileInsights.muscleRecovery(sessions, exercises)

        assertEquals(MuscleGroup.entries.size, recovery.size)
        assertEquals(2_000L, recovery.first { it.muscle == MuscleGroup.QUADS }.lastTrainedAtMillis)
        assertEquals(2_000L, recovery.first { it.muscle == MuscleGroup.CHEST }.lastTrainedAtMillis)
        assertEquals(null, recovery.first { it.muscle == MuscleGroup.CALVES }.lastTrainedAtMillis)
    }

    @Test
    fun catalogKeysMapToAnatomyGroups() {
        assertEquals(MuscleGroup.QUADS, MuscleGroup.fromCatalogKey("quadriceps"))
        assertEquals(MuscleGroup.QUADS, MuscleGroup.fromCatalogKey("quads"))
        assertEquals(MuscleGroup.ABS, MuscleGroup.fromCatalogKey("core"))
        assertEquals(MuscleGroup.TRAPS, MuscleGroup.fromCatalogKey("rhomboids"))
        assertEquals(null, MuscleGroup.fromCatalogKey("unknown_muscle"))
    }

    @Test
    fun badgesReportProgressUnlocksAndNewlyEarned() {
        val totals = ProfileTotals(
            sessions = 12,
            volumeKg = 600_000.0,
            streakDays = 3,
            sessionsLast30Days = 8,
        )
        val prs = listOf(
            PersonalRecordWithExercise(
                record = PersonalRecordEntity(
                    id = "pr1",
                    userId = "local-user",
                    exerciseId = "ex_bench_press",
                    maxWeightKg = 105.0,
                    maxReps = 3,
                    estimated1rm = 115.5,
                    achievedAt = 1_000L,
                ),
                exerciseName = "Bench Press",
                primaryMuscle = "chest",
            ),
        )
        val persisted = mapOf("first_session" to 123_000L)

        val badges = ProfileInsights.badges(totals, prs, persisted)

        val first = badges.first { it.definition.code == "first_session" }
        assertTrue(first.unlocked)
        assertEquals(123_000L, first.unlockedAtMillis)

        val ten = badges.first { it.definition.code == "ten_sessions" }
        assertFalse(ten.unlocked)
        assertEquals(10, ten.progress)
        assertEquals("10 / 10", ten.progressLabel)

        val volume = badges.first { it.definition.code == "volume_500k" }
        assertEquals(500_000, volume.progress)

        val streak = badges.first { it.definition.code == "streak_7" }
        assertEquals(3, streak.progress)

        val earnedCodes = ProfileInsights.newlyEarned(badges).map { it.code }
        assertTrue("ten_sessions" in earnedCodes)
        assertTrue("volume_500k" in earnedCodes)
        assertTrue("pr_club" in earnedCodes)
        assertTrue("century_club" in earnedCodes)
        assertTrue("first_session" !in earnedCodes)
    }

    @Test
    fun formatCompactVolumeKeepsSmallNumbersExact() {
        assertEquals("0", ProfileInsights.formatCompactVolume(-10.0))
        assertEquals("9,999", ProfileInsights.formatCompactVolume(9_999.0))
        assertEquals("12.4k", ProfileInsights.formatCompactVolume(12_400.0))
        assertEquals("1.5M", ProfileInsights.formatCompactVolume(1_500_000.0))
    }

    private fun session(
        id: String,
        createdAt: Long,
        setsJson: String,
        volume: Double = 0.0,
    ): WorkoutSessionEntity = WorkoutSessionEntity(
        id = id,
        userId = "local-user",
        squadId = null,
        title = "Session",
        startedAt = createdAt,
        endedAt = createdAt + 1_000L,
        totalVolumeKg = volume,
        avgFormScore = 100.0,
        aiRecapText = null,
        setsJson = setsJson,
        createdAt = createdAt,
    )

    private fun midday(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
