package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.project.NoteListState
import org.example.project.database.FolderEntity
import org.example.project.database.NoteEntity
import org.example.project.database.SectionEntity
import org.example.project.model.SafeNoteCodec

@Composable
fun NotebooksScreen(
    folders: List<FolderEntity>,
    allSections: List<SectionEntity>,
    activeFolderId: Long?,
    activeSectionId: Long?,
    noteState: NoteListState,
    selectedNote: NoteEntity?,
    onSelectFolder: (Long?) -> Unit,
    onSelectSection: (Long?) -> Unit,
    onSelectNote: (NoteEntity?) -> Unit,
    onCreateFolder: (name: String, colorHex: String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCreateSection: (folderId: Long, name: String, colorHex: String) -> Unit,
    onDeleteSection: (Long) -> Unit,
    onCreateNewNote: () -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onShareNoteP2p: (NoteEntity) -> Unit,
    onUpdateNoteContent: (NoteEntity, String) -> Unit,
    onOpenAttachment: ((String, String) -> Unit)? = null
) {
    var showNewNotebookDialog by remember { mutableStateOf(false) }
    var showNewSectionForFolder by remember { mutableStateOf<FolderEntity?>(null) }

    val activeFolder = folders.firstOrNull { it.id == activeFolderId }
    val activeSections = remember(allSections, activeFolderId) {
        if (activeFolderId != null) allSections.filter { it.folderId == activeFolderId } else emptyList()
    }
    val activeSection = activeSections.firstOrNull { it.id == activeSectionId } ?: activeSections.firstOrNull()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isTablet = maxWidth >= 840.dp

        if (isTablet) {
            // OneNote Tablet 3-Pane Master-Detail Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Pane 1: Notebooks & Color-Coded Sections
                Surface(
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Notebooks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showNewNotebookDialog = true }) { Text("+ New") }
                        }
                        HorizontalDivider()

                        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(folders, key = { it.id }) { folder ->
                                val isExpanded = folder.id == (activeFolder?.id ?: folders.firstOrNull()?.id)
                                val folderColor = parseHexColor(folder.colorHex)
                                val folderSections = allSections.filter { it.folderId == folder.id }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isExpanded) MaterialTheme.colorScheme.surface else Color.Transparent
                                        )
                                        .padding(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectFolder(folder.id) }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(6.dp)
                                                .height(22.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(folderColor)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (folders.size > 1) {
                                            Text(
                                                text = "×",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .clickable { onDeleteFolder(folder.id) }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        folderSections.forEach { sec ->
                                            val secSelected = sec.id == activeSection?.id
                                            val secColor = parseHexColor(sec.colorHex)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (secSelected) secColor.copy(alpha = 0.16f) else Color.Transparent
                                                    )
                                                    .clickable {
                                                        if (activeFolderId != folder.id) onSelectFolder(folder.id)
                                                        onSelectSection(sec.id)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(secColor)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = sec.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (secSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (secSelected) secColor else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (folderSections.size > 1) {
                                                    Text(
                                                        text = "×",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .clickable { onDeleteSection(sec.id) }
                                                            .padding(horizontal = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        TextButton(
                                            onClick = { showNewSectionForFolder = folder },
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text("+ Section", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                VerticalDivider()

                // Pane 2: Pages in Active Section
                Surface(
                    modifier = Modifier.width(270.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeSection?.name ?: "Pages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onCreateNewNote,
                                colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("+ Page") }
                        }
                        HorizontalDivider()

                        PagesColumn(
                            noteState = noteState,
                            selectedNoteId = selectedNote?.id,
                            accentColor = parseHexColor(activeSection?.colorHex ?: activeFolder?.colorHex),
                            onSelectNote = onSelectNote,
                            onDeleteNote = onDeleteNote
                        )
                    }
                }

                VerticalDivider()

                // Pane 3: Page Viewer
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (selectedNote != null) {
                        OneNoteViewerPane(
                            note = selectedNote,
                            sectionColorHex = activeSection?.colorHex ?: activeFolder?.colorHex ?: "#7719AA",
                            onEdit = { onEditNote(selectedNote) },
                            onTogglePin = { onTogglePin(selectedNote) },
                            onShareP2p = { onShareNoteP2p(selectedNote) },
                            onUpdateContent = { updated -> onUpdateNoteContent(selectedNote, updated) },
                            onOpenAttachment = { att -> onOpenAttachment?.invoke(att.path, att.mimeType) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Select a page or create a new one",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onCreateNewNote,
                                    colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple, contentColor = Color.White)
                                ) { Text("+ New Page") }
                            }
                        }
                    }
                }
            }
        } else {
            // OneNote Phone Drill-Down Flow: Notebooks -> Sections -> Pages -> Viewer
            when {
                selectedNote != null -> {
                    PlatformBackHandler { onSelectNote(null) }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onSelectNote(null) }) { Text("< Pages") }
                            Text(
                                text = activeSection?.name ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                color = parseHexColor(activeSection?.colorHex)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OneNoteViewerPane(
                                note = selectedNote,
                                sectionColorHex = activeSection?.colorHex ?: "#7719AA",
                                onEdit = { onEditNote(selectedNote) },
                                onTogglePin = { onTogglePin(selectedNote) },
                                onShareP2p = { onShareNoteP2p(selectedNote) },
                                onUpdateContent = { updated -> onUpdateNoteContent(selectedNote, updated) },
                                onOpenAttachment = { att -> onOpenAttachment?.invoke(att.path, att.mimeType) }
                            )
                        }
                    }
                }

                activeFolder != null -> {
                    PlatformBackHandler { onSelectFolder(null) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top bar with Back + Notebook title + +Section
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    TextButton(onClick = { onSelectFolder(null) }) { Text("< Notebooks") }
                                    Text(
                                        text = activeFolder.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { showNewSectionForFolder = activeFolder }) {
                                    Text("+ Section")
                                }
                            }

                            // Horizontal OneNote Colored Section Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeSections.forEach { sec ->
                                    val secColor = parseHexColor(sec.colorHex)
                                    val isSel = sec.id == activeSection?.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) secColor else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .border(
                                                width = 1.5.dp,
                                                color = secColor,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onSelectSection(sec.id) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = sec.name,
                                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            PagesColumn(
                                noteState = noteState,
                                selectedNoteId = null,
                                accentColor = parseHexColor(activeSection?.colorHex ?: activeFolder.colorHex),
                                onSelectNote = onSelectNote,
                                onDeleteNote = onDeleteNote
                            )
                        }

                        FloatingActionButton(
                            onClick = onCreateNewNote,
                            containerColor = OneNotePurple,
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 20.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }

                else -> {
                    // Phone Notebooks List
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = "Notebooks",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 84.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(folders, key = { it.id }) { folder ->
                                    val color = parseHexColor(folder.colorHex)
                                    val secCount = allSections.count { it.folderId == folder.id }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectFolder(folder.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Classic OneNote Notebook Spine
                                            Box(
                                                modifier = Modifier
                                                    .width(14.dp)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(color)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "$secCount section${if (secCount == 1) "" else "s"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (folders.size > 1) {
                                                TextButton(onClick = { onDeleteFolder(folder.id) }) {
                                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = { showNewNotebookDialog = true },
                            containerColor = OneNotePurple,
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 20.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }

    if (showNewNotebookDialog) {
        CreateColorEntityDialog(
            title = "New Notebook",
            placeholder = "e.g. Semester Notes, Work, Personal",
            onDismiss = { showNewNotebookDialog = false },
            onConfirm = { name, colorHex ->
                onCreateFolder(name, colorHex)
                showNewNotebookDialog = false
            }
        )
    }

    showNewSectionForFolder?.let { targetFolder ->
        CreateColorEntityDialog(
            title = "New Section in ${targetFolder.name}",
            placeholder = "e.g. Lectures, Tasks, Diagrams",
            onDismiss = { showNewSectionForFolder = null },
            onConfirm = { name, colorHex ->
                onCreateSection(targetFolder.id, name, colorHex)
                showNewSectionForFolder = null
            }
        )
    }
}

@Composable
private fun PagesColumn(
    noteState: NoteListState,
    selectedNoteId: String?,
    accentColor: Color,
    onSelectNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit
) {
    when {
        noteState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OneNotePurple)
            }
        }
        noteState.notes.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No pages in this section yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                contentPadding = PaddingValues(bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(noteState.notes, key = { it.id }) { note ->
                    val isSelected = note.id == selectedNoteId
                    val preview = SafeNoteCodec.pagePreviewText(note.contentBlocks)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectNote(note) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (note.isPinned != 0L) {
                                    Text("📌", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatNoteTimestamp(note.updatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                TextButton(
                                    onClick = { onDeleteNote(note) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Del", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
private fun CreateColorEntityDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(OneNoteSectionColors.first().first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Section / Cover Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OneNoteSectionColors.forEach { (hex, _) ->
                        val color = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim(), selectedColor)
                },
                colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
