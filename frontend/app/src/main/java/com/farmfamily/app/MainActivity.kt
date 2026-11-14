package com.farmfamily.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FarmFamilyTheme { FarmFamilyApp() } }
    }
}

private val Forest = Color(0xFF174B38)
private val Green = Color(0xFF377B55)
private val Leaf = Color(0xFFE5F0E6)
private val Cream = Color(0xFFFAF9F4)
private val Ink = Color(0xFF202923)
private val Muted = Color(0xFF68736C)
private val Line = Color(0xFFE5E9E3)
private val Gold = Color(0xFFE9B949)

@Composable
private fun FarmFamilyTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Forest, onPrimary = Color.White, secondary = Green, onSecondary = Color.White,
        background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink,
        surfaceVariant = Leaf, outline = Line, tertiary = Gold
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

private enum class MainTab(val label: String) {
    Home("Home"), Records("Records"), Reports("Reports"), Assistant("AI"), More("More")
}

private data class FarmField(val name: String, val place: String, val area: String, val crop: String, val status: String)
private data class FarmExpense(val category: String, val detail: String, val amount: Int, val date: String, val field: String)

private interface FarmRepository {
    fun fields(): List<FarmField>
    fun expenses(): List<FarmExpense>
    fun addField(field: FarmField)
    fun addExpense(expense: FarmExpense)
}

private class LocalMockFarmRepository : FarmRepository {
    private val fieldRows = mutableStateListOf(
        FarmField("Khet A", "Dumara", "2 Bigha", "Rice / Dhan", "Growing"),
        FarmField("Khet B", "Dumara", "1.5 Bigha", "Wheat", "Growing"),
        FarmField("Khet C", "Basantpur", "1 Bigha", "Potato", "Planning")
    )
    private val expenseRows = mutableStateListOf(
        FarmExpense("Fertilizer", "Urea and DAP", 4500, "13 Nov", "Khet A"),
        FarmExpense("Labor", "Paddy transplanting · 5 workers", 3500, "12 Nov", "Khet A"),
        FarmExpense("Tractor", "Field preparation · 45 min", 1800, "10 Nov", "Khet B")
    )
    override fun fields() = fieldRows.toList()
    override fun expenses() = expenseRows.toList()
    override fun addField(field: FarmField) { fieldRows.add(0, field) }
    override fun addExpense(expense: FarmExpense) { expenseRows.add(0, expense) }
}

private object Strings {
    val appName = "FARM FAMILY"
    val tagline = "Grow Together. Prosper Together."
    val family = "Sharma Family"
    val date = "Saturday, 14 November 2026 · 2083 Kartik 28"
    val tabs = mapOf(MainTab.Home to "Home", MainTab.Records to "Records", MainTab.Reports to "Reports", MainTab.Assistant to "AI", MainTab.More to "More")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FarmFamilyApp() {
    val repo = remember { LocalMockFarmRepository() }
    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var route by remember { mutableStateOf<String?>(null) }
    var quickAdd by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); toast = null } }

    Scaffold(
        containerColor = Cream,
        topBar = {
            if (route == null) TopAppBar(
                title = {
                    Column {
                        Text(Strings.appName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Forest, letterSpacing = 1.sp)
                        Text(Strings.family, fontSize = 12.sp, color = Muted)
                    }
                },
                actions = {
                    IconButton(onClick = { route = "Search" }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Search, "Search farm records") }
                    IconButton(onClick = { route = "Notifications" }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.NotificationsNone, "Notifications") }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            ) else TopAppBar(
                title = { Text(route ?: "", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = { route = null }) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        },
        bottomBar = {
            if (route == null) NavigationBar(containerColor = Color.White) {
                MainTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        MainTab.Home -> Icons.Default.Home
                        MainTab.Records -> Icons.Default.FolderOpen
                        MainTab.Reports -> Icons.Default.BarChart
                        MainTab.Assistant -> Icons.Default.AutoAwesome
                        MainTab.More -> Icons.Default.Menu
                    }
                    NavigationBarItem(selected = selectedTab == tab, onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(Strings.tabs.getValue(tab), maxLines = 1) })
                }
            }
        },
        floatingActionButton = {
            if (route == null && selectedTab in listOf(MainTab.Home, MainTab.Records)) {
                ExtendedFloatingActionButton(onClick = { quickAdd = true }, icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Quick add") }, containerColor = Forest, contentColor = Color.White)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            if (route != null) DetailRoute(route!!, repo, onRoute = { route = it }, onMessage = { toast = it })
            else when (selectedTab) {
                MainTab.Home -> HomeScreen(repo, offline, onOffline = { offline = !offline }, onRoute = { route = it }, onQuick = { quickAdd = true })
                MainTab.Records -> RecordsScreen(repo, onRoute = { route = it }, onQuick = { quickAdd = true })
                MainTab.Reports -> ReportsScreen()
                MainTab.Assistant -> AssistantScreen()
                MainTab.More -> MoreScreen(onRoute = { route = it }, onOffline = { offline = !offline })
            }
        }
    }
    if (quickAdd) QuickAddSheet(onDismiss = { quickAdd = false }, onSelect = { choice ->
        quickAdd = false
        when (choice) { "Field" -> route = "Add Field"; "Expense" -> route = "Add Expense"; else -> route = "Add $choice" }
    })
}

