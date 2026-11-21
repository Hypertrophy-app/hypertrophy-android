package app.hypertrophy.core.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquadRefreshPlannerTest {
    @Test
    fun removesCachedSquadsMissingFromSnapshotAndMembers() {
        val stale = SquadRefreshPlanner.staleSquadIds(
            cachedSquadIds = listOf("squad_a", "squad_b"),
            freshSquadIds = listOf("squad_a"),
            squadIdsWithMembers = listOf("squad_a"),
        )

        assertEquals(listOf("squad_b"), stale)
    }

    @Test
    fun keepsCachedSquadThatStillHasMembers() {
        val stale = SquadRefreshPlanner.staleSquadIds(
            cachedSquadIds = listOf("squad_left", "squad_gone"),
            freshSquadIds = emptyList(),
            squadIdsWithMembers = listOf("squad_left"),
        )

        assertEquals(listOf("squad_gone"), stale)
    }

    @Test
    fun neverRemovesFreshSquads() {
        val stale = SquadRefreshPlanner.staleSquadIds(
            cachedSquadIds = listOf("squad_a", "squad_b"),
            freshSquadIds = listOf("squad_b"),
            squadIdsWithMembers = emptyList(),
        )

        assertEquals(listOf("squad_a"), stale)
    }

    @Test
    fun deduplicatesAndSortsStaleIds() {
        val stale = SquadRefreshPlanner.staleSquadIds(
            cachedSquadIds = listOf("squad_z", "squad_a", "squad_z"),
            freshSquadIds = emptyList(),
            squadIdsWithMembers = emptyList(),
        )

        assertEquals(listOf("squad_a", "squad_z"), stale)
    }

    @Test
    fun emptySnapshotAndEmptyCacheProduceNoWork() {
        val stale = SquadRefreshPlanner.staleSquadIds(
            cachedSquadIds = emptyList(),
            freshSquadIds = emptyList(),
            squadIdsWithMembers = emptyList(),
        )

        assertTrue(stale.isEmpty())
    }
}
