package app.glance.launcher.data

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [GlanceRepository] seeded with a realistic day, month, goals, projects, chores
 * and people. Writes mutate the flows, so moving a block or completing a task visibly works.
 *
 * Everything is generated relative to [clock]'s today so the demo always looks live.
 */
class FakeRepository(
    private val clock: Clock = Clock.systemDefaultZone(),
    seed: FakeSeed = FakeSeed(LocalDate.now(clock), clock.instant(), clock.zone),
) : GlanceRepository {

    private val _tasks = MutableStateFlow(seed.tasks)
    private val _goals = MutableStateFlow(seed.goals)
    private val _projects = MutableStateFlow(seed.projects)
    private val _routines = MutableStateFlow(seed.routines)
    private val _frames = MutableStateFlow(seed.frames)
    private val _dayShape = MutableStateFlow(seed.dayShape)
    private val _weather = MutableStateFlow(seed.weather)
    private val _chores = MutableStateFlow(seed.chores)
    private val _people = MutableStateFlow(seed.people)
    private val _availability = MutableStateFlow(Availability(dayGlanceInstalled = true, lastGlanceInstalled = true))

    override val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    override val goals: StateFlow<List<Goal>> = _goals.asStateFlow()
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()
    override val routines: StateFlow<List<Routine>> = _routines.asStateFlow()
    override val frames: StateFlow<List<Frame>> = _frames.asStateFlow()
    override val dayShape: StateFlow<DayShape> = _dayShape.asStateFlow()
    override val weather: StateFlow<List<WeatherDay>> = _weather.asStateFlow()
    /** The demo counts its rows. */
    override val inboxCount: StateFlow<Int?> = MutableStateFlow<Int?>(null).asStateFlow()
    override val chores: StateFlow<List<Chore>> = _chores.asStateFlow()
    override val people: StateFlow<List<Person>> = _people.asStateFlow()
    override val availability: StateFlow<Availability> = _availability.asStateFlow()

    /**
     * Rebuild the demo around today. The seed is otherwise built once, when the launcher starts,
     * and a home app's process can outlive the day; the screenshot demo calls this each time it
     * is turned on, which also drops any taps made on the demo before.
     */
    fun reseed(seed: FakeSeed = FakeSeed(LocalDate.now(clock), clock.instant(), clock.zone)) {
        _tasks.value = seed.tasks
        _goals.value = seed.goals
        _projects.value = seed.projects
        _routines.value = seed.routines
        _frames.value = seed.frames
        _dayShape.value = seed.dayShape
        _weather.value = seed.weather
        _chores.value = seed.chores
        _people.value = seed.people
    }

    override suspend fun moveTask(taskId: String, date: LocalDate, startMin: Int) {
        _tasks.update { list ->
            list.map {
                when {
                    it.id != taskId -> it
                    // Putting an Inbox task on the clock drops its inbox-only fields, as dayGLANCE's drag and
                    // schedule paths do: the deadline and priority belong to the unscheduled list.
                    it.date == null -> it.copy(date = date, startMin = startMin.coerceIn(0, 1439), isAllDay = false, deadline = null, priority = 0)
                    else -> it.copy(date = date, startMin = startMin.coerceIn(0, 1439), isAllDay = false)
                }
            }
        }
    }

    override suspend fun moveTaskToDay(taskId: String, date: LocalDate) {
        _tasks.update { list -> list.map { if (it.id == taskId && it.date != null) it.copy(date = date) else it } }
    }

    override suspend fun resizeTask(taskId: String, durationMin: Int) {
        _tasks.update { list -> list.map { if (it.id == taskId) it.copy(durationMin = durationMin.coerceAtLeast(5)) else it } }
    }

    override suspend fun setTaskCompleted(taskId: String, completed: Boolean) {
        val now = clock.instant()
        _tasks.update { list ->
            list.map {
                if (it.id == taskId && it.completable) it.copy(completed = completed, completedAt = if (completed) now else null) else it
            }
        }
    }

    override suspend fun toggleRoutine(routineId: String, date: LocalDate) {
        _routines.update { list ->
            list.map { r ->
                if (r.id != routineId) r
                else r.copy(completedDates = if (date in r.completedDates) r.completedDates - date else r.completedDates + date)
            }
        }
    }

    override suspend fun createTask(draft: TaskDraft): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = draft.title,
            date = draft.date,
            startMin = draft.startMin,
            durationMin = draft.durationMin,
            isAllDay = draft.date != null && draft.startMin == null,
            priority = draft.priority,
            deadline = draft.deadline,
            colorHex = draft.colorHex,
            projectId = draft.projectId,
        )
        _tasks.update { it + task }
        return task
    }

    override suspend fun logChoreDone(choreId: String) {
        val now = clock.instant()
        _chores.update { list -> list.map { if (it.id == choreId) it.copy(lastDoneAt = now) else it } }
    }

    override suspend fun addChore(name: String): Chore {
        val chore = Chore(id = UUID.randomUUID().toString(), name = name, category = "Inbox")
        _chores.update { it + chore }
        return chore
    }

    override val refreshRequests: StateFlow<Map<app.glance.launcher.data.provider.GlanceApp, RefreshRequest>> = MutableStateFlow(emptyMap())
    override suspend fun requestRefresh(app: app.glance.launcher.data.provider.GlanceApp): String? = null

    override suspend fun logInteraction(personId: String) {
        val now = clock.instant()
        _people.update { list ->
            list.map { if (it.id == personId) it.copy(lastInteractionAt = now, lastChannel = "text", unreturned = false) else it }
        }
    }
}