@Composable
private fun HomeScreen(repo: FarmRepository, offline: Boolean, onOffline: () -> Unit, onRoute: (String) -> Unit, onQuick: () -> Unit) {
    val fields = repo.fields()
    val expenses = repo.expenses()
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.family, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(Strings.date, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                IconButton(onClick = onOffline) { Icon(if (offline) Icons.Default.CloudOff else Icons.Default.CloudDone, if (offline) "Simulate online" else "Simulate offline", tint = if (offline) Color(0xFFB66B1E) else Green) }
            }
        }
        if (offline) item { OfflineBanner() }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Forest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("THIS SEASON", color = Color(0xFFC6DCCB), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                            Text("Rice / Dhan", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                            Text("Khet A · 2083 BS / 2026 AD", color = Color(0xFFE0E9E1), fontSize = 13.sp)
                        }
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF2D664C)), contentAlignment = Alignment.Center) { Text("🌾", fontSize = 26.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { .64f }, modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape), color = Color(0xFFA8D89A), trackColor = Color(0xFF426D54))
                        Spacer(Modifier.width(10.dp)); Text("Growing", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        item { SectionHeading("Farm overview", "See all", { onRoute("Fields") }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                KpiCard("Fields", "${fields.size}", "Across your family", Icons.Default.Landscape, Modifier.width(150.dp))
                KpiCard("Land area", "4.5 Bigha", "3 active fields", Icons.Default.SquareFoot, Modifier.width(150.dp))
                KpiCard("Active crops", "3", "2 growing", Icons.Default.Spa, Modifier.width(150.dp))
                KpiCard("Expenses", "NPR 9,800", "This season", Icons.Default.Payments, Modifier.width(150.dp))
                KpiCard("Sales", "NPR 42,000", "This season", Icons.Default.TrendingUp, Modifier.width(150.dp))
            }
        }
        item { SectionHeading("Recent activity", "View all", { onRoute("Activity") }) }
        items(expenses.take(3)) { expense -> ActivityRow(expense) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF6DE), border = BorderStroke(1.dp, Color(0xFFF0E4C3)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, null, tint = Color(0xFF8A6A22)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Backup is up to date", fontWeight = FontWeight.SemiBold); Text("Last backup · 2 days ago", color = Muted, fontSize = 12.sp) }
                    TextButton(onClick = { onRoute("Backup & Export") }) { Text("Manage") }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Leaf, modifier = Modifier.fillMaxWidth().clickable { onRoute("FARM FAMILY AI") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Forest) }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Ask FARM FAMILY AI", fontWeight = FontWeight.SemiBold); Text("Explore your farm records", color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Green)
                }
            }
        }
        item { TextButton(onClick = onQuick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a farm record") } }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF1DF), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudOff, null, tint = Color(0xFF965C18)); Spacer(Modifier.width(10.dp)); Column { Text("You're offline", fontWeight = FontWeight.SemiBold); Text("Your records are available. Changes will sync when you're back online.", fontSize = 12.sp, color = Muted) } }
    }
}

