package app.hypertrophy.feature.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * The five home cards the user can reorder or hide. Persisted in DataStore so
 * the dashboard layout survives process death and reinstalls of the same data.
 */
enum class TodayCard(val key: String, val defaultVisible: Boolean) {
    TODAYS_PLAN("todays_plan", true),
    SQUAD_LIVE_TICKER("squad_live_ticker", true),
    ACTIVE_CHALLENGE("active_challenge", true),
    MUSCLE_RECOVERY_SUMMARY("muscle_recovery_summary", true),
    CONSISTENCY_STRIP("consistency_strip", true),
}

data class DashboardLayout(
    val order: List<TodayCard> = TodayCard.entries.toList(),
    val visible: Set<TodayCard> = TodayCard.entries.toSet(),
    val collapsed: Set<TodayCard> = emptySet(),
) {
    fun isVisible(card: TodayCard): Boolean = card in visible

    fun isCollapsed(card: TodayCard): Boolean = card in collapsed

    fun moveUp(card: TodayCard): DashboardLayout {
        val index = order.indexOf(card)
        if (index <= 0) return this
        val mutable = order.toMutableList()
        mutable[index - 1] = mutable[index].also { mutable[index] = mutable[index - 1] }
        return copy(order = mutable)
    }

    fun moveDown(card: TodayCard): DashboardLayout {
        val index = order.indexOf(card)
        if (index < 0 || index >= order.lastIndex) return this
        val mutable = order.toMutableList()
        mutable[index + 1] = mutable[index].also { mutable[index] = mutable[index + 1] }
        return copy(order = mutable)
    }

    fun toggle(card: TodayCard): DashboardLayout =
        if (card in visible) copy(visible = visible - card) else copy(visible = visible + card)

    fun setVisible(card: TodayCard, isVisible: Boolean): DashboardLayout =
        if (isVisible == (card in visible)) this else toggle(card)

    fun toggleCollapsed(card: TodayCard): DashboardLayout =
        if (card in collapsed) copy(collapsed = collapsed - card) else copy(collapsed = collapsed + card)

    fun collapseAll(): DashboardLayout = copy(collapsed = visible)

    fun expandAll(): DashboardLayout = copy(collapsed = emptySet())

    fun visibleCards(): List<TodayCard> = order.filter { it in visible }
}

/**
 * One-shot bridge from the shell's Quick Workout launcher to the dashboard so
 * starting a quick workout leaves every visible card collapsed behind it.
 */
object DashboardCollapseSignal {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val collapse: SharedFlow<Unit> = requests.asSharedFlow()

    fun requestCollapse() {
        requests.tryEmit(Unit)
    }
}

class DashboardStore(
    private val dataStore: DataStore<Preferences>,
    private val userId: String,
) {
    // Layout preferences belong to the signed-in athlete, so one account never
    // sees another account's card order or hidden cards.
    private val orderKey = stringPreferencesKey("$userId.today_card_order")
    private val visibleKey = stringPreferencesKey("$userId.today_card_visible")
    private val collapsedKey = stringPreferencesKey("$userId.today_card_collapsed")

    val layout: Flow<DashboardLayout> = dataStore.data.map { prefs ->
        val orderRaw = prefs[orderKey]
        val visibleRaw = prefs[visibleKey]
        val order = orderRaw?.split(",")
            ?.mapNotNull { name -> TodayCard.entries.firstOrNull { it.name == name } }
            ?.distinct()
            ?.let { it + (TodayCard.entries - it.toSet()) }
            ?: TodayCard.entries.toList()
        val visible = visibleRaw?.split(",")
            ?.mapNotNull { name -> TodayCard.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: TodayCard.entries.toSet()
        val collapsed = prefs[collapsedKey]?.split(",")
            ?.mapNotNull { name -> TodayCard.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: emptySet()
        DashboardLayout(order = order, visible = visible, collapsed = collapsed.filter { it in visible }.toSet())
    }

    suspend fun setLayout(layout: DashboardLayout) {
        dataStore.edit { prefs ->
            prefs[orderKey] = layout.order.joinToString(",") { it.name }
            prefs[visibleKey] = layout.visible.joinToString(",") { it.name }
            prefs[collapsedKey] = layout.collapsed.joinToString(",") { it.name }
        }
    }
}
