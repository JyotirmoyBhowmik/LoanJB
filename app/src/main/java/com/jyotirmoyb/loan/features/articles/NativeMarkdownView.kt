package com.jyotirmoyb.loan.features.articles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.MarkdownBlock
import com.jyotirmoyb.loan.domain.MarkdownInlineSpan
import com.jyotirmoyb.loan.domain.MarkdownParser
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
fun NativeMarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    }
                    val color = if (block.level == 1) MaterialTheme.colorScheme.onSurface else TealPrimaryDark
                    Text(
                        text = buildInlineAnnotatedString(block.text, codeBg, codeFg),
                        style = style,
                        color = color,
                        modifier = Modifier.padding(top = if (block.level == 1) 4.dp else 8.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInlineAnnotatedString(block.text, codeBg, codeFg),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        block.items.forEach { itemText ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.ui_txt_fmt_53fe08),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimaryDark,
                                    modifier = Modifier.width(18.dp)
                                )
                                Text(
                                    text = buildInlineAnnotatedString(itemText, codeBg, codeFg),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        block.items.forEach { (num, itemText) ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.ui_txt_fmt_63a046, num),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimaryDark,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = buildInlineAnnotatedString(itemText, codeBg, codeFg),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Quote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                                    .background(TealPrimaryDark)
                            )
                            Text(
                                text = buildInlineAnnotatedString(block.text, codeBg, codeFg),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 20.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                is MarkdownBlock.Table -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                block.headers.forEach { h ->
                                    Text(
                                        text = h,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.widthIn(min = 96.dp, max = 180.dp)
                                    )
                                }
                            }
                            block.rows.forEachIndexed { idx, row ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    block.headers.indices.forEach { colIdx ->
                                        val cell = row.getOrElse(colIdx) { "" }
                                        Text(
                                            text = buildInlineAnnotatedString(cell, codeBg, codeFg),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.widthIn(min = 96.dp, max = 180.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MarkdownBlock.Divider -> {
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun buildInlineAnnotatedString(
    raw: String,
    codeBg: androidx.compose.ui.graphics.Color,
    codeFg: androidx.compose.ui.graphics.Color
): AnnotatedString {
    val spans = MarkdownParser.parseInline(raw)
    return buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is MarkdownInlineSpan.Plain -> append(span.text)
                is MarkdownInlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(span.text)
                }
                is MarkdownInlineSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(span.text)
                }
                is MarkdownInlineSpan.Code -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeFg
                    )
                ) {
                    append(" ${span.text} ")
                }
            }
        }
    }
}
