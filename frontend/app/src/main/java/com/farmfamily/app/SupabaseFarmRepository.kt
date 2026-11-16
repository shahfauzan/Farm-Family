package com.farmfamily.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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

class SupabaseFarmRepository(context: android.content.Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val localStore = FarmLocalStore(context)

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

    fun loadCurrentYearReport(accessToken: String, familyId: String): YearFarmReport? {
        require(UUID_PATTERN.matches(familyId)) { "The selected family is invalid." }
        val year = java.time.Year.now().value
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
        if (raw.isBlank()) return "Request failed ($status)."
        val error = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            null
        }
        val nested = error?.optJSONObject("error")
        val serverMessage = nested?.optString("message")?.takeIf(String::isNotBlank)
            ?: error?.optString("message")?.takeIf(String::isNotBlank)
            ?: error?.optString("msg")?.takeIf(String::isNotBlank)
        if (serverMessage?.contains(Regex("\\b(\\d{5}|SQLSTATE)\\b", RegexOption.IGNORE_CASE)) == true) {
            return friendlyStatus(status)
        }
        return serverMessage ?: friendlyStatus(status)
    }

    private fun friendlyStatus(status: Int): String = when (status) {
        401 -> "Your session has expired. Sign in again."
        403 -> "Your family role does not allow this action."
        409 -> "This record conflicts with a change made elsewhere."
        429 -> "Too many requests. Wait a moment and try again."
        in 500..599 -> "The family service is temporarily unavailable. Try again."
        else -> "The request could not be completed. Check the details and try again."
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-fA-F-]{36}$")
    }
}
