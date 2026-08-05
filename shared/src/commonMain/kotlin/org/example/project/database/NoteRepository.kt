package org.example.project.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class NoteRepository(db: HotNoteDatabase) {
    private val queries = db.noteQueries

    // Get all notes as a reactive Flow for your UI
    fun getAllNotes(): Flow<List<NoteEntity>> {
        return queries.getAllNotes()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    // Insert or update a note
    fun saveNote(id: String, title: String, contentBlocks: String, createdAt: Long, updatedAt: Long) {
        queries.insertNote(
            id = id,
            title = title,
            contentBlocks = contentBlocks,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // Get a single note by its ID
    fun getNoteById(id: String): NoteEntity? {
        return queries.getNoteById(id).executeAsOneOrNull()
    }

    // Delete a note by its ID
    fun deleteNote(id: String) {
        queries.deleteNoteById(id)
    }
}