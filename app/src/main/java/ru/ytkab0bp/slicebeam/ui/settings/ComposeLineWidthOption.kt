package ru.ytkab0bp.slicebeam.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.config.LineWidthConversion
import java.util.function.Consumer

/** Both editors write one Orca percentage, so changing the nozzle keeps the same ratio. */
object ComposeLineWidthOption {
    @Composable
    fun Content(title: String, initialValue: String, nozzleMm: Double, maxPercent: Double, helpText: String?,
                onChanged: Consumer<String>) {
        val initialPercent = LineWidthConversion.percent(initialValue, nozzleMm) ?: 100.0
        var percent by rememberSaveable(initialValue, nozzleMm) {
            mutableStateOf(LineWidthConversion.formatPercent(initialPercent))
        }
        var millimetres by rememberSaveable(initialValue, nozzleMm) {
            mutableStateOf(LineWidthConversion.formatMillimetres(
                LineWidthConversion.millimetres(initialPercent, nozzleMm)))
        }
        var changed by rememberSaveable(initialValue, nozzleMm) { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface)
                if (initialValue.trim() == "0") {
                    Text(androidx.compose.ui.res.stringResource(R.string.LineWidthAutoHint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = percent, onValueChange = { input ->
                        percent = input
                        val parsed = input.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed.isFinite() && parsed > 0.0) {
                            millimetres = LineWidthConversion.formatMillimetres(
                                LineWidthConversion.millimetres(parsed, nozzleMm))
                            changed = true
                        }
                    }, modifier = Modifier.weight(1f), label = { Text("%") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = millimetres, onValueChange = { input ->
                        millimetres = input
                        val parsed = input.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed.isFinite() && parsed > 0.0) {
                            val derived = LineWidthConversion.percentFromMillimetres(parsed, nozzleMm)
                            percent = LineWidthConversion.formatPercent(derived)
                            changed = true
                        } else {
                            percent = ""
                        }
                    }, modifier = Modifier.weight(1f), label = { Text("mm") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                val validPercent = percent.replace(',', '.').toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it in 20.0..maxPercent }
                if (!helpText.isNullOrBlank()) {
                    Text(helpText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onChanged.accept("0") }) {
                        Text(androidx.compose.ui.res.stringResource(R.string.LineWidthAutomatic))
                    }
                    TextButton(enabled = changed && validPercent != null, onClick = {
                        onChanged.accept("${LineWidthConversion.formatPercent(validPercent!!)}%")
                        changed = false
                    }) { Text(androidx.compose.ui.res.stringResource(R.string.LineWidthApply)) }
                }
            }
        }
    }
}