@Composable
private fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) } }
}

@Composable
private fun KpiCard(label: String, value: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(17.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(20.dp)); Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium); Text(sub, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun ActivityRow(expense: FarmExpense) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Leaf), contentAlignment = Alignment.Center) { Icon(Icons.Default.ReceiptLong, null, tint = Green, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("${expense.category} expense added", fontWeight = FontWeight.Medium); Text("${expense.detail} · ${expense.field}", fontSize = 12.sp, color = Muted) }
        Column(horizontalAlignment = Alignment.End) { Text("NPR ${expense.amount}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(expense.date, fontSize = 11.sp, color = Muted) }
    }
}

@Composable
private fun RecordsScreen(repo: FarmRepository, onRoute: (String) -> Unit, onQuick: () -> Unit) {
    val categories = listOf("Fields / Khet" to Icons.Default.Landscape, "Crops" to Icons.Default.Spa, "Seeds" to Icons.Default.Grain, "Fertilizer" to Icons.Default.Eco, "Labor" to Icons.Default.Groups, "Machinery" to Icons.Default.Agriculture, "Expenses" to Icons.Default.Payments, "Harvest" to Icons.Default.Agriculture, "Sales" to Icons.Default.PointOfSale, "Historical prices" to Icons.Default.ShowChart)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Your farm records", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Choose a record type to view and manage.", color = Muted) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Forest, modifier = Modifier.fillMaxWidth().clickable(onClick = onQuick)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AddCircle, null, tint = Color.White); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Quick add", color = Color.White, fontWeight = FontWeight.Bold); Text("Record work while it's fresh", color = Color(0xFFDDE8DF), fontSize = 12.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Color.White) }
            }
        }
        items(categories.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, icon) ->
                    Surface(Modifier.weight(1f).height(104.dp).clickable { onRoute(label) }, shape = RoundedCornerShape(17.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = Green); Row(verticalAlignment = Alignment.CenterVertically) { Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp)) } }
                    }
                }
            }
        }
        item { SectionHeading("Recent expenses", "View all", { onRoute("Expenses") }) }
        items(repo.expenses().take(2)) { ActivityRow(it) }
    }
}

@Composable
private fun ReportsScreen() {
    var period by remember { mutableStateOf("2083 BS") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Farm reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Understand how your family farm is doing.", color = Muted) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("2083 BS", "2026 AD").forEach { FilterChip(selected = period == it, onClick = { period = it }, label = { Text(it) }) } } }
        item { ReportCard("Seasonal overview", "Rice · Khet A", "Production", "120 Quintal", listOf("Expenses" to 9800, "Sales" to 42000, "Harvest" to 120), Forest) }
        item { ReportCard("Expense analysis", "This season · ${period}", "Total expenses", "NPR 9,800", listOf("Fertilizer" to 46, "Labor" to 36, "Tractor" to 18), Green) }
        item { ReportCard("Crop performance", "Compare harvest totals", "Top crop", "Rice · 120 Quintal", listOf("Rice" to 70, "Wheat" to 45, "Potato" to 30), Color(0xFF557D45)) }
        item { Text("Market Historical Price", fontWeight = FontWeight.Bold); Text("Compare market prices with your family's recorded expenses.", color = Muted, fontSize = 13.sp) }
        item { OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.ShowChart, null); Spacer(Modifier.width(8.dp)); Text("View historical prices") } }
    }
}

@Composable
private fun ReportCard(title: String, sub: String, metricLabel: String, metric: String, parts: List<Pair<String, Int>>, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(sub, color = Muted, fontSize = 12.sp)
            Text(metricLabel.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold); Text(metric, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            val max = (parts.maxOfOrNull { it.second } ?: 1).toFloat()
            parts.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.width(82.dp), fontSize = 12.sp); LinearProgressIndicator(progress = { value / max }, modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape), color = color, trackColor = Leaf); Spacer(Modifier.width(8.dp)); Text(if (title == "Expense analysis") "$value%" else value.toString(), fontSize = 11.sp, color = Muted) }
            }
        }
    }
}

