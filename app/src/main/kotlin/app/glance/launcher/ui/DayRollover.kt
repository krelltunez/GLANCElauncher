package app.glance.launcher.ui

import java.time.LocalDate

/**
 * The date turning over (midnight, or a time-zone change): the views that were on today move to
 * the new today, and a day the user went to stays where it is. Pure: tested on the JVM.
 *
 * The dial's day and the Planning month's selected day each follow when they were on the old
 * today. The month shown follows only with the selected day, and only when it was the old
 * today's month, so a month the user paged to is left alone.
 */
object DayRollover {
    data class Days(val dial: LocalDate, val planningMonth: LocalDate, val planningSelected: LocalDate)

    fun follow(days: Days, oldToday: LocalDate, newToday: LocalDate): Days {
        if (oldToday == newToday) return days
        val selectedFollows = days.planningSelected == oldToday
        return Days(
            dial = if (days.dial == oldToday) newToday else days.dial,
            planningMonth = if (selectedFollows && days.planningMonth == oldToday.withDayOfMonth(1)) newToday.withDayOfMonth(1) else days.planningMonth,
            planningSelected = if (selectedFollows) newToday else days.planningSelected,
        )
    }
}
