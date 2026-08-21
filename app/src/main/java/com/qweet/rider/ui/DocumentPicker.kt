package com.qweet.rider.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Reads the picked file fully into memory and wraps it as a multipart form part. Fine for
 * the small ID/RC/PAN photos and PDFs this app deals with (capped at 5MB server-side). */
fun Context.uriToMultipartPart(uri: Uri, partName: String): MultipartBody.Part? {
    val resolver = contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
    val ext = when {
        mime.contains("pdf") -> "pdf"
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        else -> "jpg"
    }
    val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(partName, "$partName.$ext", body)
}

/** A tappable card that lets the rider pick an image or PDF for one document field. Shows a
 * green check once something is picked (or the field already has a previously uploaded file). */
@Composable
fun DocumentPickerRow(
    label: String,
    hasExistingFile: Boolean,
    pickedFileName: String?,
    onPicked: (Uri) -> Unit,
    acceptPdf: Boolean = true
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPicked(uri)
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                val statusText = pickedFileName ?: if (hasExistingFile) "Uploaded" else "Not uploaded"
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pickedFileName != null || hasExistingFile)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pickedFileName != null || hasExistingFile) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(end = 8.dp)
                )
            }
            OutlinedButton(
                onClick = { launcher.launch(if (acceptPdf) "*/*" else "image/*") },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (pickedFileName != null || hasExistingFile) "Replace" else "Upload", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, tone: BadgeTone) {
    val (bg, fg) = when (tone) {
        BadgeTone.SUCCESS -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.secondary
        BadgeTone.WARNING -> androidx.compose.ui.graphics.Color(0xFFFFF3E0) to androidx.compose.ui.graphics.Color(0xFFE65100)
        BadgeTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
    }
}

enum class BadgeTone { SUCCESS, WARNING, ERROR, NEUTRAL }
