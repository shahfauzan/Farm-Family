package com.farmfamily.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmWorkspaceScaffold(
    family: FamilyMembership,
    selectedTab: String,
    hindi: Boolean = false,
    nestedTitle: String?,
    onSelectTab: (FarmTab) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onQuickAdd: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val nested = nestedTitle != null
    Scaffold(
        containerColor = FarmCream,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FarmText(if (nested) family.name.uppercase() else "FARM FAMILY", style = MaterialTheme.typography.labelMedium, color = FarmGreen, letterSpacing = 1.sp)
                        FarmText(if (nestedTitle != null) farmLabel(nestedTitle, hindi) else family.name, style = MaterialTheme.typography.titleLarge, color = FarmInk, maxLines = 1)
                    }
                },
                navigationIcon = {
                    if (nested) IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FarmForest)
                    }
                },
                actions = {
                    if (!nested) {
                        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search records", tint = FarmForest) }
                        IconButton(onClick = onNotifications) { Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = FarmForest) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FarmCream)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = FarmWhite, tonalElevation = 0.dp) {
                FarmTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.name && !nested,
                        onClick = { onSelectTab(tab) },
                        icon = {
                            Icon(
                                when (tab) {
                                    FarmTab.HOME -> Icons.Default.Home
                                    FarmTab.RECORDS -> Icons.Default.FolderOpen
                                    FarmTab.REPORTS -> Icons.Default.BarChart
                                    FarmTab.AI -> Icons.Default.AutoAwesome
                                    FarmTab.MORE -> Icons.Default.Menu
                                },
                                contentDescription = farmLabel(tab.title, hindi)
                            )
                        },
                        label = { FarmText(farmLabel(tab.title, hindi), maxLines = 1) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!nested && selectedTab in setOf(FarmTab.HOME.name, FarmTab.RECORDS.name)) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = onQuickAdd,
                    containerColor = FarmForest,
                    contentColor = FarmWhite,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { FarmText("Add record") },
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    ) { insets -> content(Modifier.fillMaxSize().padding(insets)) }
}

