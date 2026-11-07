package app.glance.launcher.system

import app.glance.launcher.system.DoubleTapLock.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapLockTest {
    @Test fun `off, a double-tap does nothing, service or not`() {
        assertEquals(Outcome.NONE, DoubleTapLock.onDoubleTap(enabled = false, serviceConnected = true, sdk = 35))
        assertEquals(Outcome.NONE, DoubleTapLock.onDoubleTap(enabled = false, serviceConnected = false, sdk = 35))
    }

    @Test fun `on with the service running, it locks`() {
        assertEquals(Outcome.LOCK, DoubleTapLock.onDoubleTap(enabled = true, serviceConnected = true, sdk = 28))
    }

    @Test fun `on without the service, it says where to turn it on rather than failing silently`() {
        assertEquals(Outcome.NEEDS_SERVICE, DoubleTapLock.onDoubleTap(enabled = true, serviceConnected = false, sdk = 35))
    }

    @Test fun `before Android 9 there is no lock action, so nothing happens`() {
        assertEquals(Outcome.NONE, DoubleTapLock.onDoubleTap(enabled = true, serviceConnected = true, sdk = 27))
    }
}
