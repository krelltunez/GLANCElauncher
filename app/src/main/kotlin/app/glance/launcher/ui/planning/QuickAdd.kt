package app.glance.launcher.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.launcher.intents.GlanceIntents
import app.glance.launcher.quickadd.QuickAddParser
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.LastGlanceGreen
import app.glance.launcher.ui.theme.GlanceTheme

/** What the planning screen's "+" adds. */
enum class QuickAddKind { TASK, CHORE }

/**
 * The planning screen's quick add: a "+" that opens Task, Chore and Person. Task and Chore take
 * one line in [QuickAddSheet] (a task through the same parser as search, so "dentist friday
 * 3pm" lands scheduled); Person opens lastGLANCE's own add-person form, since a person has
 * circles, dates and a cadence a one-line write would leave unset. Chore and Person show only
 * with lastGLANCE installed.
 */
@Composable
fun QuickAddButton(controller: LauncherController, onPick: (QuickAddKind) -> Unit, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val availability by controller.repository.availability.collectAsState()
    var open by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (open) {
            // As wide as its widest row, right-aligned over the button. The rows fill the
            // menu's width so each is a full-width tap target, which without this intrinsic
            // width stretched the menu across the screen with the labels at its far left.
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 180.dp)
                    .background(c.card, RoundedCornerShape(14.dp))
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .padding(vertical = 6.dp),
            ) {
                MenuItem(GlanceIcons.Plus, "Add task", c.brand) { open = false; onPick(QuickAddKind.TASK) }
                if (availability.lastGlanceInstalled) {
                    MenuItem(GlanceIcons.ListPlus, "Add chore", LastGlanceGreen) { open = false; onPick(QuickAddKind.CHORE) }
                    MenuItem(GlanceIcons.UserPlus, "Add person", LastGlanceGreen) { open = false; GlanceIntents.addPerson(context) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(
            Modifier
                .size(52.dp)
                .background(c.brand, CircleShape)
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (open) GlanceIcons.Close else GlanceIcons.Plus, contentDescription = if (open) "Close" else "Add", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = GlanceTheme.colors.text, fontSize = 14.sp)
    }
}

/** One line, then Add: a task (parsed for date, time and duration, previewed as chips) or a chore. */
@Composable
fun QuickAddSheet(controller: LauncherController, kind: QuickAddKind, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    var text by remember(kind) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val parsed = remember(text, kind) { if (kind == QuickAddKind.TASK && text.isNotBlank()) QuickAddParser.parse(text) else null }
    fun submit() {
        val t = text.trim()
        if (t.isEmpty()) return
        when (kind) {
            QuickAddKind.TASK -> controller.createTask(parsed?.draft ?: QuickAddParser.parse(t).draft)
            QuickAddKind.CHORE -> controller.addChore(t)
        }
        onDismiss()
    }
    LaunchedEffect(kind) { focus.requestFocus() }
    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onDismiss).imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(12.dp)
                .background(c.card, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Text(
                if (kind == QuickAddKind.TASK) "Add to dayGLANCE" else "Add a chore to lastGLANCE",
                color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().background(c.background, RoundedCornerShape(10.dp)).border(1.dp, c.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    if (kind == QuickAddKind.TASK) "Dentist friday 3pm 1h" else "Water the plants",
                                    color = c.textMuted, fontSize = 15.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            val chips = parsed?.chips.orEmpty()
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (chip in chips) {
                        Text(chip, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.background(c.track, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            } else if (kind == QuickAddKind.TASK) {
                Spacer(Modifier.height(8.dp))
                Text("Without a date it goes to the Inbox.", color = c.textMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp))
                Spacer(Modifier.width(6.dp))
                val enabled = text.isNotBlank()
                Text(
                    "Add",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(if (kind == QuickAddKind.TASK) c.brand.copy(alpha = if (enabled) 1f else 0.4f) else LastGlanceGreen.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { submit() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
