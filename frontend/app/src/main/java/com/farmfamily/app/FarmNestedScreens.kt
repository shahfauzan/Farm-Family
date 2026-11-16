package com.farmfamily.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun FarmNestedRoute(
    route: String,
    modifier: Modifier,
    viewModel: FarmFamilyViewModel,
    state: FarmUiState,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val bits = route.split(':')
    when (bits[0]) {
        "module" -> FarmModuleScreen(bits.getOrNull(1).orEmpty(), modifier, state, viewModel, onNavigate)
        "form" -> FarmRecordForm(bits.getOrNull(1).orEmpty(), bits.getOrNull(2).orEmpty(), bits.getOrNull(3), modifier, state, viewModel, onBack)
        "detail" -> FarmRecordDetail(bits.getOrNull(1).orEmpty(), bits.getOrNull(2).orEmpty(), modifier, state, viewModel, onNavigate, onBack)
        "search" -> FarmSearchScreen(modifier, state, viewModel, onNavigate)
        "quick_add" -> FarmQuickAddScreen(modifier, onNavigate)
        "members" -> FarmMembersScreen(modifier, state, viewModel)
        "invitations" -> FarmInvitationsScreen(modifier, state, viewModel)
        "notifications" -> FarmNotificationsScreen(modifier, state, viewModel)
        "backup" -> FarmBackupScreen(modifier, state, viewModel)
        "profile" -> FarmProfileScreen(modifier, state, viewModel)
        "family_settings" -> FarmFamilySettingsScreen(modifier, state, viewModel)
        "settings" -> FarmSettingsScreen(modifier, onSignOut)
        "family" -> FarmFamilyInfoScreen(modifier, state, viewModel)
        "signout" -> { LaunchedEffect(Unit) { onSignOut() }; FarmEmptyState("Signing out", "Your family records remain saved.", null, {}) }
        "report" -> FarmReportModuleScreen(bits.getOrNull(1).orEmpty(), modifier, state, viewModel, onNavigate)
        else -> FarmEmptyState("This screen is unavailable", "Go back and choose another section.", "Back", onBack)
    }
}

@Composable
private fun FarmModuleScreen(table: String, modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel, navigate: (String) -> Unit) {
    val module = FarmModules.byTable(table)
    LaunchedEffect(table) { if (module != null) vm.loadRecords(table) }
    if (module == null) return FarmEmptyState("Unknown record type", "This section is not available.", null, {})
    val rows = state.records[table]
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(module.description, style = MaterialTheme.typography.bodyMedium, color = FarmMuted) }
        if (state.family?.canAdd == true) item { Button(onClick = { navigate("form:$table:new") }, modifier = Modifier.fillMaxWidth()) { Text("Add ${module.title.removeSuffix("s")}") } }
        state.errors[table]?.let { message -> item { FarmInlineErrorCard(message) { vm.loadRecords(table) } } }
        if (table in state.loading && rows == null) item { FarmLoadingRow("Loading ${module.title.lowercase()}…") }
        if (rows.isNullOrEmpty() && table !in state.loading) item { FarmEmptyState("No ${module.title.lowercase()} yet", "Add the first record for this family.", if (state.family?.canAdd == true) "Add record" else null) { navigate("form:$table:new") } }
        items(rows.orEmpty(), key = { it.optString("id") }) { row ->
            val title = row.optString(module.primaryLabel).ifBlank { module.title.removeSuffix("s") }
            val subtitle = row.optString(module.secondaryLabel).takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            FarmRecordListRow(title, subtitle) { navigate("detail:$table:${row.optString("id")}") }
        }
    }
}

