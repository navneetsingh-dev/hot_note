package org.example.project.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class NoteRepository(db: HotNoteDatabase) {
    // Assuming your file is named Note.sq, SQLDelight generates noteQueries
    private val queries = db.noteQueries

    // ====================
    // FOLDER OPERATIONS
    // ====================

    fun getAllFolders(): Flow<List<FolderEntity>> {
        return queries.getAllFolders()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun createFolder(name: String, createdAt: Long = System.currentTimeMillis()) {
        queries.insertFolder(
            name = name,
            createdAt = createdAt
        )
    }

    // ====================
    // NOTE OPERATIONS
    // ====================

    // Get notes only for the specific folder you clicked
    fun getNotesByFolderId(folderId: Long): Flow<List<NoteEntity>> {
        return queries.getNotesByFolderId(folderId)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    // Insert or update a note (now requires the folderId)
    fun saveNote(id: String, folderId: Long, title: String, contentBlocks: String, createdAt: Long, updatedAt: Long) {
        queries.insertNote(
            id = id,
            folderId = folderId,
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