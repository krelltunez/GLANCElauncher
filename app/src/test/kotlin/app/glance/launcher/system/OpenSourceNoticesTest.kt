package app.glance.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenSourceNoticesTest {
    // Unit tests run in the module's directory; the assets are the repo's licenses/.
    private val licenses = File("../licenses")

    @Test fun `every licence file is shown and every notice has its file`() {
        val files = licenses.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(files, OpenSourceNotices.all.map { it.asset }.toSet())
    }

    @Test fun `no licence file is empty`() {
        for (notice in OpenSourceNotices.all) {
            assertTrue(notice.asset, File(licenses, notice.asset).readText().isNotBlank())
        }
    }
}
