package app.glance.launcher.ui.home

import app.glance.launcher.data.Task

/** The dial's Inbox complication count. Pure, tested on the JVM. */
object InboxCount {
    /**
     * dayGLANCE's own count when it sends one: its Inbox filters (project tasks hidden by default,
     * priority, tags, completed) and user lens, which the task rows do not carry (docs/decisions.md,
     * decision 55). Counting rows is the fallback for a build that sends none, and the demo.
     */
    fun of(appCount: Int?, tasks: List<Task>): Int =
        appCount ?: tasks.count { it.date == null && !it.completed }
}
