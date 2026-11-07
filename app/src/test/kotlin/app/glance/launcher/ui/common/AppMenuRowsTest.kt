package app.glance.launcher.ui.common

import app.glance.launcher.ui.common.AppMenuRows.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppMenuRowsTest {
    @Test fun `a pinned app offers the drawer's full menu, wherever it was pressed`() {
        assertEquals(
            listOf(Row.APP_INFO, Row.TOGGLE_HIDDEN, Row.TOGGLE_HOME, Row.TOGGLE_DOCK, Row.UNINSTALL),
            AppMenuRows.forPin(shortcut = false),
        )
    }

    @Test fun `a pinned shortcut offers its app and its own removal, never acts on the app's install`() {
        val rows = AppMenuRows.forPin(shortcut = true)
        assertEquals(listOf(Row.OPEN_APP, Row.APP_INFO, Row.REMOVE_FROM_HOME), rows)
        assertFalse(Row.UNINSTALL in rows)
        assertFalse(Row.TOGGLE_HIDDEN in rows)
    }

    @Test fun `labels follow the app's state`() {
        fun label(row: Row, hidden: Boolean = false, home: Boolean = false, dock: Boolean = false) =
            AppMenuRows.label(row, "Maps", hidden, home, dock)
        assertEquals("Unpin from Home", label(Row.TOGGLE_HOME, home = true))
        assertEquals("Pin to Home", label(Row.TOGGLE_HOME))
        assertEquals("Unpin from dock", label(Row.TOGGLE_DOCK, dock = true))
        assertEquals("Pin to dock", label(Row.TOGGLE_DOCK))
        assertEquals("Show in drawer", label(Row.TOGGLE_HIDDEN, hidden = true))
        assertEquals("Hide from drawer", label(Row.TOGGLE_HIDDEN))
        assertEquals("Open Maps", label(Row.OPEN_APP))
    }
}