@Composable
private fun FarmRecordForm(table: String, mode: String, recordId: String?, modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel, back: () -> Unit) {
    val module = FarmModules.byTable(table) ?: return FarmEmptyState("Unknown record type", "This form is not available.", null, {})
    val existing = state.records[table].orEmpty().firstOrNull { it.optString("id") == recordId }
    var formReady by rememberSaveable(table, recordId) { mutableStateOf(false) }
    LaunchedEffect(table, recordId) { vm.clearRecordMessage(table); vm.clearConflict(); formReady = true }
    LaunchedEffect(formReady, state.messages[table]) { if (formReady && state.messages[table] == "Changes saved.") back() }
    val values = remember(table, recordId) { mutableStateMapOf<String, String>().apply { putAll(existing?.toInputValues(module).orEmpty()) } }
    var validation by rememberSaveable(table, recordId) { mutableStateOf<String?>(null) }
    val family = state.family
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (mode == "new") "Add ${module.title.removeSuffix("s").lowercase()}" else "Edit ${module.title.removeSuffix("s").lowercase()}", style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        module.inputs.forEach { input ->
            item(key = input.key) {
                when {
                    input.key in setOf("costs_complete", "revenue_complete") && family?.role != "OWNER" -> Text("${input.label}: ${if (values[input.key] == "true") "Yes" else "Not confirmed"} · Only an owner can confirm completeness.", color = FarmMuted, style = MaterialTheme.typography.bodySmall)
                    input.type == FarmInputType.BOOLEAN -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = values[input.key] == "true", onCheckedChange = { values[input.key] = it.toString() })
                        Text(input.label, modifier = Modifier.padding(top = 12.dp), color = FarmInk)
                    }
                    input.type == FarmInputType.CHOICE -> {
                        var expanded by remember(input.key) { mutableStateOf(false) }
                        Column {
                            Text(input.label, style = MaterialTheme.typography.labelMedium, color = FarmMuted)
                            TextButton(onClick = { expanded = true }) { Text(values[input.key]?.takeIf { it.isNotBlank() } ?: "Choose ${input.label.lowercase()}") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                input.choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { values[input.key] = choice; expanded = false }) }
                            }
                        }
                    }
                    input.type == FarmInputType.RELATION -> {
                        val options = state.records[input.relationTable].orEmpty()
                        var expanded by remember(input.key) { mutableStateOf(false) }
                        LaunchedEffect(input.relationTable) { input.relationTable?.let(vm::loadRecords) }
                        Column {
                            Text(input.label, style = MaterialTheme.typography.labelMedium, color = FarmMuted)
                            TextButton(onClick = { expanded = true }) {
                                val selected = options.firstOrNull { it.optString("id") == values[input.key] }
                                Text(selected?.optString(input.relationLabel).orEmpty().ifBlank { "Choose ${input.label.lowercase()}" })
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                options.forEach { option -> DropdownMenuItem(text = { Text(option.optString(input.relationLabel)) }, onClick = { values[input.key] = option.optString("id"); expanded = false }) }
                            }
                        }
                    }
                    else -> OutlinedTextField(
                        value = values[input.key].orEmpty(), onValueChange = { entered ->
                            if (input.type != FarmInputType.NUMBER || entered.isEmpty() || entered.matches(Regex("-?\\d*(\\.\\d*)?"))) values[input.key] = entered
                            validation = null
                        }, modifier = Modifier.fillMaxWidth(), label = { Text(input.label + if (input.required) " *" else "") },
                        minLines = if (input.type == FarmInputType.MULTILINE) 3 else 1, maxLines = if (input.type == FarmInputType.MULTILINE) 5 else 1,
                        singleLine = input.type != FarmInputType.MULTILINE
                    )
                }
            }
        }
        (validation ?: state.errors[table])?.let { item { FarmNotice(it, true) } }
        item {
            Button(onClick = {
                validation = module.inputs.firstNotNullOfOrNull { input ->
                    val value = values[input.key].orEmpty().trim()
                    when {
                        input.required && value.isBlank() -> "${input.label} is required."
                        value.length > input.maxLength -> "${input.label} is too long."
                        input.type == FarmInputType.NUMBER && value.isNotBlank() && value.toDoubleOrNull() == null -> "Enter a valid ${input.label.lowercase()}."
                        input.numberMin != null && value.isNotBlank() && (value.toDoubleOrNull() ?: input.numberMin!!) < input.numberMin!! -> "${input.label} is below the allowed minimum."
                        input.numberMax != null && value.isNotBlank() && (value.toDoubleOrNull() ?: input.numberMax!!) > input.numberMax!! -> "${input.label} is above the allowed maximum."
                        else -> null
                    }
                }
                if (validation == null) {
                    val json = JSONObject()
                    module.inputs.forEach fieldLoop@{ field ->
                        if (field.key in setOf("costs_complete", "revenue_complete") && family?.role != "OWNER") return@fieldLoop
                        val raw = values[field.key]?.trim().orEmpty()
                        if (raw.isNotEmpty()) when (field.type) {
                            FarmInputType.NUMBER -> json.put(field.key, raw.toDouble())
                            FarmInputType.BOOLEAN -> json.put(field.key, raw.toBoolean())
                            else -> json.put(field.key, raw)
                        }
                    }
                    if (table == "fields") {
                        val lat = values["latitude"]?.toDoubleOrNull(); val lon = values["longitude"]?.toDoubleOrNull()
                        if (values["location_label"].orEmpty().isNotBlank() || lat != null || lon != null) {
                            val location = JSONObject()
                            values["location_label"]?.takeIf { it.isNotBlank() }?.let { location.put("label", it) }
                            lat?.let { location.put("latitude", it) }; lon?.let { location.put("longitude", it) }
                            json.remove("location_label"); json.remove("latitude"); json.remove("longitude"); json.put("location", location)
                        }
                    }
                    if (mode == "new") vm.createRecord(table, json)
                    else if (existing != null) vm.updateRecord(table, existing.optString("id"), existing.optLong("record_version"), json)
                }
            }, enabled = if (mode == "new") family?.canAdd == true else family?.canEdit == true, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save record") }
        }
        state.errors[table]?.let { item { FarmNotice(it, true) } }
        state.conflict?.takeIf { it.has("server_record") }?.let { conflict -> item {
            Column(Modifier.fillMaxWidth().background(FarmWarning).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This record changed on another device", style = MaterialTheme.typography.titleMedium, color = FarmForest)
                Text("You opened version ${conflict.optLong("expected_version")}; the latest is version ${conflict.optLong("actual_version")}.", color = FarmInk)
                Text("Latest saved values: ${conflict.optJSONObject("server_record")?.toString().orEmpty()}", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
                Text("Review the latest version before continuing. Your unsaved form will stay open until you discard it.", style = MaterialTheme.typography.bodySmall, color = FarmInk)
                TextButton(onClick = { vm.clearConflict(); vm.clearRecordMessage(table); back() }) { Text("Discard draft and return") }
            }
        } }
        if ("${table}_save" in state.loading) item { FarmLoadingRow("Saving securely…") }
        item { Text("Your family’s access rules are checked again when this record is saved.", style = MaterialTheme.typography.bodySmall, color = FarmMuted) }
    }
}

