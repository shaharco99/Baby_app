package com.oryareach.core.scanner

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Launches Google Play services' on-device QR scanner so a partner's invite code can be read
 * straight off their screen instead of typed out character by character. Same "no cloud call"
 * shape as [rememberDocumentScanner] — Play services owns the camera permission prompt and the
 * scanning activity itself, so there is nothing to declare in the manifest.
 *
 * Returns a launch function; callers wire it to a button. A no-op is returned if the current
 * context is not an [Activity] (the scanner needs one to launch its own screen), which should
 * not happen inside a normal screen.
 */
@Composable
fun rememberInvitationCodeScanner(onScanned: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val callback = rememberUpdatedState(onScanned)

    // GmsBarcodeScanning.getClient() can throw (see rememberDocumentScanner's note on
    // GmsDocumentScanning.getClient — same ML Kit module, same failure mode observed on-device)
    // — caught so an unavailable scanner doesn't crash the screen it's used from.
    val scanner = remember {
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(context, options)
        }.getOrNull()
    }

    if (activity == null || scanner == null) return {}

    return {
        scanner.startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let { callback.value(it) } }
            .addOnFailureListener { }
    }
}
