package com.farmfamily.app

import android.os.Handler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor

private enum class FarmDestination(val label: String) {
    HOME("Home"),
    RECORDS("Records"),
    REPORTS("Reports"),
    AI("AI"),
    MORE("More")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthenticatedFarmApp(
    session: AuthSession,
    repository: SupabaseFarmRepository,
    executor: Executor,
    mainHandler: Handler,
    onSignOut: () -> Unit
) {
    var memberships by remember { mutableStateOf<List<FamilyMembership>?>(null) }
    var selectedFamily by remember { mutableStateOf<FamilyMembership?>(null) }
    var familyLoading by remember { mutableStateOf(true) }
    var familyError by remember { mutableStateOf<String?>(null) }
    var fields by remember { mutableStateOf<List<FarmFieldRecord>?>(null) }
    var recordsOffline by remember { mutableStateOf(false) }
    var pendingFieldCount by remember { mutableStateOf(0) }
    var season by remember { mutableStateOf<CropSeasonSummary?>(null) }
    var recordsLoading by remember { mutableStateOf(false) }
    var recordsError by remember { mutableStateOf<String?>(null) }
    var yearlyReport by remember { mutableStateOf<YearFarmReport?>(null) }
    var reportLoading by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var savingField by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isAddingField by rememberSaveable { mutableStateOf(false) }
    var fieldDraftId by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf(FarmDestination.HOME.name) }
    var toast by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    fun reloadFamilies() {
        familyLoading = true
        familyError = null
        executor.execute {
            try {
                val result = repository.loadFamilies(session.accessToken, session.userId)
                mainHandler.post {
                    memberships = result
                    selectedFamily = selectedFamily?.let { current ->
                        result.firstOrNull { it.id == current.id }
                    } ?: result.singleOrNull()
                    familyLoading = false
                }
            } catch (error: Exception) {
                mainHandler.post {
                    familyError = error.message ?: "Family details could not be loaded."
                    familyLoading = false
                }
            }
        }
    }

    fun reloadRecords(family: FamilyMembership) {
        recordsLoading = true
        recordsError = null
        executor.execute {
            try {
                val fieldSnapshot = repository.loadFields(session.accessToken, family.id)
                var seasonLoadError: String? = null
                val loadedSeason = try {
                    repository.loadCurrentSeason(session.accessToken, family.id)
                } catch (error: Exception) {
                    seasonLoadError = error.message ?: "Current crop season could not be loaded."
                    null
                }
                mainHandler.post {
                    fields = fieldSnapshot.fields
                    recordsOffline = fieldSnapshot.isOffline
                    pendingFieldCount = fieldSnapshot.pendingCount
                    season = loadedSeason
                    recordsError = fieldSnapshot.syncError ?: seasonLoadError
                    recordsLoading = false
                }
            } catch (error: Exception) {
                mainHandler.post {
                    recordsError = error.message ?: "Farm records could not be loaded."
                    recordsLoading = false
                }
            }
        }
    }

    fun reloadReport(family: FamilyMembership) {
        reportLoading = true
        reportError = null
        executor.execute {
            try {
                val result = repository.loadCurrentYearReport(session.accessToken, family.id)
                mainHandler.post {
                    yearlyReport = result
                    reportLoading = false
                }
            } catch (error: Exception) {
                mainHandler.post {
                    reportError = error.message ?: "Farm reports could not be loaded."
                    reportLoading = false
                }
            }
        }
    }

    fun runFamilyAction(action: () -> Unit) {
        familyLoading = true
        familyError = null
        executor.execute {
            try {
                action()
                val result = repository.loadFamilies(session.accessToken, session.userId)
                check(result.isNotEmpty()) {
                    "The operation completed, but family membership could not be verified. Reload and check your family access."
                }
                mainHandler.post {
                    memberships = result
                    selectedFamily = result.singleOrNull()
                    familyLoading = false
                }
            } catch (error: Exception) {
                mainHandler.post {
                    familyError = error.message ?: "Family setup could not be completed."
                    familyLoading = false
                }
            }
        }
    }

