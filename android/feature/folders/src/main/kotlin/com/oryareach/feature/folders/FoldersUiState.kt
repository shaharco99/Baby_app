package com.oryareach.feature.folders

import androidx.compose.runtime.Immutable
import com.oryareach.core.model.Document
import com.oryareach.core.model.Folder

@Immutable
data class FoldersUiState(
    // Persisted snapshot: the current folder's contents, already decrypted.
    val children: List<Folder> = emptyList(),
    val documents: List<Document> = emptyList(),
    /** Ancestors of the current folder, root first. Empty means we are at the root. */
    val breadcrumb: List<Folder> = emptyList(),

    // Editable input: the create/rename dialogs.
    val formName: String = "",
    val renamingFolder: Folder? = null,
    val renamingDocument: Document? = null,

    // Transient UI-only.
    val createDialogVisible: Boolean = false,
    val deleteConfirmFolder: Folder? = null,
    val importing: Boolean = false,
    val deleteConfirmDocument: Document? = null,
    val previewDocument: Document? = null,
    val previewContent: DocumentPreview? = null,
    val previewLoading: Boolean = false,
    val refreshing: Boolean = false,
) {
    val currentParentId: String? get() = breadcrumb.lastOrNull()?.id
    val canSubmitCreate: Boolean get() = formName.isNotBlank()
    val canSubmitRename: Boolean get() = formName.isNotBlank()
}

sealed interface DocumentPreview {
    data class Text(val content: String) : DocumentPreview
    data class Image(val bytes: ByteArray) : DocumentPreview
    data class Pdf(val pageBytes: ByteArray) : DocumentPreview
    data object Unsupported : DocumentPreview
    data object Failed : DocumentPreview
}
