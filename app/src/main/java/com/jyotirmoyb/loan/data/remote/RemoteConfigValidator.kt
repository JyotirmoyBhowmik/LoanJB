package com.jyotirmoyb.loan.data.remote

import com.squareup.moshi.Moshi

/**
 * Validates remote-config JSON documents strictly against remote-config/schema.json rules
 * before accepting or caching any online payload.
 */
object RemoteConfigValidator {

    private const val MAX_PAYLOAD_BYTES = 512 * 1024 // 512 KB cap to prevent DoS
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val KEY_REGEX = Regex("^[a-z0-9._-]{2,80}$")

    fun parseAndValidate(
        rawJson: String,
        moshi: Moshi = RemoteConfigMoshi.instance
    ): Result<RemoteConfigDocument> {
        if (rawJson.isBlank()) {
            return Result.failure(IllegalArgumentException("Remote config payload is empty."))
        }
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            return Result.failure(IllegalArgumentException("Remote config payload exceeds size limit."))
        }

        val adapter = moshi.adapter(RemoteConfigDocument::class.java)
        val parsed = runCatching { adapter.fromJson(rawJson) }.getOrElse { err ->
            return Result.failure(IllegalArgumentException("Malformed JSON: ${err.message}", err))
        } ?: return Result.failure(IllegalArgumentException("Remote config document decoded to null."))

