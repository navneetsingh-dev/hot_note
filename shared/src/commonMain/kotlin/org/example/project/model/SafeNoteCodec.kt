package org.example.project.model

import kotlin.math.PI

/**
 * Crash-safe, zero-dependency codec for notes, drawings, attachments, HTML escaping, and JSON.
 * Handles legacy ("DRAWING:...", "|||PAGE_BREAK|||") and extended formats gracefully without throwing
 * on malformed inputs, NaN/Infinity floats, or truncated payloads.
 */
object SafeNoteCodec {

    fun splitPages(contentBlocks: String?): List<String> {
        if (contentBlocks.isNullOrEmpty()) return listOf("")
        val parts = contentBlocks.split(PAGE_DELIMITER)
        return if (parts.isEmpty()) listOf("") else parts
    }

    fun joinPages(pages: List<String>): String {
        if (pages.isEmpty()) return ""
        return pages.joinToString(PAGE_DELIMITER)
    }

    fun isDrawingPage(pageContent: String?): Boolean {
        if (pageContent == null) return false
        return pageContent.startsWith(EXTENDED_DRAWING_PREFIX) ||
            pageContent.startsWith(LEGACY_DRAWING_PREFIX)
    }

    fun defaultStrokeWidth(tool: DrawingTool): Float = when (tool) {
        DrawingTool.PEN -> 4f
        DrawingTool.SKETCH -> 7f
        DrawingTool.BRUSH -> 18f
    }

    fun defaultColorArgb(tool: DrawingTool): Long = when (tool) {
        DrawingTool.PEN -> 0xFF1A1A1AL
        DrawingTool.SKETCH -> 0xFF4A4A4AL
        DrawingTool.BRUSH -> 0x997719AAL
    }