    fun createField(
        family: FamilyMembership,
        localId: String,
        name: String,
        area: Double,
        unit: String,
        location: String,
        notes: String
    ) {
        savingField = true
        saveError = null
        executor.execute {
            try {
                val result = repository.createField(
                    session.accessToken,
                    family.id,
                    localId,
                    name,
                    area,
                    unit,
                    location,
                    notes
                )
                if (result.state == FieldSaveState.FAILED) {
                    repository.discardFailedField(family.id, localId)
                    throw IllegalStateException(result.message ?: "The field could not be synchronized.")
                }
                mainHandler.post {
                    savingField = false
                    isAddingField = false
                    destination = FarmDestination.RECORDS.name
                    toast = when (result.state) {
                        FieldSaveState.SYNCED -> "Field saved to your family records."
                        FieldSaveState.PENDING -> "Field saved on this device. It will sync when you're online."
                        FieldSaveState.FAILED -> error("Handled above")
                    }
                    reloadRecords(family)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    saveError = error.message ?: "Field could not be saved."
                    savingField = false
                }
            }
        }
    }

    fun openAddField() {
        fieldDraftId = UUID.randomUUID().toString()
        saveError = null
        isAddingField = true
    }

    LaunchedEffect(session.userId, session.accessToken) {
        reloadFamilies()
    }
    LaunchedEffect(selectedFamily?.id, session.accessToken) {
        selectedFamily?.let(::reloadRecords)
    }
    LaunchedEffect(selectedFamily?.id, destination) {
        if (destination == FarmDestination.REPORTS.name) {
            selectedFamily?.let(::reloadReport)
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHost.showSnackbar(it)
            toast = null
        }
    }

    val familyList = memberships
    if (familyLoading && familyList == null) {
        FarmLoadingScreen("Loading your family...")
        return
    }
    if (familyList == null) {
        FarmErrorScreen(
            title = "Family details could not be loaded",
            message = familyError ?: "Try again to continue.",
            onRetry = ::reloadFamilies
        )
        return
    }
    if (familyList.isEmpty()) {
        FamilySetupScreen(
            loading = familyLoading,
            error = familyError,
            onCreate = { name -> runFamilyAction { repository.createFamily(session.accessToken, name) } },
            onJoin = { token -> runFamilyAction { repository.acceptInvitation(session.accessToken, token) } }
        )
        return
    }
    if (selectedFamily == null) {
        FamilyChooserScreen(families = familyList, onSelect = { selectedFamily = it })
        return
    }

