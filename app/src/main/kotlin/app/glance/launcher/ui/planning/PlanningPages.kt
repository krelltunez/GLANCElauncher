package app.glance.launcher.ui.planning

import app.glance.launcher.data.Availability
import app.glance.launcher.data.DataSource
import app.glance.launcher.data.provider.GlanceApp

/**
 * Which pages the launcher has and in what order (D80): Home, then one planning page per app,
 * each a swipe further from Home. Pure, so the order is tested on the JVM; `LauncherRoot` lays
 * the pager out from it and the page indicator draws its dots from it.
 *
 * Pager indices count AWAY from Home: Home is 0, the app nearest Home is 1, the next is 2. The
 * pager is laid out in reverse (`reverseLayout`), so index 0 is the rightmost page and on screen
 * the pages read [leftToRight] with Home last. Counting this way is what keeps the pager still
 * when the list of apps changes at runtime: an app that joins the end of [apps] takes a new
 * highest index, and no page already there, Home least of all, gets a new one. For the cases
 * that does not cover (an app leaving or joining the middle of the list) every page also has a
 * stable [key], which the pager uses to stay on the page it was showing.
 */
object PlanningPages {
    /** Home's pager index, whatever the apps: the start page, and where Home and Back return. */
    const val HOME = 0

    /**
     * The apps with a planning page, nearest Home first. dayGLANCE and lastGLANCE always have one
     * (an app that is not installed shows its demo data). thingGLANCE is optional and has a page
     * only while its own data can be read, or under the screenshot demo: there is no demo page
     * for an app the user does not have (D81). It is the last of the list, so it comes and goes
     * without any other page moving.
     */
    fun apps(availability: Availability): List<GlanceApp> =
        if (availability.thingGlance == DataSource.LIVE || availability.demoForced) ALL else REQUIRED

    private val REQUIRED = listOf(GlanceApp.DAYGLANCE, GlanceApp.LASTGLANCE)
    private val ALL = REQUIRED + GlanceApp.THINGGLANCE

    /** How many pages the pager has: Home and one per app. */
    fun count(apps: List<GlanceApp>): Int = apps.size + 1

    /** [app]'s pager index, or null when it has no page. */
    fun indexOf(app: GlanceApp, apps: List<GlanceApp>): Int? = apps.indexOf(app).takeIf { it >= 0 }?.let { it + 1 }

    /** The app whose planning page is at pager [index]; null on Home and past either end. */
    fun appAt(index: Int, apps: List<GlanceApp>): GlanceApp? = apps.getOrNull(index - 1)

    /** A page's identity for the pager, the same wherever the page sits: "home" or the app's name. */
    fun key(index: Int, apps: List<GlanceApp>): String = appAt(index, apps)?.name ?: "home"

    /** The planning apps as their pages sit on screen, furthest from Home first; Home follows the last. */
    fun leftToRight(apps: List<GlanceApp>): List<GlanceApp> = apps.asReversed()

    /** The app's name as the launcher writes it, for labels and content descriptions. */
    fun name(app: GlanceApp): String = when (app) {
        GlanceApp.DAYGLANCE -> "dayGLANCE"
        GlanceApp.LASTGLANCE -> "lastGLANCE"
        GlanceApp.THINGGLANCE -> "thingGLANCE"
    }
}