    fun parseDrawingString(serialized: String?): List<StrokeLine> {
        if (serialized.isNullOrBlank()) return emptyList()
        return try {
            val isV2 = serialized.startsWith(EXTENDED_DRAWING_PREFIX)
            val stripped = when {
                isV2 -> serialized.removePrefix(EXTENDED_DRAWING_PREFIX)
                serialized.startsWith(LEGACY_DRAWING_PREFIX) -> serialized.removePrefix(LEGACY_DRAWING_PREFIX)
                else -> return emptyList()
            }
            if (stripped.isBlank()) return emptyList()

            val body = if (isV2 && stripped.startsWith("PAPER=")) {
                val semiIdx = stripped.indexOf('\n')
                if (semiIdx >= 0) stripped.substring(semiIdx + 1) else ""
            } else {
                stripped
            }
            if (body.isBlank()) return emptyList()

            val lines = mutableListOf<StrokeLine>()
            for (lineStr in body.split("|")) {
                if (lineStr.isBlank()) continue
                val colonIdx = lineStr.indexOf(':')
                if (colonIdx <= 0 || colonIdx >= lineStr.length - 1) continue

                val header = lineStr.substring(0, colonIdx)
                val pointsStr = lineStr.substring(colonIdx + 1)

                var tool = DrawingTool.PEN
                var colorArgb: Long? = null
                var width: Float? = null

                // Header format:
                // Legacy: PEN
                // V2: PEN#FF112233@4.5
                val atIdx = header.indexOf('@')
                val toolAndColor = if (atIdx >= 0) {
                    width = header.substring(atIdx + 1).toFloatOrNull()?.takeIf { it.isFinite() && it > 0f && it <= 200f }
                    header.substring(0, atIdx)
                } else {
                    header
                }
                val hashIdx = toolAndColor.indexOf('#')
                val toolName = if (hashIdx >= 0) {
                    val hexStr = toolAndColor.substring(hashIdx + 1)
                    colorArgb = hexStr.toLongOrNull(16)?.let {
                        if (hexStr.length <= 6) it or 0xFF000000L else it
                    }
                    toolAndColor.substring(0, hashIdx)
                } else {
                    toolAndColor
                }
                tool = DrawingTool.entries.firstOrNull { it.name == toolName } ?: DrawingTool.PEN

                val points = pointsStr.split(";").mapNotNull { pointStr ->
                    val commaIdx = pointStr.indexOf(',')
                    if (commaIdx <= 0 || commaIdx >= pointStr.length - 1) return@mapNotNull null
                    val x = pointStr.substring(0, commaIdx).toFloatOrNull()
                    val y = pointStr.substring(commaIdx + 1).toFloatOrNull()
                    if (x != null && y != null && x.isFinite() && y.isFinite()) {
                        StrokePoint(x, y)
                    } else {
                        null
                    }
                }
                if (points.isNotEmpty()) {
                    lines.add(
                        StrokeLine(
                            points = points,
                            tool = tool,
                            colorArgb = colorArgb ?: defaultColorArgb(tool),
                            strokeWidth = width ?: defaultStrokeWidth(tool)
                        )
                    )
                }
            }
            lines
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun parsePaperStyle(serialized: String?): PaperStyle {
        if (serialized.isNullOrBlank()) return PaperStyle.RULED
        if (!serialized.startsWith(EXTENDED_DRAWING_PREFIX)) return PaperStyle.RULED
        val body = serialized.removePrefix(EXTENDED_DRAWING_PREFIX)
        if (!body.startsWith("PAPER=")) return PaperStyle.RULED
        val firstLine = body.substringBefore('\n')
        val name = firstLine.removePrefix("PAPER=").trim()
        return PaperStyle.entries.firstOrNull { it.name == name } ?: PaperStyle.RULED
    }

    fun serializeDrawing(lines: List<StrokeLine>, paperStyle: PaperStyle = PaperStyle.RULED): String {
        if (lines.isEmpty() && paperStyle == PaperStyle.RULED) return ""
        val serializedLines = lines.mapNotNull { line ->
            val validPoints = line.points.filter { it.x.isFinite() && it.y.isFinite() }
            if (validPoints.isEmpty()) return@mapNotNull null
            val coords = validPoints.joinToString(";") { "${round1(it.x)},${round1(it.y)}" }
            val colorHex = (line.colorArgb and 0xFFFFFFFFL).toString(16).padStart(8, '0').uppercase()
            val width = round1(line.strokeWidth ?: defaultStrokeWidth(line.tool))
            "${line.tool.name}#$colorHex@$width:$coords"
        }.joinToString("|")

        return "${EXTENDED_DRAWING_PREFIX}PAPER=${paperStyle.name}\n$serializedLines"
    }

    private fun round1(value: Float): Float {
        if (!value.isFinite()) return 0f
        return kotlin.math.round(value * 10f) / 10f
    }

    fun parseAttachments(json: String?): List<NoteAttachment> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val parsed = SimpleJson.parse(json) as? List<*> ?: return emptyList()
            parsed.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = map["name"]?.toString()?.takeIf { it.isNotBlank() } ?: "attachment"
                val path = map["path"]?.toString() ?: ""
                val mimeType = map["mimeType"]?.toString() ?: "application/octet-stream"
                val sizeBytes = (map["sizeBytes"] as? Number)?.toLong() ?: 0L
                NoteAttachment(id = id, name = name, path = path, mimeType = mimeType, sizeBytes = sizeBytes)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun serializeAttachments(attachments: List<NoteAttachment>): String {
        if (attachments.isEmpty()) return "[]"
        val list = attachments.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "path" to it.path,
                "mimeType" to it.mimeType,
                "sizeBytes" to it.sizeBytes
            )
        }
        return SimpleJson.stringify(list)
    }

