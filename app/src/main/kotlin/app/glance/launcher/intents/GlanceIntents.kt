package app.glance.launcher.intents

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.glance.launcher.system.LaunchOptions
import java.time.LocalDate

/**
 * Deep links into the two apps. Reads come from the repository; these only open screens or
 * hand off a capture, so business logic stays in the apps.
 *
 * dayGLANCE: `dayglance://` is a registered VIEW scheme, so an implicit intent resolves.
 * lastGLANCE: `lastglance://` has no intent filter, so it goes as an explicit intent to its
 * MainActivity, which parses the URI itself.
 */
object GlanceIntents {
    const val DAYGLANCE_PACKAGE = "com.dayglance.app"
    const val LASTGLANCE_PACKAGE = "com.lastglance.app"
    private const val LASTGLANCE_MAIN = "com.lastglance.app.MainActivity"

    /** dayGLANCE's own voice capture entry point; the launcher asks for no microphone permission. */
    const val ACTION_DAYGLANCE_VOICE = "com.dayglance.app.ACTION_VOICE_INPUT"

    fun openDayGlance(context: Context, link: String = "dayglance://today") {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(DAYGLANCE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent, "dayGLANCE")
    }

    fun openDay(context: Context, date: LocalDate, dial: Boolean = false) =
        openDayGlance(context, "dayglance://day?date=$date&view=${if (dial) "dial" else "month"}")

    fun openTask(context: Context, taskId: String) = openDayGlance(context, "dayglance://task?id=$taskId")
    fun openGoal(context: Context, goalId: String) = openDayGlance(context, "dayglance://goal?id=$goalId")
    fun openProject(context: Context, projectId: String) = openDayGlance(context, "dayglance://project?id=$projectId")
    /**
     * The Goals & Projects space on its Goals or Projects panel. dayGLANCE's `goal` and `project`
     * actions open the space today (unfocused without an `id`, on whichever panel was last shown)
     * and ignore the `tab` parameter; honouring it in `resolveGoalsLink` is the app-side ask.
     */
    fun openGoalsPanel(context: Context) = openDayGlance(context, "dayglance://goal?tab=goals")
    fun openProjectsPanel(context: Context) = openDayGlance(context, "dayglance://project?tab=projects")
    /**
     * The Inbox tab. dayGLANCE does not handle `dayglance://inbox` yet (it has only
     * `newInboxTask`, the capture form); until it does, the link opens the app where it was.
     * Adding the action to the app's deep-link switch is the one-line counterpart.
     */
    fun openInbox(context: Context) = openDayGlance(context, "dayglance://inbox")
    fun newInboxTask(context: Context) = openDayGlance(context, "dayglance://newInboxTask")

    fun openLastGlance(context: Context, link: String = "lastglance://filter/soon") {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setClassName(LASTGLANCE_PACKAGE, LASTGLANCE_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent, "lastGLANCE")
    }

    /** Plain launch: the app opens wherever it was last (all chores by default), with no filter forced. */
    fun launchLastGlance(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(LASTGLANCE_PACKAGE)
        if (intent == null) {
            Toast.makeText(context, "lastGLANCE is not installed.", Toast.LENGTH_SHORT).show()
            return
        }
        start(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), "lastGLANCE")
    }

    fun openChore(context: Context, choreId: String) = openLastGlance(context, "lastglance://chore/$choreId")
    fun openPerson(context: Context, personId: String) = openLastGlance(context, "lastglance://person/$personId")
    fun openPeople(context: Context) = openLastGlance(context, "lastglance://people")
    /** Chores with Soon off, on the Pinned tab (lastGLANCE #395); an older lastGLANCE opens where it was. */
    fun openPinned(context: Context) = openLastGlance(context, "lastglance://pinned")
    /** People, with the add-person form open (lastGLANCE's `action/addPerson`). */
    fun addPerson(context: Context) = openLastGlance(context, "lastglance://action/addPerson")
    /** dayGLANCE opened plainly, wherever it was last. */
    fun launchDayGlance(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(DAYGLANCE_PACKAGE)
        if (intent == null) {
            Toast.makeText(context, "dayGLANCE is not installed.", Toast.LENGTH_SHORT).show()
            return
        }
        start(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), "dayGLANCE")
    }

    /**
     * Chores mode. lastGLANCE has `lastglance://people` but no chores counterpart: it switches
     * to Chores only for a chore or filter link. `lastglance://chores` is the one-line ask of
     * the app; until it lands, an unknown host opens the app where it was.
     */
    fun openChores(context: Context) = openLastGlance(context, "lastglance://chores")

    /**
     * dayGLANCE's own voice capture: the mic's long press, and its fallback on a phone with no
     * speech recognizer or without the microphone permission. dayGLANCE's voice-capture action,
     * else the `dayglance://voiceInput` deep link, and if dayGLANCE is not installed a note
     * instead of a crash.
     */
    fun startVoiceCapture(context: Context) {
        val action = Intent(ACTION_DAYGLANCE_VOICE).setPackage(DAYGLANCE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(action, 0) != null) {
            context.startActivity(action, LaunchOptions.iconSplash())
            return
        }
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("dayglance://voiceInput")).setPackage(DAYGLANCE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(link, 0) != null) {
            context.startActivity(link, LaunchOptions.iconSplash())
            return
        }
        Toast.makeText(context, "Voice capture opens dayGLANCE once it is installed (placeholder intent fired).", Toast.LENGTH_SHORT).show()
    }

    fun installPrompt(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            Toast.makeText(context, "Install $packageName to use this.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun start(context: Context, intent: Intent, appName: String) {
        try {
            context.startActivity(intent, LaunchOptions.iconSplash())
        } catch (e: Exception) {
            Toast.makeText(context, "$appName is not installed. In the proof of concept this is a deep link to ${intent.data}.", Toast.LENGTH_SHORT).show()
        }
    }

    fun componentName(packageName: String, className: String): ComponentName = ComponentName(packageName, className)
}
