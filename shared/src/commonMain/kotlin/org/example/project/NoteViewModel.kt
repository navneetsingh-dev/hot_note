package org.example.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository

// 1. Define the UI State
data class NoteListState(
    val notes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = false
)

// 2. Create the ViewModel
class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    // Transform the repository Flow into a StateFlow that Compose can easily observe
    val state: StateFlow<NoteListState> = repository.getAllNotes()
        .map { notes -> NoteListState(notes = notes, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NoteListState(isLoading = true)
        )

    // Handle user intents
    fun deleteNoteById(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }
    fun saveNote(title: String, content: String) {
        viewModelScope.launch {
            // Generating a random ID since we aren't using a UUID library yet
            val id = kotlin.random.Random.nextLong().toString()
            val timestamp = 0L

            repository.saveNote(
                id = id,
                title = title,
                contentBlocks = content,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        }
    }
}