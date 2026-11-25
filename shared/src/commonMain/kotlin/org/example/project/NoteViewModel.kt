package org.example.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.database.FolderEntity
import org.example.project.database.NoteEntity
import org.example.project.database.NoteRepository
import org.example.project.database.SectionEntity
import org.example.project.database.SharedFileEntity
import org.example.project.model.ConflictResolution
import org.example.project.model.NoteConflict
import org.example.project.model.NoteFilter
import org.example.project.model.NoteTransferPayload
import org.example.project.model.SafeNoteCodec
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class NoteListState(
    val notes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = false
)

enum class AppDestination {
    HOME,
    NOTEBOOKS,
    P2P_SHARE
}

@OptIn(ExperimentalTime::class)
class NoteViewModel(
    private val repository: NoteRepository,
    private val driver: SqlDriver? = null
) : ViewModel() {

    private val _destination = MutableStateFlow(AppDestination.HOME)
    val destination: StateFlow<AppDestination> = _destination.asStateFlow()

    private val _activeFolderId = MutableStateFlow<Long?>(null)
    val activeFolderId: StateFlow<Long?> = _activeFolderId.asStateFlow()

    private val _activeSectionId = MutableStateFlow<Long?>(null)
    val activeSectionId: StateFlow<Long?> = _activeSectionId.asStateFlow()

    private val _selectedNoteId = MutableStateFlow<String?>(null)
    val selectedNoteId: StateFlow<String?> = _selectedNoteId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _noteFilter = MutableStateFlow(NoteFilter.ALL)
    val noteFilter: StateFlow<NoteFilter> = _noteFilter.asStateFlow()

    private val _pendingConflicts = MutableStateFlow<List<NoteConflict>>(emptyList())
    val pendingConflicts: StateFlow<List<NoteConflict>> = _pendingConflicts.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultNotebook()
        }
    }

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSections: StateFlow<List<SectionEntity>> = repository.getAllSections()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSections: StateFlow<List<SectionEntity>> = _activeFolderId
        .flatMapLatest { folderId ->
            if (folderId != null) repository.getSectionsByFolderId(folderId) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allNotes: StateFlow<List<NoteEntity>> = repository.getAllNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHomeNotes: StateFlow<List<NoteEntity>> = combine(
        allNotes,
        _searchQuery,
        _noteFilter
    ) { notes, query, filter ->
        applyFilterAndSearch(notes, query, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val noteState: StateFlow<NoteListState> = combine(_activeFolderId, _activeSectionId) { fId, sId ->
        fId to sId
    }.flatMapLatest { (folderId, sectionId) ->
        when {
            sectionId != null -> repository.getNotesBySectionId(sectionId)
            folderId != null -> repository.getNotesByFolderId(folderId)
            else -> flowOf(emptyList())
        }
    }.map { notes ->
        NoteListState(notes = notes, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NoteListState(isLoading = true)
    )

    val sharedFiles: StateFlow<List<SharedFileEntity>> = repository.getAllSharedFiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun applyFilterAndSearch(
        notes: List<NoteEntity>,
        query: String,
        filter: NoteFilter
    ): List<NoteEntity> {
        val q = query.trim().lowercase()
        return notes.filter { note ->
            val matchesQuery = q.isEmpty() ||
                note.title.lowercase().contains(q) ||
                note.contentBlocks.lowercase().contains(q)
            if (!matchesQuery) return@filter false

            val pages = SafeNoteCodec.splitPages(note.contentBlocks)
            val hasInk = pages.any { SafeNoteCodec.isDrawingPage(it) }
            val hasText = pages.any { !SafeNoteCodec.isDrawingPage(it) && it.isNotBlank() }
            val hasAttachments = SafeNoteCodec.parseAttachments(note.attachmentsJson).isNotEmpty()

            when (filter) {
                NoteFilter.ALL -> true
                NoteFilter.TEXT_ONLY -> hasText && !hasInk
                NoteFilter.INK_ONLY -> hasInk
                NoteFilter.PINNED -> note.isPinned != 0L
                NoteFilter.WITH_ATTACHMENTS -> hasAttachments
            }
        }
    }

    // --- NAVIGATION & SEARCH INTENTS ---

    fun setDestination(dest: AppDestination) {
        _destination.value = dest
    }

    fun selectFolder(folderId: Long?) {
        _activeFolderId.value = folderId
        if (folderId == null) {
            _activeSectionId.value = null
            _selectedNoteId.value = null
        } else {
            val firstSection = allSections.value.firstOrNull { it.folderId == folderId }
            _activeSectionId.value = firstSection?.id
        }
    }

    fun selectSection(sectionId: Long?) {
        _activeSectionId.value = sectionId
        _selectedNoteId.value = null
    }

    fun selectNote(noteId: String?) {
        _selectedNoteId.value = noteId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: NoteFilter) {
        _noteFilter.value = filter
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
    }

    // --- NOTEBOOK & SECTION CRUD ---

    fun createFolder(name: String, colorHex: String = "#7719AA") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createFolder(name = name, colorHex = colorHex)
            _activeFolderId.value = newId
            val sections = allSections.value.filter { it.folderId == newId }
            _activeSectionId.value = sections.firstOrNull()?.id
        }
    }

    fun updateFolder(id: Long, name: String, colorHex: String) {
        viewModelScope.launch {
            repository.updateFolder(id, name, colorHex)
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            repository.deleteFolder(id)
            if (_activeFolderId.value == id) {
                _activeFolderId.value = null
                _activeSectionId.value = null
                _selectedNoteId.value = null
            }
        }
    }

    fun createSection(folderId: Long, name: String, colorHex: String = "#7719AA") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val secId = repository.createSection(folderId = folderId, name = name, colorHex = colorHex)
            if (_activeFolderId.value == folderId) {
                _activeSectionId.value = secId
            }
        }
    }

    fun updateSection(id: Long, name: String, colorHex: String) {
        viewModelScope.launch {
            repository.updateSection(id, name, colorHex)
        }
    }

    fun deleteSection(id: Long) {
        viewModelScope.launch {
            repository.deleteSection(id)
            if (_activeSectionId.value == id) {
                _activeSectionId.value = null
                _selectedNoteId.value = null
            }
        }
    }

    // --- NOTE CRUD ---

    fun saveNote(
        title: String,
        content: String,
        existingNote: NoteEntity? = null,
        attachmentsJson: String = existingNote?.attachmentsJson ?: "[]",
        targetFolderId: Long? = null,
        targetSectionId: Long? = null
    ) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val resolvedFolderId: Long
            val resolvedSectionId: Long?

            if (existingNote != null) {
                resolvedFolderId = targetFolderId ?: existingNote.folderId
                resolvedSectionId = targetSectionId ?: existingNote.sectionId
            } else if (targetFolderId != null) {
                resolvedFolderId = targetFolderId
                resolvedSectionId = targetSectionId
            } else if (_activeFolderId.value != null) {
                resolvedFolderId = _activeFolderId.value!!
                resolvedSectionId = _activeSectionId.value
            } else {
                val (defFolder, defSection) = repository.ensureDefaultNotebook()
                resolvedFolderId = defFolder
                resolvedSectionId = defSection
            }

            val noteId = existingNote?.id ?: generateUniqueId(now)
            val createdAt = existingNote?.createdAt ?: now
            val pinned = (existingNote?.isPinned ?: 0L) != 0L

            repository.saveNote(
                id = noteId,
                folderId = resolvedFolderId,
                sectionId = resolvedSectionId,
                title = title.ifBlank { "Untitled Page" },
                contentBlocks = content,
                createdAt = createdAt,
                updatedAt = now,
                isPinned = pinned,
                attachmentsJson = attachmentsJson
            )
            _selectedNoteId.value = noteId
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            repository.setNotePinned(
                id = note.id,
                pinned = note.isPinned == 0L,
                updatedAt = now
            )
        }
    }

    fun deleteNoteById(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
            if (_selectedNoteId.value == id) {
                _selectedNoteId.value = null
            }
        }
    }

    // --- P2P INCOMING NOTE & CONFLICT RESOLUTION ---

    fun importIncomingNote(payload: NoteTransferPayload) {
        viewModelScope.launch {
            val existing = repository.getNoteById(payload.id)
            if (existing == null) {
                val (defFolder, defSection) = repository.ensureDefaultNotebook()
                val folderId = _activeFolderId.value ?: defFolder
                val sectionId = _activeSectionId.value ?: defSection
                repository.saveNote(
                    id = payload.id,
                    folderId = folderId,
                    sectionId = sectionId,
                    title = payload.title,
                    contentBlocks = payload.contentBlocks,
                    createdAt = payload.createdAt.takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds(),
                    updatedAt = payload.updatedAt.takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds(),
                    isPinned = false,
                    attachmentsJson = payload.attachmentsJson
                )
                _statusMessage.value = "Received note \"${payload.title}\" from ${payload.senderName}"
            } else if (existing.contentBlocks == payload.contentBlocks && existing.title == payload.title) {
                _statusMessage.value = "Note \"${payload.title}\" is already up to date."
            } else {
                _pendingConflicts.update { list ->
                    if (list.any { it.incoming.id == payload.id }) list else list + NoteConflict(payload, existing)
                }
            }
        }
    }

    fun resolveConflict(conflict: NoteConflict, resolution: ConflictResolution) {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            when (resolution) {
                ConflictResolution.KEEP_LOCAL -> {
                    _statusMessage.value = "Kept local version of \"${conflict.existing.title}\""
                }
                ConflictResolution.REPLACE_LOCAL -> {
                    repository.saveNote(
                        id = conflict.existing.id,
                        folderId = conflict.existing.folderId,
                        sectionId = conflict.existing.sectionId,
                        title = conflict.incoming.title,
                        contentBlocks = conflict.incoming.contentBlocks,
                        createdAt = conflict.existing.createdAt,
                        updatedAt = maxOf(now, conflict.incoming.updatedAt),
                        isPinned = conflict.existing.isPinned != 0L,
                        attachmentsJson = conflict.incoming.attachmentsJson
                    )
                    _statusMessage.value = "Updated \"${conflict.incoming.title}\" from ${conflict.incoming.senderName}"
                }
                ConflictResolution.KEEP_BOTH -> {
                    repository.saveNote(
                        id = generateUniqueId(now),
                        folderId = conflict.existing.folderId,
                        sectionId = conflict.existing.sectionId,
                        title = "${conflict.incoming.title} (from ${conflict.incoming.senderName})",
                        contentBlocks = conflict.incoming.contentBlocks,
                        createdAt = now,
                        updatedAt = now,
                        isPinned = false,
                        attachmentsJson = conflict.incoming.attachmentsJson
                    )
                    _statusMessage.value = "Saved copy of \"${conflict.incoming.title}\""
                }
            }
            _pendingConflicts.update { list -> list.filterNot { it.incoming.id == conflict.incoming.id } }
        }
    }

    fun generateUniqueId(nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String {
        val rand = Random.nextInt(100000, 999999)
        return "note_${nowMillis}_$rand"
    }

    override fun onCleared() {
        super.onCleared()
        driver?.close()
    }
}
