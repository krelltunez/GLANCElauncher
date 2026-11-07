package app.glance.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glance.daydial.formatClock
import app.glance.launcher.data.Cadence
import app.glance.launcher.intents.GlanceIntents
import app.glance.launcher.ui.GlanceIcons
import app.glance.launcher.ui.LauncherController
import app.glance.launcher.ui.common.AppIcon
import app.glance.launcher.ui.common.ShortcutIcon
import app.glance.launcher.ui.common.topBarsPadding
import app.glance.launcher.ui.search.CaptureTarget
import app.glance.launcher.ui.search.SearchActions
import app.glance.launcher.ui.search.SearchEngine
import app.glance.launcher.ui.search.SearchResult
import app.glance.launcher.ui.theme.GlanceTheme
import app.glance.launcher.ui.theme.hexColor
import java.time.Instant

/**
 * The search bar: apps and GLANCE data in one place, doubling as quick capture. The chip
 * toggles the capture target (dayGLANCE task or lastGLANCE done/item); typing `lg ` does the
 * same. The mic listens on the device and types what it hears into search, running a clear
 * command itself ([SearchEngine.autoRun]); a long press hands off to dayGLANCE's voice capture.
 */
/**
 * The bar on Home. It only looks like a field: tapping it opens [SearchOverlay], which takes
 * the whole screen above the keyboard so results stay visible while typing.
 */
@Composable
fun SearchBarTrigger(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val (effectiveTarget, _) = SearchEngine.targetFor(controller.searchQuery, controller.captureTarget)
    var dragUp by remember { mutableStateOf(0f) }
    Row(
        modifier
            .height(52.dp)
            .background(c.card, RoundedCornerShape(26.dp))
            .border(1.dp, c.border, RoundedCornerShape(26.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragUp = 0f },
                    onVerticalDrag = { _, dy -> dragUp += dy },
                    onDragEnd = { if (dragUp < -60f) controller.openDrawer(); dragUp = 0f },
                )
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            GlanceIcons.Grid, contentDescription = "All apps", tint = c.textSecondary,
            modifier = Modifier.size(36.dp).clickable { controller.openDrawer() }.padding(8.dp),
        )
        Spacer(Modifier.width(4.dp))
        TargetChip(effectiveTarget) {
            controller.captureTarget = if (controller.captureTarget == CaptureTarget.DAYGLANCE) CaptureTarget.LASTGLANCE else CaptureTarget.DAYGLANCE
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).fillMaxHeight().clickable { controller.searchActive = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                placeholderFor(effectiveTarget),
                color = c.textMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        MicButton(
            listening = false,
            onClick = { controller.voiceStartPending = true; controller.searchActive = true },
            onLongClick = { GlanceIntents.startVoiceCapture(context) },
        )
    }
}

private fun placeholderFor(target: CaptureTarget) =
    if (target == CaptureTarget.DAYGLANCE) "Search, or type a task: \"call dentist tomorrow 2pm\"" else "Log a done or add an item in lastGLANCE"

/** The mic: a tap listens in the launcher (red while it does), a long press opens dayGLANCE's voice capture. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = GlanceTheme.colors
    Box(
        Modifier
            .size(38.dp)
            .background(if (listening) c.danger else c.accent, CircleShape)
            .combinedClickable(
                onClickLabel = if (listening) "Stop listening" else "Voice search",
                onLongClickLabel = "Voice capture in dayGLANCE",
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(GlanceIcons.Mic, contentDescription = if (listening) "Stop listening" else "Voice search", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * Full-screen search: the field at the top with the keyboard focused, results filling the
 * space above the keyboard. Back or the close button returns to Home.
 */
