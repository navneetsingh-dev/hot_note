package org.example.project.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteRepository(db: HotNoteDatabase) {
    private val queries = db.noteQueries

    // ====================
    // FOLDER OPERATIONS
    // ====================

    fun getAllFolders(): Flow<List<FolderEntity>> {
        return queries.getAllFolders()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun createFolder(name: String, createdAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        queries.insertFolder(
            name = name,
            createdAt = createdAt
        )
    }

    // ====================
    // NOTE OPERATIONS
    // ====================

    fun getNotesByFolderId(folderId: Long): Flow<List<NoteEntity>> {
        return queries.getNotesByFolderId(folderId)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun saveNote(
        id: String,
        folderId: Long,
        title: String,
        contentBlocks: String,
        createdAt: Long,
        updatedAt: Long
    ) = withContext(Dispatchers.IO) {
        queries.insertNote(
            id = id,
            folderId = folderId,
            title = title,
            contentBlocks = contentBlocks,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    suspend fun getNoteById(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        queries.getNoteById(id).executeAsOneOrNull()
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        queries.deleteNoteById(id)
    }
}