@Composable
private fun FarmRecordDetail(table: String, id: String, modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel, navigate: (String) -> Unit, back: () -> Unit) {
    val module = FarmModules.byTable(table) ?: return FarmEmptyState("Record unavailable", "This record type could not be opened.", null, {})
    LaunchedEffect(table) { vm.loadRecords(table) }
    val row = state.records[table].orEmpty().firstOrNull { it.optString("id") == id }
    if (row == null) return FarmEmptyState("Record not found", "It may have been removed or you may no longer have access.", "Back", back)
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(row.optString(module.primaryLabel).ifBlank { module.title }, style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        module.inputs.forEach { field ->
            val value = if (field.key == "location_label") row.optJSONObject("location")?.optString("label") else row.opt(field.key)?.toString()?.takeUnless { it == "null" }
            if (!value.isNullOrBlank()) item {
                Column(Modifier.fillMaxWidth().background(FarmWhite).padding(12.dp)) {
                    Text(field.label, style = MaterialTheme.typography.labelMedium, color = FarmMuted)
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = FarmInk)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.family?.canEdit == true) Button(onClick = { navigate("form:$table:edit:$id") }) { Text("Edit") }
                if (state.family?.canDelete == true) TextButton(onClick = {
                    vm.deleteRecord(table, id, row.optLong("record_version")); back()
                }) { Text("Move to recycle history") }
            }
        }
        state.errors[table]?.let { item { FarmNotice(it, true) } }
    }
}

