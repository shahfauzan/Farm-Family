package com.farmfamily.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class FamilyMembership(
    val id: String,
    val name: String,
    val currency: String,
    val role: String,
    val canView: Boolean,
    val canAdd: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean
)

data class FarmFieldRecord(
    val id: String,
    val localId: String? = null,
    val name: String,
    val area: Double?,
    val areaUnit: String?,
    val location: String,
    val syncStatus: String
)

data class FieldRecordsSnapshot(
    val fields: List<FarmFieldRecord>,
    val isOffline: Boolean,
    val pendingCount: Int,
    val syncError: String? = null
)

enum class FieldSaveState { SYNCED, PENDING, FAILED }

data class FieldSaveResult(val state: FieldSaveState, val message: String? = null)

data class YearFarmReport(
    val year: Int,
    val revenue: Double?,
    val cost: Double?,
    val profit: Double?,
    val harvestQuantity: Double?
)

data class CropSeasonSummary(
    val name: String,
    val crop: String,
    val field: String,
    val purpose: String
)

data class FarmAiAnswer(val explanation: String, val intent: String, val records: Int)

data class BackupRecord(val id: String, val format: String, val createdAt: String)

data class RecordWriteResult(val record: JSONObject?, val conflict: JSONObject? = null)

data class FamilyMemberRecord(
    val userId: String,
    val name: String,
    val role: String,
    val canView: Boolean,
    val canAdd: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val removed: Boolean
)

data class FamilyInvitationRecord(
    val id: String,
    val role: String,
    val status: String,
    val expiresAt: String
)

data class CreatedInvitation(val id: String, val token: String, val expiresAt: String)

