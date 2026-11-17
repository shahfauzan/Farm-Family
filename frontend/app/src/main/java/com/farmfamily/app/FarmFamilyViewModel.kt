package com.farmfamily.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class FarmUiState(
    val authLoading: Boolean = true,
    val authSession: AuthSession? = null,
    val authMessage: String? = null,
    val authMessageIsError: Boolean = false,
    val passwordRecovery: Boolean = false,
    val familyLoading: Boolean = false,
    val memberships: List<FamilyMembership>? = null,
    val family: FamilyMembership? = null,
    val familyError: String? = null,
    val records: Map<String, List<JSONObject>> = emptyMap(),
    val reportRecords: Map<String, List<JSONObject>> = emptyMap(),
    val financeReports: Map<String, List<JSONObject>> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val messages: Map<String, String> = emptyMap(),
    val yearReport: YearFarmReport? = null,
    val aiAnswer: FarmAiAnswer? = null,
    val weather: JSONObject? = null,
    val members: List<FamilyMemberRecord>? = null,
    val invitations: List<FamilyInvitationRecord>? = null,
    val createdInvitation: CreatedInvitation? = null,
    val notifications: List<JSONObject>? = null,
    val backupRecords: List<BackupRecord>? = null,
    val recycleRecords: List<JSONObject>? = null,
    val restorePreview: JSONObject? = null,
    val restoreResult: JSONObject? = null,
    val conflict: JSONObject? = null,
    val profileName: String = "",
    val searchResults: List<JSONObject>? = null
)

