package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.data.security.AppLockManager
import com.jyotirmoyb.loan.data.security.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanTrackerAndSecurityTest {

    private fun sampleHomeLoanInput(): CalculationInput = CalculationInput(
        categoryId = "home_regular",
        principal = 2_800_000.0,
        annualRate = 8.5,
        tenureMonths = 240,
        startYear = 2026,
        startMonth = 4,
        emiDayOfMonth = 5
    )

    private fun sampleSavedEntity(
        id: Long = 1L,
        nickname: String = "SBI Home Loan",
        lender: String = "State Bank of India",
        categoryId: String = "home_regular",
        categoryName: String = "Home Loan",
        paidMonthsCsv: String = "",
        emiDayOfMonth: Int = 5,
        autoDebitNach: Boolean = true
    ): SavedLoanEntity = SavedLoanEntity(
        id = id,
        nickname = nickname,
        lender = lender,
        categoryId = categoryId,
        categoryName = categoryName,
        variantSummary = "Floating EBLR",
        inputJson = "{}",
        paidMonthsCsv = paidMonthsCsv,
        emiDayOfMonth = emiDayOfMonth,
        autoDebitNach = autoDebitNach
    )

    // -------------------------------------------------------------------------
    // 1. Prepayment Schedule Recomputation (Reduce Tenure & Reduce EMI)
    // -------------------------------------------------------------------------

    @Test
    fun prepaymentReduceTenure_recomputesShorterScheduleAndSavesInterest() {
        val baseInput = sampleHomeLoanInput()
        val entity = sampleSavedEntity()
        val baseline = LoanTrackerEngine.evaluateTrackedLoan(entity, baseInput)

        val afterPrepayInput = LoanTrackerEngine.applyPrepayment(
            input = baseInput,
            monthNumber = 12,
            amount = 300_000.0,
            reduceTenure = true
        )
        val recomputed = LoanTrackerEngine.evaluateTrackedLoan(entity, afterPrepayInput)

        assertTrue(
            "Expected tenure to shrink after ₹3L prepayment, baseline=${baseline.result.actualTenureMonths}, actual=${recomputed.result.actualTenureMonths}",
            recomputed.result.actualTenureMonths < baseline.result.actualTenureMonths
        )
        assertTrue("Expected monthsSaved > 0", recomputed.monthsSaved > 0)
        assertEquals(
            baseline.result.actualTenureMonths - recomputed.result.actualTenureMonths,
            recomputed.monthsSaved
        )
        assertTrue(
            "Expected total interest to drop after prepayment",
            recomputed.result.totalInterest < baseline.result.totalInterest
        )
        assertTrue(
            "Expected interestSaved > ₹5,00,000 on ₹3L early prepayment of 20yr home loan, got ${recomputed.interestSaved}",
            recomputed.interestSaved > 500_000.0
        )
        val month12Row = recomputed.result.schedule.first { it.monthNumber == 12 }
        assertEquals(300_000.0, month12Row.extraPrepayment, 0.01)
    }

    @Test
    fun prepaymentReduceEmi_recomputesLowerEmiAndSavesInterest() {
        val baseInput = sampleHomeLoanInput()
        val entity = sampleSavedEntity()
        val baseline = LoanTrackerEngine.evaluateTrackedLoan(entity, baseInput)

        val afterPrepayInput = LoanTrackerEngine.applyPrepayment(
            input = baseInput,
            monthNumber = 12,
            amount = 300_000.0,
            reduceTenure = false
        )
        val recomputed = LoanTrackerEngine.evaluateTrackedLoan(entity, afterPrepayInput)

        val emiBefore = recomputed.result.schedule.first { it.monthNumber == 12 }.emiPaid
        val emiAfter = recomputed.result.schedule.first { it.monthNumber == 13 }.emiPaid

        assertTrue(
            "Expected EMI after month 12 to drop: before=$emiBefore, after=$emiAfter",
            emiAfter < emiBefore - 1_000.0
        )
        assertEquals(
            "Tenure should remain approximately 240 months in REDUCE_EMI mode",
            baseline.result.actualTenureMonths,
            recomputed.result.actualTenureMonths
        )
        assertTrue(
            "Expected interestSaved > 0 in REDUCE_EMI mode",
            recomputed.interestSaved > 100_000.0
        )
    }

    // -------------------------------------------------------------------------
    // 2. Floating-Rate Reset Schedule Recomputation
    // -------------------------------------------------------------------------

    @Test
    fun floatingRateCut_recomputesLowerInterestAndUpdatesActiveRate() {
        val baseInput = sampleHomeLoanInput()
        val entity = sampleSavedEntity()
        val baseline = LoanTrackerEngine.evaluateTrackedLoan(entity, baseInput)

        val resetInput = LoanTrackerEngine.applyRateChange(
            input = baseInput,
            afterMonth = 12,
            newAnnualRate = 7.5
        )
        val recomputed = LoanTrackerEngine.evaluateTrackedLoan(entity, resetInput)

        assertEquals(8.5, recomputed.result.schedule.first { it.monthNumber == 12 }.activeRate, 0.001)
        assertEquals(7.5, recomputed.result.schedule.first { it.monthNumber == 13 }.activeRate, 0.001)
        assertTrue(
            "Rate cut from 8.5% to 7.5% should lower total interest",
            recomputed.result.totalInterest < baseline.result.totalInterest
        )
        assertTrue("Expected positive interestSaved on rate cut", recomputed.interestSaved > 0.0)
    }

    @Test
    fun floatingRateHike_increasesTotalInterestAndClampsInterestSavedAtZero() {
        val baseInput = sampleHomeLoanInput()
        val entity = sampleSavedEntity()
        val baseline = LoanTrackerEngine.evaluateTrackedLoan(entity, baseInput)

        val hikeInput = LoanTrackerEngine.applyRateChange(
            input = baseInput,
            afterMonth = 12,
            newAnnualRate = 9.75
        )
        val recomputed = LoanTrackerEngine.evaluateTrackedLoan(entity, hikeInput)

        assertEquals(9.75, recomputed.result.schedule.first { it.monthNumber == 13 }.activeRate, 0.001)
        assertTrue(
            "Rate hike from 8.5% to 9.75% should increase total interest",
            recomputed.result.totalInterest > baseline.result.totalInterest
        )
        assertEquals("interestSaved must not be negative on rate hike", 0.0, recomputed.interestSaved, 0.001)
    }

    @Test
    fun sequentialFloatingRateResets_applyExactRateTransitionsInOrder() {
        val baseInput = sampleHomeLoanInput()
        val entity = sampleSavedEntity()

        val afterFirst = LoanTrackerEngine.applyRateChange(baseInput, afterMonth = 12, newAnnualRate = 9.0)
        val afterSecond = LoanTrackerEngine.applyRateChange(afterFirst, afterMonth = 24, newAnnualRate = 7.75)
        val recomputed = LoanTrackerEngine.evaluateTrackedLoan(entity, afterSecond)

        assertEquals(8.5, recomputed.result.schedule.first { it.monthNumber == 12 }.activeRate, 0.001)
        assertEquals(9.0, recomputed.result.schedule.first { it.monthNumber == 13 }.activeRate, 0.001)
        assertEquals(9.0, recomputed.result.schedule.first { it.monthNumber == 24 }.activeRate, 0.001)
        assertEquals(7.75, recomputed.result.schedule.first { it.monthNumber == 25 }.activeRate, 0.001)
    }

    // -------------------------------------------------------------------------
    // 3. Paid EMIs & Portfolio Metrics Aggregation
    // -------------------------------------------------------------------------

    @Test
    fun markingEmisPaid_advancesNextDueDateAndUpdatesPortfolioMetrics() {
        val homeInput = LoanTrackerEngine.applyPrepayment(
            sampleHomeLoanInput(),
            monthNumber = 6,
            amount = 200_000.0,
            reduceTenure = true
        )
        val homeEntity = sampleSavedEntity(
            id = 1L,
            nickname = "HDFC Home",
            paidMonthsCsv = "1,2,3",
            emiDayOfMonth = 10,
            autoDebitNach = true
        )
        val carInput = CalculationInput(
            categoryId = "auto_car",
            principal = 800_000.0,
            annualRate = 9.25,
            tenureMonths = 60,
            startYear = 2026,
            startMonth = 4,
            emiDayOfMonth = 5
        )
        val carEntity = sampleSavedEntity(
            id = 2L,
            nickname = "Creta Car Loan",
            lender = "ICICI Bank",
            categoryId = "auto_car",
            categoryName = "Car Loan",
            paidMonthsCsv = "1",
            emiDayOfMonth = 5,
            autoDebitNach = false
        )

        val homeSummary = LoanTrackerEngine.evaluateTrackedLoan(homeEntity, homeInput)
        val carSummary = LoanTrackerEngine.evaluateTrackedLoan(carEntity, carInput)

        assertEquals(4, homeSummary.nextDueMonthNumber)
        assertEquals(2, carSummary.nextDueMonthNumber)
        assertTrue(homeSummary.outstandingPrincipal < homeInput.principal)
        assertTrue(homeSummary.totalPaidSoFar > 0.0)

        val metrics = LoanTrackerEngine.computePortfolioMetrics(
            loans = listOf(homeSummary, carSummary),
            monthlyIncome = 150_000.0
        )

        assertEquals(2, metrics.activeLoanCount)
        assertEquals(
            LoanCalculationEngine.roundPaisa(homeSummary.outstandingPrincipal + carSummary.outstandingPrincipal),
            metrics.totalOutstandingDebt,
            0.05
        )
        assertTrue("DTI should be positive", metrics.dtiPercent > 0.0)
        assertTrue("Portfolio totalInterestSaved should aggregate home savings", metrics.totalInterestSaved > 0.0)
        assertTrue("Portfolio totalMonthsSaved should aggregate home tenure reduction", metrics.totalMonthsSaved > 0)
    }

    // -------------------------------------------------------------------------
    // 4. Universal India Loan Closure Checklist + Vehicle Form 35
    // -------------------------------------------------------------------------

    @Test
    fun closureChecklist_homeLoanIncludesNocLienCibilNachWithoutForm35() {
        val checklist = LoanTrackerEngine.buildClosureChecklist(
            categoryId = "home_regular",
            categoryName = "Home Loan"
        )
        val joined = checklist.joinToString(" | ").lowercase()
        assertTrue(joined.contains("noc"))
        assertTrue(joined.contains("lien") || joined.contains("cersai"))
        assertTrue(joined.contains("cibil"))
        assertTrue(joined.contains("nach"))
        assertFalse("Home loan should not include RTO Form 35", joined.contains("form 35"))
    }

    @Test
    fun closureChecklist_vehicleLoanIncludesForm35AndParivahanHypothecationRemoval() {
        val checklist = LoanTrackerEngine.buildClosureChecklist(
            categoryId = "auto_car",
            categoryName = "New Car Loan"
        )
        val joined = checklist.joinToString(" | ").lowercase()
        assertTrue(joined.contains("noc"))
        assertTrue(joined.contains("cibil"))
        assertTrue(joined.contains("form 35"))
        assertTrue(joined.contains("parivahan"))
    }

    // -------------------------------------------------------------------------
    // 5. Next EMI Widget Selection
    // -------------------------------------------------------------------------

    @Test
    fun nextEmiWidgetData_selectsChronologicallySoonestActiveLoan() {
        val laterLoan = LoanTrackerEngine.evaluateTrackedLoan(
            sampleSavedEntity(
                id = 10L,
                nickname = "SBI Home",
                lender = "SBI",
                paidMonthsCsv = "1,2,3", // next due: month 4 (Jul 2026)
                emiDayOfMonth = 15,
                autoDebitNach = true
            ),
            sampleHomeLoanInput()
        )
        val soonerLoan = LoanTrackerEngine.evaluateTrackedLoan(
            sampleSavedEntity(
                id = 20L,
                nickname = "Axis Car",
                lender = "Axis Bank",
                categoryId = "auto_car",
                categoryName = "Car Loan",
                paidMonthsCsv = "1", // next due: month 2 (May 2026)
                emiDayOfMonth = 7,
                autoDebitNach = false
            ),
            CalculationInput(
                categoryId = "auto_car",
                principal = 600_000.0,
                annualRate = 9.0,
                tenureMonths = 60,
                startYear = 2026,
                startMonth = 4,
                emiDayOfMonth = 7
            )
        )

        val widget = LoanTrackerEngine.selectNextEmiWidgetData(
            loans = listOf(laterLoan, soonerLoan),
            currency = CurrencyCode.INR
        )

        assertTrue(widget.hasActiveLoan)
        assertEquals(20L, widget.loanId)
        assertEquals("Axis Car", widget.nickname)
        assertEquals("Axis Bank", widget.lender)
        assertEquals(7, widget.emiDayOfMonth)
        assertFalse(widget.autoDebitNach)
        assertEquals(2, widget.activeLoanCount)
        assertTrue(widget.emiAmountFormatted.startsWith("₹"))
    }

    @Test
    fun nextEmiWidgetData_returnsInactiveWhenNoUnpaidLoansExist() {
        val shortInput = CalculationInput(
            categoryId = "personal",
            principal = 10_000.0,
            annualRate = 12.0,
            tenureMonths = 2,
            startYear = 2026,
            startMonth = 1,
            emiDayOfMonth = 5
        )
        val fullyPaid = LoanTrackerEngine.evaluateTrackedLoan(
            sampleSavedEntity(paidMonthsCsv = "1,2"),
            shortInput
        )
        assertNull(fullyPaid.nextDueMonthNumber)

        val widget = LoanTrackerEngine.selectNextEmiWidgetData(listOf(fullyPaid), CurrencyCode.INR)
        assertFalse(widget.hasActiveLoan)
    }

    // -------------------------------------------------------------------------
    // 6. AES-256-GCM Backup Encryption & Integrity Verification
    // -------------------------------------------------------------------------

    @Test
    fun backupCrypto_encryptDecryptRoundTripPreservesUtf8AndHindiText() {
        val sampleJson = """{"version":2,"note":"होम लोन ₹28,00,000 SBI NACH"}"""
        val password = "StrongPassword#2026"

        val encrypted = BackupCrypto.encryptJson(sampleJson, password).getOrThrow()
        assertTrue(BackupCrypto.isEncryptedEnvelope(encrypted))
        assertFalse("Plaintext must not leak into encrypted envelope", encrypted.contains("28,00,000"))

        val decrypted = BackupCrypto.decryptJson(encrypted, password).getOrThrow()
        assertEquals(sampleJson, decrypted)
    }

    @Test
    fun backupCrypto_usesUniqueSaltAndIvPerEncryption() {
        val sampleJson = """{"version":2,"loans":[]}"""
        val password = "MyVaultPassword99"

        val enc1 = BackupCrypto.encryptJson(sampleJson, password).getOrThrow()
        val enc2 = BackupCrypto.encryptJson(sampleJson, password).getOrThrow()

        assertNotEquals("Each backup export must produce fresh random salt & IV", enc1, enc2)
        assertEquals(sampleJson, BackupCrypto.decryptJson(enc1, password).getOrThrow())
        assertEquals(sampleJson, BackupCrypto.decryptJson(enc2, password).getOrThrow())
    }

    @Test
    fun backupCrypto_wrongPasswordAndTamperedCiphertextFailAuthentication() {
        val sampleJson = """{"version":2,"loans":[{"nickname":"HDFC"}]}"""
        val encrypted = BackupCrypto.encryptJson(sampleJson, "CorrectPass123").getOrThrow()

        // Wrong password must fail
        val wrongPassResult = BackupCrypto.decryptJson(encrypted, "WrongPass123")
        assertTrue("Decryption with wrong password must fail", wrongPassResult.isFailure)

        // Tamper a character in ciphertextB64 -> GCM authentication tag must reject
        val marker = "\"ciphertextB64\": \""
        val idx = encrypted.indexOf(marker)
        assertTrue(idx >= 0)
        val charPos = idx + marker.length + 2
        val originalChar = encrypted[charPos]
        val flippedChar = if (originalChar == 'A') 'B' else 'A'
        val tampered = encrypted.substring(0, charPos) + flippedChar + encrypted.substring(charPos + 1)

        val tamperedResult = BackupCrypto.decryptJson(tampered, "CorrectPass123")
        assertTrue("Tampered GCM ciphertext must fail authentication", tamperedResult.isFailure)

        // Short password must fail on encryption
        assertTrue(BackupCrypto.encryptJson(sampleJson, "123").isFailure)
        assertFalse(BackupCrypto.isEncryptedEnvelope(sampleJson))
    }

    // -------------------------------------------------------------------------
    // 7. Salted PBKDF2 App Lock PIN Verification
    // -------------------------------------------------------------------------

    @Test
    fun appLockPin_saltedHashVerifiesValidPinAndRejectsWrongOrMalformedPins() {
        val (salt1, hash1) = AppLockManager.createPinRecord("2580")
        val (salt2, hash2) = AppLockManager.createPinRecord("2580")

        assertNotEquals("Salts must be random per PIN setup", salt1, salt2)
        assertNotEquals("Salted hashes for same PIN must differ", hash1, hash2)

        assertTrue(AppLockManager.verifyPinHash("2580", salt1, hash1))
        assertTrue(AppLockManager.verifyPinHash("2580", salt2, hash2))
        assertFalse(AppLockManager.verifyPinHash("2581", salt1, hash1))
        assertFalse(AppLockManager.verifyPinHash("", salt1, hash1))

        assertTrue(AppLockManager.isValidPinFormat("1234"))
        assertTrue(AppLockManager.isValidPinFormat("123456"))
        assertFalse(AppLockManager.isValidPinFormat("123"))
        assertFalse(AppLockManager.isValidPinFormat("1234567"))
        assertFalse(AppLockManager.isValidPinFormat("12ab"))
    }
}