/** Deterministic seed data built around [today]. */
class FakeSeed(val today: LocalDate, val now: Instant, val zone: ZoneId) {
    private var counter = 0
    private fun id(prefix: String) = "$prefix-${++counter}"
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun daysAgo(days: Long, hour: Int = 10): Instant =
        today.minusDays(days).atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()

    val goals: List<Goal> = listOf(
        Goal("goal-ship", "Ship GLANCElauncher v1", "A focus launcher with the Day Dial at its heart", TaskColors.ORANGE, today.plusDays(45)),
        Goal("goal-fit", "Run a half marathon", "Spring race, 12-week plan", TaskColors.GREEN, today.plusDays(84)),
        Goal("goal-home", "Make the house low-maintenance", "Automate and simplify upkeep", TaskColors.TEAL, today.plusDays(2)),
    )

    val projects: List<Project> = listOf(
        Project("proj-dial", "Day Dial module", "goal-ship", TaskColors.ORANGE, "Compose port of the in-app dial"),
        Project("proj-home", "Home screen", "goal-ship", TaskColors.YELLOW),
        Project("proj-planning", "Planning screen", "goal-ship", TaskColors.PINK),
        Project("proj-base", "Base training", "goal-fit", TaskColors.GREEN),
        Project("proj-strength", "Strength and mobility", "goal-fit", TaskColors.TEAL),
        Project("proj-garden", "Garden automation", "goal-home", TaskColors.TEAL),
        Project("proj-reading", "Reading list", null, TaskColors.INDIGO, "Standalone project"),
    )

    val frames: List<Frame> = listOf(
        Frame("frame-morning", "Morning focus", at(8, 30), at(12, 0), "#93c5fd", setOf(1, 2, 3, 4, 5)),
        Frame("frame-admin", "Admin", at(13, 0), at(14, 0), "#c8c8d2", setOf(1, 2, 3, 4, 5)),
        Frame("frame-evening", "Evening", at(18, 0), at(21, 30), "#c4b5fd"),
    )

    val routines: List<Routine> = listOf(
        Routine("routine-pages", "Morning pages", at(6, 45), at(7, 5), setOf(today, today.minusDays(1))),
        Routine("routine-stretch", "Stretch", at(12, 30), at(12, 45)),
        Routine("routine-shutdown", "Shutdown ritual", at(17, 30), at(17, 45)),
        Routine("routine-read", "Read", at(21, 30), at(22, 0)),
    )

