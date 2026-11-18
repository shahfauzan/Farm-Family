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
    val aiExpenseDraft: AiExpenseDraft? = null,
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
    val avatarUrl: String? = null,
    val searchResults: List<JSONObject>? = null,
    val pendingInvitationToken: String? = null,
    val invitationJoinError: String? = null
)

internal data class AiExpenseDraft(val description: String, val amount: Double, val date: String)

internal class FarmFamilyViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = SupabaseAuthRepository(application)
    private val farm = SupabaseFarmRepository(application)
    private val preferences = application.getSharedPreferences("farm_family_preferences", Context.MODE_PRIVATE)
    private val _state = androidx.compose.runtime.mutableStateOf(
        FarmUiState(pendingInvitationToken = preferences.getString("pending_invitation_token", null))
    )
    private val autoNightEnabled = androidx.compose.runtime.mutableStateOf(preferences.getBoolean("auto_night_mode", true))
    val darkMode = androidx.compose.runtime.mutableStateOf(
        if (autoNightEnabled.value) isNightTime() else preferences.getBoolean("dark_mode", false)
    )
    val hindiLanguage = androidx.compose.runtime.mutableStateOf(
        preferences.getBoolean("hindi_language", preferences.getBoolean("awadhi_language", false))
    )
    val state: androidx.compose.runtime.State<FarmUiState> = _state

    fun setDarkMode(enabled: Boolean) {
        autoNightEnabled.value = false
        darkMode.value = enabled
        preferences.edit().putBoolean("auto_night_mode", false).putBoolean("dark_mode", enabled).apply()
    }
    fun setAutoNightMode(enabled: Boolean) {
        autoNightEnabled.value = enabled
        preferences.edit().putBoolean("auto_night_mode", enabled).apply()
        if (enabled) darkMode.value = isNightTime()
    }
    fun isAutoNightModeEnabled(): Boolean = autoNightEnabled.value

    private fun isNightTime(): Boolean {
        val hour = java.time.LocalTime.now().hour
        return hour >= 19 || hour < 6
    }
    fun setHindiLanguage(enabled: Boolean) { hindiLanguage.value = enabled; preferences.edit().putBoolean("hindi_language", enabled).apply() }
    private var started = false
    private var pendingSyncJob: Job? = null
    private var invitationJoinJob: Job? = null
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
                if (session != null) {
                    if (_state.value.pendingInvitationToken != null) tryAutoJoinInvitation() else loadFamilies()
                }
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
                if (_state.value.pendingInvitationToken != null && !callback.passwordRecovery) tryAutoJoinInvitation() else loadFamilies()
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
                    if (_state.value.pendingInvitationToken != null) tryAutoJoinInvitation() else loadFamilies()
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

    fun receiveInvitationLink(rawToken: String?) {
        val inviteToken = rawToken?.trim()?.takeIf { INVITATION_TOKEN_PATTERN.matches(it) }
        if (inviteToken == null) {
            update { it.copy(authMessage = "This invitation link is not valid. Ask the family owner for a new link.", authMessageIsError = true) }
            return
        }
        preferences.edit().putString("pending_invitation_token", inviteToken).apply()
        update { it.copy(pendingInvitationToken = inviteToken, invitationJoinError = null, authMessage = null) }
        tryAutoJoinInvitation()
    }

    fun joinFamily(invitationOrLink: String) {
        val value = invitationOrLink.trim()
        val inviteToken = if (value.startsWith("farmfamily://", ignoreCase = true)) {
            runCatching { Uri.parse(value).getQueryParameter("token") }.getOrNull()
        } else value
        if (inviteToken.isNullOrBlank() ||
            !(INVITATION_CODE_PATTERN.matches(inviteToken) || INVITATION_TOKEN_PATTERN.matches(inviteToken))) {
            update { it.copy(familyError = "Enter a valid 8-digit invitation code.") }
            return
        }
        preferences.edit().putString("pending_invitation_token", inviteToken).apply()
        update { it.copy(pendingInvitationToken = inviteToken, invitationJoinError = null, familyError = null) }
        tryAutoJoinInvitation()
    }

    fun retryPendingInvitation() {
        update { it.copy(invitationJoinError = null) }
        tryAutoJoinInvitation()
    }

    fun dismissPendingInvitation() {
        preferences.edit().remove("pending_invitation_token").apply()
        update { it.copy(pendingInvitationToken = null, invitationJoinError = null) }
        if (_state.value.authSession != null && _state.value.memberships == null) loadFamilies()
    }

    private fun tryAutoJoinInvitation() {
        val inviteToken = _state.value.pendingInvitationToken ?: return
        val session = _state.value.authSession ?: return
        if (invitationJoinJob?.isActive == true) return
        update { it.copy(familyLoading = true, invitationJoinError = null, familyError = null) }
        invitationJoinJob = viewModelScope.launch {
            try {
                val joinedFamilyId = withContext(Dispatchers.IO) { farm.acceptInvitation(session.accessToken, inviteToken) }
                val families = withContext(Dispatchers.IO) { farm.loadFamilies(session.accessToken, session.userId) }
                val joinedFamily = families.firstOrNull { it.id == joinedFamilyId }
                    ?: throw IllegalStateException("The invited family could not be opened. Refresh your family list and try again.")
                preferences.edit().remove("pending_invitation_token").apply()
                update {
                    it.copy(
                        familyLoading = false,
                        memberships = families,
                        family = joinedFamily,
                        familyError = null,
                        pendingInvitationToken = null,
                        invitationJoinError = null,
                        records = emptyMap(),
                        yearReport = null
                    )
                }
            } catch (error: Exception) {
                update {
                    it.copy(
                        familyLoading = false,
                        invitationJoinError = error.message ?: "The invitation could not be accepted. Check your connection and retry."
                    )
                }
            }
        }
    }

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
        val writingIntent = Regex("(?i)\\b(add|create|record|log|save|enter|track|jodo|darj)\\b").containsMatchIn(question) || Regex("दर्ज|जोड़|लिख").containsMatchIn(question)
        val expenseIntent = Regex("(?i)\\b(expense|expenses|cost|kharch|kharcha)\\b|खर्च|व्यय|लागत").containsMatchIn(question)
        if (writingIntent && expenseIntent) {
            val amountMatch = Regex("(?i)(?:npr|rs\\.?|रु\\.?\\s*)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)").findAll(question).lastOrNull()
            val amount = amountMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            val description = question
                .replace(Regex("(?i)\\b(add|create|record|log|save|enter|track|expense|expenses|cost|costs|kharch|kharcha|npr|rs\\.?|for|on|of)\\b"), " ")
                .replace(Regex("\\b[0-9][0-9,]*(?:\\.[0-9]+)?\\b"), " ")
                .replace(Regex("[\\s,.:;%-]+"), " ").trim().take(200)
            if (amount == null || amount < 0 || description.length < 2) {
                update { it.copy(aiExpenseDraft = null, errors = it.errors + ("ai" to "To draft an expense, include its name and amount, for example: Add expense seeds NPR 500.")) }
                return
            }
            val date = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(question)?.value ?: java.time.LocalDate.now().toString()
            update { it.copy(aiExpenseDraft = AiExpenseDraft(description, amount, date), aiAnswer = null, errors = it.errors - "ai", messages = it.messages - "expenses") }
            return
        }
        val family = _state.value.family ?: return
        val session = _state.value.authSession ?: return
        update { it.copy(loading = it.loading + "ai", errors = it.errors - "ai", aiAnswer = null, aiExpenseDraft = null) }
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { farm.askFarmAi(session.accessToken, family.id, question) }
                update { it.copy(loading = it.loading - "ai", aiAnswer = answer) }
            } catch (error: Exception) {
                update { it.copy(loading = it.loading - "ai", errors = it.errors + ("ai" to (error.message ?: "Farm AI is unavailable."))) }
            }
        }
    }

    fun confirmAiExpense() {
        val draft = _state.value.aiExpenseDraft ?: return
        update { it.copy(aiExpenseDraft = null) }
        createRecord("expenses", JSONObject()
            .put("description", draft.description)
            .put("expense_date", draft.date)
            .put("amount", draft.amount)
            .put("currency", "NPR"))
    }

    fun cancelAiExpense() { update { it.copy(aiExpenseDraft = null) } }

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

    fun exportBackup(format: String) = task("backups", {
        val message = farm.exportBackup(token(), familyId(), format)
        message to farm.loadBackups(token(), familyId())
    }) { state, result ->
        state.copy(backupRecords = result.second, messages = state.messages + ("backups" to result.first))
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

    fun loadAvatar() {
        val session = _state.value.authSession ?: return
        val path = session.avatarPath ?: return
        task("avatar", { farm.profileAvatarUrl(session.accessToken, path) }) { state, url -> state.copy(avatarUrl = url) }
    }

    fun uploadAvatar(uri: Uri) = task("avatar", {
        val session = _state.value.authSession ?: error("Sign in again to change your profile picture.")
        val path = farm.uploadProfileAvatar(session.accessToken, uri)
        val url = farm.profileAvatarUrl(session.accessToken, path)
        auth.updateAvatarPath(session.accessToken, path)
        path to url
    }) { state, (path, url) ->
        state.copy(authSession = state.authSession?.copy(avatarPath = path), avatarUrl = url, messages = state.messages + ("profile" to "Profile picture saved to your account."))
    }

    fun removeAvatar() = task("avatar", {
        val session = _state.value.authSession ?: error("Sign in again to change your profile picture.")
        farm.deleteProfileAvatar(session.accessToken)
        auth.updateAvatarPath(session.accessToken, "")
    }) { state, _ -> state.copy(authSession = state.authSession?.copy(avatarPath = null), avatarUrl = null, messages = state.messages + ("profile" to "Profile picture removed.")) }

    fun updateFamily(name: String, currency: String, defaultAreaUnit: String) = task("family_settings", {
        farm.updateFamilyName(token(), familyId(), name, currency, defaultAreaUnit)
    }) { state, _ -> state.copy(family = state.family?.copy(name = name.trim(), currency = currency, defaultAreaUnit = defaultAreaUnit), messages = state.messages + ("family_settings" to "Family settings saved.")) }

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

    private companion object {
        val INVITATION_CODE_PATTERN = Regex("[0-9]{8}")
        val INVITATION_TOKEN_PATTERN = Regex("[a-fA-F0-9]{64}")
    }
}
