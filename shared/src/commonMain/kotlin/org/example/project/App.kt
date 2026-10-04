package org.example.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.example.project.database.DatabaseDriverFactory
import org.example.project.database.FolderEntity
import org.example.project.database.HotNoteDatabase
import org.example.project.database.NoteRepository
import org.example.project.ui.NoteListScreen
import org.example.project.ui.TopicListScreen

private val NotionDarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF9B9B9B),
    background = Color(0xFF191919),
    surface = Color(0xFF202020),
    surfaceVariant = Color(0xFF252525),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

private val NotionLightColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF737373),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF7F7F5),
    primaryContainer = Color(0xFFEBEBEB),
    onPrimaryContainer = Color(0xFF000000)
)

@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    val database = remember { HotNoteDatabase(driverFactory.createDriver()) }
    val repository = remember { NoteRepository(database) }
    val viewModel = remember { NoteViewModel(repository) }

    // --- NEW: Initialize the Bluetooth Controller ---
    val bluetoothController = rememberBluetoothController()

    // 1. Collect the real DB streams from the ViewModel
    val folders by viewModel.folders.collectAsState()
    val noteState by viewModel.noteState.collectAsState()

    // 2. UI Routing State (Now using the real FolderEntity from SQLDelight)
    var selectedFolder by remember { mutableStateOf<FolderEntity?>(null) }

    val colorScheme = if (isSystemInDarkTheme()) NotionDarkColors else NotionLightColors

    MaterialTheme(colorScheme = colorScheme) {
        if (selectedFolder == null) {
            // SCREEN 1: Real Folders from Database
            TopicListScreen(
                folders = folders,
                onFolderClick = { folder ->
                    selectedFolder = folder
                    viewModel.selectFolder(folder.id) // Tell ViewModel to load this folder's notes
                },
                onCreateFolder = { folderName ->
                    viewModel.createFolder(folderName)
                },
                // --- NEW: Pass the Bluetooth trigger to the UI ---
                onStartServer = {
                    bluetoothController.startServer()
                }
            )
        } else {
            // SCREEN 2: Notes linked to the selected folder
            NoteListScreen(
                state = noteState,
                onSaveNote = { title, content -> viewModel.saveNote(title, content) },
                onDeleteNote = { id -> viewModel.deleteNoteById(id) },
                onBack = {
                    selectedFolder = null
                    viewModel.selectFolder(null) // Tell ViewModel we left the folder
                }
            )
        }
    }
}