package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.example.project.database.DatabaseDriverFactory
import org.example.project.database.HotNoteDatabase
import org.example.project.database.NoteRepository
import org.example.project.ui.NoteListScreen

@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    val database = remember { HotNoteDatabase(driverFactory.createDriver()) }
    val repository = remember { NoteRepository(database) }
    val viewModel = remember { NoteViewModel(repository) }
    val state by viewModel.state.collectAsState()

    MaterialTheme {
        NoteListScreen(
            state = state,
            onSaveNote = { title, content -> viewModel.saveNote(title, content) },
            onDeleteNote = { id -> viewModel.deleteNoteById(id) }
        )
    }
}