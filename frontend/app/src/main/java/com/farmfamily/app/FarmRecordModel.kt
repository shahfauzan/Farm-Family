package com.farmfamily.app

import org.json.JSONObject

internal enum class FarmInputType { TEXT, MULTILINE, NUMBER, DATE, CHOICE, BOOLEAN, RELATION }

internal data class FarmInput(
    val key: String,
    val label: String,
    val type: FarmInputType = FarmInputType.TEXT,
    val required: Boolean = false,
    val choices: List<String> = emptyList(),
    val relationTable: String? = null,
    val relationLabel: String = "name",
    val relationValue: String = "id",
    val numberMin: Double? = null,
    val numberMax: Double? = null,
    val maxLength: Int = 500
)

internal data class FarmModule(
    val table: String,
    val title: String,
    val description: String,
    val iconKey: String,
    val orderColumn: String,
    val primaryLabel: String,
    val secondaryLabel: String,
    val inputs: List<FarmInput>
)

internal object FarmModules {
    val all = listOf(
        FarmModule("fields", "Fields", "Land and field details", "field", "name", "name", "area_unit", listOf(
            FarmInput("name", "Field name", required = true, maxLength = 120),
            FarmInput("area", "Area", FarmInputType.NUMBER, numberMin = 0.0001),
            FarmInput("area_unit", "Area unit", choices = listOf("Bigha", "Kattha", "Dhur", "Acre", "Hectare", "Square metre")),
            FarmInput("location_label", "Location"),
            FarmInput("latitude", "Latitude", FarmInputType.NUMBER, numberMin = -90.0, numberMax = 90.0),
            FarmInput("longitude", "Longitude", FarmInputType.NUMBER, numberMin = -180.0, numberMax = 180.0),
            FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("crop_seasons", "Crop seasons", "Crops grown on fields by season", "season", "start_date", "season_name", "purpose", listOf(
            FarmInput("season_name", "Season name", required = true, maxLength = 120),
            FarmInput("field_id", "Field", FarmInputType.RELATION, required = true, relationTable = "fields", relationLabel = "name"),
            FarmInput("crop_id", "Crop", FarmInputType.RELATION, required = true, relationTable = "crops", relationLabel = "name"),
            FarmInput("purpose", "Purpose", FarmInputType.CHOICE, true, listOf("SALE", "HOUSEHOLD", "BOTH")),
            FarmInput("area", "Season area", FarmInputType.NUMBER, numberMin = 0.0001),
            FarmInput("area_unit", "Area unit", choices = listOf("Bigha", "Kattha", "Dhur", "Acre", "Hectare")),
            FarmInput("start_date", "Start date", FarmInputType.DATE),
            FarmInput("end_date", "End date", FarmInputType.DATE),
            FarmInput("costs_complete", "All season costs recorded", FarmInputType.BOOLEAN),
            FarmInput("revenue_complete", "All season revenue recorded", FarmInputType.BOOLEAN),
            FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("crops", "Crops", "Crop names and varieties", "crop", "name", "name", "variety", listOf(
            FarmInput("name", "Crop name", required = true, maxLength = 120),
            FarmInput("variety", "Variety", maxLength = 120),
            FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("seed_records", "Seeds", "Seed purchases and use", "seed", "purchased_on", "seed_name", "quantity", listOf(
            FarmInput("seed_name", "Seed name", required = true, maxLength = 160),
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, relationTable = "crop_seasons", relationLabel = "season_name", maxLength = 500),
            FarmInput("quantity", "Quantity", FarmInputType.NUMBER, numberMin = 0.0), FarmInput("unit", "Unit"),
            FarmInput("amount", "Amount", FarmInputType.NUMBER, numberMin = 0.0), FarmInput("currency", "Currency", choices = listOf("NPR")),
            FarmInput("purchased_on", "Purchase date", FarmInputType.DATE), FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("fertilizer_records", "Fertilizer", "Products and applications", "fertilizer", "applied_on", "product_name", "quantity", listOf(
            FarmInput("product_name", "Product name", required = true, maxLength = 160),
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, relationTable = "crop_seasons", relationLabel = "season_name"),
            FarmInput("quantity", "Quantity", FarmInputType.NUMBER, numberMin = 0.0), FarmInput("unit", "Unit"),
            FarmInput("amount", "Amount", FarmInputType.NUMBER, numberMin = 0.0), FarmInput("currency", "Currency", choices = listOf("NPR")),
            FarmInput("applied_on", "Applied on", FarmInputType.DATE), FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("labor_records", "Labor", "Work and labor costs", "labor", "work_date", "description", "amount", listOf(
            FarmInput("work_date", "Work date", FarmInputType.DATE, required = true),
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, relationTable = "crop_seasons", relationLabel = "season_name"),
            FarmInput("worker_count", "Number of workers", FarmInputType.NUMBER, numberMin = 0.0),
            FarmInput("hours", "Hours", FarmInputType.NUMBER, numberMin = 0.0),
            FarmInput("description", "Work description", maxLength = 300), FarmInput("amount", "Amount", FarmInputType.NUMBER, numberMin = 0.0),
            FarmInput("currency", "Currency", choices = listOf("NPR"))
        )),
        FarmModule("machinery_usage", "Machinery", "Equipment and service costs", "machinery", "usage_date", "service_name", "amount", listOf(
            FarmInput("service_name", "Service", required = true, maxLength = 160), FarmInput("provider", "Provider"),
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, relationTable = "crop_seasons", relationLabel = "season_name"),
            FarmInput("usage_date", "Date", FarmInputType.DATE), FarmInput("quantity", "Quantity", FarmInputType.NUMBER, numberMin = 0.0),
            FarmInput("unit", "Unit"), FarmInput("amount", "Amount", FarmInputType.NUMBER, numberMin = 0.0),
            FarmInput("currency", "Currency", choices = listOf("NPR")), FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("expenses", "Expenses", "Track farm costs", "expense", "expense_date", "description", "amount", listOf(
            FarmInput("description", "What was the expense?", required = true, maxLength = 200),
            FarmInput("category_id", "Category", FarmInputType.RELATION, relationTable = "expense_categories", relationLabel = "name"),
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, relationTable = "crop_seasons", relationLabel = "season_name"),
            FarmInput("expense_date", "Date", FarmInputType.DATE, required = true), FarmInput("amount", "Amount", FarmInputType.NUMBER, true, numberMin = 0.0),
            FarmInput("quantity", "Quantity", FarmInputType.NUMBER, numberMin = 0.0), FarmInput("unit_price", "Unit price", FarmInputType.NUMBER, numberMin = 0.0),
            FarmInput("currency", "Currency", FarmInputType.CHOICE, true, listOf("NPR")), FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("harvest_records", "Harvests", "Harvest quantities and grades", "harvest", "harvest_date", "quantity", "unit", listOf(
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, true, relationTable = "crop_seasons", relationLabel = "season_name"),
            FarmInput("harvest_date", "Harvest date", FarmInputType.DATE, true), FarmInput("quantity", "Quantity", FarmInputType.NUMBER, true, numberMin = 0.0),
            FarmInput("unit", "Unit", FarmInputType.CHOICE, true, listOf("kg", "quintal", "tonne", "basket", "piece")),
            FarmInput("grade", "Grade"), FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("sales", "Sales", "Produce sold and revenue", "sale", "sold_on", "buyer", "total_amount", listOf(
            FarmInput("crop_season_id", "Crop season", FarmInputType.RELATION, relationTable = "crop_seasons", relationLabel = "season_name"),
            FarmInput("harvest_record_id", "Harvest", FarmInputType.RELATION, relationTable = "harvest_records", relationLabel = "quantity"),
            FarmInput("sold_on", "Sale date", FarmInputType.DATE, true), FarmInput("quantity", "Quantity", FarmInputType.NUMBER, true, numberMin = 0.0),
            FarmInput("unit", "Unit", required = true), FarmInput("unit_price", "Price per unit", FarmInputType.NUMBER, true, numberMin = 0.0),
            FarmInput("total_amount", "Total amount", FarmInputType.NUMBER, true, numberMin = 0.0),
            FarmInput("currency", "Currency", FarmInputType.CHOICE, true, listOf("NPR")), FarmInput("buyer", "Buyer"), FarmInput("notes", "Notes", FarmInputType.MULTILINE)
        )),
        FarmModule("historical_prices", "Historical prices", "Local crop market prices", "price", "observed_on", "crop_name", "price", listOf(
            FarmInput("crop_name", "Crop", required = true, maxLength = 120), FarmInput("crop_id", "Crop record", FarmInputType.RELATION, relationTable = "crops", relationLabel = "name"),
            FarmInput("observed_on", "Observed on", FarmInputType.DATE, true), FarmInput("market", "Market"),
            FarmInput("quantity_unit", "Price unit", required = true), FarmInput("price", "Price", FarmInputType.NUMBER, true, numberMin = 0.0),
            FarmInput("currency", "Currency", FarmInputType.CHOICE, true, listOf("NPR")), FarmInput("source", "Source")
        )),
        FarmModule("expense_categories", "Expense categories", "Organize your farm spending", "category", "name", "name", "is_custom", listOf(
            FarmInput("name", "Category name", required = true, maxLength = 100), FarmInput("is_custom", "Custom category", FarmInputType.BOOLEAN)
        ))
    )

    fun byTable(table: String): FarmModule? = all.firstOrNull { it.table == table }
}

internal data class RecordSyncState(
    val pending: Int = 0,
    val offline: Boolean = false,
    val error: String? = null
)

internal fun JSONObject.toInputValues(module: FarmModule): Map<String, String> = buildMap {
    module.inputs.forEach { field ->
        val value = when (field.key) {
            "location_label" -> optJSONObject("location")?.optString("label")
            "latitude" -> optJSONObject("location")?.let { if (it.isNull("latitude")) null else it.optString("latitude") }
            "longitude" -> optJSONObject("location")?.let { if (it.isNull("longitude")) null else it.optString("longitude") }
            else -> if (isNull(field.key)) null else opt(field.key)?.toString()
        }
        put(field.key, value.orEmpty())
    }
}
