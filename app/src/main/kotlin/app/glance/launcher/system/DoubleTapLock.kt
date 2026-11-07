package app.glance.launcher.system

/**
 * What a double-tap on Home's empty space does. Pure, tested on the JVM; [LockScreenService]
 * does the locking.
 */
object DoubleTapLock {
    /** `GLOBAL_ACTION_LOCK_SCREEN` arrived in Android 9. */
    const val MIN_SDK = 28

    enum class Outcome {
        /** Lock the screen. */
        LOCK,
        /** Switched on, but the accessibility service is not running: say where to turn it on. */
        NEEDS_SERVICE,
        /** Off, or unsupported: the tap means nothing, as before. */
        NONE,
    }

    fun onDoubleTap(enabled: Boolean, serviceConnected: Boolean, sdk: Int): Outcome = when {
        !enabled || sdk < MIN_SDK -> Outcome.NONE
        serviceConnected -> Outcome.LOCK
        else -> Outcome.NEEDS_SERVICE
    }
}
