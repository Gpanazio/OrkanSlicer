package ru.ytkab0bp.slicebeam.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync
import java.util.Locale

/** Chooses model-to-filament routing before G-code is generated. */
object PreSliceFilamentDialog {
    internal fun supportsPhysicalSlots(printer: ConfigObject): Boolean =
        printer.get("printer_model").orEmpty().lowercase(Locale.ROOT).startsWith("anycubic kobra 3") ||
            (printer.title.orEmpty().contains("Anycubic", true) && printer.title.orEmpty().contains("Kobra 3", true))
    fun interface Callback {
        /** Zero-based slot indices, or -1 to preserve that object's imported part/paint colors. */
        fun onSelected(slotIndices: IntArray)
    }

    @JvmStatic
    fun show(context: Context, model: Model?, callback: Callback,
             positiveLabel: Int = R.string.PreSliceStart,
             titleLabel: Int = R.string.PreSliceSlotTitle) {
        val slots = Prefs.getFilamentSlots().ifEmpty {
            listOf(FilamentSlot(AndroidColor.DKGRAY, "PLA")).also(Prefs::setFilamentSlots)
        }
        val objectCount = (model?.objectsCount ?: 0).coerceAtLeast(0)
        val initialSlots = PreSliceFilamentAssignments.initialSlotIndices(
            IntArray(objectCount) { index -> model?.getExtruder(index) ?: 1 },
            slots.size,
        )
        val assignmentsMatch = PreSliceFilamentAssignments.allUseSameSlot(initialSlots)
        val usedTools = (0 until objectCount).flatMap { model?.getUsedFilaments(it)?.toList().orEmpty() }
            .distinct().sorted().map { it - 1 }.toIntArray()
        val sliceTemperatures = runCatching {
            val snapshot = SliceBeam.buildCurrentConfigObject(model?.requiredFilamentCount ?: -1)
            val bedKeys = ru.ytkab0bp.slicebeam.config.BedSurfaceTemperaturePolicy.keysFor(snapshot["curr_bed_type"])
            usedTools.map { tool ->
                fun value(key: String): String {
                    val values = snapshot[key].orEmpty().split(',').map(String::trim)
                    return (values.getOrNull(tool) ?: values.lastOrNull()).orEmpty().ifBlank { "?" }
                }
                context.getString(R.string.PreSliceTemperatureRow, tool,
                    value(bedKeys.firstLayer), value(bedKeys.otherLayers),
                    value("nozzle_temperature_initial_layer"), value("nozzle_temperature"))
            }
        }.getOrDefault(emptyList())
        val hasPartColors = usedTools.size > 1 ||
            (TemporaryProjectSettings.active && (model?.requiredFilamentCount ?: 1) > 1)
        val config = SliceBeam.CONFIG
        val printer = config?.let { current -> current.presets?.get("printer")?.let(current::findPrinter) }
        val printerTitle = printer?.title.orEmpty()
        val physicalGates = if (printer != null && supportsPhysicalSlots(printer))
            MmuFilamentAssignmentDialog.availableGates(printerTitle, true) else IntArray(0)
        val colorMapping = mutableStateOf(if (physicalGates.isNotEmpty())
            MmuFilamentAssignmentDialog.initialMapping(printerTitle, maxOf(slots.size, model?.requiredFilamentCount ?: 1), physicalGates).toList()
            else emptyList())
        val onePhysicalGate = mutableIntStateOf(colorMapping.value.firstOrNull() ?: physicalGates.firstOrNull() ?: 0)
        // A project may use only T1, while its palette still contains T0 and T1.
        // Never flatten an imported 3MF merely because it has one object/color.
        val preserveColors = mutableStateOf(hasPartColors)
        val selectedSlots = mutableStateOf(initialSlots.toList())
        val useOneSlot = mutableStateOf(assignmentsMatch)
        val allSlotIndex = mutableIntStateOf(initialSlots.firstOrNull() ?: 0)

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                BeamComposeTheme {
                    PreSliceFilamentContent(
                        model = model,
                        slots = slots,
                        objectCount = objectCount,
                        selectedSlots = selectedSlots.value,
                        onSelectedSlotsChange = { selectedSlots.value = it },
                        useOneSlot = useOneSlot.value,
                        onUseOneSlotChange = { useOneSlot.value = it },
                        allSlotIndex = allSlotIndex.intValue,
                        onAllSlotChange = { allSlotIndex.intValue = it },
                        hasPartColors = hasPartColors,
                        preserveColors = preserveColors.value,
                        onPreserveColorsChange = { preserveColors.value = it },
                        printerTitle = printerTitle,
                        physicalGates = physicalGates,
                        colorMapping = colorMapping.value,
                        usedTools = usedTools,
                        sliceTemperatures = sliceTemperatures,
                        onColorMappingChange = { colorMapping.value = it },
                        onePhysicalGate = onePhysicalGate.intValue,
                        onOnePhysicalGateChange = { onePhysicalGate.intValue = it },
                    )
                }
            }
        }
        val dialog = BeamAlertDialogBuilder(context)
            .setTitle(titleLabel)
            .setView(composeView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(positiveLabel, null)
            .apply {
                if (sliceTemperatures.isNotEmpty() && context is ru.ytkab0bp.slicebeam.MainActivity) {
                    setNeutralButton(R.string.PreSliceEditTemperatures) { _, _ ->
                        (context.navigationDelegate as? ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
                            ?.switchToSlot(3)
                    }
                }
            }
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val oneSource = if (useOneSlot.value && !preserveColors.value && physicalGates.isNotEmpty()) {
                val physical = MmuFilamentSync.gateSlot(printerTitle, onePhysicalGate.intValue)
                val candidates = slots.mapIndexed { index, slot -> MmuColorAssignmentPolicy.Slot(index, slot.color, slot.type) }
                MmuColorAssignmentPolicy.suggest(physical?.color, physical?.type, allSlotIndex.intValue, candidates)
            } else allSlotIndex.intValue
            val result = PreSliceFilamentAssignments.resolve(selectedSlots.value.toIntArray(),
                oneSource, useOneSlot.value, preserveColors.value)
            if (colorMapping.value.isNotEmpty() && (preserveColors.value || useOneSlot.value)) {
                val gates = colorMapping.value.toMutableList()
                if (!preserveColors.value) gates[oneSource] = onePhysicalGate.intValue
                val activeTools = if (preserveColors.value) usedTools.toList() else listOf(oneSource)
                if (MmuFilamentAssignmentDialog.hasMaterialMismatch(printerTitle, gates, activeTools)) {
                    Toast.makeText(context, R.string.ModelColorSlotsMaterialMismatch, Toast.LENGTH_LONG).show()
                }
                MmuFilamentSync.setSelectedGates(printerTitle, gates.toIntArray())
            }
            dialog.dismiss()
            callback.onSelected(result)
        }
    }
}

