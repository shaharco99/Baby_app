package com.oryareach.feature.folders

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.common.AppResult
import com.oryareach.core.database.repository.DocumentRepository
import com.oryareach.core.database.repository.FolderRepository
import com.oryareach.core.model.Document
import com.oryareach.core.model.Folder
import com.oryareach.core.network.auth.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@Stable
interface FoldersActions {
    fun onOpenFolder(folder: Folder)
    fun onNavigateUp()
    fun onNavigateToBreadcrumb(index: Int)
    fun onAddClick()
    fun onDismissCreateDialog()
    fun onNameChange(value: String)
    fun onCreateSubmit()
    fun onRenameClick(folder: Folder)
    fun onRenameChange(value: String)
    fun onRenameSubmit()
    fun onDismissRename()
    fun onDeleteClick(folder: Folder)
    fun onConfirmDelete()
    fun onDismissDelete()
    fun onDocumentPicked(name: String, mimeType: String, bytes: ByteArray)
    fun onDeleteDocumentClick(document: Document)
    fun onConfirmDeleteDocument()
    fun onDismissDeleteDocument()
    fun onRenameDocumentClick(document: Document)
    fun onRenameDocumentChange(value: String)
    fun onRenameDocumentSubmit()
    fun onDismissRenameDocument()
    fun onMoveDocument(document: Document, folderId: String)
    fun onPreviewDocument(document: Document)
    fun onDismissPreview()
    fun onRefresh()
}

/**
 * The workspace id is read once, same as every other tab's ViewModel: routing already
 * guarantees a paired, unlocked device by the time this screen is reachable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel(
    private val repository: FolderRepository,
    private val documents: DocumentRepository,
    private val auth: AuthRepository,
    private val syncEngine: com.oryareach.core.sync.SyncEngine,
    private val workspaceId: () -> String?,
) : ViewModel(), FoldersActions {

    private val _uiState = MutableStateFlow(FoldersUiState())
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    /** Kept separate from [_uiState] so editing the create/rename form never re-triggers
     * [FolderRepository.observeChildren] — only an actual navigation should. */
    private val currentParentId = MutableStateFlow<String?>(null)

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                currentParentId.flatMapLatest { parentId ->
                    combine(repository.observeChildren(id, parentId), documents.observeInFolder(id, parentId)) {
                        children, docs -> children to docs
                    }
                }.collect { (children, docs) -> set { it.copy(children = children, documents = docs) } }
            }
        }
    }

    override fun onOpenFolder(folder: Folder) = navigateTo(_uiState.value.breadcrumb + folder)

    override fun onNavigateUp() = navigateTo(_uiState.value.breadcrumb.dropLast(1))

    override fun onNavigateToBreadcrumb(index: Int) = navigateTo(
        if (index < 0) emptyList() else _uiState.value.breadcrumb.take(index + 1),
    )

    private fun navigateTo(breadcrumb: List<Folder>) {
        set { it.copy(breadcrumb = breadcrumb) }
        currentParentId.value = breadcrumb.lastOrNull()?.id
    }

    override fun onAddClick() = set { it.copy(createDialogVisible = true, formName = "") }
    override fun onDismissCreateDialog() = set { it.copy(createDialogVisible = false) }
    override fun onNameChange(value: String) = set { it.copy(formName = value) }

    override fun onCreateSubmit() {
        val state = _uiState.value
        val workspace = workspaceId() ?: return
        if (!state.canSubmitCreate) return

        viewModelScope.launch {
            repository.create(workspace, auth.currentUserId().orEmpty(), state.formName.trim(), state.currentParentId)
            set { it.copy(createDialogVisible = false, formName = "") }
        }
    }

    override fun onRenameClick(folder: Folder) = set { it.copy(renamingFolder = folder, formName = folder.name) }
    override fun onRenameChange(value: String) = set { it.copy(formName = value) }
    override fun onDismissRename() = set { it.copy(renamingFolder = null) }

    override fun onRenameSubmit() {
        val state = _uiState.value
        val folder = state.renamingFolder ?: return
        if (!state.canSubmitRename) return

        viewModelScope.launch {
            repository.rename(folder.id, state.formName.trim())
            set { it.copy(renamingFolder = null) }
        }
    }

    override fun onDeleteClick(folder: Folder) = set { it.copy(deleteConfirmFolder = folder) }
    override fun onDismissDelete() = set { it.copy(deleteConfirmFolder = null) }

    override fun onConfirmDelete() {
        val workspace = workspaceId() ?: return
        val folder = _uiState.value.deleteConfirmFolder ?: return

        viewModelScope.launch {
            repository.delete(workspace, folder.id)
            set { it.copy(deleteConfirmFolder = null) }
        }
    }

    override fun onDocumentPicked(name: String, mimeType: String, bytes: ByteArray) {
        val workspace = workspaceId() ?: return
        val parentId = _uiState.value.currentParentId

        viewModelScope.launch {
            set { it.copy(importing = true) }
            documents.upload(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                folderId = parentId,
                name = name,
                mimeType = mimeType,
                bytes = bytes,
            )
            set { it.copy(importing = false) }
        }
    }

    override fun onDeleteDocumentClick(document: Document) = set { it.copy(deleteConfirmDocument = document) }
    override fun onDismissDeleteDocument() = set { it.copy(deleteConfirmDocument = null) }

    override fun onConfirmDeleteDocument() {
        val document = _uiState.value.deleteConfirmDocument ?: return
        viewModelScope.launch {
            documents.delete(document.id)
            set { it.copy(deleteConfirmDocument = null) }
        }
    }

    override fun onRenameDocumentClick(document: Document) =
        set { it.copy(renamingDocument = document, formName = document.name) }
    override fun onRenameDocumentChange(value: String) = set { it.copy(formName = value) }
    override fun onDismissRenameDocument() = set { it.copy(renamingDocument = null) }

    override fun onRenameDocumentSubmit() {
        val state = _uiState.value
        val document = state.renamingDocument ?: return
        if (!state.canSubmitRename) return

        viewModelScope.launch {
            documents.rename(document.id, state.formName.trim())
            set { it.copy(renamingDocument = null) }
        }
    }

    override fun onMoveDocument(document: Document, folderId: String) {
        if (document.folderId == folderId) return
        viewModelScope.launch { documents.move(document.id, folderId) }
    }

    override fun onPreviewDocument(document: Document) {
        set { it.copy(previewDocument = document, previewContent = null, previewLoading = true) }
        viewModelScope.launch {
            val content = when (val result = documents.download(document.id)) {
                is AppResult.Failure -> DocumentPreview.Failed
                is AppResult.Success -> toPreview(document.mimeType, result.data)
            }
            set { it.copy(previewContent = content, previewLoading = false) }
        }
    }

    override fun onDismissPreview() = set { it.copy(previewDocument = null, previewContent = null) }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }
        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun toPreview(mimeType: String, bytes: ByteArray): DocumentPreview = when {
        mimeType.startsWith("text/") || mimeType == "application/json" ->
            DocumentPreview.Text(bytes.toString(Charsets.UTF_8))
        mimeType.startsWith("image/") -> DocumentPreview.Image(bytes)
        mimeType == "application/pdf" -> DocumentPreview.Pdf(bytes)
        else -> DocumentPreview.Unsupported
    }

    private fun set(block: (FoldersUiState) -> FoldersUiState) {
        _uiState.value = block(_uiState.value)
    }
}