@Composable
internal fun FarmHomeScreen(
    modifier: Modifier,
    family: FamilyMembership,
    userName: String,
    fields: List<JSONObject>,
    seasons: List<JSONObject>,
    report: YearFarmReport?,
    weather: JSONObject?,
    loading: Set<String>,
    errors: Map<String, String>,
    onRetry: () -> Unit,
    onLoadWeather: (Double, Double) -> Unit,
    onNavigate: (String) -> Unit
) {
    val date = rememberSaveable { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }
    val firstName = userName.trim().substringBefore(' ')
    val activeSeasons = seasons.filter { it.isNull("end_date") }
    val pending = fields.count { it.optString("sync_status") in setOf("PENDING", "FAILED", "CONFLICT") }
    val location = fields.firstNotNullOfOrNull { it.optJSONObject("location")?.takeIf { value -> !value.isNull("latitude") && !value.isNull("longitude") } }
    val lat = location?.optDouble("latitude")?.takeIf(Double::isFinite)
    val lon = location?.optDouble("longitude")?.takeIf(Double::isFinite)
    LaunchedEffect(family.id, lat, lon) {
        if (weather == null && lat != null && lon != null && "weather" !in loading) onLoadWeather(lat, lon)
    }
    val totalRevenue = report?.revenue
    val totalCost = report?.cost

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FarmText(date, style = MaterialTheme.typography.labelMedium, color = FarmMuted)
                FarmText(if (firstName.isBlank()) "Welcome to your farm" else "Good day, $firstName", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
                FarmText("A shared place for your family’s farm work.", style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
            }
        }
        errors["sync"]?.let { message -> item { FarmNotice(message, true) } }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = FarmForest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FarmText(family.name, style = MaterialTheme.typography.headlineMedium, color = FarmWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
                            FarmText("${family.role.lowercase().replaceFirstChar { it.uppercase() }} · ${family.currency}", style = MaterialTheme.typography.bodySmall, color = FarmLeaf)
                        }
                        Surface(shape = CircleShape, color = FarmWhite.copy(alpha = 0.13f)) {
                            Icon(Icons.Default.Home, contentDescription = "Family farm", tint = FarmWhite, modifier = Modifier.padding(10.dp).size(22.dp))
                        }
                    }
                    androidx.compose.material3.Divider(color = FarmWhite.copy(alpha = .18f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FarmHeroMetric(fields.size.toString(), "Fields", Modifier.weight(1f))
                        FarmHeroMetric(activeSeasons.size.toString(), "Growing seasons", Modifier.weight(1f))
                        FarmHeroMetric(pending.toString(), "Need sync", Modifier.weight(1f))
                    }
                    if (activeSeasons.isNotEmpty()) {
                        FarmText("CURRENT SEASON", style = MaterialTheme.typography.labelSmall, color = FarmLeaf)
                        activeSeasons.take(2).forEach { season ->
                            FarmText(
                                listOf(season.optString("season_name"), season.optJSONObject("crops")?.optString("name").orEmpty(), season.optJSONObject("fields")?.optString("name").orEmpty())
                                    .filter(String::isNotBlank).joinToString(" · ").ifBlank { "Crop season" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = FarmWhite
                            )
                        }
                    } else if (fields.isEmpty()) {
                        FarmText("Add your first field to begin tracking farm work.", style = MaterialTheme.typography.bodySmall, color = FarmLeaf)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmSectionTitle("At a glance", "This year")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FarmMetricBlock("Sales recorded", totalRevenue?.let { "${family.currency} ${farmNumber(it)}" } ?: "No totals yet", Modifier.weight(1f))
                    FarmMetricBlock("Expenses recorded", totalCost?.let { "${family.currency} ${farmNumber(it)}" } ?: "No totals yet", Modifier.weight(1f))
                }
                report?.profit?.let { profit ->
                    FarmMetricBlock("Profit · ${report.year}", "${family.currency} ${farmNumber(profit)}", Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmSectionTitle("Weather", if (lat == null) "Add field coordinates" else null)
                when {
                    lat == null || lon == null -> FarmInlineNote("Add latitude and longitude to a field to see local conditions.")
                    "weather" in loading -> FarmLoadingRow("Getting local weather…")
                    errors["weather"] != null -> FarmInlineErrorCard(errors.getValue("weather"), onRetry = { onLoadWeather(lat, lon) })
                    weather != null -> FarmWeatherRow(weather)
                    else -> FarmInlineNote("Weather hasn’t been requested yet.")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FarmSectionTitle("Your records", "View all", onAction = { onNavigate("module:fields") })
                if (fields.isEmpty()) FarmEmptyState(
                    title = "No fields yet",
                    description = "Add a field to start a season and keep the family’s records together.",
                    action = if (family.canAdd) "Add a field" else null,
                    onAction = { onNavigate("form:fields:new") }
                ) else fields.take(4).forEach { field ->
                    FarmRecordListRow(
                        title = field.optString("name").ifBlank { "Field" },
                        subtitle = listOf(
                            field.optDouble("area").takeIf { !field.isNull("area") }?.let(::farmNumber),
                            field.optString("area_unit").takeIf(String::isNotBlank),
                            field.optJSONObject("location")?.optString("label")?.takeIf(String::isNotBlank)
                        ).filterNotNull().joinToString(" · ").ifBlank { "Field details" },
                        status = field.optString("sync_status"),
                        onClick = { onNavigate("detail:fields:${field.optString("id")}") }
                    )
                }
            }
        }
        if (report == null && errors["home_report"] != null) {
            item { FarmInlineErrorCard(errors.getValue("home_report"), onRetry = onRetry) }
        }
    }
}

@Composable
private fun FarmHeroMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FarmText(value, style = MaterialTheme.typography.titleLarge, color = FarmWhite)
        FarmText(label, style = MaterialTheme.typography.labelSmall, color = FarmLeaf)
    }
}

@Composable
internal fun FarmMetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(FarmWhite, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FarmText(label, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
        FarmText(value, style = MaterialTheme.typography.titleMedium, color = FarmForest)
    }
}

