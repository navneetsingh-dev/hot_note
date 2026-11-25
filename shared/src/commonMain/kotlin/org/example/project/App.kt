package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.database.DatabaseDriverFactory
import org.example.project.database.HotNoteDatabase
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository
import org.example.project.model.ConflictResolution
import org.example.project.model.NoteConflict
import org.example.project.p2p.rememberP2pController
import org.example.project.ui.HomeScreen
import org.example.project.ui.NotebooksScreen
import org.example.project.ui.OneNoteEditorScreen
import org.example.project.ui.OneNotePurple
import org.example.project.ui.OneNoteTheme
import org.example.project.ui.OneNoteViewerPane
import org.example.project.ui.P2pShareScreen
import org.example.project.ui.PlatformBackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    val repoHolder = remember(driverFactory) {
        val driver = driverFactory.createDriver()
        val database = HotNoteDatabase(driver)
        val repository = NoteRepository(database)
        Triple(driver, database, repository)
    }
    val repository = repoHolder.third

    val viewModel = viewModel {
        NoteViewModel(repository = repository, driver = repoHolder.first)
    }

    val p2pController = rememberP2pController(
        repository = repository,
        onNoteReceived = { payload -> viewModel.importIncomingNote(payload) }
    )

    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allSections by viewModel.allSections.collectAsStateWithLifecycle()
    val activeFolderId by viewModel.activeFolderId.collectAsStateWithLifecycle()
    val activeSectionId by viewModel.activeSectionId.collectAsStateWithLifecycle()
    val selectedNoteId by viewModel.selectedNoteId.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val homeNotes by viewModel.filteredHomeNotes.collectAsStateWithLifecycle()
    val noteState by viewModel.noteState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val noteFilter by viewModel.noteFilter.collectAsStateWithLifecycle()
    val sharedFiles by viewModel.sharedFiles.collectAsStateWithLifecycle()
    val pendingConflicts by viewModel.pendingConflicts.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val serverState by p2pController.serverState.collectAsStateWithLifecycle()

    // Editor / quick viewer state
    var isEditorOpen by remember { mutableStateOf(false) }
    var editorNote by remember { mutableStateOf<NoteEntity?>(null) }
    var editorDrawStart by remember { mutableStateOf(false) }
    var homePreviewNoteId by remember { mutableStateOf<String?>(null) }
    var noteToShareP2p by remember { mutableStateOf<NoteEntity?>(null) }

    val selectedNotebookNote = remember(allNotes, selectedNoteId) {
        selectedNoteId?.let { id -> allNotes.firstOrNull { it.id == id } }
    }
    val homePreviewNote = remember(allNotes, homePreviewNoteId) {
        homePreviewNoteId?.let { id -> allNotes.firstOrNull { it.id == id } }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    OneNoteTheme {
        if (isEditorOpen) {
            PlatformBackHandler { isEditorOpen = false }
            val targetSection = allSections.firstOrNull {
                it.id == (editorNote?.sectionId ?: activeSectionId)
            }
            OneNoteEditorScreen(
                existingNote = editorNote,
                sectionName = targetSection?.name ?: "Quick Notes",
                sectionColorHex = targetSection?.colorHex ?: "#7719AA",
                startInDrawMode = editorDrawStart,
                onSave = { title, content, attachmentsJson ->
                    viewModel.saveNote(
                        title = title,
                        content = content,
                        existingNote = editorNote,
                        attachmentsJson = attachmentsJson
                    )
                    isEditorOpen = false
                    editorNote = null
                },
                onShareNote = { note ->
                    isEditorOpen = false
                    noteToShareP2p = note
                    viewModel.setDestination(AppDestination.P2P_SHARE)
                },
                onCancel = {
                    isEditorOpen = false
                    editorNote = null
                }
            )
        } else if (destination == AppDestination.HOME && homePreviewNote != null) {
            PlatformBackHandler { homePreviewNoteId = null }
            val sec = allSections.firstOrNull { it.id == homePreviewNote.sectionId }
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(sec?.name ?: "Page", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            TextButton(onClick = { homePreviewNoteId = null }) {
                                Text("< Home", color = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = OneNotePurple,
                            titleContentColor = Color.White
                        )
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    OneNoteViewerPane(
                        note = homePreviewNote,
                        sectionColorHex = sec?.colorHex ?: "#7719AA",
                        onEdit = {
                            editorNote = homePreviewNote
                            editorDrawStart = false
                            isEditorOpen = true
                        },
                        onTogglePin = { viewModel.togglePin(homePreviewNote) },
                        onShareP2p = {
                            homePreviewNoteId = null
                            noteToShareP2p = homePreviewNote
                            viewModel.setDestination(AppDestination.P2P_SHARE)
                        },
                        onUpdateContent = { updated ->
                            viewModel.saveNote(
                                title = homePreviewNote.title,
                                content = updated,
                                existingNote = homePreviewNote
                            )
                        },
                        onOpenAttachment = { att ->
                            p2pController.openReceivedFile(att.path, att.mimeType)
                        }
                    )
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 840.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "N",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Hot Note", fontWeight = FontWeight.Bold)
                                }
                            },
                            actions = {
                                if (serverState.isRunning) {
                                    Surface(
                                        color = Color(0xFF217346),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .clickable { viewModel.setDestination(AppDestination.P2P_SHARE) }
                                    ) {
                                        Text(
                                            text = "P2P Live :${serverState.port}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = OneNotePurple,
                                titleContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        if (!isTablet) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = destination == AppDestination.HOME,
                                    onClick = { viewModel.setDestination(AppDestination.HOME) },
                                    icon = { Text("⌂", style = MaterialTheme.typography.titleMedium) },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = destination == AppDestination.NOTEBOOKS,
                                    onClick = {
                                        if (activeFolderId == null && folders.isNotEmpty()) {
                                            viewModel.selectFolder(folders.first().id)
                                        }
                                        viewModel.setDestination(AppDestination.NOTEBOOKS)
                                    },
                                    icon = { Text("▤", style = MaterialTheme.typography.titleMedium) },
                                    label = { Text("Notebooks") }
                                )
                                NavigationBarItem(
                                    selected = destination == AppDestination.P2P_SHARE,
                                    onClick = { viewModel.setDestination(AppDestination.P2P_SHARE) },
                                    icon = { Text("⇄", style = MaterialTheme.typography.titleMedium) },
                                    label = { Text("P2P Share") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (isTablet) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                NavigationRailItem(
                                    selected = destination == AppDestination.HOME,
                                    onClick = { viewModel.setDestination(AppDestination.HOME) },
                                    icon = { Text("⌂", style = MaterialTheme.typography.titleLarge) },
                                    label = { Text("Home") }
                                )
                                NavigationRailItem(
                                    selected = destination == AppDestination.NOTEBOOKS,
                                    onClick = {
                                        if (activeFolderId == null && folders.isNotEmpty()) {
                                            viewModel.selectFolder(folders.first().id)
                                        }
                                        viewModel.setDestination(AppDestination.NOTEBOOKS)
                                    },
                                    icon = { Text("▤", style = MaterialTheme.typography.titleLarge) },
                                    label = { Text("Notebooks") }
                                )
                                NavigationRailItem(
                                    selected = destination == AppDestination.P2P_SHARE,
                                    onClick = { viewModel.setDestination(AppDestination.P2P_SHARE) },
                                    icon = { Text("⇄", style = MaterialTheme.typography.titleLarge) },
                                    label = { Text("P2P") }
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (destination) {
                                AppDestination.HOME -> {
                                    HomeScreen(
                                        notes = homeNotes,
                                        folders = folders,
                                        sections = allSections,
                                        searchQuery = searchQuery,
                                        activeFilter = noteFilter,
                                        onSearchChange = { viewModel.updateSearchQuery(it) },
                                        onFilterChange = { viewModel.setFilter(it) },
                                        onNoteClick = { note -> homePreviewNoteId = note.id },
                                        onNewTextNote = {
                                            editorNote = null
                                            editorDrawStart = false
                                            isEditorOpen = true
                                        },
                                        onNewInkNote = {
                                            editorNote = null
                                            editorDrawStart = true
                                            isEditorOpen = true
                                        },
                                        onOpenP2pShare = { viewModel.setDestination(AppDestination.P2P_SHARE) },
                                        onTogglePin = { viewModel.togglePin(it) },
                                        onDeleteNote = { viewModel.deleteNoteById(it.id) }
                                    )
                                }

                                AppDestination.NOTEBOOKS -> {
                                    NotebooksScreen(
                                        folders = folders,
                                        allSections = allSections,
                                        activeFolderId = activeFolderId,
                                        activeSectionId = activeSectionId,
                                        noteState = noteState,
                                        selectedNote = selectedNotebookNote,
                                        onSelectFolder = { viewModel.selectFolder(it) },
                                        onSelectSection = { viewModel.selectSection(it) },
                                        onSelectNote = { viewModel.selectNote(it?.id) },
                                        onCreateFolder = { name, color -> viewModel.createFolder(name, color) },
                                        onDeleteFolder = { viewModel.deleteFolder(it) },
                                        onCreateSection = { fId, name, color -> viewModel.createSection(fId, name, color) },
                                        onDeleteSection = { viewModel.deleteSection(it) },
                                        onCreateNewNote = {
                                            editorNote = null
                                            editorDrawStart = false
                                            isEditorOpen = true
                                        },
                                        onEditNote = { note ->
                                            editorNote = note
                                            editorDrawStart = false
                                            isEditorOpen = true
                                        },
                                        onDeleteNote = { viewModel.deleteNoteById(it.id) },
                                        onTogglePin = { viewModel.togglePin(it) },
                                        onShareNoteP2p = { note ->
                                            noteToShareP2p = note
                                            viewModel.setDestination(AppDestination.P2P_SHARE)
                                        },
                                        onUpdateNoteContent = { note, updated ->
                                            viewModel.saveNote(
                                                title = note.title,
                                                content = updated,
                                                existingNote = note
                                            )
                                        },
                                        onOpenAttachment = { path, mime ->
                                            p2pController.openReceivedFile(path, mime)
                                        }
                                    )
                                }

                                AppDestination.P2P_SHARE -> {
                                    P2pShareScreen(
                                        p2pController = p2pController,
                                        notes = allNotes,
                                        sharedFiles = sharedFiles,
                                        preselectedNoteToShare = noteToShareP2p,
                                        onClearPreselectedNote = { noteToShareP2p = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Conflict Resolution Dialog (Issue #18 & #21)
        pendingConflicts.firstOrNull()?.let { conflict ->
            ConflictResolutionDialog(
                conflict = conflict,
                onResolve = { resolution -> viewModel.resolveConflict(conflict, resolution) }
            )
        }
    }
}

@Composable
private fun ConflictResolutionDialog(
    conflict: NoteConflict,
    onResolve: (ConflictResolution) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onResolve(ConflictResolution.KEEP_LOCAL) },
        title = { Text("Note Version Conflict") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "\"${conflict.incoming.title}\" from ${conflict.incoming.senderName} differs from your local note.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Choose how to resolve without losing edits:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onResolve(ConflictResolution.KEEP_BOTH) },
                colors = ButtonDefaults.buttonColors(containerColor = OneNotePurple)
            ) {
                Text("Keep Both")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onResolve(ConflictResolution.KEEP_LOCAL) }) {
                    Text("Keep Local")
                }
                OutlinedButton(onClick = { onResolve(ConflictResolution.REPLACE_LOCAL) }) {
                    Text("Use Incoming")
                }
            }
        }
    )
}
