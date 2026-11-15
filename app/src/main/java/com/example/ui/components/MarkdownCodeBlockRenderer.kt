package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

private sealed interface MarkdownSegment {
    data class Prose(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

@Composable
fun MarkdownMessageContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseMarkdownSegments(content) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Prose -> {
                    if (segment.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = formatInlineMarkdown(segment.text),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is MarkdownSegment.CodeBlock -> {
                    CodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    isDark: Boolean
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val highlighted = remember(code, language, isDark) {
        SyntaxHighlighter.highlight(code, language, isDark)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        SelectionContainer {
            Text(
                text = highlighted,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

private fun parseMarkdownSegments(input: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val lines = input.lines()
    val proseBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCode = false
    var currentLang = ""

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                if (proseBuffer.isNotEmpty()) {
                    result += MarkdownSegment.Prose(proseBuffer.toString().trimEnd())
                    proseBuffer.clear()
                }
                inCode = true
                currentLang = trimmed.removePrefix("```").trim()
            } else {
                result += MarkdownSegment.CodeBlock(currentLang, codeBuffer.toString().trimEnd())
                codeBuffer.clear()
                inCode = false
                currentLang = ""
            }
        } else if (inCode) {
            if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
            codeBuffer.append(line)
        } else {
            if (proseBuffer.isNotEmpty()) proseBuffer.append('\n')
            proseBuffer.append(line)
        }
    }

    if (inCode && codeBuffer.isNotEmpty()) {
        result += MarkdownSegment.CodeBlock(currentLang, codeBuffer.toString())
    } else if (proseBuffer.isNotEmpty()) {
        result += MarkdownSegment.Prose(proseBuffer.toString().trimEnd())
    }
    return result
}

@Composable
private fun formatInlineMarkdown(text: String): AnnotatedString {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val inlineCodeFg = MaterialTheme.colorScheme.primary
    val headingColor = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { idx, rawLine ->
            val line = when {
                rawLine.startsWith("### ") -> rawLine.removePrefix("### ")
                rawLine.startsWith("## ") -> rawLine.removePrefix("## ")
                rawLine.startsWith("# ") -> rawLine.removePrefix("# ")
                rawLine.startsWith("- ") -> "• " + rawLine.removePrefix("- ")
                else -> rawLine
            }
            val isHeading = rawLine.startsWith("#")
            val lineStart = length

            var i = 0
            while (i < line.length) {
                when {
                    line.startsWith("**", i) -> {
                        val end = line.indexOf("**", i + 2)
                        if (end > i + 2) {
                            val startIdx = length
                            append(line.substring(i + 2, end))
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), startIdx, length)
                            i = end + 2
                        } else {
                            append(line[i])
                            i++
                        }
                    }
                    line[i] == '`' -> {
                        val end = line.indexOf('`', i + 1)
                        if (end > i + 1) {
                            val startIdx = length
                            append(line.substring(i + 1, end))
                            addStyle(
                                SpanStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 13.sp,
                                    background = inlineCodeBg,
                                    color = inlineCodeFg
                                ),
                                startIdx,
                                length
                            )
                            i = end + 1
                        } else {
                            append(line[i])
                            i++
                        }
                    }
                    line[i] == '*' && (i + 1 < line.length && line[i + 1] != ' ') -> {
                        val end = line.indexOf('*', i + 1)
                        if (end > i + 1) {
                            val startIdx = length
                            append(line.substring(i + 1, end))
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), startIdx, length)
                            i = end + 1
                        } else {
                            append(line[i])
                            i++
                        }
                    }
                    else -> {
                        append(line[i])
                        i++
                    }
                }
            }

            if (isHeading) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = headingColor, fontSize = 16.sp),
                    lineStart,
                    length
                )
            }
            if (idx < lines.lastIndex) append('\n')
        }
    }
}
