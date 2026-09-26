package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.NoteListState
import org.example.project.database.NoteEntity

enum class DrawingTool {
    PEN, SKETCH, BRUSH
}

data class StrokeLine(
    val points: List<Offset>,
    val tool: DrawingTool = DrawingTool.PEN
)

// Helper to convert the database text back into live drawing lines for viewing & editing
fun parseDrawingString(serialized: String): List<StrokeLine> {
    val lines = mutableListOf<StrokeLine>()
    val stripped = serialized.removePrefix("DRAWING:")
    val lineStrs = stripped.split("|")
    for (lineStr in lineStrs) {
        if (lineStr.isBlank()) continue
        val parts = lineStr.split(":")
        if (parts.size != 2) continue
        val tool = try { DrawingTool.valueOf(parts[0]) } catch (e: Exception) { DrawingTool.PEN }
        val points = parts[1].split(";").mapNotNull { pointStr ->
            val coords = pointStr.split(",")
            if (coords.size == 2) {
                val x = coords[0].toFloatOrNull()
                val y = coords[1].toFloatOrNull()
                if (x != null && y != null) Offset(x, y) else null
            } else null
        }
        if (points.isNotEmpty()) {
            lines.add(StrokeLine(points, tool))
        }
    }
    return lines
}

@Composable
fun NoteListScreen(
    state: NoteListState,
    onSaveNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var selectedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isPhone = maxWidth < 840.dp // Updated threshold to match your earlier BoxWithConstraints rules

        // RULE 1: STRICT FULL-SCREEN OVERRIDE FOR CREATING OR EDITING NOTES
        if (isCreatingNew || isEditing) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                TabletEditor(
                    initialTitle = if (isEditing) selectedNote?.title ?: "" else "",
                    initialContent = if (isEditing) selectedNote?.contentBlocks ?: "" else "",
                    onSave = { title, content ->
                        if (isEditing && selectedNote != null) {
                            onDeleteNote(selectedNote!!.id) // Remove old version
                        }
                        onSaveNote(title, content) // Save new version
                        isCreatingNew = false
                        isEditing = false
                        selectedNote = null
                    },
                    onCancel = {
                        isCreatingNew = false
                        isEditing = false
                    }
                )
            }
        }
        // RULE 2: PHONE MODE - View Selected Note (Full Screen)
        else if (isPhone && selectedNote != null) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(
                        onClick = { selectedNote = null },
                        modifier = Modifier.padding(8.dp)
                    ) { Text("< Back to Notes", color = MaterialTheme.colorScheme.primary) }

                    NoteViewer(
                        note = selectedNote!!,
                        onEdit = { isEditing = true }
                    )
                }
            }
        }
        // RULE 3: PHONE MODE - Sidebar Note List (Full Screen)
        else if (isPhone && selectedNote == null) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Hot Note", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FloatingActionButton(
                            onClick = {
                                selectedNote = null
                                isCreatingNew = true
                                isEditing = false
                            },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) { Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    if (state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (state.notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.notes) { note ->
                                SidebarNoteCard(
                                    note = note,
                                    isSelected = false,
                                    onClick = { selectedNote = note },
                                    onDelete = { onDeleteNote(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
        // RULE 4: TABLET MODE - Split Screen (Only for viewing notes)
        else {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.weight(0.35f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Hot Note", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FloatingActionButton(
                                onClick = {
                                    selectedNote = null
                                    isCreatingNew = true
                                    isEditing = false
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) { Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        if (state.isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else if (state.notes.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.notes) { note ->
                                    SidebarNoteCard(
                                        note = note,
                                        isSelected = selectedNote?.id == note.id,
                                        onClick = { selectedNote = note },
                                        onDelete = {
                                            if (selectedNote?.id == note.id) selectedNote = null
                                            onDeleteNote(note.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(modifier = Modifier.weight(0.65f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                    if (selectedNote != null) {
                        NoteViewer(
                            note = selectedNote!!,
                            onEdit = { isEditing = true }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a note from the sidebar or create a new one", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabletEditor(initialTitle: String = "", initialContent: String = "", onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    val isInitialDrawing = initialContent.startsWith("DRAWING:")

    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var title by remember { mutableStateOf(initialTitle) }
    var textContent by remember { mutableStateOf(if (isInitialDrawing) "" else initialContent) }
    var drawingContent by remember { mutableStateOf(if (isInitialDrawing) initialContent else "") }
    var isDrawingMode by remember { mutableStateOf(if (initialContent.isNotBlank()) isInitialDrawing else true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (!isDrawingMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable { isDrawingMode = false }
                    ) {
                        Text(
                            text = "Type",
                            color = if (!isDrawingMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDrawingMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.clickable { isDrawingMode = true }
                    ) {
                        Text(
                            text = "Draw",
                            color = if (isDrawingMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (isDrawingMode) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (currentTool == DrawingTool.PEN) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { currentTool = DrawingTool.PEN }
                        ) {
                            Text(
                                text = "Pen",
                                color = if (currentTool == DrawingTool.PEN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (currentTool == DrawingTool.SKETCH) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { currentTool = DrawingTool.SKETCH }
                        ) {
                            Text(
                                text = "Sketch",
                                color = if (currentTool == DrawingTool.SKETCH) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (currentTool == DrawingTool.BRUSH) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { currentTool = DrawingTool.BRUSH }
                        ) {
                            Text(
                                text = "Brush",
                                color = if (currentTool == DrawingTool.BRUSH) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.secondary) }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val finalContent = if (isDrawingMode) drawingContent else textContent
                            onSave(title, finalContent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Save") }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Page Title...", style = MaterialTheme.typography.headlineLarge, color = Color.Gray) },
            textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        if (isDrawingMode) {
            StylusCanvas(
                modifier = Modifier.fillMaxSize(),
                selectedTool = currentTool,
                initialSerialized = initialContent,
                onPathsChanged = { drawingContent = it }
            )
        } else {
            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                placeholder = { Text("Start typing...", color = Color.Gray) },
                modifier = Modifier.fillMaxSize().padding(16.dp).background(Color(0xFFF9F9F9)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )
        }
    }
}

@Composable
fun SidebarNoteCard(note: NoteEntity, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!note.contentBlocks.startsWith("DRAWING:")) {
                Text(text = note.contentBlocks, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
            } else {
                Text(text = "[Handwritten Sketch]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun NoteViewer(note: NoteEntity, onEdit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF9F9F9)).padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = note.title, style = MaterialTheme.typography.displaySmall, color = Color.Black)

            // Edit Button (Forced Blue so it's always visible on the paper texture)
            TextButton(onClick = onEdit) {
                Text("Edit", color = Color.Blue)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (note.contentBlocks.startsWith("DRAWING:")) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val lineSpacing = 80f
                var y = lineSpacing
                while (y < canvasHeight) {
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 2f
                    )
                    y += lineSpacing
                }

                val lines = parseDrawingString(note.contentBlocks)
                lines.forEach { line ->
                    val path = Path()
                    if (line.points.isNotEmpty()) {
                        path.moveTo(line.points.first().x, line.points.first().y)
                        for (i in 1 until line.points.size) { path.lineTo(line.points[i].x, line.points[i].y) }
                    }
                    val (color, strokeWidth) = getToolStyle(line.tool)
                    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        } else {
            Text(text = note.contentBlocks, style = MaterialTheme.typography.bodyLarge, color = Color.Black)
        }
    }
}

@Composable
fun StylusCanvas(
    modifier: Modifier = Modifier,
    selectedTool: DrawingTool,
    initialSerialized: String = "",
    onPathsChanged: (String) -> Unit
) {
    var lines by remember(initialSerialized) {
        mutableStateOf(if (initialSerialized.isNotBlank() && initialSerialized.startsWith("DRAWING:")) parseDrawingString(initialSerialized) else emptyList())
    }
    var currentLine by remember { mutableStateOf(emptyList<Offset>()) }

    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) {
            val serialized = lines.joinToString("|") { line ->
                val coords = line.points.joinToString(";") { "${it.x},${it.y}" }
                "${line.tool}:$coords"
            }
            onPathsChanged("DRAWING:$serialized")
        }
    }

    Canvas(
        modifier = modifier.background(Color(0xFFF9F9F9)).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> currentLine = listOf(offset) },
                onDrag = { change, _ -> currentLine = currentLine + change.position },
                onDragEnd = {
                    lines = lines + StrokeLine(currentLine, selectedTool)
                    currentLine = emptyList()
                    val serialized = lines.joinToString("|") { line ->
                        val coords = line.points.joinToString(";") { "${it.x},${it.y}" }
                        "${line.tool}:$coords"
                    }
                    onPathsChanged("DRAWING:$serialized")
                }
            )
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val lineSpacing = 80f
        var y = lineSpacing
        while (y < canvasHeight) {
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(canvasWidth, y),
                strokeWidth = 2f
            )
            y += lineSpacing
        }

        lines.forEach { line ->
            val path = Path()
            if (line.points.isNotEmpty()) {
                path.moveTo(line.points.first().x, line.points.first().y)
                for (i in 1 until line.points.size) { path.lineTo(line.points[i].x, line.points[i].y) }
            }
            val (color, strokeWidth) = getToolStyle(line.tool)
            drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (currentLine.isNotEmpty()) {
            val path = Path()
            path.moveTo(currentLine.first().x, currentLine.first().y)
            for (i in 1 until currentLine.size) { path.lineTo(currentLine[i].x, currentLine[i].y) }
            val (color, strokeWidth) = getToolStyle(selectedTool)
            drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

fun getToolStyle(tool: DrawingTool): Pair<Color, Float> {
    return when (tool) {
        DrawingTool.PEN -> Pair(Color.Black, 4f)
        DrawingTool.SKETCH -> Pair(Color.DarkGray, 8f)
        DrawingTool.BRUSH -> Pair(Color.Black.copy(alpha = 0.6f), 20f)
    }
}