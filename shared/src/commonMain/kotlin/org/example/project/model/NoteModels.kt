package org.example.project.model

import org.example.project.database.NoteEntity

const val PAGE_DELIMITER = "|||PAGE_BREAK|||"
const val LEGACY_DRAWING_PREFIX = "DRAWING:"
const val EXTENDED_DRAWING_PREFIX = "DRAWING_V2:"

enum class DrawingTool {
    PEN, SKETCH, BRUSH
}

enum class PaperStyle {
    RULED, GRID, BLANK
}

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class StrokeLine(
    val points: List<StrokePoint>,
    val tool: DrawingTool = DrawingTool.PEN,
    val colorArgb: Long = 0xFF1A1A1AL,
    val strokeWidth: Float? = null
)

data class NoteAttachment(
    val id: String,
    val name: String,
    val path: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L
)

data class NotePageData(
    val text: String = "",
    val isDrawing: Boolean = false,
    val strokes: List<StrokeLine> = emptyList(),
    val paperStyle: PaperStyle = PaperStyle.RULED
)

data class NoteTransferPayload(
    val id: String,
    val title: String,
    val contentBlocks: String,
    val attachmentsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val senderName: String = "Peer"
)

data class NoteConflict(
    val incoming: NoteTransferPayload,
    val existing: NoteEntity
)

enum class ConflictResolution {
    KEEP_BOTH,
    REPLACE_LOCAL,
    KEEP_LOCAL
}

enum class NoteFilter {
    ALL,
    TEXT_ONLY,
    INK_ONLY,
    PINNED,
    WITH_ATTACHMENTS
}
