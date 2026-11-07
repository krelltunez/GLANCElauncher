package app.glance.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Double-tap to lock: an accessibility service whose only use is
 * `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`, the one way a home app can lock the screen
 * and leave fingerprint and face unlock working (a device admin's `lockNow` demands the PIN).
 * It asks for no events and cannot read window content (res/xml/lock_screen_service.xml). The
 * user turns it on in Android's Accessibility settings after the disclosure in Settings → Double
 * tap to lock; until then a double-tap only says where to turn it on.
 */
class LockScreenService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = WeakReference(this)
        _connected.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (instance?.get() === this) instance = null
        _connected.value = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "LockScreenService"
        @Volatile private var instance: WeakReference<LockScreenService>? = null

        private val _connected = MutableStateFlow(false)
        /** The user has turned the service on and the system has bound it. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** Locks the screen; false when the service is not running or the system refused. */
        fun lock(): Boolean {
            if (Build.VERSION.SDK_INT < DoubleTapLock.MIN_SDK) return false
            val service = instance?.get() ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No accessibility settings", e)
            }
        }
    }
}
