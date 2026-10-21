package ru.ytkab0bp.slicebeam.fragment
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Process
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.appcompat.app.AlertDialog
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.HashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder
import ru.ytkab0bp.slicebeam.components.MmuFilamentAssignmentDialog
import ru.ytkab0bp.slicebeam.components.PreSliceFilamentDialog
import ru.ytkab0bp.slicebeam.components.SliceProgressBottomSheet
import ru.ytkab0bp.slicebeam.components.UnfoldMenu
import ru.ytkab0bp.slicebeam.components.WorkspaceProfileSheet
import ru.ytkab0bp.slicebeam.components.bed_menu.BedMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.OptionsMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.TransformMenu
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.events.SlicingProgressEvent
import ru.ytkab0bp.slicebeam.navigation.Fragment
import ru.ytkab0bp.slicebeam.slic3r.Bed3D
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.ImportProgressCallback
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.ThemeAwareComposeView
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveWorkspaceNavigation
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveNavigationPolicy
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceNavigationItem
import ru.ytkab0bp.slicebeam.ui.workspace.MeasureModePanel
import ru.ytkab0bp.slicebeam.ui.workspace.PlateTabs
import ru.ytkab0bp.slicebeam.ui.workspace.PlateTabsPolicy
import ru.ytkab0bp.slicebeam.ui.workspace.PlateFootprint
import ru.ytkab0bp.slicebeam.ui.workspace.PlateThumbnail
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceProfileStatus
import ru.ytkab0bp.slicebeam.ui.workspace.PrinterWebCloseButton
import ru.ytkab0bp.slicebeam.ui.workspace.PrinterWebOpenButton
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.events.TemporaryProjectSettingsChangedEvent
import ru.ytkab0bp.slicebeam.ui.settings.TemporaryProjectSettingsBar
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync
import ru.ytkab0bp.slicebeam.utils.QidiGCode3mfPackager
import ru.ytkab0bp.slicebeam.utils.StlImportPreflight
import ru.ytkab0bp.slicebeam.utils.PrinterWebOriginPolicy
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.BedSwipeDownLayout
import ru.ytkab0bp.slicebeam.view.DividerView
import ru.ytkab0bp.slicebeam.view.GLView
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout
class BedFragment:Fragment() {
var overlayLayout: FrameLayout? = null
var snackbarsLayout: SnackbarsLayout? = null
var glView: GLView? = null
private var navigationView: ComposeView? = null
private var selectedMenuNavigationSlot by mutableIntStateOf(0)
private var menuNavigationThemeRevision by mutableIntStateOf(0)
private var isAnimatingMenu:Boolean = false
private var currentMenuSlot:Int = 0
private lateinit var menuView: FrameLayout
private var menuMap = object:SparseArray<BedMenu>() {
override fun get(key:Int):BedMenu {
var menu = super.get(key)
if (menu == null)
{
when (MenuCategory.values()[key]) {
BedFragment.MenuCategory.FILE -> menu = FileMenu()
BedFragment.MenuCategory.CAMERA -> menu = CameraMenu()
BedFragment.MenuCategory.TRANSFORM -> menu = TransformMenu()
BedFragment.MenuCategory.OPTIONS -> menu = OptionsMenu()
BedFragment.MenuCategory.SLICE_AND_EXPORT -> menu = SliceMenu()
else -> menu = FileMenu()
}
put(key, menu)
}
return menu
}
}
private var contentView: View? = null
// Refactored plate management
 private var platesModels = ArrayList<Model?>()
private val projectPlateIds = java.util.ArrayList<Int>()
var currentPlateIndex = 0
private var gCodeResult: GCodeProcessorResult? = null
private var currentUnfoldMenu: UnfoldMenu? = null
private var totalPlates = 1 // used for importing
private var currentProjectFile: File? = null
private var sourceProjectPlateCount:Int = 0
private var plateTabsScroll: ComposeView? = null
private var plateTabCount by mutableIntStateOf(1)
private var selectedPlateTabIndex by mutableIntStateOf(0)
private var plateObjectCounts by mutableStateOf<List<Int>>(emptyList())
private var plateThumbnails by mutableStateOf<List<PlateThumbnail>>(emptyList())
@Volatile private var platePreviewRevision = 0
private var platePreviewBuiltRevision = -1
private var plateTabsThemeRevision by mutableIntStateOf(0)
private val platePickerOpen = mutableStateOf(false)
private var printerWebButton: ComposeView? = null
private var printerWebCloseButton: ComposeView? = null
private var printerWebAvailable by mutableStateOf(false)
private var printerWebThemeRevision by mutableIntStateOf(0)
private var profileStatusText: ComposeView? = null
private var projectSettingsBar: ComposeView? = null
private var profileStatusTitle by mutableStateOf("")
private var profileStatusSummary by mutableStateOf("")
private var profileStatusDescription by mutableStateOf("")
private var profileStatusThemeRevision by mutableIntStateOf(0)
private var compactPortraitLayout:Boolean = false
private var sessionRestored:Boolean = false
private val undoHistory = ArrayDeque<Model>()
private val redoHistory = ArrayDeque<Model>()
private var stableModelSnapshot: Model? = null
private var modelHistoryDisabledForMemory:Boolean = false
private var largeModelWarningShown:Boolean = false
@Volatile private var modelLoadInProgress:Boolean = false
@Volatile private var modelImportGeneration:Int = 0
@Volatile private var importSimplifyDecision: Boolean? = null
@Volatile private var plateLoadGeneration:Int = 0
@Volatile private var destroyed:Boolean = false
private val pendingPlateModelsLock = Object()
private val pendingPlateModels = HashSet<Model>()
private val plateLoadExecutor = Executors.newSingleThreadExecutor({ r-> Thread(r, "OrcaPlateLoader") })
val currentModel:Model?
get() {
if (platesModels.isEmpty()) return null
return platesModels.get(currentPlateIndex)
}
val plateCount:Int
get() {
return platesModels.size
}
val selectedTriangleCount:Int
get() {
val model = currentModel
val selected = if (glView != null) glView!!.renderer.selectedObject else -1
return if (model == null || selected < 0) 0 else model.getTriangleCount(selected)
}
private var paintModeView: ru.ytkab0bp.slicebeam.view.PaintModeView? = null
private var measureModeView: ThemeAwareComposeView? = null
private var variableLayerHeightModeView: ru.ytkab0bp.slicebeam.view.VariableLayerHeightModeView? = null
private var embossModeView: ru.ytkab0bp.slicebeam.view.EmbossModeView? = null
private lateinit var swipeDownLayout: BedSwipeDownLayout
private var hasWebError:Boolean = false
private var panelWebView: WebView? = null
private var panelWebViewContainer: FrameLayout? = null
private var configuredWebHost: String? = null
private var panelWebViewError: LinearLayout? = null
private var webViewErrIcon: ImageView? = null
private lateinit var webViewErrDescription: TextView
private var webViewProgressBar: ProgressBar? = null
val isPaintMode:Boolean
get() {
return paintModeView != null
}
val isMeasureMode:Boolean
get() {
return measureModeView != null
}
val isVariableLayerHeightMode:Boolean
get() {
return variableLayerHeightModeView != null
}
val isEmbossMode:Boolean
get() {
return embossModeView != null
}
private val isLargeModelInMemory:Boolean
get() {
return Debug.getNativeHeapAllocatedSize() >= LARGE_MODEL_NATIVE_HEAP_BYTES
}
private fun getProjectPlateId(index:Int):Int {
return if (index >= 0 && index < projectPlateIds.size) projectPlateIds.get(index) else 0
}
private val sliceInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
private var slicedConfigUid = -1
private var slicedPrinter: String? = null
fun hasSliceResult():Boolean {
return gCodeResult != null && ru.ytkab0bp.slicebeam.utils.SliceResultValidity.isCurrent(
    slicedConfigUid, slicedPrinter, SliceBeam.CONFIG_UID, SliceBeam.CONFIG?.presets?.get("printer"))
}
private var preparedFilamentModel: Model? = null
private var preparedFilamentObjectCount = -1
private var preparedFilamentPrinter = ""
private var preparedFilamentPalette = ""
private var preparedFilamentSettingsRevision = -1L
private var preparedFilamentConfigUid = -1

private fun filamentPaletteSignature(): String = Prefs.getFilamentSlots().joinToString("|") {
    "${it.color}:${it.type.orEmpty()}"
}

/** Apply one assignment from the shared model/pre-slice dialog and refresh the model preview. */
private fun applyFilamentAssignment(model: Model, selectedSlots: IntArray, after: () -> Unit) {
    val viewport = glView ?: return
    viewport.queueEvent {
        if (currentModel !== model) return@queueEvent
        selectedSlots.forEachIndexed { index, slot ->
            if (slot >= 0) viewport.renderer.setObjectUniformFilament(index, slot + 1)
        }
        val printerTitle = SliceBeam.CONFIG?.presets?.get("printer").orEmpty()
        val colors = Prefs.getFilamentPalette()
        val gates = MmuFilamentSync.getSelectedGates(printerTitle,
            maxOf(colors.size, model.requiredFilamentCount))
        val preview = gates?.let { mapping -> colors.copyOf().also { displayed ->
            displayed.indices.forEach { tool ->
                MmuFilamentSync.gateSlot(printerTitle, mapping[tool])?.color?.let { displayed[tool] = it }
            }
        } }
        viewport.renderer.setAssignedSlotPreview(model, preview)
        viewport.requestRender()
        ViewUtils.postOnMainThread {
            if (destroyed || currentModel !== model || getContext() == null) return@postOnMainThread
            preparedFilamentModel = model
            preparedFilamentObjectCount = model.objectsCount
            preparedFilamentPrinter = printerTitle
            preparedFilamentPalette = filamentPaletteSignature()
            preparedFilamentSettingsRevision = TemporaryProjectSettings.sliceRevision
            preparedFilamentConfigUid = SliceBeam.CONFIG_UID
            after()
        }
    }
}

/** Uses the same assignment as slicing, including a preview of the selected slot colors. */
fun showModelColorSlots(synchronize: Boolean = false) {
    val context = getContext() ?: return
    val model = currentModel ?: return
    val config = SliceBeam.CONFIG ?: return
    val printer = config.presets?.get("printer")?.let(config::findPrinter) ?: return
    if (synchronize && PreSliceFilamentDialog.supportsPhysicalSlots(printer)) {
        MmuFilamentSync.synchronize(printer) { _, error ->
            ViewUtils.postOnMainThread {
                if (destroyed || currentModel !== model || getContext() == null) return@postOnMainThread
                if (error == null) showModelColorSlots()
                else BeamAlertDialogBuilder(context).setTitle(R.string.MmuSyncFailed).setMessage(error)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }
        return
    }
    PreSliceFilamentDialog.show(context, model, { selectedSlots ->
        applyFilamentAssignment(model, selectedSlots) {
            Toast.makeText(context, R.string.ModelColorSlotsSaved, Toast.LENGTH_SHORT).show()
        }
    }, R.string.ModelColorSlotsApply, R.string.ModelColorSlotsAction)
}
/** Selects model filament slots first, then slices and runs the continuation on success. */
 @JvmOverloads fun startSlicing(onSuccess:Runnable? = null) {
val ctx = getContext()
if (ctx == null || glView == null) return
val model = glView!!.renderer.model
if (model == null)
{
BeamAlertDialogBuilder(ctx)
.setTitle(R.string.SliceFailed)
.setMessage(R.string.SliceFailedNoModels)
.setPositiveButton(android.R.string.ok, null)
.show()
return
}
val activeModel = model ?: return
if (preparedFilamentModel === activeModel &&
    preparedFilamentObjectCount == activeModel.objectsCount &&
    preparedFilamentPrinter == SliceBeam.CONFIG?.presets?.get("printer").orEmpty() &&
    preparedFilamentPalette == filamentPaletteSignature() &&
    preparedFilamentSettingsRevision == TemporaryProjectSettings.sliceRevision &&
    preparedFilamentConfigUid == SliceBeam.CONFIG_UID) {
    runSlice(activeModel, onSuccess)
    return
}
PreSliceFilamentDialog.show(ctx, activeModel, { selectedSlots ->
    applyFilamentAssignment(activeModel, selectedSlots) { runSlice(activeModel, onSuccess) }
})
}
private fun showMmuSyncFailure(context:Context, onContinue: Runnable) {
if (getContext() == null) return
BeamAlertDialogBuilder(context).setTitle(R.string.MmuSyncFailed)
.setMessage(R.string.MmuAssignmentSyncFailed)
.setPositiveButton(R.string.PreSliceOffline) { _, _ -> onContinue.run() }
.setNegativeButton(android.R.string.cancel, null).show()
}
private fun runSlice(model:Model, onSuccess:Runnable? = null) {
val ctx = getContext()
if (ctx == null || glView == null || model !== currentModel) return
// Native slicing writes a shared config and output path; never allow two writers.
if (!sliceInFlight.compareAndSet(false, true)) return
slicedConfigUid = -1
tempExportingFile = null
val cfg = SliceBeam.getCurrentConfigFile()
val gcode = tempGCodePath
val settingsRevision = TemporaryProjectSettings.sliceRevision
if (!DEBUG_VIEWER) SliceProgressBottomSheet(ctx).show()
Thread({ try
{
Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
SliceBeam.ensureCompatibleSelection()
val sliceConfigUid = SliceBeam.CONFIG_UID
val slicePrinter = SliceBeam.CONFIG?.presets?.get("printer")
val activeConfig = SliceBeam.CONFIG
val activePrinter = if (activeConfig == null) null
else activeConfig.findPrinter(activeConfig.presets?.get("printer"))
SliceBeam.genCurrentConfig(model, QidiGCode3mfPackager.isQidiQ2(activePrinter))
val produced = if (!DEBUG_VIEWER)
{
val result = model.slice(cfg.getAbsolutePath(), gcode.getAbsolutePath(),
{ progress, text-> SliceBeam.EVENT_BUS.fireEvent(SlicingProgressEvent(progress, text)) },
SliceBeam.PENDING_CALIB_MODE, SliceBeam.PENDING_CALIB_START,
SliceBeam.PENDING_CALIB_END, SliceBeam.PENDING_CALIB_STEP)
SliceBeam.PENDING_CALIB_MODE = 0
SliceBeam.EVENT_BUS.fireEvent(SlicingProgressEvent(100, ""))
result
}
else
{
GCodeProcessorResult(gcode)
}
ViewUtils.postOnMainThread({
val viewport = glView
sliceInFlight.set(false)
if (destroyed || viewport == null || model !== currentModel || settingsRevision != TemporaryProjectSettings.sliceRevision ||
    !ru.ytkab0bp.slicebeam.utils.SliceResultValidity.isCurrent(sliceConfigUid, slicePrinter,
        SliceBeam.CONFIG_UID, SliceBeam.CONFIG?.presets?.get("printer"))) {
    produced.release()
    if (!destroyed && getContext() != null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ProjectSettingsReslice))
    return@postOnMainThread
}
slicedConfigUid = sliceConfigUid
slicedPrinter = slicePrinter
gCodeResult = produced
viewport.queueEvent({ viewport.renderer.setGCodeViewer(produced)
viewport.requestRender() })
tempFileName = produced.getRecommendedName()
tempExportingFile = null
val portrait = usesCompactPortraitLayout(ctx)
(menuMap.get(MenuCategory.SLICE_AND_EXPORT.ordinal) as SliceMenu)
.refreshItems(portrait)
if (onSuccess != null) onSuccess.run() })
}
catch (e:Exception) {
Log.e("BedFragment", "Slice failed", e)
ViewUtils.postOnMainThread({ sliceInFlight.set(false)
SliceBeam.EVENT_BUS.fireEvent(SlicingProgressEvent(100, ""))
BeamAlertDialogBuilder(ctx)
.setTitle(R.string.SliceFailed)
.setMessage(e.message)
.setPositiveButton(android.R.string.ok, null)
.show() })
}
}).start()
}
fun simplifySelectedObject(keepRatio:Float) {
val model = currentModel
val selected = if (glView != null) glView!!.renderer.selectedObject else -1
if (model == null || selected < 0) return
val tag = "model-simplify"
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
SnackbarsLayout.Type.LOADING, R.string.ModelSimplifyRunning).tag(tag))
glView!!.queueEvent({ try
{
val before = model.getTriangleCount(selected)
val after = model.simplify(selected, keepRatio)
glView!!.renderer.invalidateGlModel(selected)
glView!!.requestRender()
ViewUtils.postOnMainThread({ updateModel()
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
SliceBeam.INSTANCE!!.getResources().getQuantityString(
R.plurals.ModelSimplifyDone, after, before, after))) })
}
catch (e:Exception) {
Log.e("BedFragment", "Could not simplify model", e)
ViewUtils.postOnMainThread({ SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
SnackbarsLayout.Type.ERROR, R.string.ModelSimplifyFailed)) })
}
})
}
fun moveSelectedObjectToPlate(targetPlateIndex:Int, onComplete:Runnable) {
val sourcePlateIndex = currentPlateIndex
val selectedObject = glView!!.renderer.selectedObject
val source = currentModel
if ((source == null || selectedObject < 0 || targetPlateIndex < 0
|| targetPlateIndex >= platesModels.size || targetPlateIndex == sourcePlateIndex))
    return
var target = platesModels.get(targetPlateIndex)
if (target == null)
{
if (getProjectPlateId(targetPlateIndex) > 0 && currentProjectFile != null)
{
val sourceModel = source
val sourceObject = selectedObject
switchPlate(targetPlateIndex, true, { loadedTarget, firstObject, addedObjects->
if (loadedTarget == null || sourceModel.objectsCount <= sourceObject) return@switchPlate
glView!!.queueEvent({ val movedObject = loadedTarget.objectsCount
loadedTarget.addObject(sourceModel, sourceObject)
sourceModel.deleteObject(sourceObject)
glView!!.renderer.resetGlModels()
glView!!.requestRender()
ViewUtils.postOnMainThread({ glView!!.selectModel(movedObject, { updateModel()
if (onComplete != null) onComplete.run() }) }) }) })
return
}
target = Model()
platesModels.set(targetPlateIndex, target)
}
val finalTarget = target
glView!!.queueEvent({ finalTarget.addObject(source, selectedObject)
glView!!.renderer.deleteObject(selectedObject)
glView!!.requestRender()
ViewUtils.postOnMainThread({ switchPlate(targetPlateIndex)
val movedObject = finalTarget.objectsCount - 1
glView!!.selectModel(movedObject, { updateModel()
if (onComplete != null) onComplete.run() }) }) })
}
override fun onCreate() {
super.onCreate()
SliceBeam.EVENT_BUS.registerListener(this)
}
@EventHandler(runOnMainThread = true)
fun onNeedSnackbar(e:NeedSnackbarEvent) {
val s = SnackbarsLayout.Snackbar(e.type, e.title ?: "")
if (e.tag != null)
{
s.tag(e.tag!!)
}
if (e.buttonTitle != null)
{
s.lifetime = 0
s.buttonTitle = e.buttonTitle
s.buttonClick = e.buttonClick
}
snackbarsLayout!!.show(s)
}
@EventHandler(runOnMainThread = true)
fun onTemporaryProjectSettingsChanged(event: TemporaryProjectSettingsChangedEvent) {
if (getView() == null || glView == null) return
refreshProfileStatus()
if (event.settingsChanged && gCodeResult != null) {
    val previous = gCodeResult
    gCodeResult = null
    val viewport = glView!!
    viewport.queueEvent {
        viewport.renderer.setGCodeViewer(null)
        previous?.release()
        viewport.requestRender()
    }
    tempExportingFile = null
    (menuMap.get(MenuCategory.SLICE_AND_EXPORT.ordinal) as? SliceMenu)?.refreshItems(usesCompactPortraitLayout(getContext()!!))
}
glView?.requestRender()
}
@EventHandler(runOnMainThread = true)
fun onDismissSnackbar(e:NeedDismissSnackbarEvent) {
snackbarsLayout!!.dismiss(e.tag)
}
private fun showPrimeTowerPositionDialogForCurrentPlate() {
val context = getContext() ?: return
val config = SliceBeam.buildCurrentConfigObject()
val positions = PrintConfigToolsLogic.towerPositions(
    config.get("wipe_tower_x"),
    config.get("wipe_tower_y"),
)
val plateIndex = currentPlateIndex.coerceIn(0, positions.plateCount - 1)
val xValues = mutableStateListOf(positions.x[plateIndex])
val yValues = mutableStateListOf(positions.y[plateIndex])
val error = mutableStateOf(false)
val composeView = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
}
val editorPositions = PrintConfigTowerPositions(1, xValues.toList(), yValues.toList())
val dialog = BeamAlertDialogBuilder(context)
    .setTitle(R.string.PrimeTowerPosition)
    .setView(composeView)
    .setPositiveButton(android.R.string.ok, null)
    .setNegativeButton(android.R.string.cancel, null)
    .create()
