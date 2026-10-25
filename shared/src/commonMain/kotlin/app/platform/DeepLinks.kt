package app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Requests from outside the UI (a tapped notification, later a widget or a URL) to open a screen.
 * Platform code calls [openHabit]; App() navigates and calls [consumed].
 */
object DeepLinks {
    private val _habitId = MutableStateFlow<String?>(null)
    val habitId: StateFlow<String?> = _habitId.asStateFlow()

    fun openHabit(id: String) { _habitId.value = id }
    fun consumed() { _habitId.value = null }
}
