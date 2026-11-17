package app.hypertrophy.feature.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {
    private val store = DashboardStore(dataStore)

    val layout: StateFlow<DashboardLayout> = store.layout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardLayout())

    fun moveUp(card: TodayCard) {
        viewModelScope.launch { store.setLayout(layout.value.moveUp(card)) }
    }

    fun moveDown(card: TodayCard) {
        viewModelScope.launch { store.setLayout(layout.value.moveDown(card)) }
    }

    fun toggle(card: TodayCard) {
        viewModelScope.launch { store.setLayout(layout.value.toggle(card)) }
    }
}
