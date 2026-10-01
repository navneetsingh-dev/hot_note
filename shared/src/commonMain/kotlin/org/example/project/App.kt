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
import org.example.project.database.HotNoteDatabase
import org.example.project.database.NoteRepository
import org.example.project.ui.NoteListScreen
import org.example.project.ui.TopicFolder
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
    val state by viewModel.state.collectAsState()

    val colorScheme = if (isSystemInDarkTheme()) NotionDarkColors else NotionLightColors

    // 1. The Routing State: Tracks which folder you are currently viewing
    var selectedFolder by remember { mutableStateOf<TopicFolder?>(null) }

    // 2. Temporary Mock Data (We will replace this with SQLDelight later)
    var mockFolders by remember {
        mutableStateOf(listOf(
            TopicFolder(1, "Computer Science", 4),
            TopicFolder(2, "Personal", 1)
        ))
    }

    MaterialTheme(colorScheme = colorScheme) {
        // 3. The Traffic Cop Logic
        if (selectedFolder == null) {
            // If no folder is selected, show Screen 1 (The File Manager)
            TopicListScreen(
                folders = mockFolders,
                onFolderClick = { folder -> selectedFolder = folder }, // Tapping a folder updates the state
                onCreateFolder = { folderName ->
                    val newFolder = TopicFolder(id = (mockFolders.size + 1).toLong(), name = folderName)
                    mockFolders = mockFolders + newFolder
                }
            )
        } else {
            // If a folder IS selected, show Screen 2 (The Note Editor)
            NoteListScreen(
                state = state,
                onSaveNote = { title, content -> viewModel.saveNote(title, content) },
                onDeleteNote = { id -> viewModel.deleteNoteById(id) },
                onBack = { selectedFolder = null }

            )
        }
    }
}