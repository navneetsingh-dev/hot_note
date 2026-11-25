package org.example.project

import org.example.project.model.DrawingTool
import org.example.project.model.NoteAttachment
import org.example.project.model.NoteTransferPayload
import org.example.project.model.PaperStyle
import org.example.project.model.SafeNoteCodec
import org.example.project.model.SimpleJson
import org.example.project.model.StrokeLine
import org.example.project.model.StrokePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeNoteCodecTest {

    @Test
    fun legacyDrawingParsesCleanly() {
        val raw = "DRAWING:PEN:10.0,20.0;30.5,40.5|BRUSH:5,6;7,8"
        val parsed = SafeNoteCodec.parseDrawingString(raw)
        assertEquals(2, parsed.size)
        assertEquals(DrawingTool.PEN, parsed[0].tool)
        assertEquals(2, parsed[0].points.size)
        assertEquals(10.0f, parsed[0].points[0].x)
        assertEquals(DrawingTool.BRUSH, parsed[1].tool)
    }

    @Test
    fun extendedDrawingRoundTripPreservesPaperColorAndWidth() {
        val strokes = listOf(
            StrokeLine(
                points = listOf(StrokePoint(12.3f, 45.6f), StrokePoint(78.9f, 101.2f)),
                tool = DrawingTool.SKETCH,
                colorArgb = 0xFF7719AAL,
                strokeWidth = 9.5f
            )
        )
        val encoded = SafeNoteCodec.serializeDrawing(strokes, PaperStyle.GRID)
        assertTrue(SafeNoteCodec.isDrawingPage(encoded))
        assertEquals(PaperStyle.GRID, SafeNoteCodec.parsePaperStyle(encoded))

        val decoded = SafeNoteCodec.parseDrawingString(encoded)
        assertEquals(1, decoded.size)
        assertEquals(DrawingTool.SKETCH, decoded[0].tool)
        assertEquals(0xFF7719AAL, decoded[0].colorArgb)
        assertEquals(9.5f, decoded[0].strokeWidth)
        assertEquals(2, decoded[0].points.size)
    }

    @Test
    fun malformedDrawingsNeverCrash() {
        val fullyCorruptCases = listOf(
            null,
            "",
            "DRAWING:",
            "DRAWING:UNKNOWN_TOOL:NaN,Infinity;abc,def|BROKEN_NO_COLON",
            "DRAWING:PEN:1.0"
        )
        for (input in fullyCorruptCases) {
            val parsed = SafeNoteCodec.parseDrawingString(input)
            assertTrue(parsed.isEmpty(), "Expected empty strokes for '$input'")
        }
        assertEquals(PaperStyle.RULED, SafeNoteCodec.parsePaperStyle("DRAWING_V2:PAPER=NONEXISTENT\n"))

        // Partially corrupt line recovers valid points and falls back safely for bad color/width/paper
        val partial = SafeNoteCodec.parseDrawingString("DRAWING_V2:PAPER=INVALID\nPEN#ZZZZ@-5:1,2;x,y")
        assertEquals(1, partial.size)
        assertEquals(1, partial[0].points.size)
        assertEquals(DrawingTool.PEN, partial[0].tool)
        assertEquals(SafeNoteCodec.defaultStrokeWidth(DrawingTool.PEN), partial[0].strokeWidth)
    }

    @Test
    fun previewAndSanitizeFileNameWorkSafely() {
        assertEquals("[Ink Sketch] (2 pages)", SafeNoteCodec.pagePreviewText("DRAWING:PEN:1,2|||PAGE_BREAK|||Hello"))
        assertEquals("Empty page", SafeNoteCodec.pagePreviewText(""))
        assertEquals("my_note_2026_.pdf", SafeNoteCodec.sanitizeFileName("my/note:2026?.pdf"))
    }

    @Test
    fun splitAndJoinPagesHandleEmptyInputs() {
        assertEquals(listOf(""), SafeNoteCodec.splitPages(null))
        assertEquals(listOf(""), SafeNoteCodec.splitPages(""))
        val pages = listOf("Page 1", "Page 2")
        assertEquals(pages, SafeNoteCodec.splitPages(SafeNoteCodec.joinPages(pages)))
    }

    @Test
    fun attachmentsAndPayloadJsonRoundTrip() {
        val attachments = listOf(
            NoteAttachment("a1", "lecture.pdf", "/tmp/lecture.pdf", "application/pdf", 2048L)
        )
        val attJson = SafeNoteCodec.serializeAttachments(attachments)
        val parsedAtts = SafeNoteCodec.parseAttachments(attJson)
        assertEquals(attachments, parsedAtts)
        assertTrue(SafeNoteCodec.parseAttachments("not valid json").isEmpty())

        val payload = NoteTransferPayload(
            id = "n_1",
            title = "Arch Linux \"Notes\" & Stuff",
            contentBlocks = "Line 1\nLine 2",
            attachmentsJson = attJson,
            createdAt = 1000L,
            updatedAt = 2000L,
            senderName = "ThinkPad"
        )
        val json = SafeNoteCodec.serializeNotePayload(payload)
        val decoded = SafeNoteCodec.parseNotePayload(json)
        assertNotNull(decoded)
        assertEquals(payload, decoded)
        assertNull(SafeNoteCodec.parseNotePayload("{bad json"))
    }

    @Test
    fun htmlEscapePreventsXssAndMarkdownExportsCleanly() {
        val escaped = SafeNoteCodec.escapeHtml("<script>alert('xss')</script> & \"test\"")
        assertFalse(escaped.contains("<script>"))
        assertTrue(escaped.contains("&lt;script&gt;"))
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&quot;"))

        val md = SafeNoteCodec.toMarkdown("Chemistry", "Notes here|||PAGE_BREAK|||DRAWING:PEN:1,2;3,4")
        assertTrue(md.contains("# Chemistry"))
        assertTrue(md.contains("## Page 1"))
        assertTrue(md.contains("Handwritten Ink Canvas: 1 stroke(s)"))
    }

    @Test
    fun simpleJsonHandlesUnicodeAndEscapes() {
        val input = mapOf("msg" to "Hello\n\"World\" \\ \u0041", "ok" to true, "count" to 42)
        val json = SimpleJson.stringify(input)
        val parsed = SimpleJson.parse(json) as Map<*, *>
        assertEquals("Hello\n\"World\" \\ A", parsed["msg"])
        assertEquals(true, parsed["ok"])
        assertEquals(42L, parsed["count"])
    }
}
