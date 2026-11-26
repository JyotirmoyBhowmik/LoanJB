package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.market.SecureApiKeyStore
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.RemoteConfigMoshi
import com.jyotirmoyb.loan.data.remote.RemoteConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarketAndArticlesTest {

    @Test
    fun stalenessEvaluator_respectsConfigurableHourThreshold() {
        val now = 1_760_000_000_000L
        val exact24hAgo = now - 24L * MarketStalenessEvaluator.MILLIS_PER_HOUR
        val justOver24hAgo = exact24hAgo - 1_000L

        assertFalse(MarketStalenessEvaluator.isStale(exact24hAgo, now, maxAgeHours = 24))
        assertTrue(MarketStalenessEvaluator.isStale(justOver24hAgo, now, maxAgeHours = 24))
        assertTrue(MarketStalenessEvaluator.isStale(exact24hAgo, now, maxAgeHours = 12))
        assertFalse(MarketStalenessEvaluator.isStale(justOver24hAgo, now, maxAgeHours = 48))
        assertTrue(MarketStalenessEvaluator.isStale(0L, now, maxAgeHours = 24))
    }

    @Test
    fun computeDelta_calculatesPositiveNegativeAndHandlesNull() {
        val up = MarketStalenessEvaluator.computeDelta(84.50, 84.00)
        assertNotNull(up)
        assertEquals(0.50, up!!.absoluteChange, 1e-4)
        assertEquals(0.60, up.percentChange, 0.01)

        val down = MarketStalenessEvaluator.computeDelta(6.25, 6.50)
        assertNotNull(down)
        assertEquals(-0.25, down!!.absoluteChange, 1e-4)
        assertEquals(-3.85, down.percentChange, 0.01)

        assertNull(MarketStalenessEvaluator.computeDelta(null, 6.50))
        assertNull(MarketStalenessEvaluator.computeDelta(6.25, null))
    }

    @Test
    fun selectBestQuote_enforcesLayeredPriorityAndNeverFabricatesValues() {
        val manual = MarketQuoteSample("k", 90.0, null, "INR/USD", MarketSourceType.MANUAL, "Manual", 5000L)
        val live = MarketQuoteSample("k", 85.0, null, "INR/USD", MarketSourceType.LIVE_API, "Live", 4000L)
        val online = MarketQuoteSample("k", 84.5, null, "INR/USD", MarketSourceType.ONLINE, "Remote", 3000L)
        val cached = MarketQuoteSample("k", 84.2, null, "INR/USD", MarketSourceType.LIVE_API, "Cached", 2000L)
        val builtIn = MarketQuoteSample("k", 84.0, null, "INR/USD", MarketSourceType.BUILT_IN, "Seed", 1000L)

        assertEquals(manual, MarketStalenessEvaluator.selectBestQuote(manual, live, online, cached, builtIn))
        assertEquals(live, MarketStalenessEvaluator.selectBestQuote(null, live, online, cached, builtIn))
        assertEquals(online, MarketStalenessEvaluator.selectBestQuote(null, null, online, cached, builtIn))
        assertEquals(cached, MarketStalenessEvaluator.selectBestQuote(null, null, null, cached, builtIn))
        assertEquals(builtIn, MarketStalenessEvaluator.selectBestQuote(null, null, null, null, builtIn))
        assertNull(MarketStalenessEvaluator.selectBestQuote(null, null, null, null, null))
    }

    @Test
    fun formatMetricValue_formatsIndianMarketUnitsAndNullGracefully() {
        assertEquals("Unavailable", MarketStalenessEvaluator.formatMetricValue(null, "% p.a."))
        assertEquals("6.25%", MarketStalenessEvaluator.formatMetricValue(6.25, "% p.a."))
        assertEquals("₹84.45", MarketStalenessEvaluator.formatMetricValue(84.45, "INR/USD"))
        assertTrue(MarketStalenessEvaluator.formatMetricValue(78500.0, "INR/10g").startsWith("₹"))
    }

    @Test
    fun secureApiKeyMasking_hidesPrefixAndLeavesLast4() {
        assertEquals("••••••7890", SecureApiKeyStore.maskKey("sk_live_1234567890"))
        assertEquals("••••", SecureApiKeyStore.maskKey("abc"))
    }

    @Test
    fun markdownParser_parsesHeadingsListsQuotesTablesAndInlineSpans() {
        val sample = """
            # Main Title
            ## Subtitle
            Intro with **bold**, *italic*, and `code` spans.

            - Bullet one
            - Bullet two

            1. Step one
            2. Step two

            > Important RBI note here.

            | Col A | Col B |
            | --- | --- |
            | Val 1 | Val 2 |

            ---
        """.trimIndent()

        val blocks = MarkdownParser.parse(sample)
        assertTrue(blocks[0] is MarkdownBlock.Heading && (blocks[0] as MarkdownBlock.Heading).level == 1)
        assertTrue(blocks[1] is MarkdownBlock.Heading && (blocks[1] as MarkdownBlock.Heading).level == 2)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
        assertTrue(blocks[3] is MarkdownBlock.BulletList && (blocks[3] as MarkdownBlock.BulletList).items.size == 2)
        assertTrue(blocks[4] is MarkdownBlock.NumberedList && (blocks[4] as MarkdownBlock.NumberedList).items.size == 2)
        assertTrue(blocks[5] is MarkdownBlock.Quote)
        assertTrue(blocks[6] is MarkdownBlock.Table)
        val table = blocks[6] as MarkdownBlock.Table
        assertEquals(listOf("Col A", "Col B"), table.headers)
        assertEquals(listOf(listOf("Val 1", "Val 2")), table.rows)
        assertTrue(blocks[7] is MarkdownBlock.Divider)

        val spans = MarkdownParser.parseInline("Use **EBLR** or *MCLR* via `RBI`")
        assertTrue(spans.any { it == MarkdownInlineSpan.Bold("EBLR") })
        assertTrue(spans.any { it == MarkdownInlineSpan.Italic("MCLR") })
        assertTrue(spans.any { it == MarkdownInlineSpan.Code("RBI") })
    }

    @Test
    fun readingTimeEstimator_computesReasonableMinutes() {
        assertEquals(1, ReadingTimeEstimator.estimateMinutes("Short guide."))
        val fourHundredWords = (1..420).joinToString(" ") { "finance" }
        assertEquals(3, ReadingTimeEstimator.estimateMinutes(fourHundredWords))
    }

    @Test
    fun bundledRatesJson_containsMarketBenchmarksAndEditableBankFdSeeds() {
        val ratesFile = resolveAssetsFile("remote-config/rates.json")
        assertTrue("rates.json should exist", ratesFile.exists())
        val doc = RemoteConfigValidator.parseAndValidate(ratesFile.readText(), RemoteConfigMoshi.instance)
            .getOrThrow()

        val keys = doc.items.map { it.key }.toSet()
        val requiredMarketKeys = listOf(
            ConfigKeys.RBI_REPO_RATE,
            ConfigKeys.MARKET_RBI_SDF_RATE,
            ConfigKeys.MARKET_RBI_MSF_RATE,
            ConfigKeys.MARKET_RBI_CRR,
            ConfigKeys.MARKET_GOLD_24K_10G,
            ConfigKeys.MARKET_GOLD_22K_10G,
            ConfigKeys.MARKET_SILVER_1KG,
            ConfigKeys.MARKET_USD_INR,
            ConfigKeys.MARKET_SENSEX,
            ConfigKeys.MARKET_NIFTY_50,
            ConfigKeys.MARKET_GSEC_10Y_YIELD
        )
        requiredMarketKeys.forEach { k ->
            assertTrue("Missing market key $k in rates.json", k in keys)
        }
        val fds = doc.bankFdRates
        assertNotNull("bankFdRates should be populated", fds)
        assertTrue("Expected at least 6 seed banks", fds!!.size >= 6)
    }

    @Test
    fun bundledArticlesJson_hasOver30ArticlesWithValidDatesAndExistingMarkdownAssets() {
        val articlesJsonFile = resolveAssetsFile("remote-config/articles.json")
        assertTrue("articles.json should exist", articlesJsonFile.exists())
        val doc = RemoteConfigValidator.parseAndValidate(articlesJsonFile.readText(), RemoteConfigMoshi.instance)
            .getOrThrow()

        val articles = doc.articles
        assertNotNull("articles array should be present", articles)
        assertTrue("Expected at least 30 bundled articles, found ${articles!!.size}", articles.size >= 30)

        val isoRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val seenIds = HashSet<String>()
        for (article in articles) {
            assertTrue("Duplicate article id: ${article.id}", seenIds.add(article.id))
            assertTrue("Invalid lastReviewed date on ${article.id}", isoRegex.matches(article.lastReviewed))
            assertFalse("Blank title on ${article.id}", article.title.isBlank())
            assertFalse("Blank summary on ${article.id}", article.summary.isBlank())

            val assetPath = article.assetPath
            assertNotNull("Missing assetPath on ${article.id}", assetPath)
            val mdFile = resolveAssetsFile(assetPath!!)
            assertTrue("Markdown asset missing on disk: $assetPath", mdFile.exists())
            val content = mdFile.readText()
            assertTrue("Markdown asset too short: $assetPath", content.length > 200)
            val blocks = MarkdownParser.parse(content)
            assertTrue("Parsed markdown should have multiple blocks: $assetPath", blocks.size >= 3)
        }
    }

    private fun resolveAssetsFile(relativeAssetPath: String): File {
        val candidates = listOf(
            File("src/main/assets/$relativeAssetPath"),
            File("app/src/main/assets/$relativeAssetPath")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}
