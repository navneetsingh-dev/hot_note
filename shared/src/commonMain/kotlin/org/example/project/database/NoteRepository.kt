package org.example.project.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class NoteRepository(private val db: HotNoteDatabase) {
    private val queries = db.noteQueries

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // ====================
    // NOTEBOOK (FOLDER) OPERATIONS
    // ====================

    fun getAllFolders(): Flow<List<FolderEntity>> {
        return queries.getAllFolders()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun getFoldersSnapshot(): List<FolderEntity> = withContext(Dispatchers.IO) {
        queries.getAllFolders().executeAsList()
    }

    suspend fun createFolder(
        name: String,
        colorHex: String = "#7719AA",
        createdAt: Long = nowMillis()
    ): Long = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            queries.insertFolder(
                name = name.trim().ifBlank { "Untitled Notebook" },
                createdAt = createdAt,
                colorHex = colorHex
            )
            val folderId = queries.lastInsertRowId().executeAsOne()
            queries.insertSection(
                folderId = folderId,
                name = "Quick Notes",
                colorHex = colorHex,
                createdAt = createdAt
            )
            folderId
        }
    }

    suspend fun updateFolder(id: Long, name: String, colorHex: String) = withContext(Dispatchers.IO) {
        queries.updateFolder(
            name = name.trim().ifBlank { "Untitled Notebook" },
            colorHex = colorHex,
            id = id
        )
    }

    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        db.transaction {
            val notes = queries.getNotesByFolderId(id).executeAsList()
            for (note in notes) {
                queries.deleteNoteById(note.id)
            }
            val sections = queries.getSectionsByFolderId(id).executeAsList()
            for (section in sections) {
                queries.deleteSectionById(section.id)
            }
            queries.deleteFolderById(id)
        }
    }

    suspend fun ensureDefaultNotebook(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            val folders = queries.getAllFolders().executeAsList()
            val folderId = if (folders.isEmpty()) {
                val now = nowMillis()
                queries.insertFolder(
                    name = "My Notebook",
                    createdAt = now,
                    colorHex = "#7719AA"
                )
                queries.lastInsertRowId().executeAsOne()
            } else {
                folders.first().id
            }

            val sections = queries.getSectionsByFolderId(folderId).executeAsList()
            val sectionId = if (sections.isEmpty()) {
                queries.insertSection(
                    folderId = folderId,
                    name = "Quick Notes",
                    colorHex = "#7719AA",
                    createdAt = nowMillis()
                )
                queries.lastInsertRowId().executeAsOne()
            } else {
                sections.first().id
            }
            folderId to sectionId
        }
    }

    // ====================
    // SECTION OPERATIONS
    // ====================

    fun getAllSections(): Flow<List<SectionEntity>> {
        return queries.getAllSections()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getSectionsByFolderId(folderId: Long): Flow<List<SectionEntity>> {
        return queries.getSectionsByFolderId(folderId)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun createSection(
        folderId: Long,
        name: String,
        colorHex: String = "#7719AA",
        createdAt: Long = nowMillis()
    ): Long = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            queries.insertSection(
                folderId = folderId,
                name = name.trim().ifBlank { "New Section" },
                colorHex = colorHex,
                createdAt = createdAt
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun updateSection(id: Long, name: String, colorHex: String) = withContext(Dispatchers.IO) {
        queries.updateSection(
            name = name.trim().ifBlank { "New Section" },
            colorHex = colorHex,
            id = id
        )
    }

    suspend fun deleteSection(id: Long) = withContext(Dispatchers.IO) {
        db.transaction {
            val notes = queries.getNotesBySectionId(id).executeAsList()
            for (note in notes) {
                queries.deleteNoteById(note.id)
            }
            queries.deleteSectionById(id)
        }
    }

    // ====================
    // NOTE (PAGE) OPERATIONS
    // ====================

    fun getAllNotes(): Flow<List<NoteEntity>> {
        return queries.getAllNotes()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun getAllNotesSnapshot(): List<NoteEntity> = withContext(Dispatchers.IO) {
        queries.getAllNotes().executeAsList()
    }

    fun getNotesByFolderId(folderId: Long): Flow<List<NoteEntity>> {
        return queries.getNotesByFolderId(folderId)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getNotesBySectionId(sectionId: Long): Flow<List<NoteEntity>> {
        return queries.getNotesBySectionId(sectionId)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return queries.searchNotes(query)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun saveNote(
        id: String,
        folderId: Long,
        sectionId: Long? = null,
        title: String,
        contentBlocks: String,
        createdAt: Long,
        updatedAt: Long,
        isPinned: Boolean = false,
        attachmentsJson: String = "[]"
    ) = withContext(Dispatchers.IO) {
        queries.insertNote(
            id = id,
            folderId = folderId,
            title = title.ifBlank { "Untitled Page" },
            contentBlocks = contentBlocks,
            createdAt = createdAt,
            updatedAt = updatedAt,
            sectionId = sectionId,
            isPinned = if (isPinned) 1L else 0L,
            attachmentsJson = attachmentsJson
        )
    }

    suspend fun setNotePinned(id: String, pinned: Boolean, updatedAt: Long = nowMillis()) = withContext(Dispatchers.IO) {
        queries.updateNotePinned(
            isPinned = if (pinned) 1L else 0L,
            updatedAt = updatedAt,
            id = id
        )
    }

    suspend fun getNoteById(id: String): NoteEntity? = withContext(Dispatchers.IO) {
        queries.getNoteById(id).executeAsOneOrNull()
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        queries.deleteNoteById(id)
    }

    // ====================
    // SHARED FILE OPERATIONS
    // ====================

    fun getAllSharedFiles(): Flow<List<SharedFileEntity>> {
        return queries.getAllSharedFiles()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun getAllSharedFilesSnapshot(): List<SharedFileEntity> = withContext(Dispatchers.IO) {
        queries.getAllSharedFiles().executeAsList()
    }

    suspend fun getSharedFileById(id: String): SharedFileEntity? = withContext(Dispatchers.IO) {
        queries.getSharedFileById(id).executeAsOneOrNull()
    }

    suspend fun saveSharedFile(
        id: String,
        fileName: String,
        filePath: String,
        mimeType: String,
        sizeBytes: Long,
        sha256: String,
        direction: String,
        peerName: String,
        createdAt: Long = nowMillis()
    ) = withContext(Dispatchers.IO) {
        queries.insertSharedFile(
            id = id,
            fileName = fileName,
            filePath = filePath,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            direction = direction,
            peerName = peerName,
            createdAt = createdAt
        )
    }

    suspend fun deleteSharedFile(id: String) = withContext(Dispatchers.IO) {
        queries.deleteSharedFileById(id)
    }
}