    val dayShape = DayShape(
        dayStartMin = at(6, 30),
        dayStopMin = at(23, 0),
        sunriseMin = at(7, 12),
        sunsetMin = at(18, 24),
        use24HourClock = false,
        // Denver, so the demo has a moon.
        lat = 39.74,
        lon = -104.99,
    )

    /** Today and tomorrow: a cool morning, a mild afternoon with a shower, clear tomorrow. */
    val weather: List<WeatherDay> = listOf(today, today.plusDays(1)).mapIndexed { i, d ->
        WeatherDay(d, "fahrenheit", (0 until 24).associateWith { h ->
            val temp = (52 + 14 * kotlin.math.sin((h - 6) / 24.0 * Math.PI).coerceAtLeast(0.0) - i * 3).toInt()
            val code = if (i == 0 && h in 14..15) 61 else if (h in 10..16) 1 else 0
            val uv = if (h in 7..18) Math.round(6.5 * kotlin.math.sin((h - 7) / 11.0 * Math.PI) * 10) / 10.0 else 0.0
            app.glance.daydial.HourWeather(temp, code, uv)
        })
    }

    val tasks: List<Task> = buildList {
        // A full day today.
        fun today(title: String, h: Int, m: Int, dur: Int, color: String, project: String? = null, done: Boolean = false, source: TaskSource = TaskSource.DAYGLANCE) {
            add(Task(id(if (source == TaskSource.CALENDAR) "evt" else "task"), title, today, at(h, m), dur, completed = done,
                completedAt = if (done) today.atTime(h, m).plusMinutes(dur.toLong()).atZone(zone).toInstant() else null,
                colorHex = color, projectId = project, source = source))
        }
        today("Plan the day #planning", 7, 15, 20, TaskColors.BLUE, done = true)
        today("Easy run 6k #training", 7, 45, 45, TaskColors.GREEN, "proj-base", done = true)
        today("Dial geometry port", 8, 45, 90, TaskColors.ORANGE, "proj-dial", done = true)
        today("Hub readout and action sheet", 10, 30, 75, TaskColors.ORANGE, "proj-dial")
        today("Standup", 10, 0, 15, "#9ca3af", source = TaskSource.CALENDAR)
        today("Lunch with Priya", 12, 0, 60, TaskColors.PINK)
        set(size - 1, last().copy(energy = "restore"))
        today("Inbox zero", 13, 0, 30, TaskColors.INDIGO)
        today("Expense report", 13, 30, 30, TaskColors.INDIGO)
        today("Pinned apps grid", 14, 15, 90, TaskColors.YELLOW, "proj-home")
        today("Design review", 15, 0, 60, "#9ca3af", source = TaskSource.CALENDAR)
        today("Search and quick capture", 16, 0, 75, TaskColors.YELLOW, "proj-home")
        today("Groceries", 18, 0, 45, TaskColors.TEAL)
        today("Cook dinner", 18, 45, 60, TaskColors.PINK)
        set(size - 1, last().copy(energy = "restore"))
        today("Water the seedlings #garden", 19, 45, 15, TaskColors.TEAL, "proj-garden")
        today("Read: The Design of Everyday Things", 21, 30, 45, TaskColors.INDIGO, "proj-reading")
        add(Task(id("task"), "Pay dentist bill", today, null, 0, isAllDay = true, colorHex = TaskColors.RED))
        add(Task(id("task"), "Priya's birthday", today, null, 0, isAllDay = true, colorHex = TaskColors.PINK, completed = true))

        // Yesterday with an overrun past midnight, so the carried-in arc shows at the top.
        add(Task(id("task"), "Late movie", today.minusDays(1), at(22, 30), 120, colorHex = TaskColors.PURPLE))

        // Inbox tasks (unscheduled).
        add(Task(id("inbox"), "Call dentist", null, null, 15, priority = 2, colorHex = TaskColors.RED, deadline = today.plusDays(2)))
        add(Task(id("inbox"), "Renew passport", null, null, 60, priority = 3, colorHex = TaskColors.RED, deadline = today.plusDays(20)))
        add(Task(id("inbox"), "Order new running shoes", null, null, 20, priority = 1, colorHex = TaskColors.GREEN, projectId = "proj-base"))
        add(Task(id("inbox"), "Sketch planning screen layout", null, null, 45, colorHex = TaskColors.PINK, projectId = "proj-planning"))
        add(Task(id("inbox"), "Write README for the launcher", null, null, 60, colorHex = TaskColors.ORANGE, projectId = "proj-home"))
        add(Task(id("inbox"), "Research drip irrigation controllers", null, null, 30, colorHex = TaskColors.TEAL, projectId = "proj-garden"))

        // Project history: completed work in the past weeks drives the progress bars.
        fun past(daysBack: Long, title: String, dur: Int, project: String, color: String, done: Boolean = true) {
            val d = today.minusDays(daysBack)
            add(Task(id("task"), title, d, at(9, 0), dur, completed = done,
                completedAt = if (done) d.atTime(10, 0).atZone(zone).toInstant() else null, colorHex = color, projectId = project))
        }
        past(12, "Read dayDial.js", 60, "proj-dial", TaskColors.ORANGE)
        past(9, "Lane packing", 90, "proj-dial", TaskColors.ORANGE)
        past(6, "Needle and trail", 60, "proj-dial", TaskColors.ORANGE)
        past(10, "Window size classes", 60, "proj-home", TaskColors.YELLOW)
        past(4, "Search bar", 45, "proj-home", TaskColors.YELLOW)
        past(8, "Month grid", 90, "proj-planning", TaskColors.PINK)
        past(21, "Long run 14k", 90, "proj-base", TaskColors.GREEN)
        past(14, "Long run 16k", 100, "proj-base", TaskColors.GREEN)
        past(7, "Long run 18k", 110, "proj-base", TaskColors.GREEN)
        past(3, "Tempo 8k", 50, "proj-base", TaskColors.GREEN)
        past(11, "Hip mobility", 30, "proj-strength", TaskColors.TEAL)
        past(5, "Core circuit", 30, "proj-strength", TaskColors.TEAL)
        past(16, "Map the garden zones", 45, "proj-garden", TaskColors.TEAL)
        past(2, "Finish chapter 4", 40, "proj-reading", TaskColors.INDIGO)

        // Upcoming scheduled work spread across the month, for the month view and agenda.
        fun future(daysAhead: Long, title: String, h: Int, m: Int, dur: Int, color: String, project: String? = null, source: TaskSource = TaskSource.DAYGLANCE) {
            add(Task(id(if (source == TaskSource.CALENDAR) "evt" else "task"), title, today.plusDays(daysAhead), at(h, m), dur, colorHex = color, projectId = project, source = source))
        }
        future(1, "Hub typography pass", 9, 0, 90, TaskColors.ORANGE, "proj-dial")
        future(1, "Dentist", 14, 0, 60, "#9ca3af", source = TaskSource.CALENDAR)
        future(2, "Intervals 6x800", 7, 30, 50, TaskColors.GREEN, "proj-base")
        future(2, "Planning screen: goals tiles", 10, 0, 120, TaskColors.PINK, "proj-planning")
        future(3, "Team offsite", 9, 0, 480, "#9ca3af", source = TaskSource.CALENDAR)
        future(4, "Long run 20k", 7, 0, 120, TaskColors.GREEN, "proj-base")
        future(5, "Install drip timer", 10, 0, 90, TaskColors.TEAL, "proj-garden")
        future(6, "Finish chapter 5", 21, 0, 40, TaskColors.INDIGO, "proj-reading")
        future(8, "Dock and recents", 9, 0, 120, TaskColors.YELLOW, "proj-home")
        future(9, "Mobility session", 18, 0, 30, TaskColors.TEAL, "proj-strength")
        future(11, "Parents visiting", 12, 0, 300, TaskColors.PINK)
        future(13, "Dial gestures: move and resize", 9, 0, 150, TaskColors.ORANGE, "proj-dial")
        future(15, "Quarterly review", 13, 0, 90, "#9ca3af", source = TaskSource.CALENDAR)
        future(18, "Long run 22k", 7, 0, 130, TaskColors.GREEN, "proj-base")
        future(20, "Compost turn", 16, 0, 30, TaskColors.TEAL, "proj-garden")
        future(24, "Release candidate build", 10, 0, 120, TaskColors.ORANGE, "proj-home")
        future(27, "Book club", 19, 0, 90, TaskColors.INDIGO, "proj-reading")
        // Earlier this month for the month grid.
        for (back in listOf(13L, 15L, 17L, 19L)) {
            add(Task(id("task"), "Deep work", today.minusDays(back), at(9, 0), 120, completed = true,
                completedAt = daysAgo(back, 11), colorHex = TaskColors.BLUE))
            add(Task(id("evt"), "Sync", today.minusDays(back), at(14, 0), 30, completed = false, colorHex = "#9ca3af", source = TaskSource.CALENDAR))
        }
    }