    fun serializeNotePayload(payload: NoteTransferPayload): String {
        return SimpleJson.stringify(
            mapOf(
                "id" to payload.id,
                "title" to payload.title,
                "contentBlocks" to payload.contentBlocks,
                "attachmentsJson" to payload.attachmentsJson,
                "createdAt" to payload.createdAt,
                "updatedAt" to payload.updatedAt,
                "senderName" to payload.senderName
            )
        )
    }

    fun parseNotePayload(json: String): NoteTransferPayload? {
        return try {
            val map = SimpleJson.parse(json) as? Map<*, *> ?: return null
            val id = map["id"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val title = map["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Imported Note"
            val contentBlocks = map["contentBlocks"]?.toString() ?: ""
            val attachmentsJson = map["attachmentsJson"]?.toString() ?: "[]"
            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            val updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: createdAt
            val senderName = map["senderName"]?.toString() ?: "Peer"
            NoteTransferPayload(
                id = id,
                title = title,
                contentBlocks = contentBlocks,
                attachmentsJson = attachmentsJson,
                createdAt = createdAt,
                updatedAt = updatedAt,
                senderName = senderName
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun toMarkdown(title: String, contentBlocks: String): String {
        val pages = splitPages(contentBlocks)
        return buildString {
            append("# ").append(title.ifBlank { "Untitled Page" }).append("\n\n")
            pages.forEachIndexed { index, page ->
                if (pages.size > 1) {
                    append("## Page ").append(index + 1).append("\n\n")
                }
                if (isDrawingPage(page)) {
                    val strokes = parseDrawingString(page)
                    append("*[Handwritten Ink Canvas: ${strokes.size} stroke(s)]*\n\n")
                } else {
                    append(page.trimEnd()).append("\n\n")
                }
                if (index < pages.lastIndex) {
                    append("---\n\n")
                }
            }
        }
    }

    fun pagePreviewText(contentBlocks: String): String {
        val pages = splitPages(contentBlocks)
        val first = pages.firstOrNull().orEmpty()
        val raw = if (isDrawingPage(first)) {
            "[Ink Sketch]"
        } else {
            first.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: "Empty page"
        }
        return if (pages.size > 1) "$raw (${pages.size} pages)" else raw
    }

    fun escapeHtml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_").trim()
        return cleaned.ifBlank { "file_${PI.toInt()}" }
    }
}

/**
 * Minimal, pure-Kotlin JSON parser and serializer (KMP-safe, no external dependencies).
 */
object SimpleJson {

    fun stringify(value: Any?): String = buildString { writeValue(value, this) }

    private fun writeValue(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(value)
            is Number -> {
                val d = value.toDouble()
                if (d.isFinite()) sb.append(value) else sb.append("0")
            }
            is String -> writeString(value, sb)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(k?.toString() ?: "", sb)
                    sb.append(':')
                    writeValue(v, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeValue(item, sb)
                }
                sb.append(']')
            }
            else -> writeString(value.toString(), sb)
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    fun parse(json: String): Any? {
        val parser = Parser(json)
        val result = parser.parseValue()
        parser.skipWhitespace()
        return result
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val ch = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '$ch' at $pos")
            }
        }

        private fun parseLiteral(expected: String, result: Any?): Any? {
            if (!src.startsWith(expected, pos)) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos += expected.length
            return result
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // '{'
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                if (pos >= src.length || src[pos] != '"') {
                    throw IllegalArgumentException("Expected string key at $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (pos >= src.length || src[pos] != ':') {
                    throw IllegalArgumentException("Expected ':' at $pos")
                }
                pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
            throw IllegalArgumentException("Unterminated object")
        }

        private fun parseArray(): List<Any?> {
            pos++ // '['
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                list.add(parseValue())
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
            throw IllegalArgumentException("Unterminated array")
        }

        private fun parseString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                                val code = src.substring(pos, pos + 4).toInt(16)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Unknown escape '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val raw = src.substring(start, pos)
            return if (isFloat) {
                raw.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$raw'")
            } else {
                raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$raw'")
            }
        }
    }
}
