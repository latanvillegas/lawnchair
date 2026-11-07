package app.lawnchair.icons.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import com.android.launcher3.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable import actions for the per-app icon picker.
 * Calls [onImport] with the persisted filename, not a transient content URI.
 * The caller must register that filename with the selected app's icon override.
 */
@Composable
fun ImportedIconPickerActions(
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingIcon by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // Copying an original happens before the user accepts it. Track unconfirmed
    // files across IO cancellation and dispose so leaving the picker won't leak
    // 32 MiB per abandoned selection into app-private storage.
    val unconfirmed = remember { ConcurrentHashMap.newKeySet<String>() }
    val disposed = remember { AtomicBoolean(false) }

    DisposableEffect(context) {
        onDispose {
            disposed.set(true)
            val abandoned = unconfirmed.toList()
            unconfirmed.clear()
            if (abandoned.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    abandoned.forEach { ImportedIconStore.discardUnconfirmed(context, it) }
                }
            }
        }
    }

    fun importSelected(uri: Uri?) {
        if (uri == null || importing) return
        importing = true
        error = null
        scope.launch {
            try {
                val (name, bitmap) = withContext(Dispatchers.IO) {
                    val importedName = ImportedIconStore.importFromUri(context, uri)
                    unconfirmed.add(importedName)
                    try {
                        if (disposed.get()) throw CancellationException("Icon picker disposed")
                        importedName to ImportedIconStore.decode(context, importedName, 512)
                    } catch (failure: Throwable) {
                        unconfirmed.remove(importedName)
                        ImportedIconStore.discardUnconfirmed(context, importedName)
                        throw failure
                    }
                }
                if (disposed.get()) {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        ImportedIconStore.discardUnconfirmed(context, name)
                    }
                    unconfirmed.remove(name)
                    return@launch
                }
                val previous = pendingIcon
                pendingIcon = name
                preview = bitmap.asImageBitmap()
                if (previous != null && previous != name) {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        ImportedIconStore.discardUnconfirmed(context, previous)
                    }
                    unconfirmed.remove(previous)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                Text(stringResource(id = R.string.icon_import_gallery))
            }
        }
        TextButton(
            onClick = { files.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
            enabled = !importing,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Text(stringResource(id = R.string.icon_import_files))
            }
        }
        if (importing) Text(stringResource(id = R.string.icon_import_in_progress))
        preview?.let { image ->
            Image(bitmap = image, contentDescription = stringResource(id = R.string.icon_import_preview), modifier = Modifier.size(96.dp))
        }
        if (pendingIcon != null) {
            TextButton(onClick = {
                val selected = pendingIcon
                if (selected != null) {
                    try {
                        // Only the confirmed original becomes a persistent override.
                        onImport(selected)
                        unconfirmed.remove(selected)
                        pendingIcon = null
                        preview = null
                    } catch (exception: Exception) {
                        error = exception.localizedMessage ?: "Unable to assign icon"
                    }
                }
            }) { Text(stringResource(id = R.string.icon_import_confirm)) }
        }
        error?.let { Text(it) }
    }
}
