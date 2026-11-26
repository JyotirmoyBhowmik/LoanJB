package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import kotlin.math.ceil

enum class ArticleCategory(val displayTitle: String) {
    LOANS_EMI("Loans & EMI"),
    PREPAYMENT("Prepayment"),
    CREDIT_SCORE("Credit Score"),
    TAX_SAVING("Tax Saving"),
    MUTUAL_FUNDS_SIP("Mutual Funds & SIP"),
    DEPOSITS_SCHEMES("Deposits & Schemes"),
    INSURANCE_PLANNING("Insurance & Planning");

    companion object {
        fun fromWireName(raw: String?): ArticleCategory {
            val cleaned = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it.name.equals(cleaned, ignoreCase = true) ||
                    it.displayTitle.equals(cleaned, ignoreCase = true)
            } ?: LOANS_EMI
        }
    }
}

data class ArticleItem(
    val id: String,
    val title: String,
    val summary: String,
    val category: ArticleCategory,
    val lastReviewed: String,
    val tags: List<String> = emptyList(),
    val markdownBody: String,
    val readingTimeMinutes: Int,
    val sourceName: String = "LoanJB Editorial",
    val isFromOnlineConfig: Boolean = false,
    val isBookmarked: Boolean = false
) {
    val lastReviewedFormatted: String
        get() = DataSourceResolver.formatIsoToIndiaDate(lastReviewed)
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<Pair<Int, String>>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

sealed interface MarkdownInlineSpan {
    data class Plain(val text: String) : MarkdownInlineSpan
    data class Bold(val text: String) : MarkdownInlineSpan
    data class Italic(val text: String) : MarkdownInlineSpan
    data class Code(val text: String) : MarkdownInlineSpan
}

object ReadingTimeEstimator {
    private const val WORDS_PER_MINUTE = 200.0
    private val MARKDOWN_NOISE = Regex("[#*_`|>\\-\\[\\]()]+")
    private val WHITESPACE = Regex("\\s+")

    fun estimateMinutes(markdown: String): Int {
        val cleaned = markdown.replace(MARKDOWN_NOISE, " ").trim()
        if (cleaned.isEmpty()) return 1
        val words = cleaned.split(WHITESPACE).count { it.isNotBlank() }
        return ceil(words / WORDS_PER_MINUTE).toInt().coerceAtLeast(1)
    }
}

/**
 * Pure Kotlin native Markdown parser for offline financial articles.
 * Produces structured AST blocks rendered strictly via Jetpack Compose Text/Row/Column
 * (zero WebView, zero raw HTML evaluation).
 */
object MarkdownParser {

    private val NUMBERED_ITEM_REGEX = Regex("^(\\d+)[.)]\\s+(.*)$")
    private val TABLE_SEPARATOR_REGEX = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)+\\|?$")

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val raw = lines[i]
            val trimmed = raw.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Horizontal rule
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(MarkdownBlock.Divider)
                i++
                continue
            }

            // Headings (#, ##, ###)
            if (trimmed.startsWith("#")) {
                val hashes = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                val titleText = trimmed.drop(hashes).trim()
                if (titleText.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Heading(level = hashes, text = titleText))
                }
                i++
                continue
            }

            // Blockquote (> ...)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                blocks.add(MarkdownBlock.Quote(quoteLines.joinToString("\n")))
                continue
            }

            // Markdown table (header | separator | rows)
            if (trimmed.contains("|") && i + 1 < lines.size && TABLE_SEPARATOR_REGEX.matches(lines[i + 1].trim())) {
                val headers = parseTableRow(trimmed)
                i += 2 // skip header + separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val rowLine = lines[i].trim()
                    if (rowLine.isEmpty() || !rowLine.contains("|")) break
                    rows.add(parseTableRow(rowLine))
                    i++
                }
                if (headers.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Table(headers = headers, rows = rows))
                }
                continue
            }

            // Unordered bullet list (- or *)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ")) {
                        items.add(t.drop(2).trim())
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MarkdownBlock.BulletList(items))
                continue
            }

            // Ordered numbered list (1. ...)
            val numberedMatch = NUMBERED_ITEM_REGEX.matchEntire(trimmed)
            if (numberedMatch != null) {
                val items = mutableListOf<Pair<Int, String>>()
                while (i < lines.size) {
                    val m = NUMBERED_ITEM_REGEX.matchEntire(lines[i].trim()) ?: break
                    val num = m.groupValues[1].toIntOrNull() ?: (items.size + 1)
                    items.add(num to m.groupValues[2].trim())
                    i++
                }
                blocks.add(MarkdownBlock.NumberedList(items))
                continue
            }

            // Paragraph (collect consecutive non-special lines)
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.isEmpty() ||
                    t.startsWith("#") ||
                    t.startsWith(">") ||
                    t.startsWith("- ") ||
                    t.startsWith("* ") ||
                    t == "---" || t == "***" || t == "___" ||
                    NUMBERED_ITEM_REGEX.matches(t) ||
                    (t.contains("|") && i + 1 < lines.size && TABLE_SEPARATOR_REGEX.matches(lines[i + 1].trim()))
                ) {
                    break
                }
                paraLines.add(t)
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
            }
        }

        return blocks
    }

    fun parseInline(text: String): List<MarkdownInlineSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = mutableListOf<MarkdownInlineSpan>()
        val buffer = StringBuilder()
        var idx = 0

        fun flushPlain() {
            if (buffer.isNotEmpty()) {
                spans.add(MarkdownInlineSpan.Plain(buffer.toString()))
                buffer.clear()
            }
        }

        while (idx < text.length) {
            when {
                text.startsWith("**", idx) -> {
                    val end = text.indexOf("**", idx + 2)
                    if (end > idx + 2) {
                        flushPlain()
                        spans.add(MarkdownInlineSpan.Bold(text.substring(idx + 2, end)))
                        idx = end + 2
                    } else {
                        buffer.append(text[idx])
                        idx++
                    }
                }
                text[idx] == '`' -> {
                    val end = text.indexOf('`', idx + 1)
                    if (end > idx + 1) {
                        flushPlain()
                        spans.add(MarkdownInlineSpan.Code(text.substring(idx + 1, end)))
                        idx = end + 1
                    } else {
                        buffer.append(text[idx])
                        idx++
                    }
                }
                text[idx] == '*' -> {
                    val end = text.indexOf('*', idx + 1)
                    if (end > idx + 1) {
                        flushPlain()
                        spans.add(MarkdownInlineSpan.Italic(text.substring(idx + 1, end)))
                        idx = end + 1
                    } else {
                        buffer.append(text[idx])
                        idx++
                    }
                }
                else -> {
                    buffer.append(text[idx])
                    idx++
                }
            }
        }
        flushPlain()
        return spans
    }

    private fun parseTableRow(line: String): List<String> {
        return line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }
    }
}
