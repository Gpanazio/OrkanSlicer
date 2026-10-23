package ru.ytkab0bp.slicebeam.components.bed_menu

import android.content.Context
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.ThemeAwareComposeView
import ru.ytkab0bp.slicebeam.utils.IOUtils
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import java.io.File
import java.io.IOException

/** Coordinates the Slice menu, printer preparation and Kotlin upload controller. */
class SliceMenu : ListBedMenu() {
    private var lastUid = 0
    private var portrait = true
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())

    override fun onCreateView(ctx: Context, portrait: Boolean): ThemeAwareComposeView {
        this.portrait = portrait
        val themedView = ThemeAwareComposeView(ctx)
        themedView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        composeActions.value = createActions()
        themedView.setContent {
            BeamComposeTheme(themeRevision = themedView.themeRevision) {
                BedMenuActionsContent(composeActions.value, portrait)
            }
        }
        return themedView
    }

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> = emptyList()

    private fun createActions(): List<BedComposeMenuAction> {
        lastUid = SliceBeam.CONFIG_UID
        val bed = fragment ?: return emptyList()
        if (!bed.hasSliceResult()) {
            val hasModel = bed.currentModel?.objectsCount?.let { it > 0 } == true
            return SliceMenuItems.createBeforeSlice(bed, hasModel)
        }
        return SliceMenuItems.createAfterSlice(bed, this) { type, host, apiKey, printer, print ->
            refreshMmuAndPrepare(type, host, apiKey, printer, print)
        }
    }

    fun refreshItems(portrait: Boolean) {
        this.portrait = portrait
        composeActions.value = createActions()
    }

    fun refreshInfoItems() {
        composeActions.value = createActions()
    }

    private fun refreshMmuAndPrepare(
        type: String,
        host: String,
        apiKey: String?,
        printer: ConfigObject,
        print: Boolean,
    ) {
        if (SlicePrintPreparationDialog.isAnycubicKobra(printer)) {
            MmuFilamentSync.synchronize(printer) { _, error ->
                if (error != null) {
                    val context = fragment?.getContext()
                    if (context != null) {
                        BeamAlertDialogBuilder(context)
                            .setTitle(R.string.MmuSyncFailed)
                            .setMessage(R.string.MmuAssignmentSyncFailed)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    return@synchronize
                }
                scanToolCountAndPrepare(type, host, apiKey, printer, print)
            }
        } else {
            scanToolCountAndPrepare(type, host, apiKey, printer, print)
        }
    }

    private fun scanToolCountAndPrepare(
        type: String,
        host: String,
        apiKey: String?,
        printer: ConfigObject,
        print: Boolean,
    ) {
        val bed = fragment ?: return
        if (!bed.hasSliceResult()) {
            SliceUploadController.showPreparationFailure(bed)
            return
        }
        val gcode: File = BedFragment.tempGCodePath
        IOUtils.IO_POOL.execute {
            val activeTools = try {
                SlicePreparationTools.readUsedTools(gcode)
            } catch (_: IOException) {
                SliceUploadController.showPreparationFailure(bed)
                return@execute
            }
            val sourceFilamentTypes = try {
                SlicePreparationTools.readFilamentTypes(gcode)
            } catch (_: IOException) {
                SliceUploadController.showPreparationFailure(bed)
                return@execute
            }
            val pressureAdvance = try {
                SlicePreparationTools.readPressureAdvanceCommands(gcode)
            } catch (_: IOException) {
                SliceUploadController.showPreparationFailure(bed)
                return@execute
            }
            val toolCount = maxOf(1, (activeTools.maxOrNull() ?: 0) + 1)
            ViewUtils.postOnMainThread {
                if (bed.getContext() != null && bed.hasSliceResult()) {
                    SlicePrintPreparationDialog.show(
                        bed,
                        printer,
                        print,
                        toolCount,
                        activeTools,
                        sourceFilamentTypes,
                        pressureAdvance,
                        this::activatePrinterProfile,
                        { updatedPrinter ->
                            refreshMmuAndPrepare(
                                updatedPrinter.get("host_type") ?: "octoprint",
                                updatedPrinter.get("print_host").orEmpty(),
                                updatedPrinter.get("printhost_apikey"), updatedPrinter, print)
                        },
                    ) { sendType, sendHost, sendApiKey, targetPrinter, shouldPrint ->
                        SliceUploadController.upload(
                            bed,
                            sendType,
                            sendHost,
                            sendApiKey,
                            shouldPrint,
                            targetPrinter,
                        )
                    }
                }
            }
        }
    }

    private fun activatePrinterProfile(printer: ConfigObject): Boolean {
        val context = fragment?.getContext()
        if (!SliceBeam.applyProfileSelection(printer = printer.title)) {
            if (context != null) {
                android.widget.Toast.makeText(context, R.string.ConfigSaveFailed, android.widget.Toast.LENGTH_LONG).show()
            }
            return false
        }
        fragment?.refreshProfileStatus()
        return true
    }

    override fun onViewCreated(v: View) {
        super.onViewCreated(v)
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                if (lastUid != SliceBeam.CONFIG_UID) {
                    refreshItems(view.width < view.height)
                }
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }
}
