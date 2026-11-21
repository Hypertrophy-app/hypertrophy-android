package app.hypertrophy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A cached squad together with the local user's role inside it. */
data class SquadWithRole(
    @Embedded val squad: SquadEntity,
    @ColumnInfo(name = "role") val role: String,
)

/**
 * Offline-first squad cache. The UI reads squads and rosters from Room; the
 * network layer writes a full snapshot through [refreshSquadsForUser] and the
 * outbox handles member-visible mutations.
 */
@Dao
interface SquadDao {
    @Query(
        """
        SELECT squads.*, squad_members.role AS role FROM squads
        INNER JOIN squad_members ON squad_members.squad_id = squads.id
        WHERE squad_members.user_id = :userId
        ORDER BY squads.name ASC
        """,
    )
    fun observeSquadsForUser(userId: String): Flow<List<SquadWithRole>>

    @Query("SELECT * FROM squads WHERE id = :squadId LIMIT 1")
    fun observeSquad(squadId: String): Flow<SquadEntity?>

    @Query("SELECT * FROM squads WHERE id = :squadId LIMIT 1")
    suspend fun findSquad(squadId: String): SquadEntity?

    @Query("SELECT * FROM squad_members WHERE squad_id = :squadId ORDER BY joined_at ASC")
    fun observeSquadMembers(squadId: String): Flow<List<SquadMemberEntity>>

    @Query("SELECT * FROM squad_members WHERE squad_id = :squadId AND user_id = :userId LIMIT 1")
    suspend fun findSquadMember(squadId: String, userId: String): SquadMemberEntity?

    @Query(
        """
        SELECT squads.id FROM squads
        INNER JOIN squad_members ON squad_members.squad_id = squads.id
        WHERE squad_members.user_id = :userId
        """,
    )
    suspend fun cachedSquadIdsForUser(userId: String): List<String>

    @Query("SELECT DISTINCT squad_id FROM squad_members")
    suspend fun squadIdsWithMembers(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSquads(squads: List<SquadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSquadMembers(members: List<SquadMemberEntity>)

    @Query("DELETE FROM squad_members WHERE squad_id IN (:squadIds)")
    suspend fun deleteSquadMembers(squadIds: List<String>)

    @Query("DELETE FROM squad_members WHERE user_id = :userId AND squad_id NOT IN (:keptSquadIds)")
    suspend fun deleteSquadMembershipsForUserExcept(userId: String, keptSquadIds: List<String>)

    @Query("DELETE FROM squad_members WHERE user_id = :userId")
    suspend fun deleteSquadMembershipsForUser(userId: String)

    @Query("DELETE FROM squads WHERE id IN (:squadIds)")
    suspend fun deleteSquadsByIds(squadIds: List<String>)

    /**
     * Replaces the local squad cache with one complete server snapshot.
     *
     * [squads] is every squad the user belongs to and [members] the complete
     * roster for those squads. Rows for squads the user left are removed, and
     * cached squads missing from the snapshot are garbage collected only when
     * no member row still references them. Not suitable for partial pages.
     */
    @Transaction
    suspend fun refreshSquadsForUser(
        userId: String,
        squads: List<SquadEntity>,
        members: List<SquadMemberEntity>,
    ) {
        val freshSquadIds = squads.map { it.id }.distinct()
        val cachedSquadIds = cachedSquadIdsForUser(userId)

        upsertSquads(squads)

        if (freshSquadIds.isNotEmpty()) {
            // Replace rosters so departed members stop showing up.
            deleteSquadMembers(freshSquadIds)
        }
        val freshSquadIdSet = freshSquadIds.toSet()
        upsertSquadMembers(members.filter { it.squadId in freshSquadIdSet })

        if (freshSquadIds.isEmpty()) {
            deleteSquadMembershipsForUser(userId)
        } else {
            deleteSquadMembershipsForUserExcept(userId, freshSquadIds)
        }

        val staleSquadIds = SquadRefreshPlanner.staleSquadIds(
            cachedSquadIds = cachedSquadIds,
            freshSquadIds = freshSquadIds,
            squadIdsWithMembers = squadIdsWithMembers(),
        )
        if (staleSquadIds.isNotEmpty()) {
            deleteSquadsByIds(staleSquadIds)
        }
    }
}

/**
 * Pure stale-row rules for [SquadDao.refreshSquadsForUser]. Kept outside the
 * DAO so they can be unit tested without a database.
 */
internal object SquadRefreshPlanner {
    /**
     * Cached squads that are absent from the fresh snapshot and no longer have
     * any member row. A squad that still has members is never removed.
     */
    fun staleSquadIds(
        cachedSquadIds: Collection<String>,
        freshSquadIds: Collection<String>,
        squadIdsWithMembers: Collection<String>,
    ): List<String> {
        val fresh = freshSquadIds.toSet()
        val occupied = squadIdsWithMembers.toSet()
        return cachedSquadIds.asSequence()
            .distinct()
            .filter { it !in fresh && it !in occupied }
            .sorted()
            .toList()
    }
}