@Composable
private fun FarmSearchScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel, navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, { query = it; submitted = false }, Modifier.fillMaxWidth(), label = { Text("Search farm records") }, singleLine = true) }
        item { Button(onClick = { if (query.trim().length >= 2) { submitted = true; vm.search(query.trim()) } }, modifier = Modifier.fillMaxWidth()) { Text("Search") } }
        if ("search" in state.loading) item { FarmLoadingRow("Searching your family records…") }
        state.errors["search"]?.let { item { FarmNotice(it, true) } }
        state.searchResults?.let { results ->
            if (submitted && results.isEmpty()) item { FarmEmptyState("No matching records", "Try another word or a shorter phrase.", null, {}) }
            items(results, key = { it.optString("id") + it.optString("table_name") }) { row ->
                val table = row.optString("table_name")
                val title = row.optString("title").ifBlank { "Farm record" }
                FarmRecordListRow(title, row.optString("subtitle"), onClick = { if (FarmModules.byTable(table) != null) navigate("detail:$table:${row.optString("id")}") })
            }
        }
    }
}

@Composable
private fun FarmQuickAddScreen(modifier: Modifier, navigate: (String) -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp)) {
        item { Text("What would you like to add?", style = MaterialTheme.typography.titleLarge, color = FarmForest) }
        items(FarmModules.all) { module -> FarmUtilityRow(module.title, module.description, moduleIcon(module.iconKey)) { navigate("form:${module.table}:new") } }
    }
}

@Composable
private fun FarmMembersScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    LaunchedEffect(state.family?.id, state.family?.role) { vm.loadMembers(includeRemoved = state.family?.role == "OWNER") }
    var editing by remember { mutableStateOf<FamilyMemberRecord?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Family members", style = MaterialTheme.typography.headlineSmall, color = FarmForest); Text("Choose who can view, add, change, or remove shared records.", color = FarmMuted) }
        if ("members" in state.loading) item { FarmLoadingRow("Loading family access…") }
        state.errors["members"]?.let { item { FarmNotice(it, true) } }
        items(state.members.orEmpty(), key = { it.userId }) { member ->
            Column(Modifier.fillMaxWidth().background(FarmWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium, color = FarmInk)
                Text("${member.role.lowercase().replaceFirstChar { it.uppercase() }} · ${if (member.removed) "Access removed" else "${if (member.canView) "Can view" else "No view"}, ${if (member.canAdd) "add" else "no add"}, ${if (member.canEdit) "edit" else "no edit"}, ${if (member.canDelete) "delete" else "no delete"}"}", style = MaterialTheme.typography.bodySmall, color = FarmMuted)
                if (state.family?.role == "OWNER") TextButton(onClick = { editing = member }) { Text(if (member.removed) "Restore access" else "Manage access") }
            }
        }
    }
    editing?.let { member ->
        var role by remember(member.userId) { mutableStateOf(member.role) }
        var canAdd by remember(member.userId) { mutableStateOf(member.canAdd) }; var canEdit by remember(member.userId) { mutableStateOf(member.canEdit) }; var canDelete by remember(member.userId) { mutableStateOf(member.canDelete) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("${if (member.removed) "Restore" else "Permissions for"} ${member.name}") },
            text = { Column {
                listOf("VIEWER", "EDITOR").forEach { candidate -> Row { androidx.compose.material3.RadioButton(role == candidate, { role = candidate }); Text(candidate, Modifier.padding(top = 12.dp)) } }
                listOf("Add records" to canAdd, "Edit records" to canEdit, "Delete records" to canDelete).forEach { (label, checked) -> Row { Checkbox(checked, { when (label) { "Add records" -> canAdd = it; "Edit records" -> canEdit = it; else -> canDelete = it } }); Text(label, Modifier.padding(top = 12.dp)) } }
            } }, confirmButton = { TextButton(onClick = { if (member.removed) vm.removeMember(member.userId, true) else if (state.family?.canEdit == true) vm.changeMemberRole(member, role, canAdd, canEdit, canDelete) else vm.removeMember(member.userId); editing = null }) { Text(if (member.removed) "Restore" else "Save") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun FarmInvitationsScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    LaunchedEffect(Unit) { vm.loadInvitations() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var role by rememberSaveable { mutableStateOf("EDITOR") }
    var days by rememberSaveable { mutableStateOf("7") }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Invitations", style = MaterialTheme.typography.headlineSmall, color = FarmForest); Text("Invite someone to join this family workspace.", color = FarmMuted) }
        item { TextButton(onClick = vm::loadInvitations) { Text("Refresh invitations") } }
        if (state.family?.role == "OWNER") item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("EDITOR", "VIEWER").forEach { TextButton(onClick = { role = it }) { Text(if (role == it) "✓ $it" else it) } } }
            OutlinedTextField(days, { if (it.all(Char::isDigit)) days = it }, Modifier.fillMaxWidth(), label = { Text("Expires in days (1–30)") }, singleLine = true)
            Button(onClick = { val d = days.toIntOrNull(); if (d != null && d in 1..30) vm.createInvitation(role, d) }, modifier = Modifier.fillMaxWidth()) { Text("Create invitation") }
        }
        state.createdInvitation?.let { invitation -> item {
            Column(Modifier.fillMaxWidth().background(FarmLeaf).padding(12.dp)) {
                Text("Invitation code: ${invitation.token}", color = FarmForest)
                Text("Expires ${invitation.expiresAt}", color = FarmMuted)
                Row {
                    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(invitation.token)) }) { Text("Copy code") }
                    TextButton(onClick = {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, "Join my FARM FAMILY workspace with this invitation code: ${invitation.token}")
                        context.startActivity(android.content.Intent.createChooser(share, "Share family invitation"))
                    }) { Text("Share") }
                }
            }
        } }
        state.errors["invitations"]?.let { item { FarmNotice(it, true) } }
        items(state.invitations.orEmpty(), key = { it.id }) { invite ->
            Column(Modifier.fillMaxWidth().background(FarmWhite).padding(12.dp)) {
                Text("${invite.role} · ${invite.status}", color = FarmForest)
                Text("Expires ${invite.expiresAt}", color = FarmMuted)
                if (state.family?.role == "OWNER" && invite.status == "PENDING") TextButton(onClick = { vm.revokeInvitation(invite.id) }) { Text("Revoke invitation") }
            }
        }
    }
}