@Composable
internal fun FarmRecordsHubScreen(
    modifier: Modifier,
    family: FamilyMembership,
    records: Map<String, List<JSONObject>>,
    onNavigate: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = FarmModules.all.filter { it.title.contains(query, true) || it.description.contains(query, true) }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FarmText("Farm records", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
                FarmText("Keep each season’s work connected to the right fields and crops.", style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { FarmText("Find a record type") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        item { FarmText("LAND & SEASONS", style = MaterialTheme.typography.labelMedium, color = FarmGreen, letterSpacing = 1.sp) }
        items(visible.filter { it.table in setOf("fields", "crop_seasons", "crops") }) { module ->
            FarmModuleRow(module, records[module.table]?.size, onClick = { onNavigate("module:${module.table}") })
        }
        item { FarmText("FARM WORK", style = MaterialTheme.typography.labelMedium, color = FarmGreen, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp)) }
        items(visible.filter { it.table !in setOf("fields", "crop_seasons", "crops") }) { module ->
            FarmModuleRow(module, records[module.table]?.size, onClick = { onNavigate("module:${module.table}") })
        }
        if (visible.isEmpty()) item { FarmEmptyState("No record type found", "Try a shorter search.", null, {}) }
        if (!family.canView) item { FarmNotice("Your family role does not grant access to farm records.", true) }
    }
}

@Composable
private fun FarmModuleRow(module: FarmModule, count: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = FarmLeaf) {
            Icon(moduleIcon(module.iconKey), contentDescription = null, tint = FarmForest, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FarmText(module.title, style = MaterialTheme.typography.titleMedium, color = FarmInk)
            FarmText(module.description, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
        }
        count?.let { FarmText(it.toString(), style = MaterialTheme.typography.labelMedium, color = FarmGreen) }
        FarmText("›", style = MaterialTheme.typography.titleLarge, color = FarmMuted)
    }
    androidx.compose.material3.Divider(color = FarmLine)
}

@Composable
internal fun FarmReportsScreen(
    modifier: Modifier,
    family: FamilyMembership,
    report: YearFarmReport?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelectYear: (Int) -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    var selectedYear by rememberSaveable { mutableStateOf(java.time.Year.now().value) }
    var showYearMenu by rememberSaveable { mutableStateOf(false) }
    val reportYear = report?.year
    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            FarmText("Reports", style = MaterialTheme.typography.headlineMedium, color = FarmForest)
            FarmText("A clear view of the work your family has recorded.", style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
        }
        item {
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { showYearMenu = true }) { FarmText("Year: $selectedYear") }
                androidx.compose.material3.DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                    (2000..java.time.Year.now().value).reversed().forEach { year ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { FarmText(year.toString()) },
                            onClick = { selectedYear = year; showYearMenu = false; onSelectYear(year) }
                        )
                    }
                }
            }
        }
        when {
            loading -> item { FarmLoadingRow("Loading family reports…") }
            error != null -> item { FarmInlineErrorCard(error, onRetry) }
            report == null -> item { FarmEmptyState("No report data yet", "Add saved farm records to build a report.", null, {}) }
            else -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FarmText("YEARLY SUMMARY · ${report.year}", style = MaterialTheme.typography.labelMedium, color = FarmGreen, letterSpacing = 1.sp)
                        FarmMetricBlock("Sales recorded", report.revenue?.let { "${family.currency} ${farmNumber(it)}" } ?: "Totals can’t be combined safely", Modifier.fillMaxWidth())
                        FarmMetricBlock("Expenses recorded", report.cost?.let { "${family.currency} ${farmNumber(it)}" } ?: "Totals can’t be combined safely", Modifier.fillMaxWidth())
                        FarmMetricBlock("Profit", report.profit?.let { "${family.currency} ${farmNumber(it)}" } ?: "Not available until costs and revenue are marked complete", Modifier.fillMaxWidth())
                        FarmMetricBlock("Harvest recorded", report.harvestQuantity?.let(::farmNumber) ?: "Measurement units can’t be combined", Modifier.fillMaxWidth())
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        FarmSectionTitle("Recorded value comparison", null)
                        val values = listOfNotNull(report.revenue, report.cost).filter { it >= 0 }
                        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                        FarmReportBar("Sales", report.revenue, max, family.currency)
                        FarmReportBar("Expenses", report.cost, max, family.currency)
                        if (report.revenue == null && report.cost == null) FarmInlineNote("There aren’t compatible currency totals to chart yet.")
                    }
                }
                item { FarmNotice("Profit is available only after the family marks crop-season costs and revenue complete.") }
            }
        }
        item {
            FarmText("EXPLORE REPORTS", style = MaterialTheme.typography.labelMedium, color = FarmGreen, letterSpacing = 1.sp)
        }
        items(listOf("expenses" to "Expense analysis", "sales" to "Sales analysis", "harvest" to "Harvest analysis", "fields" to "Field performance", "crops" to "Crop performance", "historical_prices" to "Historical prices")) { (key, label) ->
            FarmUtilityRow(label, "Family records · $reportYear", moduleIcon(key)) { onNavigate("report:$key") }
        }
    }
}