    val chores: List<Chore> = listOf(
        Chore("chore-kitchen", "Mop kitchen", "Kitchen", 7, daysAgo(9), "Droplets"),
        Chore("chore-kettle", "Descale the kettle", "Kitchen", 60, daysAgo(96), "Coffee"),
        Chore("chore-fridge", "Clean fridge shelves", "Kitchen", 30, daysAgo(23), "Refrigerator"),
        Chore("chore-sheets", "Change bed sheets", "Bedroom", 14, daysAgo(12), "BedDouble"),
        Chore("chore-filter", "Replace HVAC filter", "Home", 90, daysAgo(31), "Wind"),
        Chore("chore-plants", "Water houseplants", "Plants", 4, daysAgo(1), "Leaf"),
        Chore("chore-fertilize", "Fertilize the lawn", "Garden", 120, daysAgo(230), "Sprout"),
        Chore("chore-smoke", "Test smoke alarms", "Home", 180, daysAgo(40), "Bell"),
        Chore("chore-car", "Check tyre pressure", "Car", 30, daysAgo(27), "Car"),
        Chore("chore-oil", "Car oil change", "Car", 180, daysAgo(400), "Wrench"),
        Chore("chore-backup", "Back up laptop", "Tech", 7, daysAgo(2), "HardDrive"),
        Chore("chore-gutters", "Clear the gutters", "Home", null, null, "Home"),
    )

    val people: List<Person> = listOf(
        Person("person-mum", "Mum", null, "Family", 7, daysAgo(2, 19), "call", specialDate = java.time.MonthDay.from(today.plusDays(12))),
        Person("person-dad", "Dad", null, "Family", 7, daysAgo(8, 18), "call"),
        Person("person-sam", "Sam Okafor", "Sam", "Close friends", 14, daysAgo(16, 20), "text", specialDate = java.time.MonthDay.from(today.plusDays(3))),
        Person("person-priya", "Priya Natarajan", null, "Close friends", 14, daysAgo(3, 12), "in_person"),
        Person("person-lena", "Lena Fischer", null, "Friends", 30, daysAgo(12, 9), "text", unreturned = true),
        Person("person-marco", "Marco Bianchi", null, "Friends", 30, daysAgo(44, 21), "email"),
        Person("person-jo", "Jo Whitfield", "Jo", "Work", 21, daysAgo(5, 16), "video", specialDate = java.time.MonthDay.from(today.plusDays(25)), specialLabel = "Work anniversary"),
        Person("person-aunt", "Aunt Ruth", null, "Family", 60, null, null),
    )
}