@Composable
private fun FarmNotificationsScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    LaunchedEffect(Unit) { vm.loadNotifications() }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Notifications", style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        state.errors["notifications"]?.let { item { FarmNotice(it, true) } }
        if ("notifications" in state.loading) item { FarmLoadingRow("Loading notifications…") }
        if (state.notifications?.isEmpty() == true) item { FarmEmptyState("You’re all caught up", "Family activity notifications will appear here.", null, {}) }
        items(state.notifications.orEmpty(), key = { it.optString("id") }) { notification ->
            Column(Modifier.fillMaxWidth().background(FarmWhite).clickable { vm.markNotificationRead(notification.optString("id")) }.padding(13.dp)) {
                Text(notification.optString("content_key").replace('_', ' ').replaceFirstChar { it.uppercase() }, color = FarmInk)
                Text(notification.optJSONObject("content_args")?.toString().orEmpty(), color = FarmMuted)
                Text(notification.optString("created_at"), style = MaterialTheme.typography.labelSmall, color = FarmMuted)
            }
        }
    }
}

@Composable
private fun FarmBackupScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    LaunchedEffect(Unit) { vm.loadBackups() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBackup) }
    var restoreTarget by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Backup & restore", style = MaterialTheme.typography.headlineSmall, color = FarmForest); Text("Download a copy or restore a family backup after reviewing its contents.", color = FarmMuted) }
        item { TextButton(onClick = vm::loadBackups) { Text("Refresh backups") } }
        item { Button(onClick = { vm.exportBackup("ZIP") }, modifier = Modifier.fillMaxWidth()) { Text("Create restorable ZIP backup") } }
        item { Button(onClick = { vm.exportBackup("CSV") }, modifier = Modifier.fillMaxWidth()) { Text("Create CSV export") } }
        item { TextButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = state.family?.role == "OWNER") { Text("Import a ZIP backup") } }
        state.messages["backups"]?.let { item { FarmNotice(it) } }; state.errors["backups"]?.let { item { FarmNotice(it, true) } }
        item { FarmSectionTitle("AVAILABLE BACKUPS") }
        items(state.backupRecords.orEmpty(), key = { it.id }) { backup ->
            Column(Modifier.fillMaxWidth().background(FarmWhite).padding(12.dp)) {
                Text("${backup.format} backup", color = FarmForest); Text(backup.createdAt, color = FarmMuted)
                Row {
                TextButton(onClick = { vm.prepareBackupDownload(backup.id) { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    runCatching { context.startActivity(intent) }
                } }) { Text("Download") }
                    TextButton(onClick = { vm.previewRestore(backup.id); restoreTarget = backup.id }) { Text("Review restore") }
                }
            }
        }
        state.restorePreview?.let { preview -> item {
            Column(Modifier.fillMaxWidth().background(FarmLeaf).padding(14.dp)) {
                Text("Restore preview", style = MaterialTheme.typography.titleMedium, color = FarmForest)
                Text(preview.toString(), color = FarmInk)
                Button(onClick = { vm.confirmRestore(); restoreTarget = null }) { Text("Confirm restore") }
            }
        } }
        state.restoreResult?.let { item { FarmNotice("Restore completed. Review your records for any reported conflicts.") } }
        state.errors["restore"]?.let { item { FarmNotice(it, true) } }
    }
}