    val family = requireNotNull(selectedFamily)
    val currentDestination = FarmDestination.valueOf(destination)
    val appBarTitle = if (isAddingField) "Add field" else currentDestination.label

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isAddingField) "NEW RECORD · FAMILY DATA" else family.name.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Green
                        )
                        Text(appBarTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                },
                navigationIcon = {
                    if (isAddingField) {
                        IconButton(onClick = { isAddingField = false; saveError = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isAddingField) {
                        IconButton(onClick = { reloadRecords(family) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh family data")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                FarmDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination == item && !isAddingField,
                        onClick = { isAddingField = false; destination = item.name },
                        icon = {
                            Icon(
                                when (item) {
                                    FarmDestination.HOME -> Icons.Default.Home
                                    FarmDestination.RECORDS -> Icons.Default.FolderOpen
                                    FarmDestination.REPORTS -> Icons.Default.BarChart
                                    FarmDestination.AI -> Icons.Default.AutoAwesome
                                    FarmDestination.MORE -> Icons.Default.Menu
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                isAddingField -> AddFieldScreen(
                    localId = fieldDraftId,
                    canAdd = family.canAdd,
                    saving = savingField,
                    error = saveError,
                    onSave = { draftId, name, area, unit, location, notes ->
                        createField(family, draftId, name, area, unit, location, notes)
                    }
                )
                currentDestination in listOf(FarmDestination.HOME, FarmDestination.RECORDS) &&
                    recordsLoading && fields == null -> FarmLoadingScreen("Loading farm records...")
                currentDestination in listOf(FarmDestination.HOME, FarmDestination.RECORDS) &&
                    recordsError != null && fields == null -> FarmErrorScreen(
                    title = "Records could not be loaded",
                    message = recordsError.orEmpty(),
                    onRetry = { reloadRecords(family) }
                )
                else -> when (currentDestination) {
                    FarmDestination.HOME -> FarmHomeScreen(
                        family = family,
                        userName = session.displayName.ifBlank { session.email.substringBefore("@") },
                        fields = fields.orEmpty(),
                        season = season,
                        recordsLoading = recordsLoading,
                        recordsError = recordsError,
                        recordsOffline = recordsOffline,
                        pendingFieldCount = pendingFieldCount,
                        onFields = { destination = FarmDestination.RECORDS.name },
                        onAddField = ::openAddField,
                        onRetry = { reloadRecords(family) }
                    )
                    FarmDestination.RECORDS -> FarmRecordsScreen(
                        fields = fields.orEmpty(),
                        loading = recordsLoading,
                        error = recordsError,
                        isOffline = recordsOffline,
                        pendingCount = pendingFieldCount,
                        canAdd = family.canAdd,
                        onAddField = ::openAddField,
                        onRetry = { reloadRecords(family) }
                    )
                    FarmDestination.REPORTS -> FarmReportsScreen(
                        family = family,
                        report = yearlyReport,
                        loading = reportLoading,
                        error = reportError,
                        onRetry = { reloadReport(family) }
                    )
                    FarmDestination.AI -> FarmUnavailableScreen(
                        title = "Your farm, explained",
                        message = "Farm AI is not connected yet. No generated answers are shown as family facts."
                    )
                    FarmDestination.MORE -> FarmMoreScreen(family, onSignOut)
                }
            }
        }
    }
}

@Composable
private fun FarmHomeScreen(
    family: FamilyMembership,
    userName: String,
    fields: List<FarmFieldRecord>,
    season: CropSeasonSummary?,
    recordsLoading: Boolean,
    recordsError: String?,
    recordsOffline: Boolean,
    pendingFieldCount: Int,
    onFields: () -> Unit,
    onAddField: () -> Unit,
    onRetry: () -> Unit
) {
    val firstName = userName.trim().split(Regex("[\\s._-]+")).firstOrNull().orEmpty()
    val date = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date, style = MaterialTheme.typography.labelMedium, color = Muted)
                Text(
                    if (firstName.isBlank()) "Good morning." else "Good morning, $firstName.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Forest
                )
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Forest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (season == null) "YOUR FAMILY FARM" else "CURRENT CROP SEASON",
                        color = Color(0xFFD8E8DE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        season?.name ?: "Your family's farm",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        season?.let { listOf(it.crop, it.field).filter(String::isNotBlank).joinToString(" · ") }
                            ?.takeIf(String::isNotBlank)
                            ?: if (fields.isEmpty()) {
                                "Add a field to start organizing your farm."
                            } else {
                                "No active crop season is recorded yet."
                            },
                        color = Color(0xFFE7F1EA),
                        fontSize = 14.sp
                    )
                    if (season == null && fields.isEmpty() && family.canAdd) {
                        OutlinedButton(onClick = onAddField, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Add your first field", color = Color.White)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryTile(
                    value = fields.size.toString(),
                    label = if (fields.size == 1) "Field" else "Fields",
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    value = season?.name ?: "—",
                    label = "Current season",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Around the farm",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                TextButton(onClick = onFields) { Text("All fields") }
            }
        }
        if (recordsError != null) {
            item {
                FarmInlineError(message = recordsError, onRetry = onRetry)
            }
        }
        if (recordsLoading && fields.isEmpty()) {
            item { FarmLoadingScreen("Refreshing farm records...") }
        } else if (fields.isEmpty()) {
            item {
                FarmEmptyState(
                    title = "No fields yet",
                    message = "Add your first field to organize your family's land and crops.",
                    action = if (family.canAdd) "Add field" else null,
                    onAction = onAddField
                )
            }
        } else {
            items(fields.take(3), key = { it.id }) { field ->
                FieldRecordRow(field, onClick = onFields)
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Leaf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (recordsOffline || pendingFieldCount > 0) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (recordsOffline || pendingFieldCount > 0) Gold else Green
                    )
                    Column {
                        Text(
                            when {
                                recordsOffline -> "Offline · showing saved records"
                                pendingFieldCount > 0 -> "Changes waiting to sync"
                                else -> "Family records are up to date"
                            },
                            fontWeight = FontWeight.Medium,
                            color = Ink
                        )
                        Text(
                            when {
                                recordsOffline -> "New fields will sync when you're online."
                                pendingFieldCount > 0 -> "$pendingFieldCount field changes are saved on this device."
                                else -> "Your latest records were loaded from Supabase."
                            },
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmRecordsScreen(
    fields: List<FarmFieldRecord>,
    loading: Boolean,
    error: String?,
    isOffline: Boolean,
    pendingCount: Int,
    canAdd: Boolean,
    onAddField: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Every season. Every record.", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
                Text("Farm information shared with your family.", fontSize = 14.sp, color = Muted)
            }
        }
        if (isOffline || pendingCount > 0) {
            item {
                FarmPermissionMessage(
                    if (isOffline) {
                        "Offline · saved fields are available on this device."
                    } else {
                        "$pendingCount field changes are waiting to sync."
                    }
                )
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Leaf,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRetry)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Landscape, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fields", fontWeight = FontWeight.SemiBold)
                        Text("${fields.size} family fields", fontSize = 12.sp, color = Muted)
                    }
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh fields", tint = Green)
                }
            }
        }
        if (error != null) item { FarmInlineError(error, onRetry) }
        if (loading && fields.isEmpty()) {
            item { FarmLoadingScreen("Loading fields...") }
        } else if (fields.isEmpty()) {
            item {
                FarmEmptyState(
                    title = "No fields yet",
                    message = "Add your first field to organize your family's land.",
                    action = if (canAdd) "Add field" else null,
                    onAction = onAddField
                )
            }
        } else {
            item {
                Text("LAND & SEASONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
            }
            items(fields, key = { it.id }) { field ->
                FieldRecordRow(field, onClick = {})
            }
        }
        if (canAdd) {
            item {
                Button(
                    onClick = onAddField,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add field")
                }
            }
        }
    }
}

@Composable
private fun AddFieldScreen(
    localId: String,
    canAdd: Boolean,
    saving: Boolean,
    error: String?,
    onSave: (String, String, Double, String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("Bigha") }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val units = listOf("Bigha", "Hectare", "Acre")

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("NEW RECORD · FAMILY DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green)
                Spacer(Modifier.height(8.dp))
                Text("Land details", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Field name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it; validationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Land area *") },
                    supportingText = { Text("Enter a number greater than zero.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Area unit *") },
                    supportingText = { Text("Choose a common unit or enter your local unit.") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { option ->
                        FilterChip(
                            selected = unit == option,
                            onClick = { unit = option },
                            label = { Text(option) }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Location") },
                    supportingText = { Text("Optional") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    supportingText = { Text("Optional") },
                    minLines = 2,
                    shape = RoundedCornerShape(10.dp)
                )
            }
            val visibleError = validationError ?: error
            if (visibleError != null) {
                item { FarmInlineError(visibleError) }
            }
            if (!canAdd) {
                item {
                    FarmPermissionMessage("Your family role does not allow adding records.")
                }
            }
        }
        Button(
            onClick = {
                val parsedArea = area.trim().toDoubleOrNull()
                validationError = when {
                    name.isBlank() -> "Enter a field name."
                    parsedArea == null || parsedArea <= 0 -> "Area must be a positive number."
                    else -> null
                }
                if (validationError == null && parsedArea != null && canAdd) {
                    onSave(localId, name, parsedArea, unit, location, notes)
                }
            },
            enabled = canAdd && !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 52.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Save record")
            }
        }
    }
}

@Composable
private fun FamilySetupScreen(
    loading: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit
) {
    var createMode by rememberSaveable { mutableStateOf(true) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var invitationCode by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("FAMILY WORKSPACE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(8.dp))
        Text("Create your family", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
        Spacer(Modifier.height(8.dp))
        Text("A shared home for your farm.", fontSize = 16.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = createMode, onClick = { createMode = true }, label = { Text("Create") })
            FilterChip(selected = !createMode, onClick = { createMode = false }, label = { Text("Join") })
        }
        Spacer(Modifier.height(12.dp))
        if (createMode) {
            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Family name *") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        } else {
            OutlinedTextField(
                value = invitationCode,
                onValueChange = { invitationCode = it; validationError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Invitation code *") },
                supportingText = { Text("Use a code shared by a family owner.") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }
        val visibleError = validationError ?: error
        if (visibleError != null) {
            Spacer(Modifier.height(8.dp))
            FarmInlineError(visibleError)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                validationError = when {
                    createMode && familyName.isBlank() -> "Enter a family name."
                    !createMode && invitationCode.isBlank() -> "Enter an invitation code."
                    else -> null
                }
                if (validationError == null) {
                    if (createMode) onCreate(familyName.trim()) else onJoin(invitationCode.trim())
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(if (createMode) "Create family" else "Join family")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Family roles and record permissions are enforced by Supabase.",
            fontSize = 12.sp,
            color = Muted
        )
    }
}

@Composable
private fun FamilyChooserScreen(families: List<FamilyMembership>, onSelect: (FamilyMembership) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Choose your family", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
            Text("Select the workspace you want to open.", color = Muted)
        }
        items(families, key = { it.id }) { family ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(family) }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(family.name, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("${family.role} · ${family.currency}", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun FarmReportsScreen(
    family: FamilyMembership,
    report: YearFarmReport?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("See what your work adds up to", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
                Text(
                    report?.year?.let { "Family summary · $it" } ?: "Family summary · this year",
                    fontSize = 14.sp,
                    color = Muted
                )
            }
        }
        if (loading) {
            item { FarmLoadingScreen("Loading your family report...") }
        } else if (error != null) {
            item { FarmInlineError(error, onRetry) }
        } else if (report == null) {
            item {
                FarmEmptyState(
                    title = "No report data yet",
                    message = "Add farm records to see this family's yearly summary.",
                    action = null,
                    onAction = {}
                )
            }
        } else {
            item {
                ReportValueRow(
                    label = "Sales recorded",
                    value = report.revenue?.let { "${family.currency} ${formatNumber(it)}" } ?: "No values yet"
                )
            }
            item {
                ReportValueRow(
                    label = "Expenses recorded",
                    value = report.cost?.let { "${family.currency} ${formatNumber(it)}" } ?: "No values yet"
                )
            }
            item {
                ReportValueRow(
                    label = "Profit",
                    value = report.profit?.let { "${family.currency} ${formatNumber(it)}" }
                        ?: "Not available"
                )
            }
            if (report.profit == null) {
                item {
                    Text(
                        "Profit is shown only when the family has marked season costs and revenue complete and the records use compatible currencies.",
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
            }
            item {
                ReportValueRow(
                    label = "Harvest quantity",
                    value = report.harvestQuantity?.let(::formatNumber) ?: "Mixed measurement units"
                )
            }
            item {
                Text(
                    "Calculated from this family's saved records. Totals are withheld when currencies or measurement units cannot be safely combined.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun ReportValueRow(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = Muted)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Forest)
        }
    }
}

@Composable
private fun FarmMoreScreen(family: FamilyMembership, onSignOut: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("More", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
            Text("Family workspace and account", color = Muted)
        }
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = Leaf, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(family.name, fontWeight = FontWeight.SemiBold, color = Forest)
                    Text("${family.role} · ${family.currency}", fontSize = 14.sp, color = Muted)
                    HorizontalDivider(color = Line)
                    Text(
                        if (family.canAdd) "You can view and add farm records." else "Your role is view-only.",
                        fontSize = 14.sp,
                        color = Ink
                    )
                }
            }
        }
        item {
            FarmPermissionMessage(
                "Only family members with the required permissions can change family records."
            )
        }
        item {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun FarmUnavailableScreen(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 16.sp, color = Muted)
    }
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Forest)
            Text(label, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun FieldRecordRow(field: FarmFieldRecord, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(field.name, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val statusColor = if (field.syncStatus == "FAILED") Color(0xFFFFF3D6) else Leaf
            Surface(shape = CircleShape, color = statusColor) {
                Text(
                    when (field.syncStatus) {
                        "PENDING" -> "Pending sync"
                        "FAILED" -> "Sync failed"
                        "CONFLICT" -> "Conflict"
                        else -> "Synced"
                    },
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color = if (field.syncStatus == "FAILED") Color(0xFFB14E36) else Forest
                )
            }
        }
        Text(
            listOf(
                field.area?.let { "${formatNumber(it)} ${field.areaUnit.orEmpty()}".trim() },
                field.location.takeIf(String::isNotBlank)
            ).filterNotNull().joinToString(" · ").ifBlank { "Field record" },
            fontSize = 14.sp,
            color = Muted
        )
        HorizontalDivider(color = Line)
    }
}

@Composable
private fun FarmEmptyState(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(message, fontSize = 14.sp, color = Muted)
        if (action != null) {
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action)
            }
        }
    }
}

@Composable
private fun FarmInlineError(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF0EC),
        border = BorderStroke(1.dp, Color(0xFFE6C5BC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = Color(0xFF8F3E2C), fontSize = 14.sp)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun FarmPermissionMessage(message: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF3D6), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), fontSize = 14.sp, color = Ink)
    }
}

@Composable
private fun FarmLoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = Forest)
        Text(message, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun FarmErrorScreen(title: String, message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Forest)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 52.dp)) {
            Text("Try again")
        }
    }
}

@Composable
internal fun PasswordResetScreen(
    loading: Boolean,
    error: String?,
    onSave: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("FARM FAMILY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(8.dp))
        Text("Choose a new password", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Forest)
        Spacer(Modifier.height(8.dp))
        Text("Use the password policy configured for your account.", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; validationError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New password *") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it; validationError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm password *") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        val visibleError = validationError ?: error
        if (visibleError != null) {
            Spacer(Modifier.height(10.dp))
            FarmInlineError(visibleError)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                validationError = when {
                    password.isBlank() -> "Enter a new password."
                    password != confirmation -> "Passwords do not match."
                    else -> null
                }
                if (validationError == null) onSave(password)
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Save new password")
            }
        }
    }
}

private fun formatNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