@Composable
private fun AssistantScreen() {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf(false) }
    val suggestions = listOf("How much rice did we produce last year?", "How much fertilizer did we spend?", "Which Khet produced the most rice?", "Show tractor expenses")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Forest, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("FARM FAMILY AI", color = Color(0xFFBBD7C2), letterSpacing = 1.sp, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("Your farm records,\nmade easier to understand.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold); Text("Answers use your family's saved records.", color = Color(0xFFE0E9E1), fontSize = 12.sp) }
                }
            }
            item { Text("Try asking", fontWeight = FontWeight.Bold) }
            items(suggestions) { suggestion -> SuggestionChip(onClick = { question = suggestion; answer = true }, label = { Text(suggestion, maxLines = 2) }, icon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp)) }) }
            if (answer) item {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Rice Production · 2082 BS", color = Muted, fontSize = 12.sp); Text("120 Quintal", fontSize = 27.sp, color = Forest, fontWeight = FontWeight.Bold)
                        HorizontalDivider(color = Line); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Khet A"); Text("80 Quintal", fontWeight = FontWeight.SemiBold) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Khet B"); Text("40 Quintal", fontWeight = FontWeight.SemiBold) }
                        Text("Based on 2 harvest records", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = question, onValueChange = { question = it }, placeholder = { Text("Ask about your farm…") }, modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                IconButton(onClick = { if (question.isNotBlank()) answer = true }, modifier = Modifier.size(48.dp).clip(CircleShape).background(Forest)) { Icon(Icons.Default.Send, "Send question", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun MoreScreen(onRoute: (String) -> Unit, onOffline: () -> Unit) {
    val rows = listOf("Family members" to Icons.Default.Groups, "Activity" to Icons.Default.History, "Notifications" to Icons.Default.Notifications, "Backup & Export" to Icons.Default.Backup, "Global search" to Icons.Default.Search, "Settings" to Icons.Default.Settings, "Profile" to Icons.Default.AccountCircle, "Welcome & language" to Icons.Default.Translate, "Design system" to Icons.Default.Palette)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Family tools and app preferences", color = Muted) }
        item { OfflineBanner(); TextButton(onClick = onOffline) { Text("Toggle offline preview") } }
        items(rows) { (label, icon) -> ListRow(label, "", icon) { onRoute(label) } }
        item { Spacer(Modifier.height(10.dp)); Text("FARM FAMILY", color = Forest, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(Strings.tagline, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun ListRow(title: String, sub: String = "", icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(15.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Green); Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val choices = listOf("Field" to Icons.Default.Landscape, "Crop" to Icons.Default.Spa, "Expense" to Icons.Default.Payments, "Labor" to Icons.Default.Groups, "Machinery" to Icons.Default.Agriculture, "Harvest" to Icons.Default.Grain, "Sale" to Icons.Default.PointOfSale)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What would you like to add?", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Choose a record to get started.", color = Muted)
            choices.forEach { (name, icon) -> ListRow("Add $name", "", icon) { onSelect(name) } }
        }
    }
}

@Composable
private fun DetailRoute(route: String, repo: FarmRepository, onRoute: (String?) -> Unit, onMessage: (String) -> Unit) {
    when (route) {
        "Add Field" -> AddFieldScreen(onCancel = { onRoute(null) }, onSave = { field -> repo.addField(field); onRoute(null); onMessage("Field saved") })
        "Add Expense" -> AddExpenseScreen(onCancel = { onRoute(null) }, onSave = { expense -> repo.addExpense(expense); onRoute(null); onMessage("Expense saved") })
        "Fields", "Fields / Khet" -> FieldListScreen(repo, onRoute, onMessage)
        "Expenses" -> ExpenseListScreen(repo, onRoute)
        "Search", "Global search" -> SearchScreen(repo)
        "Notifications" -> GenericListScreen("Notifications", listOf("Backup reminder" to "Next backup in 5 days", "Records synced" to "Your latest changes are up to date"), Icons.Default.Notifications)
        "Activity" -> GenericListScreen("Family activity", listOf("Ramesh · Added fertilizer expense" to "NPR 4,500 · Today", "Sita · Updated labor record" to "Yesterday", "You · Recorded rice harvest" to "2 days ago"), Icons.Default.History)
        "Family members" -> GenericListScreen("Family members", listOf("Ramesh Sharma" to "Owner · Active", "Sita Sharma" to "Editor · Active", "Arun Sharma" to "Viewer · Active", "Invite a family member" to "Create an invite code or link"), Icons.Default.Groups)
        "Backup & Export" -> BackupScreen(onMessage)
        "Settings" -> SettingsScreen(onMessage)
        "Welcome & language" -> WelcomeScreen(onRoute)
        "Design system" -> DesignSystemScreen(onMessage)
        "Profile" -> GenericListScreen("Your profile", listOf("Anil Sharma" to "anil.sharma@example.com", "Language" to "English", "Calendar" to "Bikram Sambat + AD", "Family memberships" to "Sharma Family"), Icons.Default.AccountCircle)
        "FARM FAMILY AI" -> AssistantScreen()
        else -> CategoryScreen(route, repo, onRoute)
    }
}

@Composable
private fun WelcomeScreen(onRoute: (String?) -> Unit) {
    var language by remember { mutableStateOf("English") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Leaf), contentAlignment = Alignment.Center) { Text("🌾", fontSize = 36.sp) }
        Text(Strings.appName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Forest, letterSpacing = 1.sp)
        Text(Strings.tagline, fontSize = 17.sp, color = Muted)
        Text("Keep your family farm's fields, crops, work, and harvest together in one clear place.", fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Choose your language", fontWeight = FontWeight.Bold)
        listOf("English", "नेपाली", "Awadhi", "हिन्दी", "اردو").forEach { value ->
            Surface(shape = RoundedCornerShape(14.dp), color = if (language == value) Leaf else Color.White, border = BorderStroke(1.dp, if (language == value) Green else Line), modifier = Modifier.fillMaxWidth().clickable { language = value }) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Text(value, Modifier.weight(1f), fontWeight = FontWeight.Medium); if (language == value) Icon(Icons.Default.CheckCircle, null, tint = Forest) }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { onRoute(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Create a family") }
        OutlinedButton(onClick = { onRoute(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Join a family") }
        Text("Frontend preview · local sample family", Modifier.align(Alignment.CenterHorizontally), fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun DesignSystemScreen(onMessage: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("FARM FAMILY design system", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Shared tokens and components used across the app.", color = Muted) }
        item { SectionHeading("Color tokens") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Forest" to Forest, "Natural" to Green, "Leaf" to Leaf, "Cream" to Cream, "Highlight" to Gold).forEach { (name, color) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(color).then(Modifier)) ; Text(name, fontSize = 10.sp, color = Muted) } } } }
        item { SectionHeading("Typography") }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Display · 28sp", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Heading · 23sp", fontSize = 23.sp, fontWeight = FontWeight.SemiBold); Text("Section heading · 17sp", fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("Body · 14sp", fontSize = 14.sp); Text("Caption · 12sp", fontSize = 12.sp, color = Muted); Text("Label · 11sp", fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
        item { SectionHeading("Buttons and status") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onMessage("Primary action") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Primary") }; OutlinedButton(onClick = { onMessage("Secondary action") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Secondary") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { StatusChip("Growing"); AssistChip(onClick = {}, label = { Text("Filter") }, leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(16.dp)) }); Badge { Text("3") } } }
        item { SectionHeading("Inputs") }
        item { FormInput("Text field", "Khet A", {}, "Enter a value") }
        item { OutlinedTextField("4.5", {}, modifier = Modifier.fillMaxWidth(), label = { Text("Number field") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)) }
        item { SectionHeading("Cards, touch, spacing") }
        item { KpiCard("Total fields", "3", "4.5 Bigha", Icons.Default.Landscape, Modifier.fillMaxWidth()) }
        item { Text("Inputs and actions use 48dp minimum height. Cards use 16–20dp corners, 1dp borders, and a 4 / 8 / 12 / 16 / 24dp spacing scale.", color = Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun FieldListScreen(repo: FarmRepository, onRoute: (String?) -> Unit, onMessage: (String) -> Unit) {
    val fields = repo.fields()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Fields / Khet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${fields.size} fields · 4.5 Bigha", color = Muted) }; Button(onClick = { onRoute("Add Field") }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Text(" Add") } } }
        items(fields) { field ->
            Surface(shape = RoundedCornerShape(19.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().clickable { onMessage("${field.name} · ${field.crop}") }) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Leaf), contentAlignment = Alignment.Center) { Icon(Icons.Default.Landscape, null, tint = Green) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(field.name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(field.place, color = Muted, fontSize = 12.sp) }; StatusChip(field.status) }
                    HorizontalDivider(color = Line); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(field.area, fontWeight = FontWeight.SemiBold); Text(field.crop, color = Green, fontWeight = FontWeight.Medium) }
                }
            }
        }
        if (fields.isEmpty()) item { EmptyState("No fields yet", "Add your first Khet to start tracking your farm.", "Add Field", Icons.Default.Landscape) { onRoute("Add Field") } }
    }
}

@Composable
private fun StatusChip(value: String) { Surface(shape = RoundedCornerShape(99.dp), color = Leaf) { Text(value, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Forest, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) } }

@Composable
private fun ExpenseListScreen(repo: FarmRepository, onRoute: (String?) -> Unit) {
    val expenses = repo.expenses()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Expenses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("This season · NPR 9,800", color = Muted) }; Button(onClick = { onRoute("Add Expense") }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Text(" Add") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "This month", "This season").forEachIndexed { index, label -> FilterChip(selected = index == 0, onClick = {}, label = { Text(label) }) } } }
        items(expenses) { ActivityRow(it) }
        if (expenses.isEmpty()) item { EmptyState("No expenses yet", "Record a farm cost to understand your season spending.", "Add expense", Icons.Default.Payments) { onRoute("Add Expense") } }
    }
}

@Composable
private fun CategoryScreen(category: String, repo: FarmRepository, onRoute: (String?) -> Unit) {
    val entries = when (category) {
        "Crops" -> listOf("Rice / Dhan" to "Khet A · 2083 BS · Growing", "Wheat" to "Khet B · 2083 BS · Growing", "Potato" to "Khet C · 2083 BS · Planning")
        "Seeds" -> listOf("Rice seed · Sabitri" to "45 Kg · Purchased 4 Nov · NPR 3,600", "Wheat seed · Gautam" to "30 Kg · Purchased 1 Nov · NPR 2,100")
        "Fertilizer" -> listOf("Urea" to "50 Kg · Khet A · NPR 2,800", "DAP" to "25 Kg · Khet A · NPR 1,700")
        "Labor" -> listOf("Paddy transplanting" to "5 workers · 1 day · NPR 3,500", "Field irrigation" to "2 workers · 3 hours · NPR 900")
        "Machinery" -> listOf("Tractor" to "45 minutes · Khet B · NPR 1,800", "Pump" to "2 hours · Khet A · NPR 600")
        "Harvest" -> listOf("Rice / Dhan" to "120 Quintal · Khet A · 22 Oct 2026", "Wheat" to "45 Quintal · Khet B · 18 Apr 2026")
        "Sales" -> listOf("Rice · Ram Traders" to "60 Quintal · NPR 42,000 · Paid", "Potato · Local market" to "Pending payment")
        "Historical prices" -> listOf("Paddy · NPR 2,450 / Quintal" to "Dumara market · 8 Nov 2026", "Urea · NPR 56 / Kg" to "Local supplier · 4 Nov 2026")
        else -> emptyList()
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(category, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${entries.size} records · Sharma Family", color = Muted) }; Button(onClick = { onRoute("Add $category") }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Text(" Add") } } }
        if (category == "Crops") item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Growing", "All", "Completed").forEachIndexed { index, label -> FilterChip(selected = index == 0, onClick = {}, label = { Text(label) }) } } }
        if (category == "Historical prices") item { Surface(color = Color(0xFFFFF6DE), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("Market Historical Price · separate from family expenses", Modifier.padding(14.dp), color = Color(0xFF71551C), fontSize = 12.sp) } }
        items(entries) { (title, subtitle) -> ListRow(title, subtitle, Icons.Default.ReceiptLong) {} }
        if (entries.isEmpty()) item { EmptyState("No ${category.lowercase()} yet", "Add your first record to start building your farm history.", "Add $category", Icons.Default.Add) { onRoute("Add $category") } }
    }
}

@Composable
private fun GenericListScreen(title: String, entries: List<Pair<String, String>>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Sharma Family", color = Muted) }
        items(entries) { (name, description) -> ListRow(name, description, icon) {} }
    }
}

@Composable
private fun EmptyState(title: String, description: String, action: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(36.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(description, color = Muted); Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

@Composable
private fun AddFieldScreen(onCancel: () -> Unit, onSave: (FarmField) -> Unit) {
    var name by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var area by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("Bigha") }; var notes by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Add a field", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Add a Khet to your family's farm records.", color = Muted) }
            item { FormInput("Khet name *", name, { name = it }, "e.g. Khet D") }
            item { FormInput("Location", location, { location = it }, "Village or locality") }
            item { FormInput("Area *", area, { area = it }, "Land area", KeyboardType.Decimal) }
            item { Text("Area unit", fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Bigha", "Mandi", "Other").forEach { FilterChip(selected = unit == it, onClick = { unit = it }, label = { Text(it) }) } } }
            item { FormInput("Notes", notes, { notes = it }, "Optional details") }
            if (error) item { Text("Enter a name and a valid land area.", color = MaterialTheme.colorScheme.error) }
        }
        FormActions(onCancel, "Save field") { if (name.isBlank() || area.toDoubleOrNull() == null) error = true else onSave(FarmField(name, location.ifBlank { "Location not set" }, "${area} $unit", "No crop yet", "Planning")) }
    }
}

@Composable
private fun AddExpenseScreen(onCancel: () -> Unit, onSave: (FarmExpense) -> Unit) {
    var category by remember { mutableStateOf("Fertilizer") }; var description by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var field by remember { mutableStateOf("Khet A") }; var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Add expense", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Record a cost for your farm.", color = Muted) }
            item { Text("Category *", fontWeight = FontWeight.SemiBold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Seeds", "Fertilizer", "Labor", "Tractor", "Pump/Fuel", "Custom").forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) } } }
            item { FormInput("Description", description, { description = it }, "What was this expense for?") }
            item { FormInput("Amount (NPR) *", amount, { amount = it }, "0", KeyboardType.Decimal) }
            item { Text("Field", fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Khet A", "Khet B", "Khet C").forEach { FilterChip(selected = field == it, onClick = { field = it }, label = { Text(it) }) } } }
            item { Text("Date · 14 Nov 2026 / 2083 Kartik 28", color = Muted, fontSize = 13.sp) }
            if (error) item { Text("Enter a valid amount.", color = MaterialTheme.colorScheme.error) }
        }
        FormActions(onCancel, "Save expense") { val value = amount.toIntOrNull(); if (value == null || value <= 0) error = true else onSave(FarmExpense(category, description.ifBlank { category }, value, "14 Nov", field)) }
    }
}

@Composable
private fun FormInput(label: String, value: String, onValue: (String) -> Unit, placeholder: String, keyboard: KeyboardType = KeyboardType.Text) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); OutlinedTextField(value, onValue, modifier = Modifier.fillMaxWidth(), placeholder = { Text(placeholder) }, singleLine = keyboard != KeyboardType.Text, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(14.dp)) }
}

@Composable
private fun FormActions(onCancel: () -> Unit, saveLabel: String, onSave: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 3.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Cancel") }; Button(onClick = onSave, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(saveLabel) } } }
}

@Composable
private fun SearchScreen(repo: FarmRepository) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search fields, crops, expenses…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        if (query.isBlank()) { Text("Recent searches", fontWeight = FontWeight.Bold); listOf("Khet A", "Fertilizer", "Rice harvest").forEach { ListRow(it, "Recent search", Icons.Default.History) { query = it } } }
        else {
            Text("Results", fontWeight = FontWeight.Bold)
            repo.fields().filter { "${it.name} ${it.place} ${it.crop}".contains(query, true) }.forEach { ListRow(it.name, "${it.place} · ${it.area} · ${it.crop}", Icons.Default.Landscape) {} }
            repo.expenses().filter { "${it.category} ${it.detail} ${it.field}".contains(query, true) }.forEach { ListRow(it.category, "${it.detail} · NPR ${it.amount}", Icons.Default.Payments) {} }
        }
    }
}

