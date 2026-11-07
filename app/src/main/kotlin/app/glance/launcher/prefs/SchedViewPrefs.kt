package app.glance.launcher.prefs

/**
 * The SCHED agenda's view settings, kept across launches: the filter selections (colours, tags,
 * projects) and the three show/hide toggles. dayGLANCE keeps its toggles in localStorage the
 * same way. Pure: serialisation is tested on the JVM.
 *
 * One line per setting, the key then its values, tab-separated. A line it does not know, or a
 * flag it cannot read, falls back to the default.
 */
data class SchedViewPrefs(
    val colors: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val projectIds: Set<String> = emptySet(),
    val showEmptyDays: Boolean = false,
    val hideCompleted: Boolean = false,
    val hidePastEvents: Boolean = false,
) {
    fun serialise(): String = listOf(
        listOf(COLORS) + colors.sorted(),
        listOf(TAGS) + tags.sorted(),
        listOf(PROJECTS) + projectIds.sorted(),
        listOf(EMPTY_DAYS, showEmptyDays.toString()),
        listOf(HIDE_COMPLETED, hideCompleted.toString()),
        listOf(HIDE_PAST_EVENTS, hidePastEvents.toString()),
    ).joinToString("\n") { it.joinToString("\t") }

    companion object {
        private const val COLORS = "colors"
        private const val TAGS = "tags"
        private const val PROJECTS = "projects"
        private const val EMPTY_DAYS = "showEmptyDays"
        private const val HIDE_COMPLETED = "hideCompleted"
        private const val HIDE_PAST_EVENTS = "hidePastEvents"

        fun parse(text: String?): SchedViewPrefs {
            if (text.isNullOrBlank()) return SchedViewPrefs()
            val lines = text.split('\n').map { it.split('\t') }.filter { it.isNotEmpty() }.associate { it[0] to it.drop(1) }
            fun set(key: String) = lines[key].orEmpty().filter { it.isNotBlank() }.toSet()
            fun flag(key: String) = lines[key]?.firstOrNull()?.toBooleanStrictOrNull() ?: false
            return SchedViewPrefs(
                colors = set(COLORS), tags = set(TAGS), projectIds = set(PROJECTS),
                showEmptyDays = flag(EMPTY_DAYS), hideCompleted = flag(HIDE_COMPLETED), hidePastEvents = flag(HIDE_PAST_EVENTS),
            )
        }
    }
}
