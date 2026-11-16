package app.loki.anithere.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.loki.kit.SheetDialog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Preferences: translated from `dialogs.rs` (fn `preferences`, line 366) by egui2compose.
 *
 * The controls and their order are the desktop dialog's. NOT translated: the starting values (the
 * desktop reads them from its own state), anything it draws by hand, and enabling rules. The
 * parameter names are guessed from the Rust fields each control was bound to: check them against
 * the command's params doc before trusting OK.
 *   command: (not found)
 *   params:  (unknown)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesDialog(onRun: (command: String, params: JsonObject) -> Unit, onDismiss: () -> Unit) {
    var f0 by remember { mutableStateOf("") }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf(false) }
    var f3 by remember { mutableStateOf(false) }
    var f4 by remember { mutableStateOf(false) }
    var f5 by remember { mutableStateOf(false) }
    var f6 by remember { mutableStateOf(false) }
    var f7 by remember { mutableStateOf("") }
    SheetDialog(
        "Preferences", "", onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val params = buildJsonObject {
                if (f0.isNotBlank()) put("language", f0)
                f1.trim().replace(',', '.').toDoubleOrNull()?.let { put("timelineStartGap", it) }
                put("ui", f2)
                put("ui", f3)
                put("ui", f4)
                put("ui", f5)
                put("ui", f6)
                f7.trim().replace(',', '.').toDoubleOrNull()?.let { put("autosaveEdits", it) }
                }
                /* TODO: which command this dialog runs was not found */
                onDismiss()
            }) { Text("OK") }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Language", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(f0 == "auto.to_string()", { f0 = "auto.to_string()" }, label = { Text("System language ({name})") })
            }
            OutlinedTextField(f1, { f1 = it }, label = { Text("Frame {frame} · {seconds} s · X {x} Y {y} · {keys}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clickable { f2 = !f2 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f2, { f2 = it })
                Text("Ui")
            }
            Row(Modifier.fillMaxWidth().clickable { f3 = !f3 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f3, { f3 = it })
                Text("Ui")
            }
            Row(Modifier.fillMaxWidth().clickable { f4 = !f4 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f4, { f4 = it })
                Text("Ui")
            }
            Row(Modifier.fillMaxWidth().clickable { f5 = !f5 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f5, { f5 = it })
                Text("Ui")
            }
            Row(Modifier.fillMaxWidth().clickable { f6 = !f6 }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(f6, { f6 = it })
                Text("Ui")
            }
            OutlinedTextField(f7, { f7 = it }, label = { Text("Autosave Edits") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
    }
}