/** Pure assignment rules keep the visible slot numbers aligned with emitted T0-based tools. */
internal object PreSliceFilamentAssignments {
    fun resolve(slotIndices: IntArray, allSlot: Int, useOneSlot: Boolean, preserveColors: Boolean): IntArray = when {
        preserveColors -> IntArray(slotIndices.size) { -1 }
        useOneSlot -> applySingleSlot(slotIndices, allSlot)
        else -> slotIndices.copyOf()
    }
    fun initialSlotIndices(oneBasedExtruders: IntArray, slotCount: Int): IntArray =
        IntArray(oneBasedExtruders.size) { index ->
            if (slotCount <= 0) 0 else (oneBasedExtruders[index] - 1).coerceIn(0, slotCount - 1)
        }

    fun allUseSameSlot(slotIndices: IntArray): Boolean =
        slotIndices.isEmpty() || slotIndices.all { it == slotIndices.first() }

    fun applySingleSlot(slotIndices: IntArray, selectedSlot: Int): IntArray =
        slotIndices.copyOf().also { result -> result.indices.forEach { result[it] = selectedSlot } }
}

@Composable
private fun PreSliceFilamentContent(
    model: Model?,
    slots: List<FilamentSlot>,
    objectCount: Int,
    selectedSlots: List<Int>,
    onSelectedSlotsChange: (List<Int>) -> Unit,
    useOneSlot: Boolean,
    onUseOneSlotChange: (Boolean) -> Unit,
    allSlotIndex: Int,
    onAllSlotChange: (Int) -> Unit,
    hasPartColors: Boolean,
    preserveColors: Boolean,
    onPreserveColorsChange: (Boolean) -> Unit,
    printerTitle: String,
    physicalGates: IntArray,
    colorMapping: List<Int>,
    usedTools: IntArray,
    sliceTemperatures: List<String>,
    onColorMappingChange: (List<Int>) -> Unit,
    onePhysicalGate: Int,
    onOnePhysicalGateChange: (Int) -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(12.dp)
    var choosingObject by androidx.compose.runtime.remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.PreSliceSlotHelp),
            color = secondaryColor,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        if (hasPartColors) {
            AssignmentModeRow(
                text = stringResource(R.string.PreSliceKeepModelColors),
                selected = preserveColors,
                onClick = { onPreserveColorsChange(true) },
            )
        }
        AssignmentModeRow(
            text = stringResource(R.string.PreSliceAllObjectsMode),
            selected = useOneSlot && !preserveColors,
            onClick = { onPreserveColorsChange(false); onUseOneSlotChange(true) },
        )
        if (objectCount > 1) {
            AssignmentModeRow(
                text = stringResource(R.string.PreSliceIndividualMode),
                selected = !useOneSlot && !preserveColors,
                onClick = { onPreserveColorsChange(false); onUseOneSlotChange(false) },
            )
        }

        if (preserveColors) {
            Text(stringResource(R.string.PreSliceKeepModelColorsHelp), color = secondaryColor, fontSize = 13.sp)
            if (sliceTemperatures.isNotEmpty()) {
                Text(stringResource(R.string.PreSliceTemperatureHeading), color = primaryColor, fontSize = 13.sp)
                sliceTemperatures.forEach { row -> Text(row, color = secondaryColor, fontSize = 13.sp) }
            }
            if (physicalGates.isNotEmpty()) {
                MmuAssignmentContent(printerTitle, colorMapping.size, physicalGates, slots, colorMapping,
                    usedTools = usedTools, showHelp = false, onMappingChange = onColorMappingChange)
                if (MmuFilamentAssignmentDialog.hasMaterialMismatch(printerTitle, colorMapping,
                        usedTools.toList())) Text(
                    stringResource(R.string.PreSliceMaterialMismatchHint),
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                )
            } else {
                usedTools.forEach { index ->
                    val slot = slots.getOrNull(index) ?: return@forEach
                    SlotAssignmentRow(label = stringResource(R.string.ModelColorSource, index + 1, index,
                        slot.type.orEmpty(), String.format(Locale.ROOT, "#%06X", slot.color and 0xFFFFFF)),
                        color = slot.color, selected = false, modifier = Modifier,
                        textColor = primaryColor, showRadio = false)
                }
            }
        } else if (useOneSlot) {
            if (physicalGates.isNotEmpty()) {
                if (MmuFilamentSync.getSnapshot(printerTitle) == null) Text(
                    stringResource(R.string.ModelColorSlotsUnverified), color = secondaryColor, fontSize = 13.sp,
                )
                physicalGates.forEach { gate ->
                    val physical = MmuFilamentSync.gateSlot(printerTitle, gate)
                    SlotAssignmentRow(label = physicalSlotLabel(printerTitle, gate),
                        color = physical?.color ?: AndroidColor.GRAY, selected = onePhysicalGate == gate,
                        modifier = Modifier.clip(shape).clickable(role = Role.RadioButton) { onOnePhysicalGateChange(gate) },
                        textColor = primaryColor, showRadio = true)
                }
            } else {
            slots.forEachIndexed { index, slot ->
                SlotAssignmentRow(
                    label = formatSlot(slot, index),
                    color = slot.color,
                    selected = allSlotIndex == index,
                    modifier = Modifier.clip(shape).clickable(role = Role.RadioButton) {
                        onAllSlotChange(index)
                    },
                    textColor = if (allSlotIndex == index) MaterialTheme.colorScheme.primary else primaryColor,
                    showRadio = true,
                )
            }
            }
        } else {
            Text(
                stringResource(R.string.PreSliceIndividualHelp),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                color = secondaryColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            repeat(objectCount) { objectIndex ->
                val slotIndex = selectedSlots.getOrElse(objectIndex) { 0 }.coerceIn(0, slots.lastIndex)
                val rawName = model?.getObjectName(objectIndex)
                val objectName = rawName?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.MenuFileObjectNumber, objectIndex + 1)
                val slot = slots[slotIndex]
                val material = slot.type?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.FilamentType)
                val rowText = stringResource(R.string.PreSliceObjectSlot, objectName, slotIndex + 1, material)
                SlotAssignmentRow(
                    label = rowText,
                    color = slot.color,
                    selected = false,
                    modifier = Modifier
                        .clip(shape)
                        .clickable(role = Role.Button) { choosingObject = objectIndex },
                    textColor = primaryColor,
                    showRadio = false,
                )
            }
        }
    }

    if (choosingObject >= 0) {
        val objectIndex = choosingObject
        val rawName = model?.getObjectName(objectIndex)
        val objectName = rawName?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.MenuFileObjectNumber, objectIndex + 1)
        AlertDialog(
            onDismissRequest = { choosingObject = -1 },
            title = { Text(stringResource(R.string.PreSliceChooseObjectSlot) + " · " + objectName) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.55f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    slots.forEachIndexed { index, slot ->
                        SlotAssignmentRow(
                            label = formatSlot(slot, index),
                            color = slot.color,
                            selected = selectedSlots.getOrElse(objectIndex) { 0 } == index,
                            modifier = Modifier.clip(shape).clickable(role = Role.RadioButton) {
                                onSelectedSlotsChange(selectedSlots.toMutableList().also { it[objectIndex] = index })
                                choosingObject = -1
                            },
                            textColor = if (selectedSlots.getOrElse(objectIndex) { 0 } == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                primaryColor
                            },
                            showRadio = true,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingObject = -1 }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AssignmentModeRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(
            role = Role.RadioButton,
            onClick = onClick,
        ).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun SlotAssignmentRow(
    label: String,
    color: Int,
    selected: Boolean,
    modifier: Modifier,
    textColor: Color,
    showRadio: Boolean,
) {
    val swatch = Color(color or 0xFF000000.toInt())
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showRadio) RadioButton(selected = selected, onClick = null)
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(label, modifier = Modifier.weight(1f), color = textColor, fontSize = 15.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun formatSlot(slot: FilamentSlot, slotIndex: Int): String {
    val material = slot.type?.takeIf(String::isNotBlank) ?: stringResource(R.string.FilamentType)
    val hexColor = String.format(Locale.ROOT, "#%06X", slot.color and COLOR_MASK)
    return stringResource(R.string.PreSliceSlotLabel, slotIndex + 1, material, hexColor, slotIndex)
}

private const val COLOR_MASK = 0xFFFFFF
