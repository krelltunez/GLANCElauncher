@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.glance.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.AppIcon
import app.glance.launcher.ui.common.PinIcon
import app.glance.launcher.ui.theme.GlanceTheme
import kotlin.math.roundToInt

/**
 * Editing the pinned row: the current pins on top, where a long-press drag reorders and the
 * cross removes; the app list below, where a tap adds (or removes) a pin. Reordering lives
 * here only, never on Home.
 */
@Composable
fun PinnedEditor(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val prefs = controller.graph.prefs
    val apps = controller.graph.apps
    val pins by prefs.homePins.collectAsState()
    val all by apps.apps.collectAsState()
    val hidden by prefs.hiddenApps.collectAsState()
    val shortcuts by apps.shortcuts.collectAsState()
    val entries = remember(pins, all, shortcuts, hidden) { app.glance.launcher.apps.Pins.resolve(pins, all, shortcuts, hidden) }
    val slot = 72.dp
    val slotPx = with(LocalDensity.current) { slot.toPx() }

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableStateOf(0f) }
    val stripScroll = rememberScrollState()

    Column(
        modifier
            .background(c.background.copy(alpha = 0.98f))
            .clickable(enabled = false) {}
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pinned apps", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Close, contentDescription = "Done", tint = c.textSecondary,
                modifier = Modifier.size(28.dp).clickable { controller.pinnedEditorOpen = false },
            )
        }
        Text("Long-press and drag to reorder; swipe the strip to see the rest. Tap the cross to remove. Tap an app below to add it; an app's \"Add to Home screen\" adds its shortcut here too.", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        // The strip scrolls sideways (a plain swipe) so pins past the edge can be reached; a
        // long-press starts a reorder drag instead, which the scroll does not take.
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.card, RoundedCornerShape(12.dp))
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .horizontalScroll(stripScroll, enabled = draggingKey == null)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(92.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entries.isEmpty()) {
                Text("Nothing pinned yet.", color = c.textMuted, fontSize = 13.sp)
            }
            for (entry in entries) key(entry.key) {
                // Keyed on the app: after a swap this composable moves WITH the app, so its drag
                // gesture keeps running. Unkeyed, the slot was recomposed with the neighbour, the
                // gesture restarted, and the drag dropped mid-move (the order still saved, so it
                // "did not work, but did").
                val isDragging = entry.key == draggingKey
                Column(
                    Modifier
                        .width(slot)
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { IntOffset(if (isDragging) dragX.roundToInt() else 0, 0) }
                        .alpha(if (isDragging) 0.85f else 1f)
                        .pointerInput(entry.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingKey = entry.key; dragX = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragX += amount.x
                                    // Crossing the midpoint of a neighbour swaps with it. The order
                                    // is read fresh: this gesture outlives the composition it began in.
                                    val current = prefs.homePins.value
                                    val from = current.indexOf(entry.key)
                                    val shift = (dragX / slotPx).roundToInt()
                                    if (from >= 0 && shift != 0) {
                                        val target = (from + shift).coerceIn(0, current.size - 1)
                                        if (target != from) {
                                            val next = current.toMutableList()
                                            next.add(target, next.removeAt(from))
                                            prefs.setHomePins(next)
                                            dragX -= (target - from) * slotPx
                                        }
                                    }
                                },
                                onDragEnd = { draggingKey = null; dragX = 0f },
                                onDragCancel = { draggingKey = null; dragX = 0f },
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        PinIcon(entry, apps, size = 48.dp)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(20.dp)
                                .background(c.text, CircleShape)
                                .clickable { prefs.toggleHomePin(entry.key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = c.background, modifier = Modifier.size(12.dp))
                        }
                    }
                    Text(entry.label, color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("All apps", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) GridCells.Fixed(4) else GridCells.Adaptive(88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(all.filter { it.key !in hidden }, key = { it.key }, contentType = { "app" }) { entry ->
                val pinned = entry.key in pins
                Column(
                    Modifier
                        .clickable { prefs.toggleHomePin(entry.key) }
                        .then(if (pinned) Modifier.border(2.dp, c.brand, RoundedCornerShape(14.dp)) else Modifier)
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(entry, apps, size = 48.dp)
                    Text(entry.label, color = c.text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