@Composable
fun SearchOverlay(controller: LauncherController, modifier: Modifier = Modifier) {
    val c = GlanceTheme.colors
    val context = LocalContext.current
    val repo = controller.repository
    val apps by controller.graph.apps.apps.collectAsState()
    val hidden by controller.graph.prefs.hiddenApps.collectAsState()
    val shortcuts by controller.graph.apps.shortcuts.collectAsState()
    val contactsOn by controller.graph.prefs.contactsInSearch.collectAsState()
    // Read once per search, off the main thread; empty while off or without the permission.
    val contacts by androidx.compose.runtime.produceState(emptyList<app.glance.launcher.ui.search.ContactHit>(), contactsOn) {
        value = if (contactsOn) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.glance.launcher.system.ContactsSource.load(context) } else emptyList()
    }
    val tasks by repo.tasks.collectAsState()
    val projects by repo.projects.collectAsState()
    val goals by repo.goals.collectAsState()
    val chores by repo.chores.collectAsState()
    val people by repo.people.collectAsState()
    val query = controller.searchQuery
    val (effectiveTarget, _) = SearchEngine.targetFor(query, controller.captureTarget)
    val focus = remember { FocusRequester() }

    // Whether the query came from the mic: a spoken command leads its results ([SearchEngine.search]).
    var spoken by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val searchInput = SearchEngine.Input(apps, tasks, projects, goals, chores, people, hidden, shortcuts, contacts)
    val results = remember(query, spoken, controller.captureTarget, apps, hidden, shortcuts, contacts, tasks, projects, goals, chores, people) {
        SearchEngine.search(query, controller.captureTarget, searchInput, voice = spoken)
    }

    fun close() {
        controller.searchQuery = ""
        controller.searchActive = false
    }

    fun act(result: SearchResult) {
        // A query that found something is worth offering again; one that created an item, worked
        // out a sum or went to the web is not.
        if (result !is SearchResult.CreateTask && result !is SearchResult.AddChore && result !is SearchResult.Calculation && result !is SearchResult.WebSearch) {
            controller.graph.prefs.recordSearch(query)
        }
        when (result) {
            is SearchResult.App -> controller.graph.apps.launch(result.entry)
            is SearchResult.Shortcut -> controller.graph.apps.launchShortcut(result.shortcut)
            is SearchResult.CreateTask -> controller.createTask(result.draft)
            is SearchResult.TaskHit -> GlanceIntents.openTask(context, result.task.id)
            is SearchResult.ProjectHit -> GlanceIntents.openProject(context, result.project.id)
            is SearchResult.GoalHit -> GlanceIntents.openGoal(context, result.goal.id)
            is SearchResult.ChoreHit -> GlanceIntents.openChore(context, result.chore.id)
            is SearchResult.PersonHit -> GlanceIntents.openPerson(context, result.person.id)
            is SearchResult.LogDone -> controller.logChoreDone(result.chore.id, result.chore.name)
            is SearchResult.AddChore -> controller.addChore(result.name)
            is SearchResult.LogInteraction -> controller.logInteraction(result.person.id, result.person.name)
            is SearchResult.Calculation -> {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(result.expression, result.result))
                android.widget.Toast.makeText(context, "Copied ${result.result}", android.widget.Toast.LENGTH_SHORT).show()
            }
            is SearchResult.Setting -> SearchActions.openSettings(context, result.page)
            is SearchResult.Contact -> runCatching { context.startActivity(app.glance.launcher.system.ContactsSource.viewIntent(result.contact)) }
            is SearchResult.WebSearch -> SearchActions.webSearch(context, result.query)
            is SearchResult.SetTimer -> controller.toast =
                if (SearchActions.setTimer(context, result.seconds, result.label)) "Timer set: ${durationLabel(result.seconds)}" else "No clock app took the timer"
            is SearchResult.SetAlarm -> controller.toast =
                if (SearchActions.setAlarm(context, result.hour, result.minute)) "Alarm set for ${formatClock(result.hour * 60 + result.minute, false)}" else "No clock app took the alarm"
            is SearchResult.Navigate -> SearchActions.navigate(context, result.place)
            is SearchResult.CallContact -> app.glance.launcher.system.ContactsSource.phoneNumber(context, result.contact)
                ?.let { SearchActions.dial(context, it) }
                ?: runCatching { context.startActivity(app.glance.launcher.system.ContactsSource.viewIntent(result.contact)) }
            is SearchResult.TextContact -> app.glance.launcher.system.ContactsSource.phoneNumber(context, result.contact)
                ?.let { SearchActions.message(context, it) }
                ?: runCatching { context.startActivity(app.glance.launcher.system.ContactsSource.viewIntent(result.contact)) }
        }
        close()
    }

    // Voice: on-device speech, typed into the field as it is heard. A clear command runs when
    // the speaker stops; anything else stays as a search to tap. One recognizer per overlay,
    // reading the latest apps and data, not the ones of the frame it was made in.
    val latestInput by rememberUpdatedState(searchInput)
    val latestAct by rememberUpdatedState<(SearchResult) -> Unit> { act(it) }
    val recognizer = remember {
        app.glance.launcher.system.VoiceRecognizer(
            context,
            onPartial = { controller.searchQuery = it },
            onFinal = { text ->
                controller.searchQuery = text
                SearchEngine.autoRun(text, controller.captureTarget, latestInput)?.let { latestAct(it) }
            },
            onError = { controller.toast = it },
            onListening = { listening = it },
        )
    }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }
    fun listen() {
        if (!recognizer.available()) {
            // No recognizer on this phone: dayGLANCE's own voice capture is the next best thing.
            controller.toast = "No speech recognition on this phone: opening dayGLANCE's voice capture"
            GlanceIntents.startVoiceCapture(context)
            return
        }
        spoken = true
        controller.searchQuery = ""
        recognizer.start()
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) listen() else controller.toast = "Voice search needs the microphone. Long-press the mic for dayGLANCE's voice capture."
    }
    fun toggleListening() {
        when {
            listening -> recognizer.stop()
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED -> listen()
            else -> micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    // The mic on Home opens search listening, with the keyboard left down; otherwise search
    // opens on the keyboard.
    val openedByVoice = remember { controller.voiceStartPending }
    LaunchedEffect(Unit) {
        if (openedByVoice) {
            controller.voiceStartPending = false
            toggleListening()
        } else {
            focus.requestFocus()
        }
        // Picks up shortcut changes the callback cannot report, such as GLANCElauncher having
        // just become the default home app (only that app may read other apps' shortcuts).
        controller.graph.apps.refreshShortcuts()
    }

    Column(
        modifier
            .background(c.background)
            .clickable(enabled = false) {}
            .topBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(c.card, RoundedCornerShape(26.dp))
                .border(1.dp, c.border, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Close, contentDescription = "Close search", tint = c.textSecondary,
                modifier = Modifier.size(36.dp).clickable { close() }.padding(8.dp),
            )
            Spacer(Modifier.width(4.dp))
            TargetChip(effectiveTarget) {
                controller.captureTarget = if (controller.captureTarget == CaptureTarget.DAYGLANCE) CaptureTarget.LASTGLANCE else CaptureTarget.DAYGLANCE
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { controller.searchQuery = it; spoken = false },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { act(it) } }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(if (listening) "Listening…" else placeholderFor(effectiveTarget), color = c.textMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, contentDescription = "Clear", tint = c.textMuted,
                    modifier = Modifier.size(32.dp).clickable { controller.searchQuery = "" }.padding(7.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            MicButton(listening = listening, onClick = { toggleListening() }, onLongClick = { GlanceIntents.startVoiceCapture(context) })
        }
        Spacer(Modifier.height(10.dp))
        if (query.isBlank()) {
            SearchSuggestions(controller, apps, hidden, onQuery = { controller.searchQuery = it }, onLaunch = { entry ->
                controller.graph.apps.launch(entry)
                close()
            })
        } else if (results.isEmpty()) {
            Text("Nothing matches.", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .background(c.card, RoundedCornerShape(14.dp))
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                for (r in results) ResultRow(r, controller) { act(r) }
            }
        }
    }
}

/**
 * What search shows before anything is typed: the apps used most from the launcher (frecency,
 * [app.glance.launcher.prefs.LaunchHistory]) and the recent queries that led somewhere, a tap
 * on one putting it back in the field.
 */
@Composable
private fun SearchSuggestions(
    controller: LauncherController,
    apps: List<app.glance.launcher.apps.AppEntry>,
    hidden: Set<String>,
    onQuery: (String) -> Unit,
    onLaunch: (app.glance.launcher.apps.AppEntry) -> Unit,
) {
    val c = GlanceTheme.colors
    val prefs = controller.graph.prefs
    val history by prefs.launchHistory.collectAsState()
    val recentSearches by prefs.recentSearches.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val suggested = remember(history, byKey, hidden) {
        history.frequent(8, System.currentTimeMillis()) { it in byKey && it !in hidden }.mapNotNull { byKey[it] }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (suggested.isNotEmpty()) {
            SectionLabel("Suggested apps")
            // Four to a row, as the drawer.
            for (row in suggested.chunked(4)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    for (entry in row) {
                        Column(
                            Modifier.weight(1f).clickable { onLaunch(entry) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(entry, controller.graph.apps, size = 44.dp)
                            Text(entry.label, color = c.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (recentSearches.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent searches", Modifier.weight(1f))
                Text("Clear", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.clickable { prefs.clearRecentSearches() }.padding(8.dp))
            }
            for (q in recentSearches) {
                Row(
                    Modifier.fillMaxWidth().clickable { onQuery(q) }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GlanceIcons.History, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(q, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "Apps and their shortcuts, Settings pages, tasks, projects, goals, chores and people, and sums like 12*7. Type a task to add it; \"lg \" or the chip for lastGLANCE. Tap the mic to say it instead, or a command: open, call, text, timer, alarm, navigate, add, talked to. Hold the mic for dayGLANCE's voice capture.",
            color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = GlanceTheme.colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp, modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun TargetChip(target: CaptureTarget, onToggle: () -> Unit) {
    val c = GlanceTheme.colors
    val isDay = target == CaptureTarget.DAYGLANCE
    Row(
        Modifier
            .background(if (isDay) c.brand.copy(alpha = 0.14f) else Color(0xFF3DDC84).copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isDay) "day" else "last",
            color = if (isDay) c.brand else Color(0xFF16A34A),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text("GLANCE", color = if (isDay) c.brand else Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultRow(result: SearchResult, controller: LauncherController, onClick: () -> Unit) {
    val c = GlanceTheme.colors
    val now = remember { Instant.now() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result) {
            is SearchResult.App -> {
                AppIcon(result.entry, controller.graph.apps, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                RowText(result.entry.label, "App")
            }
            is SearchResult.Shortcut -> {
                val sc = result.shortcut
                ShortcutIcon(sc, controller.graph.apps, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                RowText(sc.label, if (sc.conversation) "${sc.appLabel} · Conversation" else "${sc.appLabel} · Shortcut")
            }
            is SearchResult.CreateTask -> {
                Glyph(Icons.Filled.Check, c.brand)
                RowText(
                    "Add task: ${result.draft.title}",
                    result.chips.filterNot { it.startsWith("#") }.joinToString(" · ").ifEmpty { "Inbox · 30m" },
                )
            }
            is SearchResult.TaskHit -> {
                Dot(hexColor(result.task.colorHex))
                val t = result.task
                RowText(t.cleanTitle, buildString {
                    if (t.date == null) append("Inbox") else { append(t.date.toString()); t.startMin?.let { append(" · "); append(formatClock(it, false)) } }
                })
            }
            is SearchResult.ProjectHit -> { Dot(hexColor(result.project.colorHex)); RowText(result.project.title, "Project") }
            is SearchResult.GoalHit -> { Glyph(GlanceIcons.Target, hexColor(result.goal.colorHex)); RowText(result.goal.title, "Goal") }
            is SearchResult.ChoreHit -> {
                val ch = result.chore
                val ratio = Cadence.ratio(Cadence.elapsedDays(ch.lastDoneAt, now), ch.targetCadenceDays)
                Dot(Color(Cadence.colorArgb(ratio)))
                RowText(ch.name, "${ch.category} · ${Cadence.formatElapsed(ch.lastDoneAt, now)}")
            }
            is SearchResult.PersonHit -> { Glyph(GlanceIcons.Users, c.textSecondary); RowText(result.person.name, "Person · ${Cadence.formatElapsed(result.person.lastInteractionAt, now)}") }
            is SearchResult.LogDone -> { Glyph(Icons.Filled.Check, Color(0xFF22C55E)); RowText("Log done: ${result.chore.name}", "lastGLANCE · ${Cadence.formatElapsed(result.chore.lastDoneAt, now)}") }
            is SearchResult.AddChore -> { Glyph(GlanceIcons.Sparkles, Color(0xFF22C55E)); RowText("Add to lastGLANCE: ${result.name}", "New item") }
            is SearchResult.LogInteraction -> { Glyph(GlanceIcons.Users, Color(0xFF22C55E)); RowText("Log contact: ${result.person.name}", "lastGLANCE People") }
            is SearchResult.Calculation -> { Glyph(GlanceIcons.Plus, c.accent); RowText("= ${result.result}", "${result.expression} · tap to copy") }
            is SearchResult.Setting -> { Glyph(GlanceIcons.Sliders, c.textSecondary); RowText(result.page.label, "Settings") }
            is SearchResult.Contact -> { Glyph(GlanceIcons.Users, c.textSecondary); RowText(result.contact.name, "Contact") }
            is SearchResult.WebSearch -> { Glyph(Icons.Filled.Search, c.textSecondary); RowText("Search the web for \"${result.query}\"", "Web") }
            is SearchResult.SetTimer -> { Glyph(GlanceIcons.Timer, c.accent); RowText("Timer: ${durationLabel(result.seconds)}", result.label ?: "Clock") }
            is SearchResult.SetAlarm -> { Glyph(GlanceIcons.AlarmClock, c.accent); RowText("Alarm: ${formatClock(result.hour * 60 + result.minute, false)}", "Clock") }
            is SearchResult.Navigate -> { Glyph(GlanceIcons.Navigation, c.accent); RowText("Directions to ${result.place}", "Maps") }
            is SearchResult.CallContact -> { Glyph(GlanceIcons.Phone, c.accent); RowText("Call ${result.contact.name}", "Opens the dialer") }
            is SearchResult.TextContact -> { Glyph(GlanceIcons.MessageSquare, c.accent); RowText("Text ${result.contact.name}", "Opens messages") }
        }
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun RowText(title: String, subtitle: String) {
    val c = GlanceTheme.colors
    Column {
        Text(title, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "10 min", "1 h 30 min", "45 s". */
private fun durationLabel(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val sec = seconds % 60
    return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, sec.takeIf { it > 0 }?.let { "$it s" })
        .joinToString(" ").ifEmpty { "0 s" }
}
