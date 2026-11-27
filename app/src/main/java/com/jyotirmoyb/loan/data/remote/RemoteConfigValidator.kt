package com.jyotirmoyb.loan.data.remote

import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Validates remote-config JSON documents and user-imported update bundles strictly
 * before accepting or caching any external payload.
 */
object RemoteConfigValidator {

    private const val MAX_PAYLOAD_BYTES = 512 * 1024 // 512 KB cap for single remote-config file
    const val MAX_UPDATE_BUNDLE_BYTES = 1024 * 1024 // 1 MB cap for update bundles
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val KEY_REGEX = Regex("^[a-z0-9._-]{2,80}$")

    fun isValidIsoCalendarDate(value: String): Boolean {
        if (!ISO_DATE_REGEX.matches(value)) return false
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = sdf.parse(value) ?: return false
            sdf.format(parsed) == value
        }.getOrDefault(false)
    }

    fun isValidHttpsUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) return false
        val rest = trimmed.removePrefix("https://")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotBlank() && host.contains('.') && !host.any { it.isWhitespace() }
    }

    fun parseAndValidate(rawJson: String, moshi: Moshi = RemoteConfigMoshi.instance): Result<RemoteConfigDocument> {
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
        if (!isValidIsoCalendarDate(doc.publishedOn)) {
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
            if (!isValidIsoCalendarDate(item.effectiveFrom)) {
                return Result.failure(
                    IllegalArgumentException("Item '${item.key}' has invalid effectiveFrom '${item.effectiveFrom}'.")
                )
            }
            if (item.sourceName.isBlank()) {
                return Result.failure(IllegalArgumentException("Item '${item.key}' has blank sourceName."))
            }
            if (item.sourceUrl.isNotBlank() && !isValidHttpsUrl(item.sourceUrl)) {
                return Result.failure(
                    IllegalArgumentException("Item '${item.key}' has non-HTTPS sourceUrl '${item.sourceUrl}'.")
                )
            }
            val validLastVerified = item.lastVerified == UNVERIFIED_MARKER ||
                isValidIsoCalendarDate(item.lastVerified)
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
            val res = validateGstSlabs(slabs)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
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
            val res = validateApyTable(rows)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        doc.pmsymTable?.let { rows ->
            val res = validatePmsymTable(rows)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        doc.seniorPensionSchemes?.let { schemes ->
            val res = validateSeniorPensionSchemes(schemes)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        doc.assetAllocationMatrix?.let { matrix ->
            if (matrix.isEmpty() ||
                matrix.any { row ->
                    val sum = row.equityPercent + row.debtPercent + row.goldPercent + row.cashLiquidPercent
                    row.ageMin < 0 || row.ageMax < row.ageMin || kotlin.math.abs(sum - 100.0) > 0.5
                }
            ) {
                return Result.failure(IllegalArgumentException("Invalid assetAllocationMatrix entries (allocations must sum to 100%)."))
            }
        }
        doc.bankFdRates?.let { banks ->
            val res = validateBankFdRates(banks)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        doc.articles?.let { list ->
            if (list.any {
                    it.id.isBlank() ||
                        it.title.isBlank() ||
                        it.category.isBlank() ||
                        !isValidIsoCalendarDate(it.lastReviewed)
                }
            ) {
                return Result.failure(IllegalArgumentException("Invalid articles entries in articles config."))
            }
        }

        return Result.success(doc)
    }

    /**
     * Strictly validates a user-imported LoanJB update bundle (max 1 MB):
     * - Rejects malformed JSON, oversized payloads (> 1 MB), unsupported bundleVersion.
     * - Rejects unknown keys, non-numeric values for numeric keys, unit mismatches,
     *   non-HTTPS sourceUrl, lastVerified != YYYY-MM-DD or still "UNVERIFIED",
     *   invalid effectiveFrom dates, and malformed taxYears/tables.
     */
    fun parseAndValidateUpdateBundle(
        rawJson: String,
        knownSeedItems: Map<String, RemoteConfigItem>,
        moshi: Moshi = RemoteConfigMoshi.instance
    ): Result<LoanJbUpdateBundle> {
        if (rawJson.isBlank()) {
            return Result.failure(IllegalArgumentException("Update bundle file is empty."))
        }
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_UPDATE_BUNDLE_BYTES) {
            return Result.failure(IllegalArgumentException("Update bundle exceeds the 1 MB file size limit."))
        }

        val adapter = moshi.adapter(LoanJbUpdateBundle::class.java)
        val bundle = runCatching { adapter.fromJson(rawJson) }.getOrElse { err ->
            return Result.failure(IllegalArgumentException("Malformed JSON: ${err.message}", err))
        } ?: return Result.failure(IllegalArgumentException("Update bundle decoded to null."))

        return validateUpdateBundle(bundle, knownSeedItems)
    }

    fun validateUpdateBundle(bundle: LoanJbUpdateBundle, knownSeedItems: Map<String, RemoteConfigItem>): Result<LoanJbUpdateBundle> {
        if (bundle.bundleVersion != 1) {
            return Result.failure(IllegalArgumentException("Unsupported bundleVersion: ${bundle.bundleVersion}"))
        }
        if (bundle.generatedOn.isNotBlank() && !isValidIsoCalendarDate(bundle.generatedOn)) {
            return Result.failure(IllegalArgumentException("Invalid generatedOn date '${bundle.generatedOn}'. Expected YYYY-MM-DD."))
        }

        val seenKeys = HashSet<String>()
        val sections = listOf(
            "rates" to bundle.rates,
            "taxslabs" to bundle.taxslabs,
            "schemes" to bundle.schemes
        )

        for ((sectionName, section) in sections) {
            if (section == null) continue
            for (item in section.items.orEmpty()) {
                val seedItem = knownSeedItems[item.key]
                    ?: return Result.failure(
                        IllegalArgumentException("Unknown key '${item.key}' in $sectionName section.")
                    )
                if (!seenKeys.add(item.key)) {
                    return Result.failure(
                        IllegalArgumentException("Duplicate key '${item.key}' in update bundle.")
                    )
                }
                if (seedItem.value.isNumeric) {
                    val num = item.value.numericValue
                        ?: return Result.failure(
                            IllegalArgumentException("Key '${item.key}' expects a numeric value, got '${item.value.textValue}'.")
                        )
                    if (!num.isFinite() || num < 0.0) {
                        return Result.failure(
                            IllegalArgumentException("Key '${item.key}' has invalid numeric value: $num.")
                        )
                    }
                } else if (item.value.textValue.isBlank()) {
                    return Result.failure(
                        IllegalArgumentException("Key '${item.key}' must not be blank.")
                    )
                }
                if (item.unit.isBlank() || !item.unit.trim().equals(seedItem.unit.trim(), ignoreCase = true)) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Unit mismatch for '${item.key}': expected '${seedItem.unit}', got '${item.unit}'."
                        )
                    )
                }
                if (!isValidIsoCalendarDate(item.effectiveFrom)) {
                    return Result.failure(
                        IllegalArgumentException("Item '${item.key}' has invalid effectiveFrom '${item.effectiveFrom}'.")
                    )
                }
                if (item.sourceName.isBlank()) {
                    return Result.failure(
                        IllegalArgumentException("Item '${item.key}' has blank sourceName.")
                    )
                }
                if (!isValidHttpsUrl(item.sourceUrl)) {
                    return Result.failure(
                        IllegalArgumentException("Item '${item.key}' must have a valid https:// sourceUrl, got '${item.sourceUrl}'.")
                    )
                }
                if (item.lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true) ||
                    !isValidIsoCalendarDate(item.lastVerified)
                ) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Item '${item.key}' has invalid or UNVERIFIED lastVerified '${item.lastVerified}'. Expected YYYY-MM-DD."
                        )
                    )
                }
            }

            section.bankFdRates?.takeIf { it.isNotEmpty() }?.let { banks ->
                validateBankFdRates(banks).onFailure { return Result.failure(it) }
            }
            section.taxYears?.takeIf { it.isNotEmpty() }?.let { years ->
                validateTaxYears(years).onFailure { return Result.failure(it) }
            }
            section.gstSlabs?.takeIf { it.isNotEmpty() }?.let { slabs ->
                validateGstSlabs(slabs).onFailure { return Result.failure(it) }
            }
            section.advanceTaxThreshold?.let { threshold ->
                if (!threshold.isFinite() || threshold < 0.0) {
                    return Result.failure(IllegalArgumentException("Invalid advanceTaxThreshold: $threshold"))
                }
            }
            section.seniorPensionSchemes?.takeIf { it.isNotEmpty() }?.let { schemes ->
                validateSeniorPensionSchemes(schemes).onFailure { return Result.failure(it) }
            }
            section.apyTable?.takeIf { it.isNotEmpty() }?.let { rows ->
                validateApyTable(rows).onFailure { return Result.failure(it) }
            }
            section.pmsymTable?.takeIf { it.isNotEmpty() }?.let { rows ->
                validatePmsymTable(rows).onFailure { return Result.failure(it) }
            }
        }

        for (key in bundle.confirmedUnchanged) {
            if (!knownSeedItems.containsKey(key)) {
                return Result.failure(
                    IllegalArgumentException("Unknown key '$key' in confirmedUnchanged.")
                )
            }
        }

        return Result.success(bundle)
    }

    private fun validateGstSlabs(slabs: List<Double>): Result<Unit> {
        if (slabs.isEmpty() || slabs.any { !it.isFinite() || it < 0.0 || it > 100.0 }) {
            return Result.failure(IllegalArgumentException("Invalid gstSlabs values in tax config."))
        }
        return Result.success(Unit)
    }

    private fun validateBankFdRates(banks: List<BankFdRateSeed>): Result<Unit> {
        if (banks.isEmpty() ||
            banks.any {
                it.bankId.isBlank() ||
                    it.bankName.isBlank() ||
                    !it.generalRate.isFinite() ||
                    it.generalRate < 0.0 ||
                    it.generalRate > 25.0 ||
                    !it.seniorRate.isFinite() ||
                    it.seniorRate < 0.0 ||
                    it.seniorRate > 25.0
            }
        ) {
            return Result.failure(IllegalArgumentException("Invalid bankFdRates entries in rates config."))
        }
        return Result.success(Unit)
    }

    private fun validateApyTable(rows: List<ApyContributionRow>): Result<Unit> {
        if (rows.isEmpty() || rows.any { it.entryAge !in 18..40 || it.pension1000 <= 0.0 || it.pension5000 <= 0.0 }) {
            return Result.failure(IllegalArgumentException("Invalid apyTable entries in schemes config."))
        }
        return Result.success(Unit)
    }

    private fun validatePmsymTable(rows: List<PmsymContributionRow>): Result<Unit> {
        if (rows.isEmpty() || rows.any { it.entryAge !in 18..40 || it.monthlyContribution <= 0.0 }) {
            return Result.failure(IllegalArgumentException("Invalid pmsymTable entries in schemes config."))
        }
        return Result.success(Unit)
    }

    private fun validateSeniorPensionSchemes(schemes: List<SeniorSchemeStatusRule>): Result<Unit> {
        if (schemes.isEmpty() ||
            schemes.any {
                it.id.isBlank() ||
                    it.name.isBlank() ||
                    !it.ratePercent.isFinite() ||
                    it.ratePercent < 0.0 ||
                    it.ratePercent > 50.0
            }
        ) {
            return Result.failure(IllegalArgumentException("Invalid seniorPensionSchemes entries in schemes config."))
        }
        return Result.success(Unit)
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
            if (!isValidIsoCalendarDate(fy.effectiveFrom)) {
                return Result.failure(IllegalArgumentException("Invalid effectiveFrom '${fy.effectiveFrom}' for ${fy.fyCode}."))
            }
            if (!fy.cessPercent.isFinite() || fy.cessPercent < 0.0 || fy.cessPercent > 25.0) {
                return Result.failure(IllegalArgumentException("Invalid cessPercent ${fy.cessPercent} for ${fy.fyCode}."))
            }
            listOf(fy.newRegime, fy.oldRegime).forEach { regime ->
                validateSlabList(regime.slabsGeneral, "${fy.fyCode} ${regime.regimeId} general")
                    .onFailure { return Result.failure(it) }
                regime.slabsSenior?.let {
                    validateSlabList(it, "${fy.fyCode} ${regime.regimeId} senior")
                        .onFailure { err -> return Result.failure(err) }
                }
                regime.slabsSuperSenior?.let {
                    validateSlabList(it, "${fy.fyCode} ${regime.regimeId} superSenior")
                        .onFailure { err -> return Result.failure(err) }
                }
                if (regime.rebateIncomeLimit < 0.0 || regime.maxRebateAmount < 0.0 || regime.standardDeductionSalaried < 0.0) {
                    return Result.failure(IllegalArgumentException("Negative regime limit in ${fy.fyCode} ${regime.regimeId}."))
                }
            }
        }
        return Result.success(Unit)
    }

    private fun validateSlabList(slabs: List<TaxSlabBand>, label: String): Result<Unit> {
        if (slabs.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty slabs for $label."))
        }
        var previousUpTo = 0.0
        for ((index, band) in slabs.withIndex()) {
            if (!band.ratePercent.isFinite() || band.ratePercent < 0.0 || band.ratePercent > 100.0) {
                return Result.failure(IllegalArgumentException("Invalid slab rate ${band.ratePercent} in $label."))
            }
            val upTo = band.upTo
            if (upTo == null) {
                if (index != slabs.lastIndex) {
                    return Result.failure(
                        IllegalArgumentException("Unbounded slab (upTo = null) must be last in $label.")
                    )
                }
            } else {
                if (!upTo.isFinite() || upTo <= previousUpTo) {
                    return Result.failure(
                        IllegalArgumentException("Tax slabs must be in strictly ascending order in $label (got $upTo after $previousUpTo).")
                    )
                }
                previousUpTo = upTo
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