@Composable
private fun BackupScreen(onMessage: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Backup & Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Keep your family's farm records safe and portable.", color = Muted) }
        item { Surface(shape = RoundedCornerShape(18.dp), color = Leaf, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("Backup health", fontWeight = FontWeight.SemiBold); Text("Up to date", color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Last backup · 2 days ago    Next reminder · 5 days", color = Muted, fontSize = 12.sp) } } }
        item { SectionHeading("Export records") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("PDF", "CSV", "XLSX", "ZIP").forEach { OutlinedButton(onClick = { onMessage("$it export prepared") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(it) } } } }
        item { SectionHeading("Import a backup") }
        item { LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)) }
        item { Text(listOf("Select backup", "Validate file", "Preview records", "Confirm import", "Import complete")[step], fontWeight = FontWeight.Bold) }
        item { Text("Fields 3 · Crops 5 · Expenses 18 · Harvests 4 · Sales 6", color = Muted, fontSize = 13.sp) }
        item { if (step < 3) Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (step == 0) "Select backup file" else "Continue") } else Button(onClick = { if (step == 3) { step = 4; onMessage("Import completed") } else step = 0 }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (step == 3) "Confirm import" else "Start another import") } }
        item { Text("Backup history", fontWeight = FontWeight.Bold) }
        items(listOf("12 Nov 2026" to "Completed · 1.4 MB", "5 Nov 2026" to "Completed · 1.3 MB")) { (d, s) -> ListRow(d, s, Icons.Default.Backup) {} }
    }
}

