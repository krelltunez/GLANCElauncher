package app.glance.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of lucide-style glyphs the launcher needs that the Material core icon set
 * lacks. Each is a 24-unit stroked path, like lucide, so they sit well with the GLANCE apps.
 */
object GlanceIcons {
    private fun stroked(name: String, builder: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(builder).build()

    private fun ImageVector.Builder.line(d: String) = addPath(
        pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )

    val Mic: ImageVector by lazy {
        stroked("Mic") {
            line("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z")
            line("M19 10v2a7 7 0 0 1-14 0v-2")
            line("M12 19v3")
        }
    }

    // Voice commands' rows (lucide timer, alarm-clock, navigation, phone, message-square).
    val Timer: ImageVector by lazy {
        stroked("Timer") { line("M10 2h4"); line("m12 14 3-3"); line("M12 6a8 8 0 1 0 0 16 8 8 0 1 0 0-16z") }
    }

    val AlarmClock: ImageVector by lazy {
        stroked("AlarmClock") {
            line("M12 5a8 8 0 1 0 0 16 8 8 0 1 0 0-16z"); line("M12 9v4l2 2"); line("M5 3 2 6"); line("m22 6-3-3")
            line("M6.38 18.7 4 21"); line("M17.64 18.67 20 21")
        }
    }

    val Navigation: ImageVector by lazy { stroked("Navigation") { line("M3 11 22 2 13 21 11 13 3 11z") } }

    val Phone: ImageVector by lazy {
        stroked("Phone") {
            line("M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z")
        }
    }

    val MessageSquare: ImageVector by lazy {
        stroked("MessageSquare") { line("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    }

    val Grid: ImageVector by lazy {
        stroked("Grid") {
            line("M3 3h7v7H3z")
            line("M14 3h7v7h-7z")
            line("M14 14h7v7h-7z")
            line("M3 14h7v7H3z")
        }
    }

    val ChevronUp: ImageVector by lazy { stroked("ChevronUp") { line("m18 15-6-6-6 6") } }

    val Sliders: ImageVector by lazy {
        stroked("Sliders") {
            line("M4 21v-7"); line("M4 10V3"); line("M12 21v-9"); line("M12 8V3"); line("M20 21v-5"); line("M20 12V3")
            line("M1 14h6"); line("M9 8h6"); line("M17 16h6")
        }
    }

    val EyeOff: ImageVector by lazy {
        stroked("EyeOff") {
            line("M9.88 9.88a3 3 0 1 0 4.24 4.24")
            line("M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68")
            line("M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61")
            line("M2 2l20 20")
        }
    }

    val Eye: ImageVector by lazy {
        stroked("Eye") {
            line("M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z")
            line("M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6Z")
        }
    }

    val Pin: ImageVector by lazy {
        stroked("Pin") {
            line("M12 17v5"); line("M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z")
        }
    }

    val Sparkles: ImageVector by lazy {
        stroked("Sparkles") {
            line("M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z")
            line("M20 3v4"); line("M22 5h-4")
        }
    }

    val Target: ImageVector by lazy {
        stroked("Target") {
            line("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20Z")
            line("M12 6a6 6 0 1 0 0 12 6 6 0 1 0 0-12Z")
            line("M12 10a2 2 0 1 0 0 4 2 2 0 1 0 0-4Z")
        }
    }

    val Flame: ImageVector by lazy {
        stroked("Flame") {
            line("M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z")
        }
    }

    val Users: ImageVector by lazy {
        stroked("Users") {
            line("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2")
            line("M9 3a4 4 0 1 0 0 8 4 4 0 1 0 0-8Z")
            line("M22 21v-2a4 4 0 0 0-3-3.87")
            line("M16 3.13a4 4 0 0 1 0 7.75")
        }
    }

    val Widget: ImageVector by lazy {
        stroked("Widget") {
            line("M3 5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
            line("M13 5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2z")
            line("M3 15a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
            line("M17 14v6"); line("M14 17h6")
        }
    }

    val Inbox: ImageVector by lazy {
        stroked("Inbox") {
            line("M22 12h-6l-2 3h-4l-2-3H2")
            line("M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z")
        }
    }

    val Cake: ImageVector by lazy {
        stroked("Cake") {
            line("M20 21v-8a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8")
            line("M4 16s.5-1 2-1 2.5 2 4 2 2.5-2 4-2 2.5 2 4 2 2-1 2-1")
            line("M2 21h20"); line("M7 8v3"); line("M12 8v3"); line("M17 8v3")
            line("M7 4h.01"); line("M12 4h.01"); line("M17 4h.01")
        }
    }

    // region SCHED agenda (lucide, as in dayGLANCE's SchedTaskCard / SchedView)

    val Circle: ImageVector by lazy { stroked("Circle") { line("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z") } }

    val CheckCircle: ImageVector by lazy {
        stroked("CheckCircle") { line("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z"); line("m9 12 2 2 4-4") }
    }

    val FileText: ImageVector by lazy {
        stroked("FileText") {
            line("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z")
            line("M14 2v4a2 2 0 0 0 2 2h4"); line("M10 9H8"); line("M16 13H8"); line("M16 17H8")
        }
    }

    val Calendar: ImageVector by lazy {
        stroked("Calendar") { line("M8 2v4"); line("M16 2v4"); line("M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"); line("M3 10h18") }
    }

    val CalendarPlus: ImageVector by lazy {
        stroked("CalendarPlus") {
            line("M8 2v4"); line("M16 2v4")
            line("M21 13V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h8")
            line("M3 10h18"); line("M16 19h6"); line("M19 16v6")
        }
    }

    val SkipForward: ImageVector by lazy { stroked("SkipForward") { line("M5 4 15 12 5 20z"); line("M19 5v14") } }

    val ExternalLink: ImageVector by lazy {
        stroked("ExternalLink") { line("M15 3h6v6"); line("M10 14 21 3"); line("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6") }
    }

    val ListFilter: ImageVector by lazy { stroked("ListFilter") { line("M3 6h18"); line("M7 12h10"); line("M10 18h4") } }

    val ChevronDown: ImageVector by lazy { stroked("ChevronDown") { line("m6 9 6 6 6-6") } }

    val ArrowDown: ImageVector by lazy { stroked("ArrowDown") { line("M12 5v14"); line("m19 12-7 7-7-7") } }

    val ArrowUp: ImageVector by lazy { stroked("ArrowUp") { line("m5 12 7-7 7 7"); line("M12 19V5") } }

    val Plus: ImageVector by lazy { stroked("Plus") { line("M5 12h14"); line("M12 5v14") } }

    val Close: ImageVector by lazy { stroked("Close") { line("M18 6 6 18"); line("m6 6 12 12") } }

    val History: ImageVector by lazy {
        stroked("History") { line("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"); line("M3 3v5h5"); line("M12 7v5l4 2") }
    }

    val UserPlus: ImageVector by lazy {
        stroked("UserPlus") {
            line("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"); line("M9 3a4 4 0 1 0 0 8 4 4 0 1 0 0-8z")
            line("M19 8v6"); line("M22 11h-6")
        }
    }

    val ListPlus: ImageVector by lazy {
        stroked("ListPlus") { line("M11 12H3"); line("M16 6H3"); line("M16 18H3"); line("M18 9v6"); line("M21 12h-6") }
    }

    val Ellipsis: ImageVector by lazy {
        stroked("Ellipsis") {
            line("M12 11a1 1 0 1 0 0 2 1 1 0 1 0 0-2z"); line("M19 11a1 1 0 1 0 0 2 1 1 0 1 0 0-2z"); line("M5 11a1 1 0 1 0 0 2 1 1 0 1 0 0-2z")
        }
    }

    // endregion
}
