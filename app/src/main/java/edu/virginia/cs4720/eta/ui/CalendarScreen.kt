package edu.virginia.cs4720.eta.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import edu.virginia.cs4720.eta.data.Plan
import edu.virginia.cs4720.eta.data.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val plans = MutableStateFlow<List<Plan>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    init {
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                PlanRepository.observe(uid)
                    .catch { error.value = it.message }
                    .collect { plans.value = it }
            }
        }
    }
}

private const val FIRST_HOUR = 7     // week grid shows 7 AM ...
private const val LAST_HOUR = 22     // ... to 10 PM
private val HOUR_HEIGHT = 44.dp
private val TIME_COL = 44.dp

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val dayTitle = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
private val shortDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val dowShort = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Sunday that starts the week containing [d]. */
private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onAddPlan: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    vm: CalendarViewModel = viewModel(),
) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf("week") }
    // Selected day, kept as text so rememberSaveable can store it.
    var selectedIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(selectedIso)
    val today = LocalDate.now()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calendar", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = { PrimaryButton("+ Add plan", { onAddPlan(selectedIso) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            error?.let { NoticeBox(it) }
            ChoiceRow(listOf("week" to "Week", "month" to "Month"), view, { view = it })

            // Previous / next week or month, with the range in the middle.
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("<", {
                    selectedIso = (if (view == "week") selected.minusWeeks(1) else selected.minusMonths(1)).toString()
                })
                val title = if (view == "week") {
                    val s = weekStart(selected)
                    "${s.format(shortDay)} – ${s.plusDays(6).format(shortDay)}"
                } else {
                    YearMonth.from(selected).format(monthTitle)
                }
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                SecondaryButton(">", {
                    selectedIso = (if (view == "week") selected.plusWeeks(1) else selected.plusMonths(1)).toString()
                })
            }
            if (selected != today) {
                TextButton(onClick = { selectedIso = today.toString() }) { Text("Jump to today", color = Ink) }
            }

            if (view == "week") {
                WeekGrid(plans, weekStart(selected), today, onOpenPlan)
            } else {
                MonthGrid(plans, selected, today, onSelect = { selectedIso = it.toString() })
                DayList(plans, selected, onOpenPlan)
            }
        }
    }
}

@Composable
private fun WeekGrid(plans: List<Plan>, start: LocalDate, today: LocalDate, onOpenPlan: (String) -> Unit) {
    val days = (0..6).map { start.plusDays(it.toLong()) }
    Column(Modifier.fillMaxSize()) {
        // Day headers
        Row(Modifier.fillMaxWidth().border(1.dp, Line)) {
            Box(Modifier.width(TIME_COL))
            days.forEachIndexed { i, d ->
                val isToday = d == today
                Column(
                    Modifier.weight(1f).background(if (isToday) Fill else Color.White).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dowShort[i], fontSize = 11.sp, color = if (isToday) Color.White else Quiet)
                    Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isToday) Color.White else Ink)
                }
            }
        }
        // Hour grid with plan blocks, scrolls vertically.
        val hours = LAST_HOUR - FIRST_HOUR
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        ) {
            val colWidth: Dp = (maxWidth - TIME_COL) / 7
            Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT * hours + 72.dp)) {
                for (h in 0 until hours) {
                    Box(
                        Modifier
                            .offset(y = HOUR_HEIGHT * h)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFDDDDDD)),
                    )
                    Text(
                        hourLabel(FIRST_HOUR + h),
                        modifier = Modifier.offset(x = 2.dp, y = HOUR_HEIGHT * h + 2.dp),
                        fontSize = 10.sp,
                        color = Quiet,
                    )
                }
                days.forEachIndexed { i, d ->
                    plans.filter { it.occursOn(d) }.forEach { p ->
                        val startH = p.localTime().hour + p.localTime().minute / 60f
                        val endH = p.localEndTime().hour + p.localEndTime().minute / 60f
                        val top = (startH - FIRST_HOUR).coerceIn(0f, hours.toFloat())
                        val bottom = (endH - FIRST_HOUR).coerceIn(top + 0.4f, hours.toFloat() + 1f)
                        Box(
                            Modifier
                                .offset(x = TIME_COL + colWidth * i + 1.dp, y = HOUR_HEIGHT * top + 1.dp)
                                .width(colWidth - 2.dp)
                                .height(HOUR_HEIGHT * (bottom - top) - 2.dp)
                                .border(1.dp, Line)
                                .background(Tint)
                                .clickable { onOpenPlan(p.id) }
                                .padding(2.dp),
                        ) {
                            Text(p.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(plans: List<Plan>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val month = YearMonth.from(selected)
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7   // blanks before day 1 (Sunday-first)
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    if (d == null) {
                        Box(Modifier.weight(1f).height(44.dp))
                    } else {
                        val on = d == selected
                        val count = plans.count { it.occursOn(d) }
                        Column(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .border(if (d == today) 2.dp else 1.dp, if (on || d == today) Line else Color(0xFFCCCCCC))
                                .background(if (on) Fill else Color.White)
                                .selectable(selected = on, onClick = { onSelect(d) }, role = Role.Button),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${d.dayOfMonth}", fontSize = 14.sp, color = if (on) Color.White else Ink)
                            Text("•".repeat(count.coerceAtMost(3)), fontSize = 10.sp, color = if (on) Color.White else Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayList(plans: List<Plan>, day: LocalDate, onOpenPlan: (String) -> Unit) {
    val dayPlans = plans.filter { it.occursOn(day) }.sortedBy { it.startTime }
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(day.format(dayTitle), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (dayPlans.isEmpty()) Text("No plans this day.", color = Quiet)
        dayPlans.forEach { p ->
            Row(
                Modifier.fillMaxWidth().border(1.dp, Line).clickable { onOpenPlan(p.id) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${Fmt.time(p.localTime())} – ${Fmt.time(p.localEndTime())}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("  ${p.title}", modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.destName, fontSize = 12.sp, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp))
            }
        }
        Box(Modifier.height(80.dp))   // room above the Add button
    }
}
