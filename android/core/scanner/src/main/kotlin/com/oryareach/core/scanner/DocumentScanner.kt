package com.oryareach.core.scanner

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

data class ScannedDocument(val name: String, val mimeType: String, val bytes: ByteArray)

/**
 * Launches Google Play services' on-device document scanner — crop, enhance, multi-page — and
 * hands back one merged PDF. Entirely on-device: no cloud vision/OCR call, which matters here
 * since the app's whole design is that Supabase never sees plaintext (see PROGRESS.md's
 * Deferred section on why cloud OCR is ruled out for this app).
 *
 * Returns a launch function; callers wire it to a button. A no-op is returned if the current
 * context is not an [Activity] (the scanner's intent-sender API needs one), which should not
 * happen inside a normal screen.
 */
@Composable
fun rememberDocumentScanner(onScanned: (ScannedDocument) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val callback = rememberUpdatedState(onScanned)

    // GmsDocumentScanning.getClient() can throw (observed: NPE deep in ML Kit's internals on
    // devices where Play Services doesn't support the document-scanner module) — caught here so
    // a scanner-unavailable device doesn't crash the whole Documents screen just for opening it.
    val scanner = remember {
        runCatching {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(20)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
        }.getOrNull()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
        val pdf = scan.pdf ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(pdf.uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        callback.value(ScannedDocument(name = "Scan-${System.currentTimeMillis()}.pdf", mimeType = "application/pdf", bytes = bytes))
    }

    if (activity == null || scanner == null) return {}

    return {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender -> launcher.launch(IntentSenderRequest.Builder(intentSender).build()) }
            .addOnFailureListener { }
    }
}