composeView.setContent {
    BeamComposeTheme {
        PrimeTowerPositionEditor(
            positions = editorPositions,
            xValues = xValues.toList(),
            yValues = yValues.toList(),
            error = error.value,
            plateNumberOffset = plateIndex,
            helpTextResource = R.string.PrimeTowerPositionTapHelp,
            onXChanged = { _, value ->
                xValues[0] = value
                error.value = false
            },
            onYChanged = { _, value ->
                yValues[0] = value
                error.value = false
            },
        )
    }
}
dialog.setOnShowListener {
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}
dialog.show()
dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
    val current = SliceBeam.buildCurrentConfigObject()
    val x = PrintConfigToolsLogic.updateTowerPosition(current.get("wipe_tower_x"), plateIndex, xValues[0])
    val y = PrintConfigToolsLogic.updateTowerPosition(current.get("wipe_tower_y"), plateIndex, yValues[0])
    if (x == null || y == null) {
        error.value = true
        return@setOnClickListener
    }
    val source = SliceBeam.CONFIG
    val base = source?.let { current ->
        current.presets?.get("print")?.let(current::findPrint)
    }
    ProfileSettingsRepository.setPendingValue(
        ConfigObject.PROFILE_LIST_PRINT,
        "wipe_tower_x",
        base?.get("wipe_tower_x"),
        x,
    )
    ProfileSettingsRepository.setPendingValue(
        ConfigObject.PROFILE_LIST_PRINT,
        "wipe_tower_y",
        base?.get("wipe_tower_y"),
        y,
    )
    glView?.requestRender()
    dialog.dismiss()
}
}
fun showUnfoldMenu(menu:UnfoldMenu, from:View) {
if (currentUnfoldMenu != null) return
menu.setOnDismiss({ if (menu.isAttached()) return@setOnDismiss
currentUnfoldMenu = null })
currentUnfoldMenu = menu
menu.show(from, this)
}
private fun showModelContextMenu(objectIndex:Int) {
val ctx = getContext()
if (ctx == null || glView == null || objectIndex == -1) return
if (currentUnfoldMenu != null)
{
currentUnfoldMenu!!.dismiss()
}
BeamAlertDialogBuilder(ctx)
.setTitle(R.string.ModelContextMenuTitle)
.setItems(arrayOf<CharSequence>(ctx.getString(R.string.ModelContextDuplicate), ctx.getString(R.string.MenuToolbarSplit), ctx.getString(R.string.MenuFileMoveToPlate), ctx.getString(R.string.ModelContextDelete), ctx.getString(R.string.ModelContextFillBed), ctx.getString(R.string.ModelContextCenterOnBed), ctx.getString(R.string.ModelContextAutoOrient), ctx.getString(R.string.ModelContextResetRotation), ctx.getString(R.string.ModelContextResetScale), ctx.getString(R.string.ModelContextSetOnFace), ctx.getString(R.string.ModelContextSelectAll), ctx.getString(R.string.ModelContextDeleteAll)), { dialog, which-> when (which) {
0 // Duplicate
-> glView!!.duplicateSelectedModel({ updateModel()
Toast.makeText(ctx, R.string.ModelContextDuplicated, Toast.LENGTH_SHORT).show() })
1 // Separate parts
-> splitSelectedObject()
2 // Move to another plate
-> ru.ytkab0bp.slicebeam.components.bed_menu.FileMenuObjectActions(this).showMoveToPlateDialog(glView!!)
3 // Delete
-> glView!!.deleteSelectedModel({ updateModel()
Toast.makeText(ctx, R.string.ModelContextDeleted, Toast.LENGTH_SHORT).show() })
4 // Fill Bed
-> glView!!.fillBedWithSelectedModel({ addedCopies->
updateModel()
Toast.makeText(ctx,
if (addedCopies > 0)
ctx.getResources().getQuantityString(
R.plurals.ModelContextFillBedFinished,
addedCopies, addedCopies)
else
ctx.getString(R.string.ModelContextFillBedNoRoom),
Toast.LENGTH_SHORT).show() })
5 // Center on Bed
-> glView!!.centerSelectedOnBed({ updateModel()
Toast.makeText(ctx, R.string.ModelContextCentered, Toast.LENGTH_SHORT).show() })
6 // Auto-orient
-> glView!!.queueEvent({ glView!!.renderer.model!!.autoOrient(objectIndex)
glView!!.renderer.model!!.ensureOnBed(objectIndex)
glView!!.renderer.invalidateGlModel(objectIndex)
glView!!.requestRender()
ViewUtils.postOnMainThread({ updateModel()
Toast.makeText(ctx, R.string.ModelContextAutoOriented, Toast.LENGTH_SHORT).show() }) })
7 // Reset Rotation
-> glView!!.queueEvent({ glView!!.renderer.setSelectionRotation((0).toDouble(), (0).toDouble(), (0).toDouble())
glView!!.renderer.model!!.ensureOnBed(objectIndex)
glView!!.renderer.invalidateSelectionObject()
glView!!.requestRender()
ViewUtils.postOnMainThread({ updateModel()
Toast.makeText(ctx, R.string.ModelContextResetRotationDone, Toast.LENGTH_SHORT).show() }) })
8 // Reset Scale
-> glView!!.queueEvent({ glView!!.renderer.setSelectionScale((1).toDouble(), (1).toDouble(), (1).toDouble())
glView!!.renderer.model!!.ensureOnBed(objectIndex)
glView!!.renderer.invalidateSelectionObject()
glView!!.requestRender()
ViewUtils.postOnMainThread({ updateModel()
Toast.makeText(ctx, R.string.ModelContextResetScaleDone, Toast.LENGTH_SHORT).show() }) })
9 // Lay on face (enter flatten mode)
-> glView!!.queueEvent({ glView!!.renderer.setInFlattenMode(true)
glView!!.requestRender() })
10 // Select All
-> glView!!.selectAllModels({ updateModel()
Toast.makeText(ctx, R.string.ModelContextSelectedAll, Toast.LENGTH_SHORT).show() })
11 // Delete All
-> glView!!.deleteAllModels({ updateModel()
Toast.makeText(ctx, R.string.ModelContextDeletedAll, Toast.LENGTH_SHORT).show() })
} })
.setNegativeButton(android.R.string.cancel, null)
.show()
}
fun showSelectedModelContextMenu() {
    showModelContextMenu(glView?.renderer?.selectedObject ?: -1)
}
@JvmOverloads fun enterPaintMode(objectIndex:Int, mode:Int = ru.ytkab0bp.slicebeam.render.GLRenderer.PAINT_MODE_COLOR) {
val ctx = getContext()
if (ctx == null || glView == null || objectIndex == -1 || paintModeView != null) return
glView!!.queueEvent({ glView!!.renderer.beginPaint(objectIndex, mode)
glView!!.requestRender() })
paintModeView = ru.ytkab0bp.slicebeam.view.PaintModeView(ctx, glView!!, Runnable { exitPaintMode() }, mode)
overlayLayout!!.addView(paintModeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}
private fun exitPaintMode() {
if (paintModeView != null)
{
overlayLayout!!.removeView(paintModeView)
paintModeView = null
}
}
fun enterMeasureMode() {
val ctx = getContext()
val currentGlView = glView
if (ctx == null || currentGlView == null || measureModeView != null) return
currentGlView.queueEvent {
currentGlView.renderer.isInMeasureMode = true
currentGlView.requestRender()
}
val composeView = ThemeAwareComposeView(ctx).apply {
setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
}
composeView.setContent {
val revision = composeView.themeRevision
BeamComposeTheme(themeRevision = revision) {
MeasureModePanel(glView = currentGlView, onExit = ::exitMeasureMode)
}
}
measureModeView = composeView
overlayLayout!!.addView(composeView, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.WRAP_CONTENT,
Gravity.BOTTOM))
}
private fun exitMeasureMode() {
if (measureModeView != null)
{
overlayLayout!!.removeView(measureModeView)
measureModeView = null
}
}
fun enterVariableLayerHeightMode() {
val ctx = getContext()
if (ctx == null || glView == null || variableLayerHeightModeView != null) return
variableLayerHeightModeView = ru.ytkab0bp.slicebeam.view.VariableLayerHeightModeView(ctx, glView!!, Runnable { exitVariableLayerHeightMode() })
overlayLayout!!.addView(variableLayerHeightModeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}
private fun exitVariableLayerHeightMode() {
if (variableLayerHeightModeView != null)
{
overlayLayout!!.removeView(variableLayerHeightModeView)
variableLayerHeightModeView = null
}
}
fun enterEmbossMode() {
val ctx = getContext()
if (ctx == null || glView == null || embossModeView != null) return
glView!!.queueEvent({ glView!!.renderer.isInEmbossMode = true
glView!!.requestRender() })
embossModeView = ru.ytkab0bp.slicebeam.view.EmbossModeView(ctx, glView!!, Runnable { exitEmbossMode() })
overlayLayout!!.addView(embossModeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}
private fun exitEmbossMode() {
if (embossModeView != null)
{
glView!!.queueEvent({ glView!!.renderer.isInEmbossMode = false
glView!!.requestRender() })
overlayLayout!!.removeView(embossModeView)
embossModeView = null
}
}
fun loadGCode(f:File) {
slicedConfigUid = SliceBeam.CONFIG_UID
slicedPrinter = SliceBeam.CONFIG?.presets?.get("printer")
gCodeResult = GCodeProcessorResult(f)
ViewUtils.postOnMainThread({ glView!!.queueEvent({ glView!!.renderer.setGCodeViewer(gCodeResult)
glView!!.requestRender() })
tempFileName = gCodeResult!!.getRecommendedName()
tempExportingFile = f
val ctx = getContext() ?: return@postOnMainThread
val portrait = isPortraitWindow(ctx)
selectMenu(ctx, portrait, MenuCategory.SLICE_AND_EXPORT.ordinal) })
}
override fun onBackPressed():Boolean {
if (embossModeView != null)
{
exitEmbossMode()
return true
}
if (variableLayerHeightModeView != null)
{
exitVariableLayerHeightMode()
return true
}
if (measureModeView != null)
{
glView!!.queueEvent({ glView!!.renderer.isInMeasureMode = false
glView!!.renderer.clearMeasurePoints()
glView!!.requestRender() })
exitMeasureMode()
return true
}
if (paintModeView != null)
{
glView!!.queueEvent({ glView!!.renderer.endPaint(true)
glView!!.requestRender() })
exitPaintMode()
return true
}
if (currentUnfoldMenu != null)
{
currentUnfoldMenu!!.dismiss()
return true
}
if (panelWebViewContainer != null && panelWebViewContainer!!.getVisibility() == View.VISIBLE)
{
destroyPrinterWebView()
return true
}
if (swipeDownLayout.onBackPressed())
{
return true
}
if (currentMenuSlot != 0)
{
val ctx = getContext() ?: return true
onMenuNavigationSelected(ctx, compactPortraitLayout, 0)
return true
}
return super.onBackPressed()
}
override fun onDestroy() {
super.onDestroy()
destroyed = true
modelImportGeneration++
plateLoadGeneration++
plateLoadExecutor.shutdownNow()
synchronized (pendingPlateModelsLock) {
for (model in pendingPlateModels)
{
model.release()
}
pendingPlateModels.clear()
}
destroyPrinterWebView()
SliceBeam.EVENT_BUS.unregisterListener(this)
for (i in 0 until menuMap.size())
{
menuMap.valueAt(i).onViewDestroyed()
}
if (!(getContext() is Activity && (getContext() as Activity).isChangingConfigurations()))
{
clearModelHistory()
for (m in platesModels)
{
if (m != null) m.release()
}
platesModels.clear()
if (gCodeResult != null)
{
gCodeResult!!.release()
gCodeResult = null
}
}
}
override fun onResume() {
super.onResume()
glView!!.onResume()
updateProfileStatus()
updatePrinterWebViewAvailability()
}
private fun updatePrinterWebViewAvailability() {
val cfg = SliceBeam.CONFIG!!.findPrinter(SliceBeam.CONFIG!!.presets!!.get("printer"))
var enable = cfg != null && !TextUtils.isEmpty(cfg.get("print_host"))
configuredWebHost = null
if (enable)
{
configuredWebHost = PrinterWebOriginPolicy.dashboardUrl(cfg!!.get("print_host"))
enable = !TextUtils.isEmpty(configuredWebHost)
}
printerWebAvailable = enable
if (!enable) destroyPrinterWebView()
}
private fun openPrinterWebView() {
updatePrinterWebViewAvailability()
if (TextUtils.isEmpty(configuredWebHost) || panelWebViewContainer == null || getContext() == null)
{
if (getContext() is ru.ytkab0bp.slicebeam.MainActivity)
{
BeamAlertDialogBuilder(getContext()!!).setTitle(R.string.PrinterWebSetupTitle)
.setMessage(R.string.PrinterWebSetupMessage)
.setNegativeButton(android.R.string.cancel, null)
.setPositiveButton(R.string.PrinterWebSetupAction, { dialog, which-> ((getContext() as ru.ytkab0bp.slicebeam.MainActivity).navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
.switchToSlot(4) })
.show()
}
else if (getContext() != null)
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.PrinterWebHostMissing))
}
return
}
panelWebViewContainer!!.setVisibility(View.VISIBLE)
panelWebViewContainer!!.bringToFront()
if (printerWebCloseButton != null) printerWebCloseButton!!.setVisibility(View.VISIBLE)
if (panelWebView != null)
{
 if (!hasWebError) return
 // A renderer that reported an error may be dead or holding a failed navigation state.
 // Recreate it so the next open actually retries the printer page.
 val failedWebView = panelWebView ?: return
 panelWebView = null
 failedWebView.stopLoading()
 panelWebViewContainer!!.removeView(failedWebView)
 failedWebView.destroy()
 hasWebError = false
 panelWebViewError!!.setVisibility(View.GONE)
}
try
{
val webView = WebView(getContext()!!)
panelWebView = webView
configurePrinterWebView(webView)
webView.setAlpha(0f)
webView.setWebViewClient(object:WebViewClient() {
override fun shouldOverrideUrlLoading(view:WebView, url:String):Boolean {
return !isAllowedPrinterWebUrl(url)
}
override fun shouldOverrideUrlLoading(view:WebView, request:android.webkit.WebResourceRequest):Boolean {
return !isAllowedPrinterWebUrl(request.getUrl().toString())
}
override fun onPageStarted(view:WebView, url:String, favicon:android.graphics.Bitmap?) {
if (view !== panelWebView) return
hasWebError = false
view.setVisibility(View.VISIBLE)
view.setAlpha(0f)
panelWebViewError!!.setVisibility(View.GONE)
webViewProgressBar!!.setAlpha(1f)
}
override fun onReceivedError(view:WebView, request:android.webkit.WebResourceRequest, error:android.webkit.WebResourceError) {
if (request.isForMainFrame) showPrinterWebLoadError(view, error.description?.toString() ?: "Page load failed")
}
override fun onReceivedError(view:WebView, errorCode:Int, description:String, failingUrl:String) {
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) showPrinterWebLoadError(view, description)
}
override fun onReceivedHttpError(view:WebView, request:android.webkit.WebResourceRequest, response:android.webkit.WebResourceResponse) {
if (request.isForMainFrame) showPrinterWebLoadError(view, "HTTP ${response.statusCode}: ${response.reasonPhrase}")
}
override fun onPageFinished(view:WebView, url:String) {
if (view === panelWebView && !hasWebError)
{
view.setVisibility(View.VISIBLE)
view.animate().alpha((1).toFloat()).setDuration(150).start()
webViewProgressBar!!.animate().alpha((0).toFloat()).setDuration(150).start()
}
}
override fun onRenderProcessGone(view:WebView, detail:android.webkit.RenderProcessGoneDetail):Boolean {
if (view === panelWebView) showPrinterWebLoadError(view, "The printer page renderer stopped.")
return true
}
private fun showPrinterWebLoadError(view:WebView, description:String) {
if (view !== panelWebView || hasWebError) return
hasWebError = true
webViewErrDescription.setText(description)
panelWebViewError!!.setVisibility(View.VISIBLE)
panelWebViewError!!.setAlpha(0f)
panelWebViewError!!.animate().alpha((1).toFloat()).setDuration(150).setListener(object:AnimatorListenerAdapter() {
override fun onAnimationEnd(animation:Animator) {
if (view === panelWebView) view.setVisibility(View.GONE)
}
}).start()
webViewProgressBar!!.animate().alpha((0).toFloat()).setDuration(150).start()
}
})
panelWebViewContainer!!.addView(webView, 0, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
panelWebViewError!!.setVisibility(View.GONE)
webViewProgressBar!!.setAlpha(1f)
hasWebError = false
configuredWebHost?.let { webView.loadUrl(it) }
}
catch (error:Exception) {
Log.w("BedFragment", "Could not open printer web interface", error)
hasWebError = true
webViewErrDescription.setText(R.string.PrinterWebOpenFailed)
panelWebViewError!!.setVisibility(View.VISIBLE)
webViewProgressBar!!.setAlpha(0f)
}
}
@SuppressLint("SetJavaScriptEnabled")
private fun configurePrinterWebView(webView:WebView) {
// Printer dashboards need JavaScript, but must stay on the user-configured printer origin.
 val settings = webView.getSettings()
settings.setJavaScriptEnabled(true)
settings.setDomStorageEnabled(true)
settings.setJavaScriptCanOpenWindowsAutomatically(false)
settings.setSupportMultipleWindows(false)
settings.setAllowFileAccess(false)
settings.setAllowContentAccess(false)
}
private fun isAllowedPrinterWebUrl(targetUrl:String):Boolean {
return PrinterWebOriginPolicy.allows(configuredWebHost, targetUrl)
}
private fun destroyPrinterWebView() {
val webView = panelWebView
panelWebView = null
hasWebError = false
if (webView != null)
{
webView.stopLoading()
if (panelWebViewContainer != null) panelWebViewContainer!!.removeView(webView)
webView.destroy()
}
if (panelWebViewError != null) panelWebViewError!!.setVisibility(View.GONE)
if (webViewProgressBar != null) webViewProgressBar!!.setAlpha(0f)
if (printerWebCloseButton != null) printerWebCloseButton!!.setVisibility(View.GONE)
if (panelWebViewContainer != null) panelWebViewContainer!!.setVisibility(View.GONE)
}
override fun onPause() {
super.onPause()
destroyPrinterWebView()
// The current model stays in memory while backgrounded; cold starts begin cleanly.
 if (isLargeModelInMemory) warnLargeModelLimitOnce()
// Free render meshes while Android hosts another activity (for example, the file
 // picker). Keep the native model; GLRenderer recreates the mesh buffers on demand.
 glView!!.queueEvent({ glView!!.renderer.releaseMeshResources() })
glView!!.onPause()
}
private fun updateProfileStatus() {
if (profileStatusText == null || SliceBeam.CONFIG == null) return
val printer = SliceBeam.CONFIG!!.findPrinter(SliceBeam.CONFIG!!.presets!!.get("printer"))
val unavailable = getContext()!!.getString(R.string.WorkspaceSettingsUnknown)
val printerName = if (printer != null) printer.title else unavailable
var nozzle = if (printer != null) printer.get("printer_variant") else null
if (TextUtils.isEmpty(nozzle) && printer != null) nozzle = printer.get("nozzle_diameter")
if (TextUtils.isEmpty(nozzle)) nozzle = unavailable
val projectSlots = TemporaryProjectSettings.slots()
val filament = if (!projectSlots.isNullOrEmpty()) {
    val materials = projectSlots.mapNotNull { it.type?.takeIf(String::isNotBlank) }.distinct()
    getContext()!!.getString(R.string.WorkspaceProjectFilaments,
        materials.joinToString("/"), projectSlots.size)
} else SliceBeam.CONFIG!!.presets!!.get("filament")
val process = SliceBeam.CONFIG!!.presets!!.get("print")
profileStatusTitle = printerName ?: unavailable
profileStatusSummary = getContext()!!.getString(
R.string.WorkspaceProfileStatusSummary,
nozzle,
if (TextUtils.isEmpty(filament)) unavailable else filament)
profileStatusDescription = getContext()!!.getString(
R.string.WorkspaceProfilesStatusDescription,
printerName, nozzle, if (TextUtils.isEmpty(filament)) unavailable else filament,
if (TextUtils.isEmpty(process)) unavailable else process)
}
fun refreshProfileStatus() {
updateProfileStatus()
updatePrinterWebViewAvailability()
}
private fun restoreSession() {
if (sessionRestored || getContext() == null) return
sessionRestored = true
// Previous session files must never load implicitly after an app restart.
 // Printer and profile selections are persisted independently.
 Prefs.getPrefs()!!.edit().putInt(SESSION_PLATE_COUNT, 0).apply()
}
@SuppressLint("SetJavaScriptEnabled")
override fun onCreateView(ctx:Context):View {
if (platesModels.isEmpty()) platesModels.add(Model())
restoreSession()
glView = GLView(ctx)
glView!!.onPrimeTowerTap = { showPrimeTowerPositionDialogForCurrentPlate() }
glView!!.renderer.model = currentModel
resetModelHistory()
glView!!.renderer.setGCodeViewer(gCodeResult)
overlayLayout = object:FrameLayout(ctx) {
protected override fun onSizeChanged(w:Int, h:Int, oldw:Int, oldh:Int) {
super.onSizeChanged(w, h, oldw, oldh)
if (currentUnfoldMenu != null)
{
currentUnfoldMenu!!.relayout()
}
}
}
plateTabsScroll = ComposeView(ctx).apply {
setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
setContent {
val revision = plateTabsThemeRevision
if (revision >= 0) BeamComposeTheme(themeRevision = revision) {
PlateTabs(
openState = platePickerOpen,
plateCount = plateTabCount,
selectedIndex = selectedPlateTabIndex,
objectCounts = plateObjectCounts,
thumbnails = plateThumbnails,
onOpen = ::refreshPlateThumbnails,
            onSelect = { index -> if (index != currentPlateIndex) switchPlate(index) },
onClose = { index -> removePlate(index) },
onAdd = { addNewPlate() },
)
}
}
}
updatePlateIndicator()
profileStatusText = ComposeView(ctx).apply {
setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
setContent {
val revision = profileStatusThemeRevision
BeamComposeTheme(themeRevision = revision) {
WorkspaceProfileStatus(
title = profileStatusTitle,
summary = profileStatusSummary,
accessibilityDescription = profileStatusDescription.ifBlank { ctx.getString(R.string.WorkspaceProfilesOpenDescription) },
onOpen = { WorkspaceProfileSheet(ctx, Runnable { refreshProfileStatus() }).show() },
)
}
}
}
updateProfileStatus()
val portrait = usesCompactPortraitLayout(ctx)
compactPortraitLayout = portrait
constructMenuView(ctx, portrait)
swipeDownLayout = BedSwipeDownLayout(ctx)
panelWebViewContainer = FrameLayout(ctx)
val wfl = panelWebViewContainer
panelWebViewError = LinearLayout(ctx)
panelWebViewError!!.setVisibility(View.GONE)
panelWebViewError!!.setOrientation(LinearLayout.VERTICAL)
panelWebViewError!!.setGravity(Gravity.CENTER)
panelWebViewError!!.setPadding(ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12), ViewUtils.dp(12))
webViewErrIcon = ImageView(ctx)
webViewErrIcon!!.setImageResource(R.drawable.globe_cross_outline_28)
panelWebViewError!!.addView(webViewErrIcon, object:LinearLayout.LayoutParams(ViewUtils.dp(28), ViewUtils.dp(28)) {
init{
bottomMargin = ViewUtils.dp(8)
}
})
webViewErrDescription = TextView(ctx)
webViewErrDescription.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (16).toFloat())
webViewErrDescription.setGravity(Gravity.CENTER)
panelWebViewError!!.addView(webViewErrDescription)
val retryWebButton = Button(ctx).apply {
text = ctx.getString(R.string.PrinterWebRetry)
setOnClickListener { openPrinterWebView() }
}
panelWebViewError!!.addView(retryWebButton, LinearLayout.LayoutParams(
ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
topMargin = ViewUtils.dp(8)
})
val browserWebButton = Button(ctx).apply {
text = ctx.getString(R.string.PrinterWebOpenInBrowser)
setOnClickListener {
val host = configuredWebHost ?: return@setOnClickListener
try {
ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(host)))
} catch (error: Exception) {
Log.w("BedFragment", "Could not open printer page in external browser", error)
Toast.makeText(ctx, R.string.PrinterWebOpenFailed, Toast.LENGTH_LONG).show()
}
}
}
panelWebViewError!!.addView(browserWebButton, LinearLayout.LayoutParams(
ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
wfl!!.addView(panelWebViewError, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
webViewProgressBar = ProgressBar(ctx)
webViewProgressBar!!.setAlpha(0f)
wfl!!.addView(webViewProgressBar, FrameLayout.LayoutParams(ViewUtils.dp(36), ViewUtils.dp(36), Gravity.CENTER))
// The printer page is opened explicitly from the workspace. Remove the drag handle and
 // its reserved top inset so the build-plate view uses the full available height.
 swipeDownLayout.setEnableTop(false)
wfl!!.setVisibility(View.GONE)
printerWebCloseButton = ComposeView(ctx).apply {
setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
setContent {
val revision = printerWebThemeRevision
if (revision >= 0) BeamComposeTheme(themeRevision = revision) {
PrinterWebCloseButton(onClose = ::destroyPrinterWebView)
}
}
}
printerWebCloseButton!!.setVisibility(View.GONE)
wfl!!.addView(printerWebCloseButton, object:FrameLayout.LayoutParams(
ViewUtils.dp(48), ViewUtils.dp(48), Gravity.TOP or Gravity.END) {
init{
rightMargin = ViewUtils.dp(12)
topMargin = rightMargin
}
})
printerWebButton = ComposeView(ctx).apply {
setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
setContent {
BeamComposeTheme(themeRevision = printerWebThemeRevision) {
PrinterWebOpenButton(hostConfigured = printerWebAvailable, onOpen = ::openPrinterWebView)
}
}
}
contentView = createWorkspaceContent(ctx, portrait)
overlayLayout!!.addView(contentView, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
configureMenuNavigation()
snackbarsLayout = SnackbarsLayout(ctx)
overlayLayout!!.addView(snackbarsLayout, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
updateAdaptiveOverlayMargins(portrait)
overlayLayout!!.addView(wfl, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
return overlayLayout!!
}
override fun onConfigurationChanged(newConfig:Configuration) {
super.onConfigurationChanged(newConfig)
val ctx = getContext()
if (ctx == null || overlayLayout == null || glView == null) return
val portrait = usesCompactPortraitLayout(ctx)
if (portrait == compactPortraitLayout) return
if (isAnimatingMenu)
{
overlayLayout!!.postDelayed({ onConfigurationChanged(newConfig) }, 300)
return
}
syncCurrentModel()
rebuildAdaptiveLayout(ctx, portrait)
}
private fun createMenuNavigationView(ctx: Context, bottomNavigation: Boolean): ComposeView {
    val items = MenuCategory.values().map { category ->
        val label = ctx.getString(category.titleRes)
        WorkspaceNavigationItem(
            slot = category.ordinal,
            label = ctx.getString(if (!bottomNavigation) when (category) {
                MenuCategory.TRANSFORM -> R.string.MenuToolbarNavigationCompact
                MenuCategory.OPTIONS -> R.string.MenuOptionsCompact
                else -> category.navigationTitleRes
            } else category.navigationTitleRes),
            icon = category.iconRes,
            accessibilityLabel = label,
        )
    }
    return ComposeView(ctx).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            key(menuNavigationThemeRevision) {
                BeamComposeTheme(themeRevision = menuNavigationThemeRevision) {
                    AdaptiveWorkspaceNavigation(
                        items = items,
                        selectedSlot = selectedMenuNavigationSlot,
                        isBottomNavigation = bottomNavigation,
                        onSelected = { slot -> onMenuNavigationSelected(ctx, bottomNavigation, slot) },
                    )
                }
            }
        }
    }
}

private fun configureMenuNavigation() {
    selectedMenuNavigationSlot = currentMenuSlot
}

private fun onMenuNavigationSelected(ctx: Context, bottomNavigation: Boolean, slot: Int) {
    if (slot !in MenuCategory.values().indices || slot == currentMenuSlot || isAnimatingMenu) return
    if (slot != MenuCategory.SLICE_AND_EXPORT.ordinal) {
        glView?.queueEvent {
            gCodeResult?.release()
            gCodeResult = null
            glView?.renderer?.setGCodeViewer(null)
            glView?.requestRender()
        }
    } else {
        (menuMap.get(MenuCategory.SLICE_AND_EXPORT.ordinal) as SliceMenu).refreshItems(bottomNavigation)
    }
    selectMenu(ctx, bottomNavigation, slot)
}
/** Keep the model viewport clear; object actions are available from the Edit menu. */
private fun createWorkspaceContent(ctx: Context, portrait: Boolean): View {
listOfNotNull(glView, menuView, navigationView, profileStatusText, printerWebButton, projectSettingsBar,
    plateTabsScroll).forEach(::detachFromParent)
detachFromParent(swipeDownLayout)
swipeDownLayout.removeAllViews()
val workspace = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
if (projectSettingsBar == null) projectSettingsBar = ComposeView(ctx).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent { BeamComposeTheme { TemporaryProjectSettingsBar() } }
}
workspace.addView(projectSettingsBar, LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
val header = LinearLayout(ctx).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(ViewUtils.dp(6), ViewUtils.dp(4), ViewUtils.dp(6), ViewUtils.dp(4))
}
header.addView(profileStatusText, LinearLayout.LayoutParams(
0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
header.addView(plateTabsScroll, LinearLayout.LayoutParams(
ViewUtils.dp(48), ViewUtils.dp(48)).apply { leftMargin = ViewUtils.dp(4) })
header.addView(printerWebButton, LinearLayout.LayoutParams(
ViewUtils.dp(48), ViewUtils.dp(48)).apply { leftMargin = ViewUtils.dp(4) })
workspace.addView(header, LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
val bedRow = FrameLayout(ctx)
bedRow.addView(glView, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
workspace.addView(bedRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
navigationView = createMenuNavigationView(ctx, bottomNavigation = portrait)
if (portrait) {
workspace.addView(menuView, LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(menuPortraitHeightDp())))
workspace.addView(DividerView(ctx), LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.dp(1)))
workspace.addView(navigationView, LinearLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}
swipeDownLayout.addView(workspace, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
if (portrait) return swipeDownLayout
return LinearLayout(ctx).apply {
orientation = LinearLayout.HORIZONTAL
addView(navigationView, LinearLayout.LayoutParams(
ViewUtils.dp(AdaptiveNavigationPolicy.NAVIGATION_RAIL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT))
addView(DividerView(ctx), LinearLayout.LayoutParams(ViewUtils.dp(1), ViewGroup.LayoutParams.MATCH_PARENT))
addView(menuView, LinearLayout.LayoutParams(ViewUtils.dp(MENU_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT))
addView(swipeDownLayout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
}
}
private fun rebuildAdaptiveLayout(ctx:Context, portrait:Boolean) {
contentView?.let(::detachFromParent)
detachFromParent(menuView)
for (index in 0 until menuMap.size()) {
val menu = menuMap.valueAt(index)
if (menu.getView() != null) menu.onViewDestroyed()
}
menuView.removeAllViews()
val activeMenu = menuMap.get(currentMenuSlot)
activeMenu.onSetBed(this)
activeMenu.onViewCreated(activeMenu.onCreateView(ctx, portrait))
menuView.addView(activeMenu.getView(), FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
contentView = createWorkspaceContent(ctx, portrait)
compactPortraitLayout = portrait
configureMenuNavigation()
overlayLayout!!.addView(contentView, 0, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
updateAdaptiveOverlayMargins(portrait)
glView!!.requestRender()
}
private fun updateAdaptiveOverlayMargins(portrait:Boolean) {
val params = snackbarsLayout?.layoutParams as? FrameLayout.LayoutParams ?: return
params.leftMargin = if (portrait) ViewUtils.dp(8)
    else ViewUtils.dp(AdaptiveNavigationPolicy.NAVIGATION_RAIL_WIDTH_DP + MENU_SIZE_DP + 8)
params.bottomMargin = if (portrait)
    ViewUtils.dp(menuPortraitHeightDp() + AdaptiveNavigationPolicy.NAVIGATION_BAR_HEIGHT_DP) else 0
snackbarsLayout!!.layoutParams = params
}
internal fun restoreWorkspaceAfterMenuDismiss() {
glView?.apply {
translationX = 0f
translationY = 0f
invalidate()
}
snackbarsLayout?.translationY = 0f
val ctx = getContext() ?: return
updateAdaptiveOverlayMargins(usesCompactPortraitLayout(ctx))
glView?.requestRender()
}
private fun menuPortraitHeightDp(): Int =
if (currentMenuSlot == MenuCategory.TRANSFORM.ordinal || currentMenuSlot == MenuCategory.CAMERA.ordinal) 124
else MENU_PORTRAIT_SIZE_DP

private fun selectMenu(ctx:Context, portrait:Boolean, slot:Int) {
if (glView!!.renderer.resetFlattenMode())
{
glView!!.requestRender()
SliceBeam.EVENT_BUS.fireEvent(FlattenModeResetEvent())
}
isAnimatingMenu = true
val prevMenu = menuMap.get(currentMenuSlot)
val forward = slot > currentMenuSlot
currentMenuSlot = slot
selectedMenuNavigationSlot = slot
if (portrait) {
menuView.layoutParams = (menuView.layoutParams as LinearLayout.LayoutParams).apply {
height = ViewUtils.dp(menuPortraitHeightDp())
}
updateAdaptiveOverlayMargins(true)
}

val currentMenu = menuMap.get(currentMenuSlot)
if (currentMenu.getView() == null)
{
currentMenu.onSetBed(this)
currentMenu.onViewCreated(currentMenu.onCreateView(ctx, portrait))
}
val v = currentMenu.getView()
if (v!!.getParent() != null)
{
menuView.removeView(v)
}
menuView.addView(v, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
val next = { if (portrait)
{
v!!.setTranslationX((v!!.getWidth() * (if (forward) 1 else -1)).toFloat())
}
else
{
v!!.setTranslationY((v!!.getHeight() * (if (forward) 1 else -1)).toFloat())
}
v!!.setAlpha(0f)
val prevView = prevMenu.getView()
SpringAnimation(FloatValueHolder((0).toFloat()))
.setMinimumVisibleChange(1 / 256f)
.setSpring(SpringForce(1f)
.setStiffness(1000f)
.setDampingRatio(1f))
.addUpdateListener({ animation, value, velocity->
prevView!!.setAlpha(1f - value)
v!!.setAlpha(value)
if (portrait)
{
prevView!!.setTranslationX(-v!!.getWidth() * value * 0.5f * (if (forward) 1 else -1))
v!!.setTranslationX(v!!.getWidth() * (1f - value) * 0.5f * (if (forward) 1 else -1))
}
else
{
prevView!!.setTranslationY(-prevView!!.getHeight() * value * 0.5f * (if (forward) 1 else -1))
v!!.setTranslationY(v!!.getHeight() * (1f - value) * 0.5f * (if (forward) 1 else -1))
} })
.addEndListener({ animation, canceled, value, velocity->
menuView.removeView(prevMenu.getView())
isAnimatingMenu = false })
.start() }
if (!v!!.isLaidOut())
{
v!!.addOnLayoutChangeListener(object:View.OnLayoutChangeListener {
override fun onLayoutChange(v:View, left:Int, top:Int, right:Int, bottom:Int, oldLeft:Int, oldTop:Int, oldRight:Int, oldBottom:Int) {
v.removeOnLayoutChangeListener(this)
next()
}
})
}
else
{
next()
}
}
fun interface ModelLoadCallback {
fun onLoaded(loadedModel:Model, firstNewObject:Int, addedObjects:Int)
fun onFailed(error:Exception) {}
fun onCancelled() {}
fun onProgress(fraction:Float) {}
}
private class ImportCancelledException : Exception()

private fun confirmLargeStlImport(triangles: Long): Boolean {
    val approved = java.util.concurrent.atomic.AtomicBoolean(false)
    val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
    val completed = CountDownLatch(1)
    val dialogRef = java.util.concurrent.atomic.AtomicReference<AlertDialog?>()
    ViewUtils.postOnMainThread {
        val ctx = getContext()
        if (destroyed || ctx == null || timedOut.get()) { completed.countDown(); return@postOnMainThread }
        val dialog = BeamAlertDialogBuilder(ctx)
            .setTitle(R.string.ImportLargeStlTitle)
            .setMessage(ctx.getString(R.string.ImportLargeStlMessage, java.text.NumberFormat.getIntegerInstance().format(triangles)))
            .setPositiveButton(R.string.ImportLargeStlContinue) { _, _ -> approved.set(true) }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { completed.countDown() }
            .create()
        dialogRef.set(dialog)
        dialog.show()
    }
    if (!completed.await(2, TimeUnit.MINUTES)) {
        timedOut.set(true)
        ViewUtils.postOnMainThread { dialogRef.getAndSet(null)?.dismiss() }
        return false
    }
    return approved.get() && !destroyed
}
private data class ImportReduction(val before: Long, val after: Long)

/** All import paths pass here before the renderer sees a large mesh. A dismissal preserves it. */
private fun simplifyImportedModel(model: Model): ImportReduction? {
    val oversized = (0 until model.objectsCount).any { model.getTriangleCount(it) > IMPORT_TRIANGLE_LIMIT }
    if (!oversized || !confirmImportSimplification()) return null
    var beforeTotal = 0L
    var afterTotal = 0L
    for (index in 0 until model.objectsCount) {
        val original = model.getTriangleCount(index)
        if (original <= IMPORT_TRIANGLE_LIMIT) continue
        var current = original
        repeat(2) {
            if (current > IMPORT_TRIANGLE_LIMIT) {
                val ratio = (IMPORT_TRIANGLE_TARGET.toDouble() / current).toFloat().coerceIn(0.01f, 0.99f)
                current = model.simplify(index, ratio)
            }
        }
        if (current > IMPORT_TRIANGLE_LIMIT || current < 4) {
            throw IllegalStateException("Import could not reduce object $index below $IMPORT_TRIANGLE_LIMIT triangles")
        }
        beforeTotal += original
        afterTotal += current
    }
    return if (beforeTotal > 0L) ImportReduction(beforeTotal, afterTotal) else null
}

private fun confirmImportSimplification(): Boolean {
    importSimplifyDecision?.let { return it }
    val decision = java.util.concurrent.atomic.AtomicBoolean(false)
    val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
    val dialogRef = java.util.concurrent.atomic.AtomicReference<AlertDialog?>()
    val completed = CountDownLatch(1)
    ViewUtils.postOnMainThread {
        val ctx = getContext()
        if (destroyed || ctx == null || timedOut.get()) {
            completed.countDown()
            return@postOnMainThread
        }
        val dialog = BeamAlertDialogBuilder(ctx)
            .setTitle(R.string.ImportSimplifyTitle)
            .setMessage(R.string.ImportSimplifyMessage)
            .setPositiveButton(R.string.ImportSimplifyAccept) { _, _ -> decision.set(true) }
            .setNegativeButton(R.string.ImportSimplifyKeep) { _, _ -> decision.set(false) }
            .setOnDismissListener { completed.countDown() }
            .create()
        dialogRef.set(dialog)
        dialog.show()
    }
    // Import runs off the main thread. A lost activity must not strand its worker.
    if (!completed.await(2, TimeUnit.MINUTES)) {
        timedOut.set(true)
        ViewUtils.postOnMainThread { dialogRef.getAndSet(null)?.dismiss() }
    }
    val approved = !timedOut.get() && decision.get()
    importSimplifyDecision = approved
    return approved
}

private fun reportImportReduction(reduction: ImportReduction?) {
    if (reduction != null) {
        ViewUtils.postOnMainThread({
            val formatter = java.text.NumberFormat.getIntegerInstance()
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                SnackbarsLayout.Type.INFO, R.string.MenuFileOpenSimplifiedLoaded,
                formatter.format(reduction.before), formatter.format(reduction.after)))
        })
    }
}
private fun addNewPlate() {
platePreviewRevision++
platesModels.add(Model())
while (projectPlateIds.size < platesModels.size - 1) projectPlateIds.add(0)
projectPlateIds.add(0)
totalPlates = platesModels.size
switchPlate(platesModels.size - 1)
updatePlateIndicator()
}
private fun removePlate(plateIndex:Int) {
if (plateIndex !in platesModels.indices) return
val hasModels = platesModels[plateIndex]?.objectsCount?.let { it > 0 } ?: (getProjectPlateId(plateIndex) > 0)
if (hasModels) {
    val ctx = getContext() ?: return
    BeamAlertDialogBuilder(ctx)
        .setMessage(R.string.PlateDeleteConfirm)
        .setPositiveButton(android.R.string.ok) { _, _ -> removePlateConfirmed(plateIndex) }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    return
}
removePlateConfirmed(plateIndex)
}
private fun removePlateConfirmed(plateIndex:Int) {
platePreviewRevision++
val closeDecision = PlateTabsPolicy.afterClose(platesModels.size, currentPlateIndex, plateIndex) ?: return
if (closeDecision.resetOnlyPlate)
{
TemporaryProjectSettings.clear()
val removed = platesModels.set(0, Model())
projectPlateIds.clear()
projectPlateIds.add(0)
currentPlateIndex = 0
currentProjectFile = null
sourceProjectPlateCount = 1
totalPlates = 1
switchPlate(0, false)
if (removed != null) glView!!.queueEvent(Runnable { removed.release() })
updatePlateIndicator()
return
}
val removed = platesModels.removeAt(plateIndex)
if (plateIndex < projectPlateIds.size) projectPlateIds.remove(plateIndex)
totalPlates = platesModels.size
currentPlateIndex = closeDecision.selectedIndex
// Re-select through the common path so any in-flight lazy load is invalidated and
// its progress indicator is dismissed after an index shift.
switchPlate(currentPlateIndex, false)
if (removed != null) glView!!.queueEvent(Runnable { removed.release() })
updatePlateIndicator()
if (platesModels.all { it != null && it.objectsCount == 0 }) TemporaryProjectSettings.clear()
}
/**
 * Hand the renderer every plate except the active one, with grid offsets relative to the
 * active plate (which always renders at the origin), so all plates are visible at once.
 */
 private fun pushPlatesToRenderer() {
val active = currentPlateIndex
val models = java.util.ArrayList(platesModels)
glView!!.queueEvent(object:Runnable {
override fun run() {
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val inactive = java.util.ArrayList<Model>()
val offsets = java.util.ArrayList<DoubleArray>()
if (models.size > 1)
{
val cols = computePlateCols(models.size)
val activeOrigin = DoubleArray(2)
val tmp = DoubleArray(2)
computePlateOrigin(active, cols, bed.getVolumeMin()!!, bed.getVolumeMax()!!, activeOrigin)
for (i in 0 until models.size)
{
if (i == active || models.get(i) == null) continue
computePlateOrigin(i, cols, bed.getVolumeMin()!!, bed.getVolumeMax()!!, tmp)
val inactiveModel = models.get(i) ?: continue
inactive.add(inactiveModel)
offsets.add(doubleArrayOf(tmp[0] - activeOrigin[0], tmp[1] - activeOrigin[1]))
}
}
glView!!.renderer.setCurrentPlateIndex(active)
glView!!.renderer.setInactivePlates(inactive, offsets)
glView!!.requestRender()
}
})
}
private fun switchPlate(newPlateIndex:Int, saveCurrent:Boolean = true, loadCallback:ModelLoadCallback? = null) {
if (destroyed || modelLoadInProgress || newPlateIndex < 0 || newPlateIndex >= platesModels.size) return
val loadGeneration = ++plateLoadGeneration
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("plate-load"))
// Save current model only if requested
 if (saveCurrent)
{
syncCurrentModel()
}
currentPlateIndex = newPlateIndex
updatePlateIndicator()
var nextModel = platesModels.get(currentPlateIndex)
// Lazy load from 3MF project if it's null (not yet loaded)
 val sourcePlateId = getProjectPlateId(currentPlateIndex)
if (nextModel == null && currentProjectFile != null && sourcePlateId > 0)
{
val requestedPlate = currentPlateIndex
val projectFile = currentProjectFile ?: return
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
SnackbarsLayout.Type.LOADING, R.string.MenuFileOpenFileLoading).tag("plate-load"))
glView!!.queueEvent({ glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.model = null
glView!!.renderer.resetGlModels()
glView!!.requestRender() })
pushPlatesToRenderer()
plateLoadExecutor.execute({ if (loadGeneration != plateLoadGeneration) return@execute
val projectBed = DoubleArray(2)
val haveProjectBed = readProjectBedSize(projectFile, projectBed)
val loaded:Model
val reduction:ImportReduction?
try
{
loaded = Model(projectFile, sourcePlateId)
reduction = try { simplifyImportedModel(loaded) } catch (e: Exception) { loaded.release(); throw e }
}
catch (e:Exception) {
android.util.Log.e("BedFragment", "Failed to load plate " + (requestedPlate + 1), e)
ViewUtils.postOnMainThread({ if (!destroyed && loadGeneration == plateLoadGeneration && getContext() != null)
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("plate-load"))
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileFailed))
} })
return@execute
}
synchronized (pendingPlateModelsLock) {
if (destroyed || loadGeneration != plateLoadGeneration)
{
loaded.release()
return@execute
}
pendingPlateModels.add(loaded)
}
glView!!.queueEvent(object:Runnable {
override fun run() {
synchronized (pendingPlateModelsLock) {
if ((destroyed || loadGeneration != plateLoadGeneration || requestedPlate != currentPlateIndex
|| requestedPlate >= platesModels.size
|| currentProjectFile == null
|| !projectFile!!.equals(currentProjectFile)))
{
pendingPlateModels.remove(loaded)
loaded.release()
return
}
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val bedW = if (haveProjectBed) projectBed[0] else bed.getVolumeMax()!!.x - bed.getVolumeMin()!!.x
val bedD = if (haveProjectBed) projectBed[1] else bed.getVolumeMax()!!.y - bed.getVolumeMin()!!.y
val origin = DoubleArray(2)
computePlateOrigin(sourcePlateId - 1, computePlateCols(sourceProjectPlateCount), bedW, bedD, origin)
if (origin[0] != 0.0 || origin[1] != 0.0)
{
for (i in 0 until loaded.objectsCount)
{
loaded.translate(i, -origin[0], -origin[1], (0).toDouble())
}
loaded.resetBoundingBox()
}
pendingPlateModels.remove(loaded)
platesModels.set(requestedPlate, loaded)
platePreviewRevision++
updatePlateIndicator()
glView!!.renderer.model = loaded
glView!!.renderer.resetGlModels()
resetModelHistory()
glView!!.requestRender()
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("plate-load"))
if (loadCallback != null)
{
loadCallback.onLoaded(loaded, 0, loaded.objectsCount)
}
reportImportReduction(reduction)
}
}
}) })
return
}
// If it's already loaded or freshly created fallback
 if (nextModel == null)
{
nextModel = Model()
platesModels.set(currentPlateIndex, nextModel)
}
val finalNext = nextModel
android.util.Log.e("BedFragment", "Queueing setModel with finalNext pointer: " + (if (finalNext != null) finalNext.pointer else "null"))
glView!!.queueEvent({ android.util.Log.e("BedFragment", "Executing setModel with finalNext pointer: " + (if (finalNext != null) finalNext.pointer else "null"))
glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.setCurrentPlateIndex(currentPlateIndex)
glView!!.renderer.model = finalNext
resetModelHistory()
glView!!.requestRender() })
pushPlatesToRenderer()
}
private fun updatePlateIndicator() {
if (plateTabsScroll == null) return
ViewUtils.postOnMainThread({ if (destroyed || plateTabsScroll == null || getContext() == null) return@postOnMainThread
plateTabCount = platesModels.size
selectedPlateTabIndex = if (platesModels.isEmpty()) 0 else currentPlateIndex.coerceIn(0, platesModels.size - 1)
plateObjectCounts = platesModels.map { it?.objectsCount ?: -1 }
})
}
/** Reuse the native model bounds to draw tiny XY footprints; never render a second 3D scene. */
private fun refreshPlateThumbnails() {
    val viewport = glView ?: return
    val revision = platePreviewRevision
    if (platePreviewBuiltRevision == revision && plateThumbnails.size == platesModels.size) return
    val models = platesModels.toList()
    viewport.queueEvent {
        val bed = viewport.renderer.bed
        val minimum = bed?.getVolumeMin()
        val maximum = bed?.getVolumeMax()
        val width = if (minimum != null && maximum != null) maximum.x - minimum.x else 0.0
        val depth = if (minimum != null && maximum != null) maximum.y - minimum.y else 0.0
        val previews = models.map { model ->
            val footprints = ArrayList<PlateFootprint>()
            if (model != null && minimum != null && width > 0.0 && depth > 0.0) try {
                val lower = Vec3d()
                val upper = Vec3d()
                val previewLimit = if (models.size > 6) 6 else 24
                for (objectIndex in 0 until model.objectsCount.coerceAtMost(previewLimit)) {
                    model.getBoundingBoxExact(objectIndex, lower, upper)
                    val left = ((lower.x - minimum.x) / width).toFloat().coerceIn(0f, 1f)
                    val bottom = ((lower.y - minimum.y) / depth).toFloat().coerceIn(0f, 1f)
                    val right = ((upper.x - minimum.x) / width).toFloat().coerceIn(0f, 1f)
                    val top = ((upper.y - minimum.y) / depth).toFloat().coerceIn(0f, 1f)
                    if (right > left && top > bottom) footprints.add(PlateFootprint(left, bottom, right - left, top - bottom))
                }
            } catch (error: Exception) {
                Log.w("BedFragment", "Plate thumbnail unavailable", error)
            }
            PlateThumbnail(footprints)
        }
        ViewUtils.postOnMainThread {
            if (destroyed || revision != platePreviewRevision || models.size != platesModels.size) return@postOnMainThread
            plateThumbnails = previews
            platePreviewBuiltRevision = revision
        }
    }
}
@Throws(Slic3rRuntimeError::class)
@JvmOverloads fun loadModel(f:File, preserveProjectLayout:Boolean = false, plateCount:Int = 1, callback:ModelLoadCallback? = null) {
if (destroyed || modelLoadInProgress)
{
if (callback != null)
    ViewUtils.postOnMainThread({ callback?.onFailed(
IllegalStateException("Ein anderes Modell wird gerade geladen.")) })
return
}
modelLoadInProgress = true
val importGeneration = ++modelImportGeneration
platePreviewRevision++
importSimplifyDecision = null
plateLoadGeneration++
Thread({ try
{
StlImportPreflight.largeTriangleCount(f, IMPORT_TRIANGLE_LIMIT.toLong())?.let {
    if (!confirmLargeStlImport(it)) throw ImportCancelledException()
}
loadModelBlocking(f, preserveProjectLayout, plateCount, importGeneration, callback)
}
catch (e:Exception) {
if (e is ImportCancelledException) {
    ViewUtils.postOnMainThread { if (!destroyed && importGeneration == modelImportGeneration) callback?.onCancelled() }
    return@Thread
}
Log.e("BedFragment", "Could not load model", e)
if (plateCount > 1) resetFailedMultiPlateImport(importGeneration)
ViewUtils.postOnMainThread({ if (destroyed || importGeneration != modelImportGeneration) return@postOnMainThread
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileFailed))
if (callback != null) callback?.onFailed(e) })
}
finally
{
modelLoadInProgress = false
} }, "OrcaModelLoader").start()
}
private fun resetFailedMultiPlateImport(importGeneration:Int) {
ViewUtils.postOnMainThread({ if (destroyed || importGeneration != modelImportGeneration) return@postOnMainThread
val failedModels = java.util.ArrayList(platesModels)
val emptyModel = Model()
platesModels.clear()
platesModels.add(emptyModel)
projectPlateIds.clear()
projectPlateIds.add(0)
currentPlateIndex = 0
totalPlates = 1
currentProjectFile = null
sourceProjectPlateCount = 1
updatePlateIndicator()
glView!!.queueEvent({ glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.releaseMeshResources()
glView!!.renderer.setCurrentPlateIndex(0)
glView!!.renderer.model = emptyModel
for (model in failedModels)
{
if (model != null && model !== emptyModel) model.release()
}
glView!!.requestRender() }) })
}
@Throws(Slic3rRuntimeError::class)
private fun loadModelBlocking(f:File, preserveProjectLayout:Boolean, plateCount:Int, importGeneration:Int, callback:ModelLoadCallback?) {
if (destroyed) return
if (importGeneration != modelImportGeneration) return
this.totalPlates = plateCount
if (plateCount > 1)
{
this.currentProjectFile = f
this.sourceProjectPlateCount = plateCount
// Re-initialize for new 3MF project
 val oldModels = java.util.ArrayList(platesModels)
platesModels.clear()
projectPlateIds.clear()
glView!!.queueEvent({ glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.model = null
glView!!.renderer.resetGlModels()
for (m in oldModels)
{
if (m != null) m.release()
} })
for (i in 0 until plateCount)
{
platesModels.add(null)
projectPlateIds.add(i + 1)
}
this.currentPlateIndex = 0
// Load only the active plate. A multi-plate project can contain several large meshes;
 // keeping every plate's native model alive during import needlessly multiplies the
 // peak memory use. Other plates are loaded by switchPlate() when the user selects one.
 // The renderer still displays the complete plate grid while unloaded plates remain
 // empty, and their models keep the original 3MF plate coordinates until normalized.
 val cols = computePlateCols(plateCount)
val fileBed = DoubleArray(2)
val haveFileBed = readProjectBedSize(f, fileBed)
// Let the normal load-failure path report a corrupt or unsupported active plate.
 // Replacing it with an empty model made a failed multi-plate import look successful.
 val firstPlate = Model(f, 1)
 val reduction = try { simplifyImportedModel(firstPlate) } catch (e: Exception) { firstPlate.release(); throw e }
 platesModels.set(0, firstPlate)
platePreviewRevision++
val finalCallback = callback
glView!!.queueEvent(object:Runnable {
override fun run() {
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val bedW = if (haveFileBed) fileBed[0] else bed.getVolumeMax()!!.x - bed.getVolumeMin()!!.x
val bedD = if (haveFileBed) fileBed[1] else bed.getVolumeMax()!!.y - bed.getVolumeMin()!!.y
val origin = DoubleArray(2)
val first = platesModels.get(0)!!
computePlateOrigin(0, cols, bedW, bedD, origin)
if (origin[0] != 0.0 || origin[1] != 0.0)
{
for (o in 0 until first.objectsCount)
{
first.translate(o, -origin[0], -origin[1], (0).toDouble())
}
}
first.resetBoundingBox()
glView!!.renderer.model = first
glView!!.renderer.resetGlModels()
if (finalCallback != null)
{
finalCallback.onLoaded(first, 0, first.objectsCount)
}
reportImportReduction(reduction)
}
})
pushPlatesToRenderer()
}
else
{
// Loading an STL or 1-plate 3MF. Just load into current plate.
 if (platesModels.size <= 1)
{
currentProjectFile = null
sourceProjectPlateCount = 1
projectPlateIds.clear()
}
if (platesModels.isEmpty())
{
platesModels.add(Model())
currentPlateIndex = 0
}
if (platesModels.get(currentPlateIndex) == null)
{
platesModels.set(currentPlateIndex, Model())
}
loadModelInternal(f, preserveProjectLayout, callback)
}
updatePlateIndicator()
}
@Throws(Slic3rRuntimeError::class)
private fun loadModelInternal(f:File, preserveProjectLayout:Boolean, callback:ModelLoadCallback?) {
// Wait, if it's a 3mf project, it already passed plate count, so currentPlateIndex is correct.
 val m = if (callback != null && f.name.endsWith(".stl", ignoreCase = true))
     Model(f, currentPlateIndex + 1, ImportProgressCallback { fraction -> callback.onProgress(fraction) })
 else Model(f, currentPlateIndex + 1) // 1-based in JNI for specific plate, or 0 for default
 val reduction = try { simplifyImportedModel(m) } catch (e: Exception) { m.release(); throw e }
val currentModel = currentModel
if (currentModel != null && currentModel.objectsCount > 0)
{
glView!!.queueEvent(object:Runnable {
override fun run() {
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val firstNewObject = currentModel.objectsCount
val addedObjects = m.objectsCount
if (!preserveProjectLayout)
{
val objMin = Vec3d()
val objMax = Vec3d()
val objTranslate = Vec3d()
for (i in 0 until addedObjects)
{
m.getTranslation(i, objTranslate)
m.getBoundingBoxExact(i, objMin, objMax)
// Only fix Z (floor level); arrange will handle X/Y placement.
 m.translate(i, objTranslate.x, objTranslate.y, -objTranslate.z + (objMax.z - objMin.z) / 2)
}
}
for (i in 0 until addedObjects)
{
currentModel.addObject(m, i)
}
m.release()
currentModel.resetBoundingBox()
if (!preserveProjectLayout)
{
bed.arrange(currentModel)
}
glView!!.renderer.resetGlModels()
if (callback != null)
{
callback?.onLoaded(currentModel, firstNewObject, addedObjects)
}
reportImportReduction(reduction)
}
})
}
else
{
glView!!.queueEvent(object:Runnable {
override fun run() {
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
if (currentModel != null) currentModel.release()
if (preserveProjectLayout && totalPlates > 1)
{
// Multi-plate 3MF meshes are stored at desktop grid coordinates. Convert
 // the selected plate back to local bed coordinates as the initial plate
 // does, using the source project's bed dimensions when available.
 val fileBed = DoubleArray(2)
val haveFileBed = readProjectBedSize(f, fileBed)
val bedW = if (haveFileBed) fileBed[0] else bed.getVolumeMax()!!.x - bed.getVolumeMin()!!.x
val bedD = if (haveFileBed) fileBed[1] else bed.getVolumeMax()!!.y - bed.getVolumeMin()!!.y
val origin = DoubleArray(2)
computePlateOrigin(currentPlateIndex, computePlateCols(totalPlates), bedW, bedD, origin)
if (origin[0] != 0.0 || origin[1] != 0.0)
{
for (i in 0 until m.objectsCount)
{
m.translate(i, -origin[0], -origin[1], (0).toDouble())
}
m.resetBoundingBox()
}
}
platesModels.set(currentPlateIndex, m)
glView!!.renderer.model = m
val addedObjects = m.objectsCount
if (!preserveProjectLayout)
{
val center = bed.getVolumeMin()!!.center(bed.getVolumeMax()!!)
val objMin = Vec3d()
val objMax = Vec3d()
val objTranslate = Vec3d()
for (i in 0 until addedObjects)
{
m.getTranslation(i, objTranslate)
m.getBoundingBoxExact(i, objMin, objMax)
// Move to center, fix Z
 m.translate(i, center.x - (objMax.x + objMin.x) / 2, center.y - (objMax.y + objMin.y) / 2, -objTranslate.z + (objMax.z - objMin.z) / 2)
}
bed.arrange(m)
}
glView!!.renderer.resetGlModels()
if (callback != null)
{
callback?.onLoaded(m, 0, addedObjects)
}
reportImportReduction(reduction)
}
})
}
glView!!.requestRender()
}
override fun onApplyTheme() {
super.onApplyTheme()
plateTabsThemeRevision++
profileStatusThemeRevision++
printerWebThemeRevision++
menuNavigationThemeRevision++
if (webViewErrIcon != null)
{
webViewErrIcon!!.setImageTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)))
webViewErrDescription.setTextColor(ThemesRepo.getColor(android.R.attr.textColorSecondary))
webViewProgressBar!!.setIndeterminateTintList(ColorStateList.valueOf(ThemesRepo.getColor(android.R.attr.textColorSecondary)))
}
menuView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground))
for (i in 0 until MenuCategory.values().size)
{
if (i != currentMenuSlot)
{
ThemesRepo.invalidateView(menuMap.get(i).getView())
}
}
}
private fun constructMenuView(ctx:Context, portrait:Boolean) {
menuView = FrameLayout(ctx)
val currentMenu = menuMap.get(currentMenuSlot)
if (currentMenu.getView() == null)
{
currentMenu.onSetBed(this)
currentMenu.onViewCreated(currentMenu.onCreateView(ctx, portrait))
}
menuView.addView(currentMenu.getView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}
fun updateModel() {
if (platesModels.isEmpty()) return
platePreviewRevision++
val currentModel = glView!!.renderer.model
platesModels.set(currentPlateIndex, currentModel)
updatePlateIndicator()
if (!modelLoadInProgress && platesModels.all { it != null && it.objectsCount == 0 }) TemporaryProjectSettings.clear()
if (isLargeModelInMemory)
{
clearModelHistory()
modelHistoryDisabledForMemory = true
warnLargeModelLimitOnce()
return
}
modelHistoryDisabledForMemory = false
if (stableModelSnapshot != null)
{
undoHistory.addLast(stableModelSnapshot)
while (undoHistory.size > MAX_HISTORY) undoHistory.removeFirst().release()
}
clearHistoryDeque(redoHistory)
stableModelSnapshot = if (currentModel != null) Model.merge(currentModel) else null
}
private fun syncCurrentModel() {
if (!platesModels.isEmpty() && glView != null)
{
platesModels.set(currentPlateIndex, glView!!.renderer.model)
}
}
private fun resetModelHistory() {
clearModelHistory()
modelHistoryDisabledForMemory = isLargeModelInMemory
if (modelHistoryDisabledForMemory)
{
warnLargeModelLimitOnce()
return
}
val current = if (glView != null) glView!!.renderer.model else null
stableModelSnapshot = if (current != null) Model.merge(current) else null
}
private fun warnLargeModelLimitOnce() {
if (largeModelWarningShown) return
largeModelWarningShown = true
ViewUtils.postOnMainThread({ SliceBeam.EVENT_BUS.fireEvent(
NeedSnackbarEvent(R.string.ModelLargeMemoryProtection)) })
}
private fun clearModelHistory() {
clearHistoryDeque(undoHistory)
clearHistoryDeque(redoHistory)
if (stableModelSnapshot != null)
{
stableModelSnapshot!!.release()
stableModelSnapshot = null
}
}
private fun clearHistoryDeque(history:ArrayDeque<Model>) {
while (!history.isEmpty()) history.removeFirst().release()
}
fun undoModelEdit() {
applyModelHistory(undoHistory, redoHistory, R.string.ModelNothingToUndo)
}
fun redoModelEdit() {
applyModelHistory(redoHistory, undoHistory, R.string.ModelNothingToRedo)
}
private fun applyModelHistory(source:ArrayDeque<Model>, destination:ArrayDeque<Model>, emptyMessage:Int) {
if (modelHistoryDisabledForMemory)
{
warnLargeModelLimitOnce()
return
}
if (source.isEmpty())
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(emptyMessage))
return
}
glView!!.queueEvent({ val current = glView!!.renderer.model
if (current != null) destination.addLast(Model.merge(current))
while (destination.size > MAX_HISTORY) destination.removeFirst().release()
val restored = source.removeLast()
glView!!.renderer.model = restored
platesModels.set(currentPlateIndex, restored)
if (current != null) current.release()
if (stableModelSnapshot != null) stableModelSnapshot!!.release()
stableModelSnapshot = Model.merge(restored)
glView!!.requestRender()
ViewUtils.postOnMainThread({ SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent()) }) })
}
/** Split the selected object into separate objects while preserving the imported layout. */
 fun splitSelectedObject() {
val idx = glView!!.renderer.selectedObject
val model = glView!!.renderer.model
if (model == null || idx == -1) return
glView!!.queueEvent({ val count = model.split(idx)
if (count <= 1)
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuToolbarSplitNothing))
return@queueEvent
}
glView!!.renderer.setSelectedObject(-1)
glView!!.renderer.resetGlModels()
glView!!.requestRender()
ViewUtils.postOnMainThread({ updateModel()
SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent())
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
SliceBeam.INSTANCE!!.getResources().getQuantityString(
R.plurals.MenuToolbarSplitDone, count, count))) }) })
}
enum class MenuCategory private constructor(
internal val titleRes:Int,
internal val iconRes:Int,
internal val navigationTitleRes:Int = titleRes,
) {
FILE(R.string.MenuFile, R.drawable.folder_simple_outline_28),
CAMERA(R.string.MenuCamera, R.drawable.camera_outline_28),
TRANSFORM(R.string.MenuToolbar, R.drawable.menu_scale_28, R.string.MenuToolbarNavigation),
OPTIONS(R.string.MenuOptions, R.drawable.settings_outline_28),
SLICE_AND_EXPORT(R.string.MenuSlice, R.drawable.magic_wand_outline_28);
}
companion object {
private val DEBUG_VIEWER = false
private val MENU_SIZE_DP = 96
private val MENU_PORTRAIT_SIZE_DP = 68
private fun isPortraitWindow(context:Context):Boolean {
val configuration = context.getResources().getConfiguration()
return AdaptiveNavigationPolicy.isPortraitWindow(
configuration.screenWidthDp,
configuration.screenHeightDp,
configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
}
private fun usesCompactPortraitLayout(context:Context):Boolean {
val configuration = context.getResources().getConfiguration()
return AdaptiveNavigationPolicy.usesCompactPortraitLayout(
configuration.screenWidthDp,
configuration.screenHeightDp,
configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
}
private val SESSION_PLATE_COUNT = "last_session_plate_count"
private val MAX_HISTORY = 5
private val LARGE_MODEL_NATIVE_HEAP_BYTES = 256L * 1024L * 1024L
private const val IMPORT_TRIANGLE_LIMIT = 500_000
private const val IMPORT_TRIANGLE_TARGET = 400_000
var tempFileName: String? = null
private var tempExportingFile: File? = null
val tempGCodePath:File
get() {
return tempExportingFile ?: File(SliceBeam.INSTANCE!!.getCacheDir(), "temp.gcode")
}
private fun detachFromParent(view:View) {
if (view != null && view.getParent() is ViewGroup)
{
(view.getParent() as ViewGroup).removeView(view)
}
}
/** Desktop OrcaSlicer plate grid column count (PartPlateList::compute_colum_count). */
 private fun computePlateCols(count:Int):Int {
val value = (Math.sqrt((count).toDouble())).toFloat()
val round = Math.round(value)
return if (value > round) round.toInt() + 1 else round.toInt()
}
/**
 * Plate origin in project world space, matching desktop PartPlateList::compute_origin:
 * stride = bed size * (1 + LOGICAL_PART_PLATE_GAP=1/5), columns extend +X, rows extend -Y.
 */
 private fun computePlateOrigin(index:Int, cols:Int, bedWidth:Double, bedDepth:Double, out:DoubleArray) {
out[0] = (index % cols).toDouble() * bedWidth * 1.2
out[1] = (-(index / cols)).toDouble() * bedDepth * 1.2
}
private fun computePlateOrigin(index:Int, cols:Int, bedMin:Vec3d, bedMax:Vec3d, out:DoubleArray) {
computePlateOrigin(index, cols, bedMax.x - bedMin.x, bedMax.y - bedMin.y, out)
}
/**
 * Bed size the project file was laid out for, from its embedded printable_area. The world
 * offsets of objects on plate 2+ are multiples of THIS bed's stride â€” which may differ from
 * the app's configured bed. Returns true and fills out[0]=width, out[1]=depth on success.
 */
 private fun readProjectBedSize(f:File, out:DoubleArray):Boolean {
try
{
java.util.zip.ZipFile(f).use({ zip->
val en = zip.getEntry("Metadata/project_settings.config")
if (en == null) return false
val cfg = org.json.JSONObject(
ru.ytkab0bp.slicebeam.utils.IOUtils.readString(zip.getInputStream(en), true))
val area = cfg.optJSONArray("printable_area")
if (area == null || area.length() == 0) return false
var minX = Double.MAX_VALUE
var maxX = -Double.MAX_VALUE
var minY = Double.MAX_VALUE
var maxY = -Double.MAX_VALUE
for (i in 0 until area.length())
{
val pt = area.getString(i).split("x")
if (pt.size != 2) return false
val x = java.lang.Double.parseDouble(pt[0])
val y = java.lang.Double.parseDouble(pt[1])
minX = Math.min(minX, x)
maxX = Math.max(maxX, x)
minY = Math.min(minY, y)
maxY = Math.max(maxY, y)
}
if (maxX <= minX || maxY <= minY) return false
out[0] = maxX - minX
out[1] = maxY - minY
return true })
}
catch (e:Exception) {
android.util.Log.w("BedFragment", "Could not read project bed size", e)
return false
}
}
}
}