@Composable
private fun SettingsScreen(onMessage: (String) -> Unit) {
    var language by remember { mutableStateOf("English") }; var calendar by remember { mutableStateOf("BS + AD") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Make FARM FAMILY work for your household.", color = Muted) }
        item { Text("LANGUAGE", fontWeight = FontWeight.Bold, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("English", "नेपाली", "Awadhi", "हिन्दी", "اردو").forEach { FilterChip(selected = language == it, onClick = { language = it; onMessage("Language preference saved") }, label = { Text(it, fontSize = 11.sp) }) } } }
        item { Text("CALENDAR", fontWeight = FontWeight.Bold, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("AD", "BS", "BS + AD").forEach { FilterChip(selected = calendar == it, onClick = { calendar = it }, label = { Text(it) }) } } }
        item { ListRow("Units", "Bigha · Mandi · Kg · Quintal", Icons.Default.SquareFoot) {} }
        item { ListRow("Notifications", "Backup and family activity", Icons.Default.Notifications) {} }
        item { ListRow("Family settings", "Members and permissions", Icons.Default.Groups) {} }
        item { ListRow("AI preferences", "How farm records are summarized", Icons.Default.AutoAwesome) {} }
        item { ListRow("Account and privacy", "Profile and sign-in settings", Icons.Default.Security) {} }
        item { OutlinedButton(onClick = { onMessage("Signed out in this frontend preview") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Sign out") } }
    }
}
