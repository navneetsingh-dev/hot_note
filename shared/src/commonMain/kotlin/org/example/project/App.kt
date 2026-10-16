package org.example.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    // 1. Tie driver, database, and repository directly to the ViewModel's lifecycle
    val viewModel = viewModel {
        val driver = driverFactory.createDriver()
        val database = HotNoteDatabase(driver)
        val repository = NoteRepository(database)
        NoteViewModel(repository = repository, driver = driver)
    }

    val bluetoothController = rememberBluetoothController()

    // 2. Collect flows with lifecycle awareness so backgrounding stops DB collection
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val noteState by viewModel.noteState.collectAsStateWithLifecycle()

    var selectedFolder by remember { mutableStateOf<FolderEntity?>(null) }

    val colorScheme = if (isSystemInDarkTheme()) NotionDarkColors else NotionLightColors

    MaterialTheme(colorScheme = colorScheme) {
        if (selectedFolder == null) {
            TopicListScreen(
                folders = folders,
                onFolderClick = { folder ->
                    selectedFolder = folder
                    viewModel.selectFolder(folder.id)
                },
                onCreateFolder = { folderName ->
                    viewModel.createFolder(folderName)
                },
                onStartServer = {
                    bluetoothController.startServer()
                }
            )
        } else {
            NoteListScreen(
                state = noteState,
                onSaveNote = { title, content -> viewModel.saveNote(title, content) },
                onDeleteNote = { id -> viewModel.deleteNoteById(id) },
                onBack = {
                    selectedFolder = null
                    viewModel.selectFolder(null)
                }
            )
        }
    }
}