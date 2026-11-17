package app.hypertrophy.feature.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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
) {
    fun isVisible(card: TodayCard): Boolean = card in visible

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

    fun visibleCards(): List<TodayCard> = order.filter { it in visible }
}

class DashboardStore(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val ORDER = stringPreferencesKey("today_card_order")
        val VISIBLE = stringPreferencesKey("today_card_visible")
    }

    val layout: Flow<DashboardLayout> = dataStore.data.map { prefs ->
        val orderRaw = prefs[Keys.ORDER]
        val visibleRaw = prefs[Keys.VISIBLE]
        val order = orderRaw?.split(",")
            ?.mapNotNull { name -> TodayCard.entries.firstOrNull { it.name == name } }
            ?.distinct()
            ?.let { it + (TodayCard.entries - it.toSet()) }
            ?: TodayCard.entries.toList()
        val visible = visibleRaw?.split(",")
            ?.mapNotNull { name -> TodayCard.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: TodayCard.entries.toSet()
        DashboardLayout(order = order, visible = visible)
    }

    suspend fun setLayout(layout: DashboardLayout) {
        dataStore.edit { prefs ->
            prefs[Keys.ORDER] = layout.order.joinToString(",") { it.name }
            prefs[Keys.VISIBLE] = layout.visible.joinToString(",") { it.name }
        }
    }
}