@Composable
private fun FarmReportBar(label: String, amount: Double?, maximum: Double, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FarmText(label, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
            FarmText(amount?.let { "$currency ${farmNumber(it)}" } ?: "Not available", style = MaterialTheme.typography.labelMedium, color = FarmForest)
        }
        LinearProgressIndicator(
            progress = { (amount?.div(maximum)?.toFloat()?.coerceIn(0f, 1f) ?: 0f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = FarmGreen,
            trackColor = FarmLeaf,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
internal fun FarmAiScreen(
    modifier: Modifier,
    answer: FarmAiAnswer?,
    loading: Boolean,
    error: String?,
    onAsk: (String) -> Unit
) {
    var question by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = FarmForest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FarmSun, modifier = Modifier.size(26.dp))
                    FarmText("Ask about your farm", style = MaterialTheme.typography.headlineSmall, color = FarmWhite)
                    FarmText("Answers use your family’s saved records and approved reports.", style = MaterialTheme.typography.bodySmall, color = FarmLeaf)
                }
            }
        }
        item {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it; validation = null },
                modifier = Modifier.fillMaxWidth(),
                label = { FarmText("What would you like to know?") },
                placeholder = { FarmText("For example: summarize this year’s expenses") },
                minLines = 3,
                maxLines = 5,
                enabled = !loading
            )
        }
        item {
            Button(onClick = {
                validation = when {
                    question.isBlank() -> "Write a question about your farm records."
                    question.length > 2000 -> "Keep your question under 2,000 characters."
                    else -> null
                }
                if (validation == null) onAsk(question.trim())
            }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FarmWhite)
                else Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                FarmText(if (loading) "Checking family records…" else "Ask Farm AI")
            }
        }
        (validation ?: error)?.let { item { FarmNotice(it, isError = validation != null || error != null) } }
        if (answer != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmSectionTitle("ANSWER", "${answer.records} supporting records")
                    Surface(shape = RoundedCornerShape(12.dp), color = FarmWhite, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FarmText(answer.explanation, style = MaterialTheme.typography.bodyMedium, color = FarmInk)
                            FarmText("Based on your family’s authorized farm data.", style = MaterialTheme.typography.labelMedium, color = FarmGreen)
                        }
                    }
                }
            }
        }
        item { FarmInlineNote("Farm AI is read-only. It cannot make or change records.") }
    }
}

@Composable
internal fun FarmMoreScreen(
    modifier: Modifier,
    family: FamilyMembership,
    session: AuthSession?,
    hindi: Boolean,
    onNavigate: (String) -> Unit
) {
    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FarmText(farmLabel("More", hindi), style = MaterialTheme.typography.headlineMedium, color = FarmForest)
            FarmText(farmLabel("Family workspace and account", hindi), style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
        }
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = FarmLeaf, modifier = Modifier.fillMaxWidth().clickable { onNavigate("family") }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FarmText(family.name, style = MaterialTheme.typography.titleMedium, color = FarmForest)
                    FarmText("${family.role} · ${family.currency}", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
                    FarmText("${session?.displayName.orEmpty().ifBlank { session?.email.orEmpty() }} · View family", style = MaterialTheme.typography.bodySmall, color = FarmInk)
                }
            }
        }
        item { FarmUtilityRow(farmLabel("Family members", hindi), farmLabel("Roles and access", hindi), Icons.Default.Home) { onNavigate("members") } }
        item { FarmUtilityRow(farmLabel("Family settings", hindi), farmLabel("Name and workspace details", hindi), Icons.Default.Home) { onNavigate("family_settings") } }
        item { FarmUtilityRow(farmLabel("Invitations", hindi), farmLabel("Invite a family member", hindi), Icons.Default.Add) { onNavigate("invitations") } }
        item { FarmUtilityRow("Notifications", "Activity and sync updates", Icons.Default.NotificationsNone) { onNavigate("notifications") } }
        item { FarmUtilityRow(farmLabel("Recycle bin", hindi), "Restore deleted records", Icons.Default.FolderOpen) { onNavigate("recycle_bin") } }
        item { FarmUtilityRow(farmLabel("Backup & restore", hindi), farmLabel("Export or restore family data", hindi), Icons.Default.FolderOpen) { onNavigate("backup") } }
        item { FarmUtilityRow(farmLabel("Profile", hindi), "Name and account details", Icons.Default.Home) { onNavigate("profile") } }
        item { FarmUtilityRow(farmLabel("Settings", hindi), farmLabel("Sync, help, and about", hindi), Icons.Default.Menu) { onNavigate("settings") } }
        item {
            FarmText("Family permissions are enforced by Supabase as well as by the controls shown in the app.", style = MaterialTheme.typography.bodySmall, color = FarmMuted, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            TextButton(onClick = { onNavigate("signout") }) { FarmText(farmLabel("Sign out", hindi)) }
        }
    }
}