internal class FarmFamilyViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = SupabaseAuthRepository(application)
    private val farm = SupabaseFarmRepository(application)
    private val _state = androidx.compose.runtime.mutableStateOf(FarmUiState())
    private val preferences = application.getSharedPreferences("farm_family_preferences", Context.MODE_PRIVATE)
    val darkMode = androidx.compose.runtime.mutableStateOf(preferences.getBoolean("dark_mode", false))
    val awadhiLanguage = androidx.compose.runtime.mutableStateOf(preferences.getBoolean("awadhi_language", false))
    val avatarUri = androidx.compose.runtime.mutableStateOf(preferences.getString("avatar_uri", null))
    val state: androidx.compose.runtime.State<FarmUiState> = _state

    fun setDarkMode(enabled: Boolean) { darkMode.value = enabled; preferences.edit().putBoolean("dark_mode", enabled).apply() }
    fun setAwadhiLanguage(enabled: Boolean) { awadhiLanguage.value = enabled; preferences.edit().putBoolean("awadhi_language", enabled).apply() }
    fun setAvatarUri(uri: String?) { avatarUri.value = uri; preferences.edit().putString("avatar_uri", uri).apply() }
    private var started = false
    private var pendingSyncJob: Job? = null
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) syncPendingRecords()
        }
    }

    init {
        runCatching { connectivityManager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback) }
    }

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    private fun syncPendingRecords() {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        if (pendingSyncJob?.isActive == true) return
        pendingSyncJob = viewModelScope.launch {
            try {
                val tables = withContext(Dispatchers.IO) { farm.syncPendingRecordsForFamily(session.accessToken, family.id) }
                if (_state.value.family?.id == family.id && _state.value.authSession?.userId == session.userId) {
                    update { it.copy(errors = it.errors - "sync") }
                    tables.forEach(::loadRecords)
                }
            } catch (_: Exception) {
                if (_state.value.family?.id == family.id) {
                    update { it.copy(errors = it.errors + ("sync" to "Some saved changes could not sync yet. Open the affected record list to retry.")) }
                }
            }
        }
    }

    private class RecordConflictException(val details: JSONObject) : IllegalStateException("This record changed on another device. Review the latest version before saving again.")

    private fun update(transform: (FarmUiState) -> FarmUiState) {
        _state.value = transform(_state.value)
    }

    fun start(initialCallback: Uri?) {
        if (started) {
            if (initialCallback != null) handleAuthCallback(initialCallback)
            return
        }
        started = true
        if (initialCallback != null) {
            handleAuthCallback(initialCallback)
            return
        }
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { auth.restoreSession() }
                update { it.copy(authLoading = false, authSession = session, authMessage = null) }
                if (session != null) loadFamilies()
            } catch (error: Exception) {
                update { it.copy(authLoading = false, authMessage = error.message ?: "Your saved session could not be restored.", authMessageIsError = true) }
            }
        }
    }

    fun handleAuthCallback(uri: Uri) {
        update { it.copy(authLoading = true, authMessage = null) }
        viewModelScope.launch {
            try {
                val callback = withContext(Dispatchers.IO) { auth.handleAuthCallback(uri) }
                update { it.copy(authLoading = false, authSession = callback.session, passwordRecovery = callback.passwordRecovery, authMessage = null) }
                loadFamilies()
            } catch (error: Exception) {
                update { it.copy(authLoading = false, authSession = null, authMessage = error.message ?: "This email link is invalid or expired.", authMessageIsError = true) }
            }
        }
    }

    fun signIn(email: String, password: String) = authAction {
        auth.signIn(email, password)
    }

    fun signUp(email: String, password: String, fullName: String) = authAction {
        auth.signUp(email, password, fullName)
    }

    fun recoverPassword(email: String) {
        update { it.copy(authLoading = true, authMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { auth.sendPasswordRecovery(email) }
                update { it.copy(authLoading = false, authMessage = "If an account matches that email, recovery instructions are on the way.", authMessageIsError = false) }
            } catch (error: Exception) {
                update { it.copy(authLoading = false, authMessage = error.message ?: "Password recovery could not be started.", authMessageIsError = true) }
            }
        }
    }

    fun setNewPassword(password: String) {
        val token = _state.value.authSession?.accessToken
        if (token.isNullOrBlank()) {
            update { it.copy(authMessage = "The recovery session has expired. Request a new password link.", authMessageIsError = true) }
            return
        }
        update { it.copy(authLoading = true, authMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { auth.updatePassword(token, password) }
                update { it.copy(authLoading = false, passwordRecovery = false, authMessage = "Your password has been updated.", authMessageIsError = false) }
            } catch (error: Exception) {
                update { it.copy(authLoading = false, authMessage = error.message ?: "Your password could not be updated.", authMessageIsError = true) }
            }
        }
    }

    fun signOut() {
        update { it.copy(authLoading = true, authMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { auth.signOut() }
                update { FarmUiState(authLoading = false) }
                started = false
            } catch (error: Exception) {
                update { it.copy(authLoading = false, authMessage = error.message ?: "Sign out could not be confirmed.", authMessageIsError = true) }
            }
        }
    }

    fun clearAuthMessage() { update { it.copy(authMessage = null, authMessageIsError = false) } }

    private fun authAction(action: () -> AuthSession?) {
        update { it.copy(authLoading = true, authMessage = null) }
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { action() }
                if (session == null) {
                    update { it.copy(authLoading = false, authMessage = "Check your email to confirm your account, then sign in.", authMessageIsError = false) }
                } else {
                    update { it.copy(authLoading = false, authSession = session, authMessage = null, passwordRecovery = false) }
                    loadFamilies()
                }
            } catch (error: Exception) {
                update { it.copy(authLoading = false, authMessage = error.message ?: "Authentication failed. Try again.", authMessageIsError = true) }
            }
        }
    }

    fun loadFamilies() {
        val session = _state.value.authSession ?: return
        update { it.copy(familyLoading = true, familyError = null) }
        viewModelScope.launch {
            try {
                val families = withContext(Dispatchers.IO) { farm.loadFamilies(session.accessToken, session.userId) }
                val oldId = _state.value.family?.id
                val family = families.firstOrNull { it.id == oldId } ?: families.singleOrNull()
                update { it.copy(familyLoading = false, memberships = families, family = family) }
            } catch (error: Exception) {
                update { it.copy(familyLoading = false, familyError = error.message ?: "Family details could not be loaded.") }
            }
        }
    }

    fun selectFamily(family: FamilyMembership) {
        update { it.copy(family = family, records = emptyMap(), yearReport = null) }
    }

    fun createFamily(name: String) = familyAction { session -> farm.createFamily(session.accessToken, name) }

    fun joinFamily(inviteToken: String) = familyAction { session -> farm.acceptInvitation(session.accessToken, inviteToken) }

    private fun familyAction(action: (AuthSession) -> Unit) {
        val session = _state.value.authSession ?: return
        update { it.copy(familyLoading = true, familyError = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action(session) }
                val families = withContext(Dispatchers.IO) { farm.loadFamilies(session.accessToken, session.userId) }
                update { it.copy(familyLoading = false, memberships = families, family = families.singleOrNull() ?: families.firstOrNull()) }
            } catch (error: Exception) {
                update { it.copy(familyLoading = false, familyError = error.message ?: "Family setup could not be completed.") }
            }
        }
    }

    fun loadRecords(table: String) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        val spec = FarmModules.byTable(table) ?: return
        update { it.copy(loading = it.loading + table, errors = it.errors - table) }
        viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) { farm.loadRecords(session.accessToken, family.id, table, spec.orderColumn) }
                update { it.copy(loading = it.loading - table, records = it.records + (table to rows), errors = it.errors - table) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - table, errors = it.errors + (table to (error.message ?: "Records could not be loaded."))) }
            }
        }
    }

    fun createRecord(table: String, values: JSONObject) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        mutate(table, "save") { farm.createRecord(session.accessToken, family.id, table, values) }
    }

    fun clearRecordMessage(table: String) {
        update { it.copy(messages = it.messages - table, errors = it.errors - table) }
    }
    fun clearConflict() { update { it.copy(conflict = null) } }

    fun updateRecord(table: String, id: String, version: Long, values: JSONObject) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        mutate(table, "save") {
            val result = farm.updateRecord(session.accessToken, family.id, table, id, version, values)
            if (result.conflict != null) throw RecordConflictException(result.conflict)
            result.record ?: throw IllegalStateException("The server did not return the saved record.")
        }
    }

    fun deleteRecord(table: String, id: String, version: Long) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        mutate(table, "delete") {
            farm.deleteRecord(session.accessToken, family.id, table, id, version)
            JSONObject()
        }
    }

    private fun mutate(table: String, operation: String, action: () -> JSONObject) {
        update { it.copy(loading = it.loading + "${table}_$operation", errors = it.errors - table, messages = it.messages - table) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() }
                val queuedOffline = result.optString("sync_status") == "PENDING"
                update { it.copy(loading = it.loading - "${table}_$operation", messages = it.messages + (table to when {
                    queuedOffline -> "Saved on this device. It will sync when you’re online."
                    operation == "delete" -> "Record moved to the family recycle history."
                    else -> "Changes saved."
                })) }
                loadRecords(table)
                loadHomeSummary()
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "${table}_$operation", errors = it.errors + (table to (error.message ?: "The change could not be saved.")), conflict = (error as? RecordConflictException)?.details ?: it.conflict) }
            }
        }
    }

    fun loadHomeSummary() {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        loadRecords("fields")
        loadRecords("crop_seasons")
        update { it.copy(loading = it.loading + "home_report", errors = it.errors - "home_report") }
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) { farm.loadCurrentYearReport(session.accessToken, family.id) }
                update { it.copy(loading = it.loading - "home_report", yearReport = report) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "home_report", errors = it.errors + ("home_report" to (error.message ?: "The family summary could not be loaded."))) }
            }
        }
    }

    fun loadYearReport(year: Int = java.time.Year.now().value) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        update { it.copy(loading = it.loading + "home_report", errors = it.errors - "home_report") }
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) { farm.loadCurrentYearReport(session.accessToken, family.id, year) }
                update { it.copy(loading = it.loading - "home_report", yearReport = report) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "home_report", errors = it.errors + ("home_report" to (error.message ?: "The report could not be loaded."))) }
            }
        }
    }

    fun askAi(question: String) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        update { it.copy(loading = it.loading + "ai", errors = it.errors - "ai", aiAnswer = null) }
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { farm.askFarmAi(session.accessToken, family.id, question) }
                update { it.copy(loading = it.loading - "ai", aiAnswer = answer) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "ai", errors = it.errors + ("ai" to (error.message ?: "Farm AI is unavailable."))) }
            }
        }
    }

    fun search(query: String) {
        if (query.trim().length < 2 || query.length > 160) {
            update { it.copy(errors = it.errors + ("search" to "Enter at least 2 characters, up to 160.")) }
            return
        }
        task("search", { farm.searchRecords(token(), familyId(), query) }) { state, rows ->
            val normalized = rows.map { result ->
                val table = result.optString("entity_table")
                val row = result.optJSONObject("record") ?: JSONObject()
                val spec = FarmModules.byTable(table)
                JSONObject(row.toString()).put("table_name", table)
                    .put("title", row.optString(spec?.primaryLabel.orEmpty()).ifBlank { spec?.title ?: "Farm record" })
                    .put("subtitle", row.optString(spec?.secondaryLabel.orEmpty()))
            }
            state.copy(searchResults = normalized)
        }
    }

    fun loadReportRecords(table: String, from: String?, to: String?) {
        if (from != null && to != null && from > to) {
            update { it.copy(errors = it.errors + ("report:$table" to "The start date must be on or before the end date.")) }
            return
        }
        val key = "report:$table"
        task(key, { farm.loadReportRecords(token(), familyId(), table, from, to) }) { state, records ->
            state.copy(reportRecords = state.reportRecords + (table to records))
        }
        if (table in setOf("expenses", "sales")) {
            task("finance:$table", { farm.loadFinanceSummary(token(), familyId(), from, to) }) { state, report ->
                state.copy(financeReports = state.financeReports + (table to report))
            }
        }
    }

    fun loadWeather(latitude: Double, longitude: Double) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        update { it.copy(loading = it.loading + "weather", errors = it.errors - "weather") }
        viewModelScope.launch {
            try {
                val weather = withContext(Dispatchers.IO) { farm.loadWeather(session.accessToken, family.id, latitude, longitude) }
                update { it.copy(loading = it.loading - "weather", weather = weather) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "weather", errors = it.errors + ("weather" to (error.message ?: "Weather is not available."))) }
            }
        }
    }

    fun loadMembers(includeRemoved: Boolean = false) = task("members", { farm.loadFamilyMembers(token(), familyId(), includeRemoved) }) { state, result -> state.copy(members = result) }

    fun changeMemberRole(member: FamilyMemberRecord, role: String, allowAdd: Boolean, allowEdit: Boolean, allowDelete: Boolean) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        update { it.copy(loading = it.loading + "members", errors = it.errors - "members") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { farm.setFamilyMember(session.accessToken, family.id, member.userId, role, true, allowAdd, allowEdit, allowDelete) }
                update { it.copy(loading = it.loading - "members", messages = it.messages + ("members" to "Family permissions updated.")) }
                loadMembers()
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "members", errors = it.errors + ("members" to (error.message ?: "Member permissions could not be updated."))) }
            }
        }
    }

    fun removeMember(memberId: String, restore: Boolean = false) {
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        update { it.copy(loading = it.loading + "members", errors = it.errors - "members") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (restore) farm.restoreFamilyMember(session.accessToken, family.id, memberId)
                    else farm.removeFamilyMember(session.accessToken, family.id, memberId)
                }
                update { it.copy(loading = it.loading - "members", messages = it.messages + ("members" to if (restore) "Member access restored." else "Member access removed.")) }
                loadMembers(includeRemoved = true)
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "members", errors = it.errors + ("members" to (error.message ?: "Member access could not be changed."))) }
            }
        }
    }

    fun loadInvitations() = task("invitations", { farm.loadInvitations(token(), familyId()) }) { state, result -> state.copy(invitations = result) }

    fun createInvitation(role: String, days: Int) = task("invitations", { farm.createInvitation(token(), familyId(), role, days) }) { state, result ->
        state.copy(createdInvitation = result, invitations = null, messages = state.messages + ("invitations" to "Invitation created. Share its code only with the intended family member."))
    }

    fun revokeInvitation(id: String) = task("invitations", { farm.revokeInvitation(token(), id) }) { state, _ ->
        state.copy(invitations = null, messages = state.messages + ("invitations" to "Invitation revoked."))
    }

    fun loadNotifications() = task("notifications", { farm.loadNotifications(token(), _state.value.authSession!!.userId, familyId()) }) { state, result -> state.copy(notifications = result) }

    fun markNotificationRead(id: String) = task("notifications_mark", { farm.markNotificationRead(token(), id) }) { state, _ ->
        val readAt = java.time.Instant.now().toString()
        state.copy(notifications = state.notifications?.map { item -> if (item.optString("id") == id) JSONObject(item.toString()).put("read_at", readAt) else item })
    }

    fun loadBackups() = task("backups", { farm.loadBackups(token(), familyId()) }) { state, result -> state.copy(backupRecords = result) }

    fun loadRecycleBin() = task("recycle_bin", { farm.loadDeletedRecords(token(), familyId()) }) { state, rows -> state.copy(recycleRecords = rows) }
    fun restoreDeletedRecord(row: JSONObject) = task("recycle_bin", {
        farm.restoreRecord(token(), familyId(), row.optString("table_name"), row)
        row.optString("table_name")
    }) { state, table -> state.copy(recycleRecords = state.recycleRecords.orEmpty().filterNot { it.optString("id") == row.optString("id") && it.optString("table_name") == table }, messages = state.messages + ("recycle_bin" to "Record restored.")) }

    fun exportBackup(format: String) = task("backups", { farm.exportBackup(token(), familyId(), format) }) { state, result ->
        state.copy(backupRecords = null, messages = state.messages + ("backups" to result))
    }

    fun prepareBackupDownload(id: String, onUrl: (String) -> Unit) {
        task("backup_download", { farm.backupDownloadUrl(token(), id) }) { state, url ->
            onUrl(url)
            state
        }
    }

    fun previewRestore(id: String) = task("restore", { farm.previewRestore(token(), id) }) { state, preview -> state.copy(restorePreview = preview) }

    fun importBackup(uri: Uri) = task("restore", {
        val familyId = familyId()
        val path = farm.uploadImportZip(token(), familyId, uri)
        farm.previewImport(token(), familyId, path)
    }) { state, preview -> state.copy(restorePreview = preview, restoreResult = null) }

    fun confirmRestore() = task("restore", {
        val importId = _state.value.restorePreview?.optString("import_id")?.takeIf(String::isNotBlank)
            ?: error("Review the backup before restoring it.")
        farm.confirmRestore(token(), importId)
    }) { state, result ->
        state.copy(restorePreview = null, restoreResult = result, messages = state.messages + ("restore" to "The backup restore completed."))
    }

    fun updateProfile(name: String) = task("profile", {
        val session = _state.value.authSession ?: error("Sign in again to update your profile.")
        auth.updateProfile(session.accessToken, name.trim())
        farm.updateProfile(session.accessToken, session.userId, name)
    }) { state, _ ->
        val session = state.authSession
        state.copy(profileName = name.trim(), authSession = session?.copy(displayName = name.trim()), messages = state.messages + ("profile" to "Profile saved."))
    }

    fun updateAccount(name: String, email: String, password: String) = task("profile", {
        val session = _state.value.authSession ?: error("Sign in again to update your account.")
        auth.updateProfile(session.accessToken, name.trim())
        farm.updateProfile(session.accessToken, session.userId, name.trim())
        if (email.trim() != session.email) auth.updateEmail(session.accessToken, email.trim())
        if (password.isNotBlank()) auth.updatePassword(session.accessToken, password)
        Triple(name.trim(), email.trim(), password.isNotBlank())
    }) { state, result ->
        val (updatedName, updatedEmail, passwordChanged) = result
        state.copy(profileName = updatedName, authSession = state.authSession?.copy(displayName = updatedName), messages = state.messages + (if (updatedEmail != state.authSession?.email) ("profile" to "Profile saved. Confirm the email change using the link we sent.") else ("profile" to if (passwordChanged) "Profile and password saved." else "Profile saved.")))
    }

    fun updateFamily(name: String, currency: String) = task("family_settings", {
        farm.updateFamilyName(token(), familyId(), name, currency)
    }) { state, _ -> state.copy(family = state.family?.copy(name = name.trim(), currency = currency), messages = state.messages + ("family_settings" to "Family name and currency saved.")) }

    private fun token(): String = _state.value.authSession?.accessToken ?: error("Sign in again to continue.")
    private fun familyId(): String = _state.value.family?.id ?: error("Choose a family to continue.")

    private fun <T> task(key: String, work: () -> T, success: (FarmUiState, T) -> FarmUiState) {
        update { it.copy(loading = it.loading + key, errors = it.errors - key) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                update { success(it.copy(loading = it.loading - key, errors = it.errors - key), result) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - key, errors = it.errors + (key to (error.message ?: "The request could not be completed."))) }
            }
        }
    }
}
