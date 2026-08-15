package dev.diegesis.app.ui.markdown

import org.junit.Test
import org.junit.Assert.*

class MarkdownParserTest {

    @Test
    fun `parseMarkdownBlocks - heading`() {
        val markdown = "# Heading 1\n## Heading 2\n### Heading 3"
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("Heading 1", (blocks[0] as MarkdownBlock.Heading).text)
        
        assertTrue(blocks[1] is MarkdownBlock.Heading)
        assertEquals(2, (blocks[1] as MarkdownBlock.Heading).level)
        assertEquals("Heading 2", (blocks[1] as MarkdownBlock.Heading).text)
        
        assertTrue(blocks[2] is MarkdownBlock.Heading)
        assertEquals(3, (blocks[2] as MarkdownBlock.Heading).level)
        assertEquals("Heading 3", (blocks[2] as MarkdownBlock.Heading).text)
    }

    @Test
    fun `parseMarkdownBlocks - paragraph`() {
        val markdown = "This is a paragraph.\nIt spans multiple lines."
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertEquals("This is a paragraph. It spans multiple lines.", (blocks[0] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun `parseMarkdownBlocks - code block`() {
        val markdown = """
            ```
            val x = 10
            fun test() {}
            ```
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.CodeBlock)
        assertEquals("val x = 10\nfun test() {}", (blocks[0] as MarkdownBlock.CodeBlock).code)
    }

    @Test
    fun `parseMarkdownBlocks - blockquote`() {
        val markdown = "> This is a quote\n> spanning two lines"
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Blockquote)
        assertEquals("This is a quote spanning two lines", (blocks[0] as MarkdownBlock.Blockquote).text)
    }

    @Test
    fun `parseMarkdownBlocks - bullet list`() {
        val markdown = """
            - Item 1
            - Item 2
            * Item 3
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.BulletList)
        val items = (blocks[0] as MarkdownBlock.BulletList).items
        assertEquals(3, items.size)
        assertEquals("Item 1", items[0])
        assertEquals("Item 2", items[1])
        assertEquals("Item 3", items[2])
    }

    @Test
    fun `parseMarkdownBlocks - numbered list`() {
        val markdown = """
            1. First item
            2. Second item
            3. Third item
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.NumberedList)
        val items = (blocks[0] as MarkdownBlock.NumberedList).items
        assertEquals(3, items.size)
        assertEquals("First item", items[0])
        assertEquals("Second item", items[1])
        assertEquals("Third item", items[2])
    }

    @Test
    fun `parseMarkdownBlocks - horizontal rule`() {
        val markdown = "---"
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.HorizontalRule)
    }

    @Test
    fun `parseMarkdownBlocks - mixed content`() {
        val markdown = """
            # Title
            
            This is a paragraph with some text.
            
            - Bullet 1
            - Bullet 2
            
            ```
            code here
            ```
            
            > A quote
            
            ---
            
            Another paragraph.
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(7, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertTrue(blocks[2] is MarkdownBlock.BulletList)
        assertTrue(blocks[3] is MarkdownBlock.CodeBlock)
        assertTrue(blocks[4] is MarkdownBlock.Blockquote)
        assertTrue(blocks[5] is MarkdownBlock.HorizontalRule)
        assertTrue(blocks[6] is MarkdownBlock.Paragraph)
    }

    @Test
    fun `parseInlineMarkdown - bold`() {
        val text = "This is **bold** text"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        // Verify the annotated string contains the text (detailed span testing requires UI framework)
        assertTrue(annotated.text.contains("bold"))
    }

    @Test
    fun `parseInlineMarkdown - italic`() {
        val text = "This is *italic* text"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        assertTrue(annotated.text.contains("italic"))
    }

    @Test
    fun `parseInlineMarkdown - bold italic`() {
        val text = "This is ***bold italic*** text"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        assertTrue(annotated.text.contains("bold italic"))
    }

    @Test
    fun `parseInlineMarkdown - inline code`() {
        val text = "This is `code` text"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        assertTrue(annotated.text.contains("code"))
    }

    @Test
    fun `parseInlineMarkdown - mixed inline formatting`() {
        val text = "**Bold** and *italic* and `code` together"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        assertTrue(annotated.text.contains("Bold"))
        assertTrue(annotated.text.contains("italic"))
        assertTrue(annotated.text.contains("code"))
    }

    @Test
    fun `parseMarkdownBlocks - empty input`() {
        val markdown = ""
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(0, blocks.size)
    }

    @Test
    fun `parseMarkdownBlocks - only blank lines`() {
        val markdown = "\n\n\n"
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(0, blocks.size)
    }

    @Test
    fun `parseMarkdownBlocks - paragraph with blank lines between`() {
        val markdown = """
            First paragraph.
            
            Second paragraph.
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertEquals("First paragraph.", (blocks[0] as MarkdownBlock.Paragraph).text)
        assertEquals("Second paragraph.", (blocks[1] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun `parseMarkdownBlocks - nested formatting in heading`() {
        val markdown = "# This is **bold** heading"
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals("This is **bold** heading", (blocks[0] as MarkdownBlock.Heading).text)
    }

    @Test
    fun `parseMarkdownBlocks - code block with language hint`() {
        val markdown = """
            ```kotlin
            val x = 10
            ```
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.CodeBlock)
        // Language hint is currently ignored, just verifying it doesn't break parsing
        assertTrue((blocks[0] as MarkdownBlock.CodeBlock).code.contains("val x = 10"))
    }

    @Test
    fun `parseInlineMarkdown - nested bold and italic`() {
        val text = "***nested formatting*** works"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        assertTrue(annotated.text.contains("nested formatting"))
    }

    @Test
    fun `parseInlineMarkdown - unclosed markdown syntax`() {
        val text = "This is **incomplete"
        val annotated = parseInlineMarkdown(text, androidx.compose.ui.text.TextStyle())
        
        // Should handle gracefully without crashing
        assertTrue(annotated.text.contains("incomplete"))
    }

    @Test
    fun `parseMarkdownBlocks - blockquote with multiple paragraphs`() {
        val markdown = """
            > First line
            > Second line
            > Third line
        """.trimIndent()
        val blocks = parseMarkdownBlocks(markdown)
        
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Blockquote)
        assertEquals("First line Second line Third line", (blocks[0] as MarkdownBlock.Blockquote).text)
    }
}