@Composable
internal fun FarmSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FarmText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = FarmForest)
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            FarmText(action, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun FarmRecordListRow(title: String, subtitle: String, status: String = "SYNCED", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FarmText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = FarmInk)
            FarmSyncPill(status)
        }
        if (subtitle.isNotBlank()) FarmText(subtitle, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
        androidx.compose.material3.Divider(color = FarmLine)
    }
}

@Composable
internal fun FarmSyncPill(status: String) {
    val color = when (status.uppercase()) {
        "PENDING", "SYNCING" -> FarmWarning
        "FAILED", "CONFLICT" -> FarmErrorSurface
        else -> FarmLeaf
    }
    val label = when (status.uppercase()) {
        "STALE" -> "Saved view"
        "PENDING" -> "Pending sync"
        "SYNCING" -> "Syncing"
        "FAILED" -> "Sync failed"
        "CONFLICT" -> "Conflict"
        else -> "Synced"
    }
    Surface(shape = CircleShape, color = color) {
        FarmText(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (status.uppercase() in setOf("FAILED", "CONFLICT")) FarmError else FarmForest)
    }
}

@Composable
internal fun FarmLoadingRow(message: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FarmGreen)
        FarmText(message, style = MaterialTheme.typography.bodyMedium, color = FarmMuted)
    }
}

@Composable
internal fun FarmInlineNote(message: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = FarmLeaf, modifier = Modifier.fillMaxWidth()) {
        FarmText(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = FarmForest)
    }
}

@Composable
internal fun FarmInlineErrorCard(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = FarmErrorSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FarmText(message, style = MaterialTheme.typography.bodySmall, color = FarmError)
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) { FarmText("Try again") }
        }
    }
}

@Composable
internal fun FarmEmptyState(title: String, description: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = FarmLeaf) { Icon(Icons.Default.FolderOpen, null, tint = FarmForest, modifier = Modifier.padding(13.dp).size(22.dp)) }
        FarmText(title, style = MaterialTheme.typography.titleMedium, color = FarmForest)
        FarmText(description, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
        action?.let { Button(onClick = onAction) { FarmText(it) } }
    }
}

@Composable
internal fun FarmUtilityRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FarmWhite).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FarmGreen, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FarmText(title, style = MaterialTheme.typography.titleMedium, color = FarmInk)
            FarmText(subtitle, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
        }
        FarmText("›", style = MaterialTheme.typography.titleLarge, color = FarmMuted)
    }
}

@Composable
private fun FarmWeatherRow(weather: JSONObject) {
    val loc = weather.optJSONObject("location")
    val condition = weather.optJSONObject("condition")
    Surface(shape = RoundedCornerShape(12.dp), color = FarmWhite, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WbSunny, null, tint = FarmSun, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FarmText(loc?.optString("name").orEmpty().ifBlank { "Farm location" }, style = MaterialTheme.typography.titleMedium, color = FarmForest)
                FarmText(condition?.optString("description").orEmpty().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = FarmMuted)
            }
            FarmText(weather.opt("temperature_c")?.takeUnless { it == JSONObject.NULL }?.let { "${farmNumber(it.toString().toDoubleOrNull() ?: 0.0)}°" } ?: "—", style = MaterialTheme.typography.headlineSmall, color = FarmForest)
        }
    }
}

internal fun moduleIcon(key: String): androidx.compose.ui.graphics.vector.ImageVector = when (key) {
    "fields", "field" -> Icons.Default.Home
    "crop", "crops", "season", "crop_seasons" -> Icons.Default.WbSunny
    "seed" -> Icons.Default.Add
    "expense", "expenses" -> Icons.Default.BarChart
    "sale", "sales" -> Icons.Default.BarChart
    "harvest", "harvest_records" -> Icons.Default.FolderOpen
    "price", "historical_prices" -> Icons.Default.CalendarMonth
    "weather" -> Icons.Default.Cloud
    else -> Icons.Default.FolderOpen
}

internal fun farmNumber(number: Double): String = if (number == number.toLong().toDouble()) number.toLong().toString() else String.format(Locale.getDefault(), "%.1f", number)

