package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.database.NoteEntity
import org.example.project.model.DrawingTool
import org.example.project.model.NoteAttachment
import org.example.project.model.PaperStyle
import org.example.project.model.SafeNoteCodec
import org.example.project.model.StrokeLine
import org.example.project.model.StrokePoint
import org.example.project.p2p.rememberFilePicker
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private enum class EditorTab {
    TYPE,
    DRAW,
    FILES
}

private val InkColorPalette = listOf(
    0xFF1A1A1AL to "Black",
    0xFF7719AAL to "Purple",
    0xFF2B579AL to "Blue",
    0xFFC50F1FL to "Red",
    0xFF217346L to "Green",
    0xFFD24726L to "Orange"
)

@OptIn(ExperimentalTime::class)
@Composable
fun OneNoteEditorScreen(
    existingNote: NoteEntity? = null,
    sectionName: String = "Quick Notes",
    sectionColorHex: String = "#7719AA",
    startInDrawMode: Boolean = false,
    onSave: (title: String, contentBlocks: String, attachmentsJson: String) -> Unit,
    onShareNote: ((NoteEntity) -> Unit)? = null,
    onCancel: () -> Unit
) {
    val noteKey = existingNote?.id ?: "new_note"
    var title by remember(noteKey) { mutableStateOf(existingNote?.title ?: "") }
    var pages by remember(noteKey) {
        mutableStateOf(
            if (existingNote == null || existingNote.contentBlocks.isEmpty()) {
                listOf("")
            } else {
                SafeNoteCodec.splitPages(existingNote.contentBlocks)
            }
        )
    }
    var attachments by remember(noteKey) {
        mutableStateOf(SafeNoteCodec.parseAttachments(existingNote?.attachmentsJson))
    }
    var currentPageIndex by remember(noteKey) { mutableStateOf(0) }
    val safePageIndex = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentPageContent = pages.getOrElse(safePageIndex) { "" }

    var activeTab by remember(noteKey, safePageIndex) {
        mutableStateOf(
            when {
                SafeNoteCodec.isDrawingPage(currentPageContent) -> EditorTab.DRAW
                startInDrawMode && currentPageContent.isBlank() -> EditorTab.DRAW
                else -> EditorTab.TYPE
            }
        )
    }

    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var isEraser by remember { mutableStateOf(false) }
    var selectedColorArgb by remember { mutableStateOf(0xFF1A1A1AL) }
    var strokeWidthOverride by remember { mutableStateOf<Float?>(null) }
    var paperStyle by remember(noteKey, safePageIndex) {
        mutableStateOf(SafeNoteCodec.parsePaperStyle(currentPageContent))
    }

    val undoStack = remember(noteKey, safePageIndex) { mutableStateListOf<List<StrokeLine>>() }
    val redoStack = remember(noteKey, safePageIndex) { mutableStateListOf<List<StrokeLine>>() }

    val pickFile = rememberFilePicker { picked ->
        val newAttachment = NoteAttachment(
            id = "att_${Clock.System.now().toEpochMilliseconds()}",
            name = picked.name,
            path = picked.uriOrPath,
            mimeType = picked.mimeType,
            sizeBytes = picked.sizeBytes
        )
        attachments = attachments + newAttachment
    }

    fun updateCurrentPage(newContent: String) {
        val mutable = pages.toMutableList()
        if (safePageIndex in mutable.indices) {
            mutable[safePageIndex] = newContent
            pages = mutable
        }
    }

    val sectionColor = parseHexColor(sectionColorHex)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top OneNote Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) {
                        Text("< Back", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(
                        color = sectionColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = sectionName,
                            color = sectionColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (existingNote != null && onShareNote != null) {
                        OutlinedButton(
                            onClick = { onShareNote(existingNote) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Share P2P", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(
                        onClick = {
                            val finalTitle = title.trim().ifBlank { "Untitled Page" }
                            val finalContent = SafeNoteCodec.joinPages(pages)
                            val finalAttachments = SafeNoteCodec.serializeAttachments(attachments)
                            onSave(finalTitle, finalContent, finalAttachments)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OneNotePurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }

            // OneNote Page Title & Timestamp Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                "Page title...",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                        innerTextField()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatNoteTimestamp(
                        existingNote?.updatedAt ?: Clock.System.now().toEpochMilliseconds()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = sectionColor.copy(alpha = 0.35f), thickness = 1.5.dp)
            }

            // Mode Tabs + Page Navigation Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        val label = when (tab) {
                            EditorTab.TYPE -> "Type"
                            EditorTab.DRAW -> "Draw"
                            EditorTab.FILES -> "Files (${attachments.size})"
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { activeTab = tab },
                            label = { Text(label) }
                        )
                    }
                }

                // Multi-page controls
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { if (safePageIndex > 0) currentPageIndex = safePageIndex - 1 },
                        enabled = safePageIndex > 0
                    ) { Text("<") }
                    Text(
                        text = "${safePageIndex + 1}/${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (safePageIndex < pages.lastIndex) {
                        TextButton(onClick = { currentPageIndex = safePageIndex + 1 }) { Text(">") }
                    } else {
                        TextButton(
                            onClick = {
                                pages = pages + ""
                                currentPageIndex = pages.lastIndex
                            }
                        ) { Text("+Page") }
                    }
                    if (pages.size > 1) {
                        TextButton(
                            onClick = {
                                val mutable = pages.toMutableList()
                                mutable.removeAt(safePageIndex)
                                pages = mutable
                                currentPageIndex = safePageIndex.coerceAtMost(mutable.lastIndex)
                            }
                        ) { Text("Del", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            // Contextual Ribbon Toolbar
            when (activeTab) {
                EditorTab.TYPE -> {
                    val textBody = if (SafeNoteCodec.isDrawingPage(currentPageContent)) "" else currentPageContent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RibbonActionChip("H1") {
                            updateCurrentPage(appendLinePrefix(textBody, "# "))
                        }
                        RibbonActionChip("H2") {
                            updateCurrentPage(appendLinePrefix(textBody, "## "))
                        }
                        RibbonActionChip("• Bullet") {
                            updateCurrentPage(appendLinePrefix(textBody, "• "))
                        }
                        RibbonActionChip("☑ To-Do") {
                            updateCurrentPage(appendLinePrefix(textBody, "- [ ] "))
                        }
                        RibbonActionChip("— Divider") {
                            val sep = if (textBody.isEmpty() || textBody.endsWith("\n")) "---\n" else "\n---\n"
                            updateCurrentPage(textBody + sep)
                        }
                    }
                }

                EditorTab.DRAW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RibbonSelectableChip("Pen", !isEraser && currentTool == DrawingTool.PEN) {
                                isEraser = false; currentTool = DrawingTool.PEN; strokeWidthOverride = null
                            }
                            RibbonSelectableChip("Sketch", !isEraser && currentTool == DrawingTool.SKETCH) {
                                isEraser = false; currentTool = DrawingTool.SKETCH; strokeWidthOverride = null
                            }
                            RibbonSelectableChip("Highlighter", !isEraser && currentTool == DrawingTool.BRUSH) {
                                isEraser = false; currentTool = DrawingTool.BRUSH; strokeWidthOverride = null
                            }
                            RibbonSelectableChip("Eraser", isEraser) {
                                isEraser = true
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Ink color dots
                            InkColorPalette.forEach { (argb, _) ->
                                val isSelected = !isEraser && selectedColorArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            isEraser = false
                                            selectedColorArgb = argb
                                        }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentStrokes = SafeNoteCodec.parseDrawingString(currentPageContent)
                            RibbonActionChip("Fine") { strokeWidthOverride = 3f }
                            RibbonActionChip("Medium") { strokeWidthOverride = 7f }
                            RibbonActionChip("Thick") { strokeWidthOverride = 16f }

                            TextButton(
                                onClick = {
                                    if (undoStack.isNotEmpty()) {
                                        redoStack.add(currentStrokes)
                                        val prev = undoStack.removeAt(undoStack.lastIndex)
                                        updateCurrentPage(SafeNoteCodec.serializeDrawing(prev, paperStyle))
                                    }
                                },
                                enabled = undoStack.isNotEmpty()
                            ) { Text("Undo") }

                            TextButton(
                                onClick = {
                                    if (redoStack.isNotEmpty()) {
                                        undoStack.add(currentStrokes)
                                        val next = redoStack.removeAt(redoStack.lastIndex)
                                        updateCurrentPage(SafeNoteCodec.serializeDrawing(next, paperStyle))
                                    }
                                },
                                enabled = redoStack.isNotEmpty()
                            ) { Text("Redo") }

                            TextButton(
                                onClick = {
                                    if (currentStrokes.isNotEmpty()) {
                                        undoStack.add(currentStrokes)
                                        redoStack.clear()
                                        updateCurrentPage(SafeNoteCodec.serializeDrawing(emptyList(), paperStyle))
                                    }
                                },
                                enabled = currentStrokes.isNotEmpty()
                            ) { Text("Clear", color = MaterialTheme.colorScheme.error) }

                            RibbonActionChip("Paper: ${paperStyle.name.lowercase().replaceFirstChar { it.uppercase() }}") {
                                val nextStyle = when (paperStyle) {
                                    PaperStyle.RULED -> PaperStyle.GRID
                                    PaperStyle.GRID -> PaperStyle.BLANK
                                    PaperStyle.BLANK -> PaperStyle.RULED
                                }
                                paperStyle = nextStyle
                                updateCurrentPage(SafeNoteCodec.serializeDrawing(currentStrokes, nextStyle))
                            }
                        }
                    }
                }

                EditorTab.FILES -> {}
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Main Page Surface
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (activeTab) {
                    EditorTab.TYPE -> {
                        val textValue = if (SafeNoteCodec.isDrawingPage(currentPageContent)) "" else currentPageContent
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            BasicTextField(
                                value = textValue,
                                onValueChange = { updateCurrentPage(it) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(520.dp),
                                decorationBox = { inner ->
                                    if (textValue.isEmpty()) {
                                        Text(
                                            "Start typing your note, or tap Draw above for ink...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                    }

                    EditorTab.DRAW -> {
                        val strokes = remember(currentPageContent) {
                            SafeNoteCodec.parseDrawingString(currentPageContent)
                        }
                        StylusCanvas(
                            modifier = Modifier.fillMaxSize(),
                            strokes = strokes,
                            selectedTool = currentTool,
                            isEraser = isEraser,
                            selectedColorArgb = selectedColorArgb,
                            strokeWidthOverride = strokeWidthOverride,
                            paperStyle = paperStyle,
                            onStrokesCommitted = { previous, updated ->
                                undoStack.add(previous)
                                redoStack.clear()
                                updateCurrentPage(SafeNoteCodec.serializeDrawing(updated, paperStyle))
                            }
                        )
                    }

                    EditorTab.FILES -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Page Attachments",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = pickFile,
                                    colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple)
                                ) {
                                    Text("+ Attach File")
                                }
                            }

                            if (attachments.isEmpty()) {
                                Text(
                                    "Attach PDFs, images, or documents to this page. They can also be shared over P2P.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                attachments.forEach { att ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(att.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${att.mimeType} • ${att.sizeBytes} bytes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            TextButton(onClick = { attachments = attachments.filterNot { it.id == att.id } }) {
                                                Text("Remove", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OneNoteViewerPane(
    note: NoteEntity,
    sectionColorHex: String = "#7719AA",
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onShareP2p: () -> Unit,
    onUpdateContent: (String) -> Unit,
    onOpenAttachment: ((NoteAttachment) -> Unit)? = null
) {
    val pages = remember(note.id, note.contentBlocks) { SafeNoteCodec.splitPages(note.contentBlocks) }
    var currentPageIndex by remember(note.id) { mutableStateOf(0) }
    val safeIndex = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentPageContent = pages.getOrElse(safeIndex) { "" }
    val attachments = remember(note.attachmentsJson) { SafeNoteCodec.parseAttachments(note.attachmentsJson) }
    val sectionColor = parseHexColor(sectionColorHex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onTogglePin) {
                        Text(if (note.isPinned != 0L) "Unpin" else "Pin")
                    }
                    TextButton(onClick = onShareP2p) {
                        Text("Share P2P")
                    }
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Edit")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatNoteTimestamp(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = sectionColor.copy(alpha = 0.4f), thickness = 1.5.dp)

            if (pages.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (safeIndex > 0) currentPageIndex = safeIndex - 1 },
                        enabled = safeIndex > 0
                    ) { Text("< Previous") }

                    Text(
                        "Page ${safeIndex + 1} of ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = { if (safeIndex < pages.lastIndex) currentPageIndex = safeIndex + 1 },
                        enabled = safeIndex < pages.lastIndex
                    ) { Text("Next >") }
                }
            }
        }

        // Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
            if (SafeNoteCodec.isDrawingPage(currentPageContent)) {
                val strokes = remember(currentPageContent) { SafeNoteCodec.parseDrawingString(currentPageContent) }
                val paper = remember(currentPageContent) { SafeNoteCodec.parsePaperStyle(currentPageContent) }
                val paperLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                val adaptiveInk = MaterialTheme.colorScheme.onSurface

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPaperBackground(paper, paperLineColor)
                    strokes.forEach { stroke ->
                        drawStrokeLine(stroke, adaptiveInk)
                    }
                }
            } else {
                val lines = remember(currentPageContent) { currentPageContent.split("\n") }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (currentPageContent.isBlank()) {
                        Text(
                            "Empty page. Tap Edit to start writing or drawing.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        lines.forEachIndexed { lineIdx, line ->
                            when {
                                line.startsWith("# ") -> {
                                    Text(
                                        text = line.removePrefix("# "),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                line.startsWith("## ") -> {
                                    Text(
                                        text = line.removePrefix("## "),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                line.startsWith("- [ ] ") || line.startsWith("- [x] ") -> {
                                    val checked = line.startsWith("- [x] ")
                                    val label = line.drop(6)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                val toggled = if (checked) "- [ ] $label" else "- [x] $label"
                                                val newLines = lines.toMutableList().apply { set(lineIdx, toggled) }
                                                val newPages = pages.toMutableList().apply { set(safeIndex, newLines.joinToString("\n")) }
                                                onUpdateContent(SafeNoteCodec.joinPages(newPages))
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (checked) "☑" else "☐",
                                            color = if (checked) OneNotePurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = if (checked) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                                line.trim() == "---" -> {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Attachments",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        attachments.forEach { att ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = onOpenAttachment != null) { onOpenAttachment?.invoke(att) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(att.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("${att.sizeBytes} B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun StylusCanvas(
    modifier: Modifier = Modifier,
    strokes: List<StrokeLine>,
    selectedTool: DrawingTool,
    isEraser: Boolean,
    selectedColorArgb: Long,
    strokeWidthOverride: Float?,
    paperStyle: PaperStyle,
    onStrokesCommitted: (previous: List<StrokeLine>, updated: List<StrokeLine>) -> Unit
) {
    val latestStrokes by rememberUpdatedState(strokes)
    val latestTool by rememberUpdatedState(selectedTool)
    val latestIsEraser by rememberUpdatedState(isEraser)
    val latestColorArgb by rememberUpdatedState(selectedColorArgb)
    val latestWidthOverride by rememberUpdatedState(strokeWidthOverride)
    val latestOnCommitted by rememberUpdatedState(onStrokesCommitted)

    var activePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    val paperLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
    val adaptiveDefaultInk = MaterialTheme.colorScheme.onSurface

    val effectiveWidth = strokeWidthOverride ?: SafeNoteCodec.defaultStrokeWidth(selectedTool)
    val effectiveColorArgb = if (selectedTool == DrawingTool.BRUSH) {
        // Highlighter uses semi-transparency
        (selectedColorArgb and 0x00FFFFFFL) or 0x66000000L
    } else {
        selectedColorArgb
    }

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (latestIsEraser) {
                            val remaining = eraseNearPoint(latestStrokes, offset.x, offset.y)
                            if (remaining.size != latestStrokes.size) {
                                latestOnCommitted(latestStrokes, remaining)
                            }
                        } else {
                            activePoints = listOf(StrokePoint(offset.x, offset.y))
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        if (latestIsEraser) {
                            val remaining = eraseNearPoint(latestStrokes, pos.x, pos.y)
                            if (remaining.size != latestStrokes.size) {
                                latestOnCommitted(latestStrokes, remaining)
                            }
                        } else {
                            activePoints = activePoints + StrokePoint(pos.x, pos.y)
                        }
                    },
                    onDragEnd = {
                        if (!latestIsEraser && activePoints.isNotEmpty()) {
                            val width = latestWidthOverride ?: SafeNoteCodec.defaultStrokeWidth(latestTool)
                            val color = if (latestTool == DrawingTool.BRUSH) {
                                (latestColorArgb and 0x00FFFFFFL) or 0x66000000L
                            } else {
                                latestColorArgb
                            }
                            val newLine = StrokeLine(
                                points = activePoints,
                                tool = latestTool,
                                colorArgb = color,
                                strokeWidth = width
                            )
                            activePoints = emptyList()
                            latestOnCommitted(latestStrokes, latestStrokes + newLine)
                        } else {
                            activePoints = emptyList()
                        }
                    },
                    onDragCancel = {
                        activePoints = emptyList()
                    }
                )
            }
    ) {
        drawPaperBackground(paperStyle, paperLineColor)

        strokes.forEach { stroke ->
            drawStrokeLine(stroke, adaptiveDefaultInk)
        }

        if (activePoints.isNotEmpty()) {
            drawStrokeLine(
                StrokeLine(
                    points = activePoints,
                    tool = selectedTool,
                    colorArgb = effectiveColorArgb,
                    strokeWidth = effectiveWidth
                ),
                adaptiveDefaultInk
            )
        }
    }
}

private fun eraseNearPoint(strokes: List<StrokeLine>, x: Float, y: Float, radiusSq: Float = 900f): List<StrokeLine> {
    return strokes.filterNot { stroke ->
        stroke.points.any { pt ->
            val dx = pt.x - x
            val dy = pt.y - y
            (dx * dx + dy * dy) <= radiusSq
        }
    }
}

private fun DrawScope.drawPaperBackground(paperStyle: PaperStyle, lineColor: Color) {
    when (paperStyle) {
        PaperStyle.BLANK -> Unit
        PaperStyle.RULED -> {
            val spacing = 72f
            var y = spacing
            while (y < size.height) {
                drawLine(color = lineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.5f)
                y += spacing
            }
        }
        PaperStyle.GRID -> {
            val spacing = 64f
            var y = spacing
            while (y < size.height) {
                drawLine(color = lineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.2f)
                y += spacing
            }
            var x = spacing
            while (x < size.width) {
                drawLine(color = lineColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.2f)
                x += spacing
            }
        }
    }
}

private fun DrawScope.drawStrokeLine(line: StrokeLine, adaptiveBlack: Color) {
    if (line.points.isEmpty()) return
    val strokeColor = if (line.colorArgb == 0xFF1A1A1AL || line.colorArgb == 0xFF000000L) {
        adaptiveBlack
    } else {
        Color(line.colorArgb)
    }
    val width = line.strokeWidth ?: SafeNoteCodec.defaultStrokeWidth(line.tool)

    if (line.points.size == 1) {
        val pt = line.points.first()
        drawCircle(color = strokeColor, radius = width / 2f, center = Offset(pt.x, pt.y))
        return
    }

    val path = Path().apply {
        val first = line.points.first()
        moveTo(first.x, first.y)
        for (i in 1 until line.points.size) {
            val pt = line.points[i]
            lineTo(pt.x, pt.y)
        }
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun RibbonActionChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RibbonSelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) OneNotePurple else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (selected) OneNotePurple else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun appendLinePrefix(existing: String, prefix: String): String {
    return when {
        existing.isEmpty() -> prefix
        existing.endsWith("\n") -> existing + prefix
        else -> "$existing\n$prefix"
    }
}
