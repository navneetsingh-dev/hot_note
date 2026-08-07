package org.example.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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

data class StrokeLine(val points: List<Offset>)

@Composable
fun NoteListScreen(
    state: NoteListState,
    onSaveNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var selectedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ==========================================
        // LEFT SIDEBAR (35% of screen width)
        // ==========================================
        Surface(
            modifier = Modifier.weight(0.35f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sidebar Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hot Note",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FloatingActionButton(
                        onClick = {
                            selectedNote = null
                            isCreatingNew = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                // Sidebar List
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.notes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.notes) { note ->
                            SidebarNoteCard(
                                note = note,
                                isSelected = selectedNote?.id == note.id,
                                onClick = {
                                    isCreatingNew = false
                                    selectedNote = note
                                },
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

        // ==========================================
        // RIGHT CANVAS EDITOR (65% of screen width)
        // ==========================================
        Surface(
            modifier = Modifier.weight(0.65f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (isCreatingNew) {
                TabletEditor(
                    onSave = { title, content ->
                        onSaveNote(title, content)
                        isCreatingNew = false
                    },
                    onCancel = { isCreatingNew = false }
                )
            } else if (selectedNote != null) {
                NoteViewer(note = selectedNote!!)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a note from the sidebar or create a new one", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun TabletEditor(onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }
    var drawingContent by remember { mutableStateOf("") }
    var isDrawingMode by remember { mutableStateOf(true) } // Default to Stylus mode for tablets

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
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

            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = {
                    if (title.isNotBlank()) {
                        val finalContent = if (isDrawingMode) drawingContent else textContent
                        onSave(title, finalContent)
                    }
                }) {
                    Text("Save to Notebook")
                }
            }
        }

        // Title Input (Borderless)
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

        // Massive Infinite Canvas / Editor
        if (isDrawingMode) {
            StylusCanvas(
                modifier = Modifier.fillMaxSize(),
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
                for (line in lines) {
                    if (line.isBlank()) continue
                    val points = line.split(";")
                    val path = Path()
                    points.forEachIndexed { index, pointStr ->
                        val coords = pointStr.split(",")
                        if (coords.size == 2) {
                            val x = coords[0].toFloatOrNull() ?: 0f
                            val y = coords[1].toFloatOrNull() ?: 0f
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                    }
                    drawPath(path, Color.DarkGray, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        } else {
            Text(text = note.contentBlocks, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun StylusCanvas(modifier: Modifier = Modifier, onPathsChanged: (String) -> Unit) {
    var lines by remember { mutableStateOf(emptyList<StrokeLine>()) }
    var currentLine by remember { mutableStateOf(emptyList<Offset>()) }

    Canvas(
        modifier = modifier.background(Color.White).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> currentLine = listOf(offset) },
                onDrag = { change, _ -> currentLine = currentLine + change.position },
                onDragEnd = {
                    lines = lines + StrokeLine(currentLine)
                    currentLine = emptyList()
                    val serialized = lines.joinToString("|") { line ->
                        line.points.joinToString(";") { "${it.x},${it.y}" }
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
            drawPath(path, Color.Black, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (currentLine.isNotEmpty()) {
            val path = Path()
            path.moveTo(currentLine.first().x, currentLine.first().y)
            for (i in 1 until currentLine.size) { path.lineTo(currentLine[i].x, currentLine[i].y) }
            drawPath(path, Color.Black, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}