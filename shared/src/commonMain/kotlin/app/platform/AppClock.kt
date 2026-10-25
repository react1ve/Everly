package app.platform

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface AppClock {
    fun today(): LocalDate
    fun now(): LocalDateTime
    /** Emits the current date and again whenever the day (or the time zone) changes. */
    val todayFlow: Flow<LocalDate>
}

class SystemAppClock : AppClock {
    private fun zone() = TimeZone.currentSystemDefault()
    override fun today(): LocalDate = Clock.System.todayIn(zone())
    override fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(zone())
    override val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(30_000)
        }
    }.distinctUntilChanged()
}
