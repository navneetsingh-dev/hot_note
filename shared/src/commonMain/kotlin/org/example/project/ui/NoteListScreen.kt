package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints // NEW IMPORT ADDED HERE
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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


@Composable
fun NoteListScreen(
    state: NoteListState,
    onSaveNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var selectedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Increased threshold to 840.dp to catch phones in landscape mode
        val isPhone = maxWidth < 840.dp

        // RULE 1: If creating a new note, ALWAYS use 100% of the screen! (Sidebar is completely gone)
        if (isCreatingNew) {
            TabletEditor(
                onSave = { title, content ->
                    onSaveNote(title, content)
                    isCreatingNew = false
                },
                onCancel = { isCreatingNew = false }
            )
        }
        // RULE 2: If viewing a note on a phone, use 100% of the screen
        else if (isPhone && selectedNote != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = { selectedNote = null },
                    modifier = Modifier.padding(8.dp)
                ) { Text("< Back to Notes") }
                NoteViewer(note = selectedNote!!)
            }
        }
        // RULE 3: If on a phone and NO note is selected, show the 100% full screen list
        else if (isPhone && selectedNote == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hot Note", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FloatingActionButton(
                        onClick = {
                            selectedNote = null
                            isCreatingNew = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
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
        // RULE 4: If on a TABLET and viewing notes, show the Split Screen List & Viewer
        else {
            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT SIDEBAR (35%)
                Surface(modifier = Modifier.weight(0.35f).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Hot Note", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FloatingActionButton(
                                onClick = {
                                    selectedNote = null
                                    isCreatingNew = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
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

                // RIGHT CANVAS EDITOR (65%)
                Surface(modifier = Modifier.weight(0.65f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                    if (selectedNote != null) {
                        NoteViewer(note = selectedNote!!)
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
fun TabletEditor(onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var title by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }
    var drawingContent by remember { mutableStateOf("") }
    var isDrawingMode by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Type", modifier = Modifier.padding(end = 8.dp))
                Switch(checked = isDrawingMode, onCheckedChange = { isDrawingMode = it })
                Text("Draw", modifier = Modifier.padding(start = 8.dp))
            }
            if(isDrawingMode){
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolButton("Pen", currentTool == DrawingTool.PEN) { currentTool = DrawingTool.PEN }
                    ToolButton("Sketch", currentTool == DrawingTool.SKETCH) { currentTool = DrawingTool.SKETCH }
                    ToolButton("Brush", currentTool == DrawingTool.BRUSH) { currentTool = DrawingTool.BRUSH }
                }
            }
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    if (title.isNotBlank()) {
                        val finalContent = if (isDrawingMode) drawingContent else textContent
                        onSave(title, finalContent)
                    }
                }) { Text("Save") }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Page Title...", style = MaterialTheme.typography.headlineLarge) },
            textStyle = MaterialTheme.typography.headlineLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Divider()

        if (isDrawingMode) {
            StylusCanvas(
                modifier = Modifier.fillMaxSize(),
                selectedTool = currentTool,
                onPathsChanged = { drawingContent = it }
            )
        } else {
            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                placeholder = { Text("Start typing...") },
                modifier = Modifier.fillMaxSize().padding(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
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
fun NoteViewer(note: NoteEntity) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = note.title, style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(24.dp))

        if (note.contentBlocks.startsWith("DRAWING:")) {
            val serialized = note.contentBlocks.removePrefix("DRAWING:")
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lines = serialized.split("|")
                for (lineStr in lines) {
                    if (lineStr.isBlank()) continue
                    val parts = lineStr.split(":")
                    if (parts.size != 2) continue
                    val tool = try { DrawingTool.valueOf(parts[0]) } catch (e: Exception) { DrawingTool.PEN }
                    val pointsStr = parts[1]

                    val path = Path()
                    val points = pointsStr.split(";")
                    points.forEachIndexed { index, pointStr ->
                        val coords = pointStr.split(",")
                        if (coords.size == 2) {
                            val x = coords[0].toFloatOrNull() ?: 0f
                            val y = coords[1].toFloatOrNull() ?: 0f
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                    }
                    val (color, strokeWidth) = getToolStyle(tool)
                    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        } else {
            Text(text = note.contentBlocks, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun StylusCanvas(modifier: Modifier = Modifier, selectedTool: DrawingTool, onPathsChanged: (String) -> Unit) {
    var lines by remember { mutableStateOf(emptyList<StrokeLine>()) }
    var currentLine by remember { mutableStateOf(emptyList<Offset>()) }

    Canvas(
        modifier = modifier.background(Color.White).pointerInput(Unit) {
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

@Composable
fun ToolButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

fun getToolStyle(tool: DrawingTool): Pair<Color, Float> {
    return when (tool) {
        DrawingTool.PEN -> Pair(Color.Black, 4f)
        DrawingTool.SKETCH -> Pair(Color.DarkGray, 8f)
        DrawingTool.BRUSH -> Pair(Color.Black.copy(alpha = 0.6f), 20f)
    }
}