@Composable
private fun FarmProfileScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    var name by rememberSaveable { mutableStateOf(state.authSession?.displayName.orEmpty()) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        item { Text(state.authSession?.email.orEmpty(), color = FarmMuted) }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Your name") }, singleLine = true) }
        item { Button(onClick = { vm.updateProfile(name) }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") } }
        state.messages["profile"]?.let { item { FarmNotice(it) } }; state.errors["profile"]?.let { item { FarmNotice(it, true) } }
    }
}

@Composable
private fun FarmFamilySettingsScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    var name by rememberSaveable(state.family?.id) { mutableStateOf(state.family?.name.orEmpty()) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Family settings", style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Family name") }, singleLine = true, enabled = state.family?.role == "OWNER") }
        if (state.family?.role == "OWNER") item { Button(onClick = { vm.updateFamily(name) }, modifier = Modifier.fillMaxWidth()) { Text("Save family") } }
        item { Text("Currency: ${state.family?.currency.orEmpty()}", color = FarmMuted) }
        state.messages["family_settings"]?.let { item { FarmNotice(it) } }; state.errors["family_settings"]?.let { item { FarmNotice(it, true) } }
    }
}

@Composable
private fun FarmFamilyInfoScreen(modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel) {
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(state.family?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        item { Text("${state.family?.role.orEmpty()} · ${state.family?.currency.orEmpty()}", color = FarmMuted) }
        item { Text("Shared family data is protected by membership permissions and server side access policies.", color = FarmInk) }
        item { Button(onClick = vm::loadFamilies) { Text("Switch or refresh family") } }
        state.memberships.orEmpty().forEach { family -> item { TextButton(onClick = { vm.selectFamily(family) }) { Text(family.name) } } }
    }
}

@Composable
private fun FarmSettingsScreen(modifier: Modifier, signOut: () -> Unit) {
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        item { FarmInlineNote("Records sync securely with the family workspace. Drafts and connectivity status are shown where available.") }
        item { Text("FARM FAMILY · Version 1.0", color = FarmMuted) }
        item { TextButton(onClick = signOut) { Text("Sign out") } }
    }
}

@Composable
private fun FarmReportModuleScreen(table: String, modifier: Modifier, state: FarmUiState, vm: FarmFamilyViewModel, navigate: (String) -> Unit) {
    val module = FarmModules.byTable(table) ?: return FarmEmptyState("Report unavailable", "This report is not available.", null, {})
    LaunchedEffect(table) { vm.loadRecords(table) }
    val rows = state.records[table].orEmpty()
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { Text("${module.title} analysis", style = MaterialTheme.typography.headlineSmall, color = FarmForest) }
        item { Text("${rows.size} records in the current family workspace", color = FarmMuted) }
        if (table == "expenses" || table == "sales") {
            val total = rows.sumOf { it.optDouble("amount", it.optDouble("total_amount", 0.0)) }
            item { FarmMetricBlock("Recorded total", "${state.family?.currency.orEmpty()} ${farmNumber(total)}", Modifier.fillMaxWidth()) }
        }
        items(rows.take(100), key = { it.optString("id") }) { row -> FarmRecordListRow(row.optString(module.primaryLabel).ifBlank { module.title }, row.optString(module.secondaryLabel)) { navigate("detail:$table:${row.optString("id")}") } }
    }
}
