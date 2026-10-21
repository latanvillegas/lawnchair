package app.lawnchair.icons.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable import actions for the per-app icon picker.
 * Calls [onImported] with the persisted filename, not a transient content URI.
 * The caller must register that filename with the selected app's icon override.
 */
@Composable
fun ImportedIconPickerActions(
    onImported: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingIcon by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    fun importSelected(uri: Uri?) {
        if (uri == null || importing) return
        importing = true
        error = null
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    ImportedIconStore.import(context, uri)
                }
                val bitmap = withContext(Dispatchers.IO) {
                    ImportedIconStore.decode(context, name, 512)
                }
                pendingIcon = name
                preview = bitmap.asImageBitmap()
            } catch (exception: Exception) {
                error = exception.localizedMessage ?: "Unable to import icon"
            } finally {
                importing = false
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> importSelected(uri) }

    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> importSelected(uri) }

    Column(modifier = modifier) {
        TextButton(onClick = { gallery.launch("image/*") }, enabled = !importing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text("Galería")
            }
        }
        TextButton(
            onClick = { files.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
            enabled = !importing,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Text("Archivos")
            }
        }
        if (importing) Text("Importing image…")
        preview?.let { image ->
            Image(bitmap = image, contentDescription = "Selected icon preview", modifier = Modifier.size(96.dp))
        }
        if (pendingIcon != null) {
            TextButton(onClick = {
                pendingIcon?.let(onImported)
                pendingIcon = null
                preview = null
            }) { Text("Use this icon") }
        }
        error?.let { Text(it) }
    }
}
