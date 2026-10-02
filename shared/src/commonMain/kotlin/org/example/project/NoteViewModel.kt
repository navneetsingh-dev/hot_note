package org.example.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.example.project.database.FolderEntity
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository

data class NoteListState(
    val notes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = false
)

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    // Tracks which folder you currently have open
    private val _activeFolderId = MutableStateFlow<Long?>(null)

    // 1. Stream of all folders from the DB for Screen 1
    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 2. Stream of notes for Screen 2 (Automatically updates when _activeFolderId changes!)
    @OptIn(ExperimentalCoroutinesApi::class)
    val noteState: StateFlow<NoteListState> = _activeFolderId
        .flatMapLatest { folderId ->
            if (folderId != null) {
                repository.getNotesByFolderId(folderId)
            } else {
                flowOf(emptyList()) // If no folder selected, return empty list
            }
        }
        .map { notes -> NoteListState(notes = notes, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NoteListState(isLoading = true)
        )

    // --- INTENTS ---

    fun selectFolder(folderId: Long?) {
        _activeFolderId.value = folderId
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name = name)
        }
    }

    fun saveNote(title: String, content: String) {
        val folderId = _activeFolderId.value ?: return // Guard: Must have a folder selected

        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            repository.saveNote(
                id = currentTime.toString(), // Simple cross-platform unique ID
                folderId = folderId,
                title = title.ifBlank { "Untitled Note" },
                contentBlocks = content,
                createdAt = currentTime,
                updatedAt = currentTime
            )
        }
    }

    fun deleteNoteById(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }
}