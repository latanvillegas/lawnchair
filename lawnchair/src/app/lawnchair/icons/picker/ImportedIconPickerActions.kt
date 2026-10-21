package app.lawnchair.icons.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    fun importSelected(uri: Uri?) {
        if (uri == null || importing) return
        importing = true
        error = null
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    ImportedIconStore.import(context, uri)
                }
                onImported(name)
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

    Column {
        TextButton(onClick = { gallery.launch("image/*") }, enabled = !importing) {
            Text("Gallery")
        }
        TextButton(
            onClick = { files.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
            enabled = !importing,
        ) {
            Text("Files")
        }
        if (importing) Text("Importing image…")
        error?.let { Text(it) }
    }
}
