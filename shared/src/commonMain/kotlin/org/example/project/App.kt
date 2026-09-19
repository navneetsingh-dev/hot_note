package org.example.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.example.project.database.DatabaseDriverFactory
import org.example.project.database.HotNoteDatabase
import org.example.project.database.NoteRepository
import org.example.project.ui.NoteListScreen
private val DarkGreyColors = darkColorScheme(
    primary = Color(0xFF9E9E9E),
    secondary = Color(0xFFBDBDBD),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C),
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color.White
)

private val LightGreyColors = lightColorScheme(
    primary = Color(0xFF424242),
    secondary = Color(0xFF616161),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE0E0E0),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black
)

@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    val colourscheme = if(isSystemInDarkTheme()) DarkGreyColors else LightGreyColors
    val database = remember { HotNoteDatabase(driverFactory.createDriver()) }
    val repository = remember { NoteRepository(database) }
    val viewModel = remember { NoteViewModel(repository) }
    val state by viewModel.state.collectAsState()

    MaterialTheme(colourscheme) {
        NoteListScreen(
            state = state,
            onSaveNote = { title, content -> viewModel.saveNote(title, content) },
            onDeleteNote = { id -> viewModel.deleteNoteById(id) }
        )
    }
}