class SupabaseFarmRepository(context: android.content.Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val localStore = FarmLocalStore(context)
    private val appContext = context.applicationContext

    fun loadRecords(accessToken: String, familyId: String, table: String, orderBy: String): List<JSONObject> {
        require(table in RECORD_TABLES) { "This record type is not supported." }
        syncPendingRecordCreates(accessToken, familyId, table, mutableSetOf())
        val safeOrder = if (orderBy in SAFE_COLUMNS) orderBy else "created_at"
        val rows = try {
            val response = JSONArray(request(
                "rest/v1/$table?select=*&family_id=eq.$familyId&deleted_at=is.null&order=$safeOrder.desc&limit=500",
                "GET", accessToken
            ))
            (0 until response.length()).map { response.getJSONObject(it) }.also { localStore.cacheRecords(familyId, table, it) }
        } catch (error: NetworkUnavailableException) {
            localStore.cachedRecords(familyId, table).map { JSONObject(it.toString()).put("sync_status", "STALE") }
        }
        val pending = localStore.pendingRecordCreates(familyId, table).map { (localId, payload) ->
            JSONObject(payload.toString()).put("id", "local_$localId").put("local_id", localId)
                .put("sync_status", payload.optString("_local_sync_status", "PENDING"))
        }
        return pending + rows.filterNot { remote -> pending.any { it.optString("local_id") == remote.optString("local_id") } }
    }

    fun createRecord(accessToken: String, familyId: String, table: String, values: JSONObject): JSONObject {
        require(table in RECORD_TABLES) { "This record type is not supported." }
        val body = JSONObject(values.toString())
            .put("family_id", familyId)
            .put("local_id", UUID.randomUUID().toString())
        val localId = body.getString("local_id")
        val raw = try {
            request("rest/v1/$table", "POST", accessToken, body, "return=representation")
        } catch (error: NetworkUnavailableException) {
            localStore.queueRecordCreate(familyId, table, localId, body)
            return JSONObject(body.toString()).put("id", "local_$localId").put("sync_status", "PENDING")
        }
        val rows = JSONArray(raw)
        return rows.optJSONObject(0) ?: JSONObject(body.toString())
    }

    fun syncPendingRecordsForFamily(accessToken: String, familyId: String): List<String> {
        val pendingTables = localStore.pendingRecordTables(familyId)
        val visited = mutableSetOf<String>()
        pendingTables.forEach { table -> syncPendingRecordCreates(accessToken, familyId, table, visited) }
        return pendingTables
    }

    private fun syncPendingRecordCreates(accessToken: String, familyId: String, table: String, visited: MutableSet<String>) {
        if (!visited.add(table)) return
        val dependencies = when (table) {
            "crop_seasons" -> listOf("fields", "crops")
            "seed_records", "fertilizer_records", "labor_records", "machinery_usage", "expenses", "harvest_records" ->
                listOf("crop_seasons") + if (table == "expenses") listOf("expense_categories") else emptyList()
            "sales" -> listOf("crop_seasons", "harvest_records")
            else -> emptyList()
        }
        dependencies.forEach { dependency -> syncPendingRecordCreates(accessToken, familyId, dependency, visited) }
        localStore.pendingRecordCreates(familyId, table).forEach { (localId, payload) ->
            try {
                payload.remove("_local_sync_status")
                val raw = request("rest/v1/$table", "POST", accessToken, payload, "return=representation")
                val serverId = JSONArray(raw).optJSONObject(0)?.optString("id").orEmpty()
                if (serverId.isNotBlank()) localStore.remapPendingRecordReferences(familyId, localId, serverId)
                localStore.removePendingRecordCreate(familyId, table, localId)
            } catch (_: NetworkUnavailableException) {
                return
            } catch (_: SupabaseApiException) {
                localStore.markPendingRecordFailed(familyId, table, localId)
            }
        }
    }

    fun updateRecord(
        accessToken: String,
        familyId: String,
        table: String,
        id: String,
        expectedVersion: Long,
        values: JSONObject
    ): RecordWriteResult {
        require(table in RECORD_TABLES) { "This record type is not supported." }
        val result = JSONObject(request(
            "rest/v1/rpc/sync_update", "POST", accessToken,
            JSONObject().put("p_table", "public.$table")
                .put("p_id", id).put("p_family", familyId)
                .put("p_expected", expectedVersion).put("p_data", values)
        ))
        return if (result.optString("status") == "CONFLICT") {
            RecordWriteResult(null, result)
        } else {
            RecordWriteResult(result.optJSONObject("record"))
        }
    }

    fun deleteRecord(accessToken: String, familyId: String, table: String, id: String, expectedVersion: Long) {
        require(table in RECORD_TABLES) { "This record type is not supported." }
        request(
            "rest/v1/rpc/soft_delete_record", "POST", accessToken,
            JSONObject().put("p_table", "public.$table").put("p_id", id)
                .put("p_family", familyId).put("p_expected", expectedVersion)
        )
    }

    fun searchRecords(accessToken: String, familyId: String, query: String): List<JSONObject> {
        val rows = JSONArray(request(
            "rest/v1/rpc/family_search", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_query", query.trim()).put("p_limit", 50)
        ))
        return (0 until rows.length()).map { rows.getJSONObject(it) }
    }

    fun loadReportRecords(accessToken: String, familyId: String, table: String, from: String?, to: String?): List<JSONObject> {
        val rows = when (table) {
            "expenses", "sales", "harvest_records" -> JSONArray(request(
                "rest/v1/rpc/family_activity_report", "POST", accessToken,
                JSONObject().put("p_family", familyId).put("p_kind", when (table) {
                    "harvest_records" -> "harvest"
                    else -> table
                }).put("p_from", from ?: JSONObject.NULL).put("p_to", to ?: JSONObject.NULL).put("p_limit", 500)
            ))
            "fields" -> JSONArray(request("rest/v1/rpc/family_field_report", "POST", accessToken, JSONObject().put("p_family", familyId)))
            "historical_prices" -> JSONArray(request(
                "rest/v1/rpc/family_historical_price_report", "POST", accessToken,
                JSONObject().put("p_family", familyId).put("p_crop", JSONObject.NULL)
                    .put("p_from", from ?: JSONObject.NULL).put("p_to", to ?: JSONObject.NULL)
            ))
            "crops" -> JSONArray(request("rest/v1/rpc/family_crop_report", "POST", accessToken, JSONObject().put("p_family", familyId).put("p_crop", JSONObject.NULL)))
            else -> throw IllegalArgumentException("This report is not available.")
        }
        return (0 until rows.length()).map { index ->
            val result = rows.getJSONObject(index)
            (result.optJSONObject("record") ?: result).let { row ->
                if (table == "fields" && row.has("field_id")) JSONObject(row.toString()).put("id", row.optString("field_id")).put("name", row.optString("field_name")) else row
            }
        }
    }

    fun loadFinanceSummary(accessToken: String, familyId: String, from: String?, to: String?): List<JSONObject> {
        val rows = JSONArray(request("rest/v1/rpc/family_finance_summary", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_from", from ?: JSONObject.NULL).put("p_to", to ?: JSONObject.NULL)))
        return (0 until rows.length()).map { rows.getJSONObject(it) }
    }

    fun loadFamilyMembers(accessToken: String, familyId: String, includeRemoved: Boolean = false): List<FamilyMemberRecord> {
        val rows = JSONArray(request(
            "rest/v1/rpc/family_member_roster", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_include_removed", includeRemoved)
        ))
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            FamilyMemberRecord(
                userId = row.optString("user_id"),
                name = row.optString("display_name").ifBlank { "Family member" },
                role = row.optString("role"),
                canView = row.optBoolean("can_view"),
                canAdd = row.optBoolean("can_add"),
                canEdit = row.optBoolean("can_edit"),
                canDelete = row.optBoolean("can_delete"),
                removed = !row.isNull("removed_at")
            )
        }
    }

    fun setFamilyMember(
        accessToken: String, familyId: String, memberId: String, role: String,
        canView: Boolean, canAdd: Boolean, canEdit: Boolean, canDelete: Boolean
    ) {
        require(role in setOf("OWNER", "EDITOR", "VIEWER")) { "Choose a valid family role." }
        request(
            "rest/v1/rpc/set_family_member", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_user", memberId).put("p_role", role)
                .put("p_can_view", canView).put("p_can_add", canAdd)
                .put("p_can_edit", canEdit).put("p_can_delete", canDelete)
        )
    }

    fun removeFamilyMember(accessToken: String, familyId: String, memberId: String) {
        request("rest/v1/rpc/remove_family_member", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_user", memberId))
    }

    fun restoreFamilyMember(accessToken: String, familyId: String, memberId: String) {
        request("rest/v1/rpc/restore_family_member", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_user", memberId))
    }

    fun loadInvitations(accessToken: String, familyId: String): List<FamilyInvitationRecord> {
        val rows = JSONArray(request(
            "rest/v1/family_invitations?select=id,intended_role,status,expires_at&family_id=eq.$familyId&order=created_at.desc",
            "GET", accessToken
        ))
        return (0 until rows.length()).map { i -> rows.getJSONObject(i) }.map {
            FamilyInvitationRecord(it.optString("id"), it.optString("intended_role"), it.optString("status"), it.optString("expires_at"))
        }
    }

    fun createInvitation(accessToken: String, familyId: String, role: String, days: Int): CreatedInvitation {
        require(role in setOf("EDITOR", "VIEWER")) { "Invitations can grant editor or viewer access." }
        require(days in 1..30) { "Choose an expiry between 1 and 30 days." }
        val rows = JSONArray(request(
            "rest/v1/rpc/create_family_invitation", "POST", accessToken,
            JSONObject().put("p_family", familyId).put("p_role", role).put("p_valid_for", "${days} days")
        ))
        val row = rows.optJSONObject(0) ?: throw IOException("The invitation response was incomplete.")
        return CreatedInvitation(row.getString("invitation_id"), row.getString("token"), row.getString("expires_at"))
    }

    fun revokeInvitation(accessToken: String, invitationId: String) {
        request("rest/v1/rpc/revoke_family_invitation", "POST", accessToken,
            JSONObject().put("p_invitation", invitationId))
    }

    fun loadNotifications(accessToken: String, userId: String, familyId: String): List<JSONObject> {
        val rows = JSONArray(request(
            "rest/v1/notifications?select=id,kind,content_key,content_args,read_at,created_at&user_id=eq.$userId" +
                "&family_id=eq.$familyId&order=created_at.desc&limit=100",
            "GET", accessToken
        ))
        return (0 until rows.length()).map { rows.getJSONObject(it) }
    }

    fun markNotificationRead(accessToken: String, id: String) {
        request("rest/v1/notifications?id=eq.$id", "PATCH", accessToken,
            JSONObject().put("read_at", java.time.Instant.now().toString()), "return=minimal")
    }

    fun updateProfile(accessToken: String, userId: String, displayName: String) {
        require(displayName.trim().isNotBlank()) { "Enter your name." }
        request("rest/v1/profiles?id=eq.$userId", "PATCH", accessToken,
            JSONObject().put("display_name", displayName.trim()), "return=minimal")
    }

    fun updateFamilyName(accessToken: String, familyId: String, name: String) {
        require(name.trim().isNotBlank() && name.trim().length <= 160) { "Enter a family name up to 160 characters." }
        request("rest/v1/families?id=eq.$familyId", "PATCH", accessToken,
            JSONObject().put("name", name.trim()), "return=minimal")
    }

    fun loadWeather(accessToken: String, familyId: String, latitude: Double, longitude: Double): JSONObject {
        return JSONObject(request(
            "functions/v1/farm-weather", "POST", accessToken,
            JSONObject().put("family_id", familyId).put("latitude", latitude).put("longitude", longitude)
        )).optJSONObject("data") ?: JSONObject()
    }

    fun loadFamilies(accessToken: String, userId: String): List<FamilyMembership> {
        val memberships = JSONArray(
            request(
                "rest/v1/family_members?select=family_id,role,can_view,can_add,can_edit,can_delete" +
                    "&user_id=eq.${encode(userId)}&removed_at=is.null",
                "GET",
                accessToken
            )
        )
        if (memberships.length() == 0) return emptyList()

        val membershipRows = (0 until memberships.length()).map { memberships.getJSONObject(it) }
        val ids = membershipRows.map { it.getString("family_id") }
        if (ids.any { !UUID_PATTERN.matches(it) }) {
            throw IOException("The family list contained an invalid family identifier.")
        }
        val idFilter = ids.joinToString(",")
        val families = JSONArray(
            request(
                "rest/v1/families?select=id,name,currency&id=in.($idFilter)&deleted_at=is.null",
                "GET",
                accessToken
            )
        )
        val familyById = (0 until families.length())
            .map { families.getJSONObject(it) }
            .associateBy { it.getString("id") }

        return membershipRows.mapNotNull { membership ->
            val familyId = membership.getString("family_id")
            val family = familyById[familyId] ?: return@mapNotNull null
            FamilyMembership(
                id = familyId,
                name = family.getString("name"),
                currency = family.optString("currency", "NPR").trim(),
                role = membership.getString("role"),
                canView = membership.optBoolean("can_view"),
                canAdd = membership.optBoolean("can_add"),
                canEdit = membership.optBoolean("can_edit"),
                canDelete = membership.optBoolean("can_delete")
            )
        }
    }

    fun createFamily(accessToken: String, name: String) {
        require(name.trim().isNotEmpty()) { "Enter a family name." }
        require(name.trim().length <= 160) { "Family name must be 160 characters or fewer." }
        request(
            "rest/v1/rpc/create_family",
            "POST",
            accessToken,
            JSONObject().put("p_name", name.trim()).put("p_currency", "NPR")
        )
    }

    fun acceptInvitation(accessToken: String, token: String) {
        require(token.isNotBlank()) { "Enter an invitation code." }
        request(
            "functions/v1/accept-invitation",
            "POST",
            accessToken,
            JSONObject().put("token", token.trim())
        )
    }

    fun loadFields(accessToken: String, familyId: String): FieldRecordsSnapshot {
        require(UUID_PATTERN.matches(familyId)) { "The selected family is invalid." }
        val syncError = syncPendingFields(accessToken, familyId)
        try {
            val rows = JSONArray(
                request(
                    "rest/v1/fields?select=id,local_id,name,area,area_unit,location,sync_status" +
                        "&family_id=eq.$familyId&deleted_at=is.null&order=name.asc",
                    "GET",
                    accessToken
                )
            )
            val fields = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val location = row.optJSONObject("location")
                FarmFieldRecord(
                    id = row.getString("id"),
                    localId = row.optString("local_id").takeIf(String::isNotBlank),
                    name = row.getString("name"),
                    area = if (row.isNull("area")) null else row.optDouble("area"),
                    areaUnit = row.optString("area_unit").takeIf(String::isNotBlank),
                    location = location?.optString("label").orEmpty(),
                    syncStatus = row.optString("sync_status", "SYNCED")
                )
            }
            localStore.cacheFields(familyId, fields)
            return FieldRecordsSnapshot(
                fields = localStore.cachedFields(familyId)?.fields.orEmpty(),
                isOffline = false,
                pendingCount = localStore.pendingFields(familyId).size,
                syncError = syncError
            )
        } catch (error: NetworkUnavailableException) {
            val cached = localStore.cachedFields(familyId) ?: throw error
            return FieldRecordsSnapshot(
                fields = cached.fields,
                isOffline = true,
                pendingCount = cached.pendingCount,
                syncError = syncError
            )
        }
    }

    fun loadCurrentSeason(accessToken: String, familyId: String): CropSeasonSummary? {
        require(UUID_PATTERN.matches(familyId)) { "The selected family is invalid." }
        val rows = JSONArray(
            request(
                "rest/v1/crop_seasons?select=season_name,purpose,fields(name),crops(name)" +
                    "&family_id=eq.$familyId&deleted_at=is.null&end_date=is.null" +
                    "&order=start_date.desc.nullslast&limit=1",
                "GET",
                accessToken
            )
        )
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return CropSeasonSummary(
            name = row.optString("season_name").ifBlank { "Current crop season" },
            crop = row.optJSONObject("crops")?.optString("name").orEmpty(),
            field = row.optJSONObject("fields")?.optString("name").orEmpty(),
            purpose = row.optString("purpose")
        )
    }

    fun loadCurrentYearReport(accessToken: String, familyId: String, reportYear: Int = java.time.Year.now().value): YearFarmReport? {
        require(UUID_PATTERN.matches(familyId)) { "The selected family is invalid." }
        require(reportYear in 1900..2200) { "Choose a report year from 1900 to 2200." }
        val year = reportYear
        val rows = JSONArray(
            request(
                "rest/v1/rpc/family_year_summary",
                "POST",
                accessToken,
                JSONObject().put("p_family", familyId).put("p_year", year)
            )
        )
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        fun numberOrNull(column: String): Double? =
            if (row.isNull(column)) null else row.optDouble(column).takeIf(Double::isFinite)

        return YearFarmReport(
            year = row.optInt("year", year),
            revenue = numberOrNull("revenue"),
            cost = numberOrNull("cost"),
            profit = numberOrNull("profit"),
            harvestQuantity = numberOrNull("harvest_quantity")
        )
    }

    fun askFarmAi(accessToken: String, familyId: String, question: String): FarmAiAnswer {
        require(question.isNotBlank()) { "Enter a question about your family's farm records." }
        require(question.length <= 2000) { "Keep your question under 2,000 characters." }
        val response = JSONObject(request(
            "functions/v1/ai-assistant", "POST", accessToken,
            JSONObject().put("family_id", familyId).put("question", question.trim())
        ))
        return FarmAiAnswer(
            explanation = response.optString("explanation").ifBlank { "No explanation was returned." },
            intent = response.optString("intent"),
            records = response.optJSONArray("data")?.length() ?: 0
        )
    }

    fun exportBackup(accessToken: String, familyId: String, format: String): String {
        require(format in setOf("CSV", "XLSX", "PDF", "ZIP")) { "Choose a supported export format." }
        val response = JSONObject(request(
            "functions/v1/family-backups", "POST", accessToken,
            JSONObject().put("action", "export").put("family_id", familyId).put("format", format)
        ))
        return "${response.optString("format", format)} backup created · ${response.optInt("record_count")} records"
    }

    fun loadBackups(accessToken: String, familyId: String): List<BackupRecord> {
        val rows = JSONArray(request(
            "rest/v1/backup_records?select=id,format,created_at,status&family_id=eq.$familyId&status=eq.COMPLETED&order=created_at.desc&limit=30",
            "GET", accessToken
        ))
        return (0 until rows.length()).map { index -> rows.getJSONObject(index) }.map {
            BackupRecord(it.getString("id"), it.optString("format"), it.optString("created_at"))
        }
    }

    fun backupDownloadUrl(accessToken: String, backupId: String): String {
        val response = JSONObject(request(
            "functions/v1/family-backups", "POST", accessToken,
            JSONObject().put("action", "download").put("backup_id", backupId)
        ))
        return response.getString("url")
    }

    fun previewRestore(accessToken: String, backupId: String): JSONObject = JSONObject(request(
        "functions/v1/family-imports", "POST", accessToken,
        JSONObject().put("action", "preview").put("backup_id", backupId)
    ))

    fun previewImport(accessToken: String, familyId: String, storagePath: String): JSONObject = JSONObject(request(
        "functions/v1/family-imports", "POST", accessToken,
        JSONObject().put("action", "preview").put("family_id", familyId).put("storage_path", storagePath)
    ))

    fun uploadImportZip(accessToken: String, familyId: String, uri: android.net.Uri): String {
        require(UUID_PATTERN.matches(familyId)) { "The selected family is invalid." }
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 50 * 1024 * 1024) { "Choose a ZIP backup smaller than 50 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("The selected backup file could not be opened.")
        require(bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) { "Choose a valid ZIP backup file." }
        val path = "$familyId/imports/${UUID.randomUUID()}.zip"
        val connection = URL("$baseUrl/storage/v1/object/family-backups/$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("apikey", anonKey)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/zip")
        connection.setRequestProperty("x-upsert", "false")
        connection.outputStream.use { it.write(bytes) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) throw IOException("Backup upload failed ($status): ${runCatching { JSONObject(response).optString("message") }.getOrNull().orEmpty().ifBlank { "check your connection and owner access" }}")
        return path
    }

    fun confirmRestore(accessToken: String, importId: String): JSONObject = JSONObject(request(
        "functions/v1/family-imports", "POST", accessToken,
        JSONObject().put("action", "confirm").put("import_id", importId)
    ))

    fun createField(
        accessToken: String,
        familyId: String,
        localId: String,
        name: String,
        area: Double,
        areaUnit: String,
        location: String,
        notes: String
    ): FieldSaveResult {
        require(UUID_PATTERN.matches(familyId)) { "The selected family is invalid." }
        require(UUID_PATTERN.matches(localId)) { "The field draft identifier is invalid." }
        require(name.trim().isNotEmpty()) { "Enter a field name." }
        require(area > 0) { "Enter an area greater than zero." }
        require(areaUnit.trim().isNotEmpty()) { "Choose an area unit." }

        val draft = PendingField(
            localId = localId,
            name = name.trim(),
            area = area,
            areaUnit = areaUnit.trim(),
            location = location.trim(),
            notes = notes.trim().ifBlank { null }
        )
        localStore.savePendingField(familyId, draft)
        try {
            val syncError = syncPendingFields(accessToken, familyId)
            val status = localStore.fieldSyncStatus(familyId, localId)
            return when {
                status == "SYNCED" -> FieldSaveResult(FieldSaveState.SYNCED)
                syncError != null -> FieldSaveResult(FieldSaveState.FAILED, syncError)
                else -> FieldSaveResult(FieldSaveState.PENDING)
            }
        } catch (error: NetworkUnavailableException) {
            return FieldSaveResult(FieldSaveState.PENDING, error.message)
        }
    }

    fun discardFailedField(familyId: String, localId: String) {
        localStore.discardPendingField(familyId, localId)
    }

    private fun syncPendingFields(accessToken: String, familyId: String): String? {
        var syncError: String? = null
        localStore.pendingFields(familyId).forEach { field ->
            try {
                request(
                    "rest/v1/fields",
                    "POST",
                    accessToken,
                    field.toJson(familyId),
                    "return=minimal"
                )
                localStore.markFieldSynced(familyId, field.localId, field.localId)
            } catch (error: NetworkUnavailableException) {
                return null
            } catch (error: SupabaseApiException) {
                if (error.statusCode == 409) {
                    val matchingRows = try {
                        JSONArray(
                            request(
                                "rest/v1/fields?select=id&family_id=eq.$familyId" +
                                    "&local_id=eq.${encode(field.localId)}&deleted_at=is.null",
                                "GET",
                                accessToken
                            )
                        )
                    } catch (verificationError: NetworkUnavailableException) {
                        return null
                    } catch (verificationError: SupabaseApiException) {
                        localStore.markFieldSyncFailed(familyId, field.localId)
                        syncError = verificationError.message
                        return@forEach
                    }
                    if (matchingRows.length() > 0) {
                        localStore.markFieldSynced(
                            familyId,
                            field.localId,
                            matchingRows.getJSONObject(0).getString("id")
                        )
                        return@forEach
                    }
                }
                localStore.markFieldSyncFailed(familyId, field.localId)
                syncError = error.message ?: "A pending field could not be synchronized."
            } catch (error: JSONException) {
                localStore.markFieldSyncFailed(familyId, field.localId)
                syncError = "A saved field could not be read for synchronization."
            }
        }
        return syncError
    }

    private fun request(
        path: String,
        method: String,
        accessToken: String,
        body: JSONObject? = null,
        prefer: String? = null
    ): String {
        if (baseUrl.isBlank() || anonKey.isBlank()) {
            throw IOException(
                "Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY in frontend/local.properties or the build environment."
            )
        }
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            throw IOException("Supabase URL must use HTTPS.")
        }

        val connection = (URL("$baseUrl/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw SupabaseApiException(status, errorMessage(status, raw))
            return raw
        } catch (error: SupabaseApiException) {
            throw error
        } catch (error: IOException) {
            throw NetworkUnavailableException(
                "Could not reach the family service. Check your connection and try again.",
                error
            )
        } finally {
            connection.disconnect()
        }
    }

    private class SupabaseApiException(val statusCode: Int, message: String) : IOException(message)

    private class NetworkUnavailableException(message: String, cause: Throwable) : IOException(message, cause)

    private fun errorMessage(status: Int, raw: String): String {
        if (raw.isBlank()) return friendlyStatus(status)
        val error = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            null
        }
        val nested = error?.optJSONObject("error")
        val serverMessage = nested?.optString("message")?.takeIf(String::isNotBlank)
            ?: error?.optString("message")?.takeIf(String::isNotBlank)
            ?: error?.optString("msg")?.takeIf(String::isNotBlank)
        val code = nested?.optString("code")?.takeIf(String::isNotBlank)
            ?: error?.optString("code")?.takeIf(String::isNotBlank)
        if (!code.isNullOrBlank()) Log.w("FarmFamily", "Supabase request failed: HTTP $status, code=$code")
        if (serverMessage.isNullOrBlank()) return friendlyStatus(status)
        val diagnostic = "$code $serverMessage".lowercase()
        if (diagnostic.contains(Regex("sqlstate|\\b[0-9a-f]{8}-[0-9a-f-]{27,}\\b")) ||
            diagnostic.contains("schema cache") || diagnostic.contains("could not find the function") ||
            diagnostic.contains("function public.") || diagnostic.contains("relation does not exist") ||
            diagnostic.contains("does not exist") || diagnostic.contains("column ") && diagnostic.contains(" does not exist")) {
            return friendlyStatus(status)
        }
        return when (code?.uppercase()) {
            "FAMILY_ACCESS_DENIED", "OWNER_REQUIRED", "EDITOR_REQUIRED", "ADD_PERMISSION_REQUIRED", "EDIT_PERMISSION_REQUIRED", "DELETE_PERMISSION_REQUIRED" -> "Your family role does not allow this action."
            "INVALID_INVITATION", "INVITATION_EXPIRED", "INVITATION_REVOKED", "INVITATION_ALREADY_USED" -> "This invitation is no longer valid. Ask the family owner for a new one."
            "RECORD_NOT_FOUND" -> "This record is no longer available. Refresh the list and try again."
            "VERSION_CONFLICT" -> "This record changed on another device. Review the latest version and try again."
            else -> serverMessage.takeUnless { it.matches(Regex("[A-Z0-9_:-]{4,}")) } ?: friendlyStatus(status)
        }
    }

    private fun friendlyStatus(status: Int): String = when (status) {
        401 -> "Your session has expired. Sign in again."
        403 -> "Your family role does not allow this action."
        404 -> "This feature is temporarily unavailable. Please try again later."
        400, 422 -> "Some details need attention. Review them and try again."
        409 -> "This record conflicts with a change made elsewhere."
        429 -> "Too many requests. Wait a moment and try again."
        in 500..599 -> "The family service is temporarily unavailable. Try again."
        else -> "The request could not be completed. Check the details and try again."
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-fA-F-]{36}$")
        val RECORD_TABLES = setOf(
            "fields", "crops", "crop_seasons", "expense_categories", "seed_records", "fertilizer_records",
            "labor_records", "machinery_usage", "expenses", "harvest_records", "sales", "historical_prices"
        )
        val SAFE_COLUMNS = setOf("name", "season_name", "expense_date", "harvest_date", "sold_on", "observed_on", "created_at", "work_date", "applied_on", "purchased_on", "usage_date")
    }
}
