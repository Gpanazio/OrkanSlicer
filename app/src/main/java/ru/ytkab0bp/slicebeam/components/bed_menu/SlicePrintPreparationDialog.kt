package ru.ytkab0bp.slicebeam.components.bed_menu

import android.content.Context
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder
import ru.ytkab0bp.slicebeam.components.MmuFilamentAssignmentDialog
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync

/** Shows printer, auto-leveling and ACE slot checks before sending a sliced job. */
object SlicePrintPreparationDialog {
    fun interface PrinterProfileAction {
        fun activate(printer: ConfigObject): Boolean
    }

    @JvmStatic
    fun isAnycubicKobra(printer: ConfigObject?): Boolean =
        SlicePreparationTools.isAnycubicKobra3Model(printer?.get("printer_model"))

    @JvmStatic
    internal fun show(
        fragment: BedFragment,
        printer: ConfigObject,
        print: Boolean,
        gcodeToolCount: Int,
        activeTools: IntArray,
        sourceFilamentTypes: List<String>,
        pressureAdvance: SlicePreparationTools.PressureAdvanceCommands,
        activateProfile: PrinterProfileAction,
        reopenAfterReslice: (ConfigObject) -> Unit,
        sendAction: SliceMenuItems.SendAction,
    ) {
        val context = fragment.getContext() ?: return
        val jobName = fragment.glView?.renderer?.gcodeResult?.getRecommendedName().orEmpty()
        val availablePrinters = runCatching {
            SliceBeam.requireConfig().printerConfigs.filter { candidate ->
                SlicePreparationTools.isSendTarget(candidate.get("host_type"), candidate.get("print_host"))
            }
        }.getOrDefault(listOf(printer))
        val selectedPrinter = mutableStateOf(printer)
        val printerChanged = mutableStateOf(false)
        val mappingRevision = mutableIntStateOf(0)

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                BeamComposeTheme {
                    val currentPrinter = selectedPrinter.value
                    val mappingSummary = remember(currentPrinter.title, mappingRevision.intValue) {
                        formatToolMapping(context, currentPrinter.title.orEmpty(), gcodeToolCount, activeTools)
                    }
                    PrintPreparationContent(
                        jobName = jobName,
                        printer = currentPrinter,
                        availablePrinters = availablePrinters,
                        mappingSummary = mappingSummary.takeIf {
                            isAnycubicKobra(currentPrinter) && activeTools.isNotEmpty()
                        },
                        pressureAdvance = pressureAdvance,
                        onSelectPrinter = { chosen ->
                            selectedPrinter.value = chosen
                            printerChanged.value = printerChanged.value || SlicePreparationTools.didPrinterChange(
                                printer.title,
                                chosen.title,
                            )
                        },
                        onEditMapping = {
                            val title = selectedPrinter.value.title.orEmpty()
                            val shown = MmuFilamentAssignmentDialog.showForActiveTools(
                                context, title, gcodeToolCount, activeTools) {
                                mappingRevision.intValue++
                            }
                            if (!shown) showNoLoadedGates(context)
                        },
                    )
                }
            }
        }
        val dialog = BeamAlertDialogBuilder(context)
            .setTitle(R.string.PrintPrepareTitle)
            .setView(composeView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                if (print) R.string.MenuSliceSendToPrinterAndPrint else R.string.MenuSliceSendToPrinter,
                null,
            )
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val targetPrinter = selectedPrinter.value
            val targetType = targetPrinter.get("host_type") ?: "octoprint"
            val targetHost = targetPrinter.get("print_host") ?: return@setOnClickListener
            val targetApiKey = targetPrinter.get("printhost_apikey")
            val activation = SlicePreparationTools.activateChangedPrinter(printer.title, targetPrinter.title) {
                activateProfile.activate(targetPrinter)
            }
            if (activation == SlicePreparationTools.PrinterActivationResult.UNCHANGED &&
                (printerChanged.value || !fragment.hasSliceResult())) {
                dialog.dismiss()
                Toast.makeText(context, R.string.PrintPrepareReslicing, Toast.LENGTH_LONG).show()
                fragment.startSlicing { reopenAfterReslice(targetPrinter) }
                return@setOnClickListener
            }
            when (activation) {
                SlicePreparationTools.PrinterActivationResult.FAILED -> return@setOnClickListener
                SlicePreparationTools.PrinterActivationResult.ACTIVATED -> {
                    dialog.dismiss()
                    Toast.makeText(context, R.string.PrintPrepareReslicing, Toast.LENGTH_LONG).show()
                    fragment.startSlicing {
                        reopenAfterReslice(targetPrinter)
                    }
                    return@setOnClickListener
                }
                SlicePreparationTools.PrinterActivationResult.UNCHANGED -> Unit
            }
            if (isAnycubicKobra(targetPrinter) && activeTools.isNotEmpty()
                && MmuFilamentSync.getSelectedGates(targetPrinter.title.orEmpty(), gcodeToolCount) == null
            ) {
                val shown = MmuFilamentAssignmentDialog.showForActiveTools(
                    context,
                    targetPrinter.title.orEmpty(),
                    gcodeToolCount,
                    activeTools,
                ) {
                    dialog.dismiss()
                    sendAction.send(targetType, targetHost, targetApiKey, targetPrinter, print)
                }
                if (!shown) showNoLoadedGates(context)
                return@setOnClickListener
            }
            if (print && isAnycubicKobra(targetPrinter)) {
                val mismatch = firstMaterialMismatch(targetPrinter.title.orEmpty(),
                    gcodeToolCount, activeTools, sourceFilamentTypes)
                if (mismatch != null) {
                    BeamAlertDialogBuilder(context)
                        .setTitle(R.string.PrintPrepareMaterialMismatchTitle)
                        .setMessage(context.getString(R.string.PrintPrepareMaterialMismatch,
                            mismatch.first, mismatch.second, mismatch.third, mismatch.fourth))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.PrintPrepareUploadOnly) { _, _ ->
                            dialog.dismiss()
                            sendAction.send(targetType, targetHost, targetApiKey, targetPrinter, false)
                        }.show()
                    return@setOnClickListener
                }
            }
            dialog.dismiss()
            sendAction.send(targetType, targetHost, targetApiKey, targetPrinter, print)
        }
    }

    private data class MaterialMismatch(val first: Int, val second: String,
                                        val third: Int, val fourth: String)

    private fun firstMaterialMismatch(printerTitle: String, toolCount: Int,
                                      activeTools: IntArray, sourceFilamentTypes: List<String>): MaterialMismatch? {
        val gates = MmuFilamentSync.getSelectedGates(printerTitle, toolCount) ?: return null
        for (tool in activeTools) {
            val source = sourceFilamentTypes.getOrNull(tool)?.trim().orEmpty()
            val gate = gates.getOrNull(tool) ?: continue
            val actual = MmuFilamentSync.gateSlot(printerTitle, gate)?.type?.trim().orEmpty()
            if (!SlicePreparationTools.materialMatchesSlicedFile(source, actual)) {
                return MaterialMismatch(tool, source.ifBlank { "?" }, gate + 1, actual.ifBlank { "?" })
            }
        }
        return null
    }

    private fun formatToolMapping(context: Context, printerTitle: String, toolCount: Int,
                                  activeTools: IntArray): String {
        val mapping = MmuFilamentSync.getSelectedGates(printerTitle, toolCount)
        val loaded = MmuFilamentSync.loadedGates(printerTitle)
        return buildString {
            append(context.getString(R.string.MmuAssignmentEdit))
            activeTools.forEach { tool ->
                var gate = mapping?.getOrNull(tool)
                    ?: MmuFilamentSync.defaultGateForTool(printerTitle, tool)
                if (gate < 0 || gate !in loaded) gate = loaded.firstOrNull() ?: -1
                append("\nT").append(tool).append(" → ")
                    .append(MmuFilamentSync.gateLabel(printerTitle, gate))
            }
        }
    }

    private fun showNoLoadedGates(context: Context) {
        BeamAlertDialogBuilder(context)
            .setTitle(R.string.MmuSyncFailed)
            .setMessage(R.string.MmuAssignmentNoLoadedSlots)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

@Composable
private fun PrintPreparationContent(
    jobName: String,
    printer: ConfigObject,
    availablePrinters: List<ConfigObject>,
    mappingSummary: String?,
    pressureAdvance: SlicePreparationTools.PressureAdvanceCommands,
    onSelectPrinter: (ConfigObject) -> Unit,
    onEditMapping: () -> Unit,
) {
    var showPrinterPicker by remember { mutableStateOf(false) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.onSurface
    val rowShape = RoundedCornerShape(12.dp)
    val plate = printer.get("curr_bed_type")
        ?.takeIf(String::isNotEmpty)
        ?: stringResource(R.string.PrintPrepareDefaultPlate)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = jobName.ifBlank { stringResource(R.string.PrintPrepareTitle) },
            color = primary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .clickable(role = Role.Button) { showPrinterPicker = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.PrintPrepareSummary, printer.title.orEmpty(), plate),
                    color = primary,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.PrintPrepareSelectPrinter),
                    color = secondary,
                    fontSize = 13.sp,
                )
            }
            Text("▾", color = secondary, fontSize = 20.sp)
        }
        Text(
            stringResource(
                if (SlicePreparationTools.hasAutomaticBedLeveling(printer.get("machine_start_gcode"))) {
                    R.string.PrintPrepareAutoLevelEnabled
                } else {
                    R.string.PrintPrepareAutoLevelUnavailable
                },
            ),
            color = secondary,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val filamentPa = pressureAdvance.filament.joinToString(", ")
        val customPa = pressureAdvance.custom.joinToString(", ")
        Text(
            text = when {
                filamentPa.isNotEmpty() -> stringResource(R.string.PrintPreparePressureAdvanceFilament, filamentPa)
                customPa.isNotEmpty() -> stringResource(R.string.PrintPreparePressureAdvanceCustomOnly, customPa)
                else -> stringResource(R.string.PrintPreparePressureAdvanceNone)
            },
            color = if (filamentPa.isEmpty()) MaterialTheme.colorScheme.error else secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (filamentPa.isNotEmpty() && customPa.isNotEmpty() && customPa != filamentPa) {
            Text(stringResource(R.string.PrintPreparePressureAdvanceAlsoCustom, customPa),
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (mappingSummary != null) {
            Text(
                text = mappingSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .clickable(role = Role.Button, onClick = onEditMapping)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                color = primary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }

    if (showPrinterPicker) {
        ComposeAlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text(stringResource(R.string.PrintPrepareSelectPrinter)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.58f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    availablePrinters.forEach { candidate ->
                        TextButton(
                            onClick = {
                                onSelectPrinter(candidate)
                                showPrinterPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(
                                text = candidate.title.orEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (candidate.title == printer.title) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    primary
                                },
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPrinterPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
