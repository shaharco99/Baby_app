package com.oryareach.core.scanner

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Renders [content] as a QR code, so a partner's phone can scan it straight off this screen
 * instead of the invite code being read aloud and typed in by hand. Encoded on-device with
 * ZXing's pure-Java writer — no network call.
 */
@Composable
fun InvitationQrCode(
    content: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = 512,
) {
    val bitmap = remember(content, sizePx) { qrCodeBitmap(content, sizePx) }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, modifier = modifier)
}

private fun qrCodeBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }
}
