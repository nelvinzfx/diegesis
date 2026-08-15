package dev.diegesis.app.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.ui.theme.DiegesisColors

/**
 * Pure Kotlin/Compose markdown renderer supporting:
 * - Headings (H1, H2, H3)
 * - Bold (**text**), Italic (*text*), Bold-Italic (***text***)
 * - Inline code (`code`)
 * - Code blocks (```)
 * - Blockquotes (>)
 * - Bullet lists (-, *)
 * - Numbered lists (1.)
 * - Horizontal rules (---)
 * - Paragraph spacing for prose (17sp, line height 1.65)
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 28.05.sp, // 1.65 line height
        color = DiegesisColors.Text
    )
) {
    Column(modifier = modifier) {
        val blocks = parseMarkdownBlocks(markdown)
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> baseStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
                        2 -> baseStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
                        else -> baseStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, baseStyle),
                        style = style,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, baseStyle),
                        style = baseStyle,
                        modifier = Modifier.padding(bottom = if (index < blocks.lastIndex) 12.dp else 0.dp)
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < blocks.lastIndex) 12.dp else 0.dp)
                            .background(DiegesisColors.Surface2)
                            .border(1.dp, DiegesisColors.Border)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = block.code,
                            style = baseStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            color = DiegesisColors.Text
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < blocks.lastIndex) 12.dp else 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(DiegesisColors.Amber)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, baseStyle),
                            style = baseStyle.copy(color = DiegesisColors.TextDim),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.BulletList -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < blocks.lastIndex) 12.dp else 0.dp)
                    ) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = "• ",
                                    style = baseStyle,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = parseInlineMarkdown(item, baseStyle),
                                    style = baseStyle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.NumberedList -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < blocks.lastIndex) 12.dp else 0.dp)
                    ) {
                        block.items.forEachIndexed { idx, item ->
                            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = "${idx + 1}. ",
                                    style = baseStyle,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = parseInlineMarkdown(item, baseStyle),
                                    style = baseStyle,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        thickness = 1.dp,
                        color = DiegesisColors.Border
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            // Code block
            line.trimStart().startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                i++ // skip opening ```
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n")))
                i++ // skip closing ```
            }
            // Horizontal rule
            line.trim().let { it == "---" || it == "***" || it == "___" } -> {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
            }
            // Heading
            line.trimStart().startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                val text = line.dropWhile { it == '#' }.trim()
                blocks.add(MarkdownBlock.Heading(level, text))
                i++
            }
            // Blockquote
            line.trimStart().startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoteLines.add(lines[i].trimStart().removePrefix(">").trim())
                    i++
                }
                blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString(" ")))
            }
            // Bullet list
            line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().let { it.startsWith("- ") || it.startsWith("* ") }) {
                    items.add(lines[i].trimStart().drop(2).trim())
                    i++
                }
                blocks.add(MarkdownBlock.BulletList(items))
            }
            // Numbered list
            line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    items.add(lines[i].trimStart().replaceFirst(Regex("^\\d+\\.\\s"), ""))
                    i++
                }
                blocks.add(MarkdownBlock.NumberedList(items))
            }
            // Blank line
            line.isBlank() -> {
                i++
            }
            // Paragraph
            else -> {
                val paraLines = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() && 
                       !lines[i].trimStart().startsWith("#") &&
                       !lines[i].trimStart().startsWith(">") &&
                       !lines[i].trimStart().let { it.startsWith("- ") || it.startsWith("* ") } &&
                       !lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*")) &&
                       !lines[i].trimStart().startsWith("```")) {
                    paraLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
            }
        }
    }

    return blocks
}

fun parseInlineMarkdown(text: String, baseStyle: TextStyle) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // Inline code: `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    val code = text.substring(i + 1, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = DiegesisColors.Surface2,
                            fontSize = 14.sp
                        )
                    ) {
                        append(" $code ")
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Bold-Italic: ***text***
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end != -1) {
                    val content = text.substring(i + 3, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                    i = end + 3
                } else {
                    append(text[i])
                    i++
                }
            }
            // Bold: **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val content = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic: *text*
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    val content = text.substring(i + 1, end)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
