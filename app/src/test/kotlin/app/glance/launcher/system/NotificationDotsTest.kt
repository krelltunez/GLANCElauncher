package app.glance.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDotsTest {
    private val own = "app.glance.launcher"

    @Test fun `an ordinary notification gives its app a dot, once however many it posts`() {
        val dots = NotificationDots.packages(
            listOf(PostedNotification("com.chat"), PostedNotification("com.chat"), PostedNotification("com.mail")),
            own,
        )
        assertEquals(setOf(DotKey("com.chat"), DotKey("com.mail")), dots)
    }

    @Test fun `a channel that does not allow badges gives no dot`() {
        assertTrue(NotificationDots.packages(listOf(PostedNotification("com.chat", canShowBadge = false)), own).isEmpty())
    }

    @Test fun `ongoing notifications (music, navigation, a download) give no dot`() {
        assertTrue(NotificationDots.packages(listOf(PostedNotification("com.music", ongoing = true)), own).isEmpty())
    }

    @Test fun `a group summary alone gives no dot, its children do`() {
        assertTrue(NotificationDots.packages(listOf(PostedNotification("com.mail", groupSummary = true)), own).isEmpty())
        assertEquals(
            setOf(DotKey("com.mail")),
            NotificationDots.packages(listOf(PostedNotification("com.mail", groupSummary = true), PostedNotification("com.mail")), own),
        )
    }

    @Test fun `an empty notification, or one from a paused app, gives no dot`() {
        assertTrue(NotificationDots.packages(listOf(PostedNotification("com.chat", hasContent = false)), own).isEmpty())
        assertTrue(NotificationDots.packages(listOf(PostedNotification("com.chat", suspended = true)), own).isEmpty())
    }

    @Test fun `the launcher never dots itself`() {
        assertTrue(NotificationDots.packages(listOf(PostedNotification(own)), own).isEmpty())
    }

    @Test fun `an app has a dot by package for the launcher's own user`() {
        val dots = setOf(DotKey("com.chat"))
        assertTrue(NotificationDots.has(dots, "com.chat", user = null, ownUser = null))
        assertFalse(NotificationDots.has(dots, "com.mail", user = null, ownUser = null))
    }

    @Test fun `the launcher's own user is held as null, another profile as itself`() {
        assertEquals(null, NotificationDots.userKey("user0", ownUser = "user0"))
        assertEquals(null, NotificationDots.userKey<String>(null, ownUser = "user0"))
        assertEquals("work10", NotificationDots.userKey("work10", ownUser = "user0"))
    }
}