        return validateDocument(parsed)
    }

    fun validateDocument(doc: RemoteConfigDocument): Result<RemoteConfigDocument> {
        if (doc.schemaVersion < 1) {
            return Result.failure(IllegalArgumentException("Unsupported schemaVersion: ${doc.schemaVersion}"))
        }
        if (doc.dataVersion.isBlank()) {
            return Result.failure(IllegalArgumentException("dataVersion must not be blank."))
        }
        if (!ISO_DATE_REGEX.matches(doc.publishedOn)) {
            return Result.failure(IllegalArgumentException("publishedOn must be YYYY-MM-DD, got: ${doc.publishedOn}"))
        }
        if (doc.items.isEmpty()) {
            return Result.failure(IllegalArgumentException("items array must not be empty."))
        }

        val seenKeys = HashSet<String>(doc.items.size)
        for ((index, item) in doc.items.withIndex()) {
            if (!KEY_REGEX.matches(item.key)) {
                return Result.failure(
                    IllegalArgumentException("Invalid item[$index].key '${item.key}'")
                )
            }
            if (!seenKeys.add(item.key)) {
                return Result.failure(
                    IllegalArgumentException("Duplicate key '${item.key}' in config document.")
                )
            }
            if (item.unit.isBlank()) {
                return Result.failure(IllegalArgumentException("Item '${item.key}' has blank unit."))
            }
            if (!ISO_DATE_REGEX.matches(item.effectiveFrom)) {
                return Result.failure(
                    IllegalArgumentException("Item '${item.key}' has invalid effectiveFrom '${item.effectiveFrom}'.")
                )
            }
            if (item.sourceName.isBlank()) {
                return Result.failure(IllegalArgumentException("Item '${item.key}' has blank sourceName."))
            }
            if (item.sourceUrl.isNotBlank() && !item.sourceUrl.startsWith("https://")) {
                return Result.failure(
                    IllegalArgumentException("Item '${item.key}' has non-HTTPS sourceUrl '${item.sourceUrl}'.")
                )
            }
            val validLastVerified = item.lastVerified == UNVERIFIED_MARKER ||
                ISO_DATE_REGEX.matches(item.lastVerified)
            if (!validLastVerified) {
                return Result.failure(
                    IllegalArgumentException("Item '${item.key}' has invalid lastVerified '${item.lastVerified}'.")
                )
            }
            val numeric = item.value.numericValue
            if (numeric != null && (!numeric.isFinite() || numeric < 0.0)) {
                return Result.failure(
                    IllegalArgumentException("Item '${item.key}' has invalid numeric value: $numeric")
                )
            }
        }

        doc.taxYears?.let { years ->
            val fyValidation = validateTaxYears(years)
            if (fyValidation.isFailure) return Result.failure(fyValidation.exceptionOrNull()!!)
        }
        doc.gstSlabs?.let { slabs ->
            if (slabs.isEmpty() || slabs.any { !it.isFinite() || it < 0.0 || it > 100.0 }) {
                return Result.failure(IllegalArgumentException("Invalid gstSlabs values in tax config."))
            }
        }
        doc.advanceTaxThreshold?.let { threshold ->
            if (!threshold.isFinite() || threshold < 0.0) {
                return Result.failure(IllegalArgumentException("Invalid advanceTaxThreshold: $threshold"))
            }
        }
        doc.advanceTaxSchedule?.let { schedule ->
            if (schedule.isEmpty() || schedule.any { it.cumulativePercent <= 0.0 || it.cumulativePercent > 100.0 }) {
                return Result.failure(IllegalArgumentException("Invalid advanceTaxSchedule entries."))
            }
        }
        doc.apyTable?.let { rows ->
            if (rows.isEmpty() || rows.any { it.entryAge !in 18..40 || it.pension1000 <= 0.0 || it.pension5000 <= 0.0 }) {
                return Result.failure(IllegalArgumentException("Invalid apyTable entries in schemes config."))
            }
        }
        doc.pmsymTable?.let { rows ->
            if (rows.isEmpty() || rows.any { it.entryAge !in 18..40 || it.monthlyContribution <= 0.0 }) {
                return Result.failure(IllegalArgumentException("Invalid pmsymTable entries in schemes config."))
            }
        }
        doc.seniorPensionSchemes?.let { schemes ->
            if (schemes.isEmpty() || schemes.any { it.id.isBlank() || it.name.isBlank() || it.ratePercent < 0.0 || it.ratePercent > 50.0 }) {
                return Result.failure(IllegalArgumentException("Invalid seniorPensionSchemes entries in schemes config."))
            }
        }
        doc.assetAllocationMatrix?.let { matrix ->
            if (matrix.isEmpty() || matrix.any { row ->
                    val sum = row.equityPercent + row.debtPercent + row.goldPercent + row.cashLiquidPercent
                    row.ageMin < 0 || row.ageMax < row.ageMin || kotlin.math.abs(sum - 100.0) > 0.5
                }
            ) {
                return Result.failure(IllegalArgumentException("Invalid assetAllocationMatrix entries (allocations must sum to 100%)."))
            }
        }
        doc.bankFdRates?.let { banks ->
            if (banks.isEmpty() || banks.any {
                    it.bankId.isBlank() || it.bankName.isBlank() ||
                        it.generalRate < 0.0 || it.generalRate > 25.0 ||
                        it.seniorRate < 0.0 || it.seniorRate > 25.0
                }
            ) {
                return Result.failure(IllegalArgumentException("Invalid bankFdRates entries in rates config."))
            }
        }
        doc.articles?.let { list ->
            if (list.any {
                    it.id.isBlank() || it.title.isBlank() || it.category.isBlank() ||
                        !ISO_DATE_REGEX.matches(it.lastReviewed)
                }
            ) {
                return Result.failure(IllegalArgumentException("Invalid articles entries in articles config."))
            }
        }

        return Result.success(doc)
    }

    private fun validateTaxYears(years: List<TaxYearRules>): Result<Unit> {
        if (years.isEmpty()) {
            return Result.failure(IllegalArgumentException("taxYears must not be empty when present."))
        }
        val seenFy = HashSet<String>(years.size)
        for (fy in years) {
            if (fy.fyCode.isBlank() || !seenFy.add(fy.fyCode)) {
                return Result.failure(IllegalArgumentException("Invalid or duplicate fyCode '${fy.fyCode}'."))
            }
            if (!ISO_DATE_REGEX.matches(fy.effectiveFrom)) {
                return Result.failure(IllegalArgumentException("Invalid effectiveFrom '${fy.effectiveFrom}' for ${fy.fyCode}."))
            }
            if (fy.cessPercent < 0.0 || fy.cessPercent > 25.0) {
                return Result.failure(IllegalArgumentException("Invalid cessPercent ${fy.cessPercent} for ${fy.fyCode}."))
            }
            listOf(fy.newRegime, fy.oldRegime).forEach { regime ->
                if (regime.slabsGeneral.isEmpty()) {
                    return Result.failure(IllegalArgumentException("Empty slabs for ${fy.fyCode} ${regime.regimeId}."))
                }
                if (regime.slabsGeneral.any { it.ratePercent < 0.0 || it.ratePercent > 100.0 }) {
                    return Result.failure(IllegalArgumentException("Invalid slab rate in ${fy.fyCode} ${regime.regimeId}."))
                }
                if (regime.rebateIncomeLimit < 0.0 || regime.maxRebateAmount < 0.0 || regime.standardDeductionSalaried < 0.0) {
                    return Result.failure(IllegalArgumentException("Negative regime limit in ${fy.fyCode} ${regime.regimeId}."))
                }
            }
        }
        return Result.success(Unit)
    }

    /**
     * Validates a manual user override value against sensible financial bounds based on unit.
     */
    fun validateManualOverride(item: ResolvedConfigItem, rawInput: String): Result<String> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Value cannot be empty."))
        }
        if (!item.isEditableNumeric) {
            if (trimmed.length > 500) {
                return Result.failure(IllegalArgumentException("Text must be 500 characters or fewer."))
            }
            return Result.success(trimmed)
        }
        val parsed = trimmed.toDoubleOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid number."))
        if (!parsed.isFinite() || parsed < 0.0) {
            return Result.failure(IllegalArgumentException("Value must be zero or positive."))
        }
        val lowerUnit = item.unit.lowercase()
        when {
            lowerUnit.contains("%") -> {
                if (parsed > 100.0) {
                    return Result.failure(IllegalArgumentException("Percentage must be between 0% and 100%."))
                }
            }
            lowerUnit.contains("year") -> {
                if (parsed > 100.0) {
                    return Result.failure(IllegalArgumentException("Years must be between 0 and 100."))
                }
            }
            else -> {
                if (parsed > 1_000_000_000.0) {
                    return Result.failure(IllegalArgumentException("Amount exceeds supported maximum."))
                }
            }
        }
        return Result.success(ConfigScalar(trimmed, parsed).formattedNumber())
    }
}
