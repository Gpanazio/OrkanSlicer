package ru.ytkab0bp.slicebeam.render
import android.opengl.GLES30.*
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.core.graphics.ColorUtils
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.LinkedHashSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent
import ru.ytkab0bp.slicebeam.slic3r.Bed3D
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.GCodeViewer
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.slic3r.GLShaderProgram
import ru.ytkab0bp.slicebeam.slic3r.GLShadersManager
import ru.ytkab0bp.slicebeam.slic3r.LargeModelPick
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.PaintSession
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.utils.DoubleMatrix
import ru.ytkab0bp.slicebeam.utils.FillBedPlanner
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.GLView
class GLRenderer(glView:GLView):GLSurfaceView.Renderer {
var camera = Camera()
var projectionMatrix = DoubleArray(16)
private var modelMatrix = DoubleArray(16)
private var normalMatrix = DoubleArray(12)
private var outModelMatrix = DoubleArray(16)
private var cutPlaneModelMatrix = DoubleArray(16)
private var cutPlaneOutModelMatrix = DoubleArray(16)
var viewportWidth:Int = 0
var viewportHeight:Int = 0
private var cameraIsDirty = true
// Instance values, should be released
 var bed: Bed3D? = null
private var lastConfigUid:Int = 0
private var shadersManager: GLShadersManager? = null
private var backgroundModel: GLModel? = null
private var selectionModel: GLModel? = null
private var glModels = ArrayList<GLModel>()
var model:Model? = null
set(model) {
field = model
if (previewPaletteModel !== model) {
    previewPaletteModel = null
    previewPalette = null
}
resetGlModels()
}
// Draggable prime tower preview. Drawn in bed coordinates and committed to wipe_tower_x/y.
 private var primeTowerModel: GLModel? = null
private var currentPlateIndex = 0
private var primeTowerPreviewX = Double.NaN
private var primeTowerPreviewY = Double.NaN
private var primeTowerGrabOffsetX = Double.NaN
private var primeTowerGrabOffsetY = Double.NaN
private var primeTowerWidth = -1.0
private var primeTowerDepth = -1.0
private var primeTowerHeight = 14.0
private val primeTowerBedHits = ArrayList<GLModel.HitResult>()
// Inactive plates of a multi-plate project. Each Model is bed-local; offsets are the plate's
 // grid position relative to the active plate (which always renders at the origin). Models are
 // owned by BedFragment; only the GLModels here are owned by the renderer.
 private val inactivePlateModels = ArrayList<Model>()
private val inactivePlateGlModels = ArrayList<MutableList<GLModel>>()
private val inactivePlateOffsets = ArrayList<DoubleArray>()
private var inactivePlateSimplePreview: Boolean? = null
private val plateViewMatrix = DoubleArray(16)
var gcodeResult: GCodeProcessorResult? = null
var viewer: GCodeViewer? = null
private var isViewerEnabled:Boolean = false
// Primary selection (drives the transform gizmo, flatten, fill-bed, duplicate).
 // selectedObjects holds the full multi-selection; when non-empty it always contains selectedObject.
 var selectedObject = -1
private val selectedObjects = LinkedHashSet<Int>()
private var selX:Double = 0.toDouble()
private var selY:Double = 0.toDouble()
private var selZ:Double = 0.toDouble()
private var selRotX:Double = 0.toDouble()
private var selRotY:Double = 0.toDouble()
private var selRotZ:Double = 0.toDouble()
private var selScaleX = 1.0
private var selScaleY = 1.0
private var selScaleZ = 1.0
private var lastDraw:Long = 0
private lateinit var glView: GLView
private var translate = Vec3d()
private var rotate = Vec3d()
private var raycastHits = ArrayList<GLModel.HitResult>()
private var lastApproximateSurfaceWarningAt = 0L
private var bbMin = Vec3d()
private var bbMax = Vec3d()
private var isInFlattenMode:Boolean = false
private var flattenPlanes = ArrayList<GLModel>()
@get:Synchronized var isInMeasureMode = false
@Synchronized set(enabled) {
field = enabled
if (!enabled)
{
clearMeasurePoints()
}
}
@get:Synchronized @set:Synchronized var isInEmbossMode = false
private var measurePointA:Vec3d? = null
private var measurePointB:Vec3d? = null
private var measurePointModel:GLModel? = null
private var cutPlaneVisible = false
private var cutPlaneZ:Float = 0.toFloat()
private var cutPlaneRotX:Float = 0.toFloat()
private var cutPlaneRotY:Float = 0.toFloat()
private var cutPlaneMin = Vec3d()
private var cutPlaneMax = Vec3d()
private var cutPlaneDirty = false
private var cutPlaneVBO = -1
private var cutPlaneVAO = -1
private var cutPlaneVertexBuffer: java.nio.FloatBuffer? = null
private val cutConnectorPreviews = java.util.HashMap<Int, MutableList<CutConnectorPreview>>()
private var cutConnectorPlacement = false
private var cutConnectorRadius = 5.0f
private var cutConnectorHeight = 10.0f
private var cutConnectorType = 0
private var cutConnectorsDirty = true
private var cutConnectorsVBO = -1
private var cutConnectorsVAO = -1
private var cutConnectorsVertexCount = 0
private var cutConnectorsVertexBuffer: java.nio.FloatBuffer? = null
private var paintSession: PaintSession? = null
var paintObject = -1
// ---- Multi-color painting (all methods run on the GL thread) ----
 var isPaintMode:Boolean = false
var paintFilament = 2 // 1-based palette index; 1 = base, so default to first painted color
var paintTool = PAINT_TOOL_BRUSH
var paintBrushRadius = 4.0 // mm
var heightBandWidth = 10.0 // mm
var bucketAngleThreshold = 30.0 // degrees
var isBrushSphere = false
private var paintPalette = IntArray(0)
private var previewPaletteModel: Model? = null
private var previewPalette: IntArray? = null

/** Display the colors of assigned printer gates without changing 3MF filament temperatures. GL thread only. */
fun setAssignedSlotPreview(target: Model, colors: IntArray?) {
    previewPaletteModel = target
    previewPalette = colors?.copyOf()
}
private val paintOverlays = ArrayList<PaintOverlay>()
// Persisted painting per object (built from committed mmu data), so colors stay after exiting paint mode.
 private val committedOverlays = java.util.HashMap<Int, MutableList<PaintOverlay>>()
// Cached per-frame values to avoid repeated lookups inside onDrawFrame
 private var cachedAccentColor:Int = 0
private var cachedHoverColor:Int = 0
private var cachedBgTop:Int = 0
private var cachedBgBottom:Int = 0
val isModelMultiColor:Boolean
get() {
if (this.model == null || this.model!!.pointer == 0L || this.model!!.objectsCount == 0) return false
var hasPaint = false
val activeExtruders = java.util.HashSet<Int>()
for (i in 0 until this.model!!.objectsCount)
{
if (this.model!!.hasPaint(i)) hasPaint = true
activeExtruders.addAll(this.model!!.getUsedFilaments(i).toList())
}
return hasPaint || activeExtruders.size > 1
}
/** Called only on the GL queue, before handing a dialog immutable values. */
internal fun captureSelectionSnapshot(allowEmptySelection: Boolean = false): ModelSelectionSnapshot? {
    val active = model ?: return null
    val count = active.objectsCount
    val indices = selectedObjects.sorted().toIntArray()
    if (indices.any { it !in 0 until count } ||
        (!allowEmptySelection && selectedObject !in indices)) return null
    return ModelEditBinding(active, active.geometryRevision, count, selectedObject, indices)
}
internal fun isSelectionSnapshotCurrent(snapshot: ModelSelectionSnapshot): Boolean {
    val active = model ?: return false
    if (active !== snapshot.model) return false
    return snapshot.matches(active, active.geometryRevision, active.objectsCount,
        selectedObject, selectedObjects.sorted().toIntArray())
}

val selectedObjectsCount:Int
get() {
return selectedObjects.size
}
private var paintModeKind = PAINT_MODE_COLOR
private class CutConnectorPreview {
internal lateinit var position:Vec3d
internal var radius:Float = 0.toFloat()
internal var height:Float = 0.toFloat()
internal var type:Int = 0
internal var rotX:Double = 0.toDouble()
internal var rotY:Double = 0.toDouble()
}
@Synchronized fun setCutConnectorPlacement(enabled:Boolean) {
cutConnectorPlacement = enabled
}
@Synchronized fun setCutConnectorDefaults(radius:Float, height:Float, type:Int) {
cutConnectorRadius = Math.max(0.1f, radius)
cutConnectorHeight = Math.max(0.1f, height)
cutConnectorType = Math.max(0, Math.min(2, type))
}
@Synchronized fun clearCutConnectorsForObject(objIdx:Int) {
val list = cutConnectorPreviews.remove(objIdx)
if (list != null) cutConnectorsDirty = true
if (this.model != null && objIdx >= 0 && objIdx < this.model!!.objectsCount)
{
this.model!!.clearConnectors(objIdx)
}
}
@Synchronized fun removeLastCutConnectorForObject(objIdx:Int) {
val list = cutConnectorPreviews.get(objIdx)
if (list == null || list.isEmpty()) return
val idx = list.size - 1
list.removeAt(idx)
if (this.model != null && objIdx >= 0 && objIdx < this.model!!.objectsCount)
{
this.model!!.removeConnector(objIdx, idx)
}
cutConnectorsDirty = true
}
@Synchronized fun getCutConnectorCount(objIdx:Int):Int {
val list = cutConnectorPreviews.get(objIdx)
return if (list == null) 0 else list.size
}
@Synchronized fun clearAllCutConnectorPreviews() {
cutConnectorPreviews.clear()
cutConnectorsDirty = true
}
@Synchronized fun setCutPlane(z:Float, rotX:Float, rotY:Float, min:Vec3d, max:Vec3d) {
cutPlaneZ = z
cutPlaneRotX = rotX
cutPlaneRotY = rotY
cutPlaneMin.set(min.x, min.y, min.z)
cutPlaneMax.set(max.x, max.y, max.z)
cutPlaneDirty = true
}
@Synchronized fun showCutPlane(show:Boolean) {
cutPlaneVisible = show
if (!show) cutConnectorPlacement = false
}
private class PaintOverlay {
var filamentIndex = 0
internal lateinit var model:GLModel
internal var color:Int = 0
}
@Volatile var bedConfigurationFailed: Boolean = false
    private set
fun setCurrentPlateIndex(currentPlateIndex:Int) {
this.currentPlateIndex = Math.max(0, currentPlateIndex)
primeTowerPreviewX = Double.NaN
primeTowerPreviewY = Double.NaN
primeTowerGrabOffsetX = Double.NaN
primeTowerGrabOffsetY = Double.NaN
}
private fun configValue(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject, key:String, fallback:String):String {
val v = if (cfg != null) cfg.get(key) else null
return if (v != null) v else fallback
}
private fun boolConfig(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject, key:String, fallback:Boolean):Boolean {
var v = configValue(cfg, key, if (fallback) "1" else "0")
if (v == null) return fallback
v = v.trim().lowercase(java.util.Locale.US)
return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v)
}
private fun isPrimeTowerEnabled(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject):Boolean {
if (cfg == null) return false
if (!isModelMultiColor) return false
if (cfg.has("enable_prime_tower")) return boolConfig(cfg, "enable_prime_tower", false)
return boolConfig(cfg, "wipe_tower", false)
}
private fun towerX(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject):Double {
if (!java.lang.Double.isNaN(primeTowerPreviewX)) return primeTowerPreviewX
return parseDouble(vectorValue(configValue(cfg, "wipe_tower_x", "15"), currentPlateIndex, 15.0), 15.0)
}
private fun towerY(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject):Double {
if (!java.lang.Double.isNaN(primeTowerPreviewY)) return primeTowerPreviewY
return parseDouble(vectorValue(configValue(cfg, "wipe_tower_y", "15"), currentPlateIndex, 15.0), 15.0)
}
private fun towerWidth(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject):Double {
var width = parseDouble(configValue(cfg, "prime_tower_width", "NaN"), Double.NaN)
if (java.lang.Double.isNaN(width)) width = parseDouble(configValue(cfg, "wipe_tower_width", "NaN"), 35.0)
return Math.max(8.0, Math.min(120.0, width))
}
private fun towerDepth(cfg:ru.ytkab0bp.slicebeam.config.ConfigObject, width:Double):Double {
val matrix = configValue(cfg, "flush_volumes_matrix", "")
var max = 0.0
if (matrix != null)
{
for (part in matrix.split("[,;]")) max = Math.max(max, parseDouble(part, 0.0))
}
if (max <= 0) max = 280.0
// Approximate the printed footprint length from purge volume so the visual marker responds
 // when the desktop-style flush-volume matrix is edited.
 return Math.max(width, Math.min(180.0, max / Math.max(1.0, width) * 6.0))
}
private fun raycastBed(screenX:Float, screenY:Float):Vec3d? {
if (bed == null || !bed!!.isValid()) return null
bed!!.raycaster.raycast(this, primeTowerBedHits, screenX, screenY)
if (primeTowerBedHits.isEmpty()) return null
return primeTowerBedHits.get(0).position
}
fun tryStartPrimeTowerDrag(screenX:Float, screenY:Float):Boolean {
if (isViewerEnabled || bed == null || !bed!!.isValid()) return false
val cfg = SliceBeam.buildCurrentConfigObject()
if (!isPrimeTowerEnabled(cfg)) return false
val p = raycastBed(screenX, screenY)
if (p == null) return false
val w = towerWidth(cfg)
val d = towerDepth(cfg, w)
val x = towerX(cfg)
val y = towerY(cfg)
if (!PrimeTowerDragLogic.containsTouchTarget(p.x, p.y, x, y, w, d)) return false
primeTowerPreviewX = x
primeTowerPreviewY = y
primeTowerGrabOffsetX = p.x - x
primeTowerGrabOffsetY = p.y - y
return true
}
fun dragPrimeTower(screenX:Float, screenY:Float):Boolean {
if (java.lang.Double.isNaN(primeTowerPreviewX) || java.lang.Double.isNaN(primeTowerPreviewY)) return false
val p = raycastBed(screenX, screenY)
if (p == null) return false
val cfg = SliceBeam.buildCurrentConfigObject()
val w = towerWidth(cfg)
val d = towerDepth(cfg, w)
val min = bed!!.getVolumeMin()
val max = bed!!.getVolumeMax()
val position = PrimeTowerDragLogic.position(
    pointerX = p.x,
    pointerY = p.y,
    grabOffsetX = primeTowerGrabOffsetX,
    grabOffsetY = primeTowerGrabOffsetY,
    bedMinX = min!!.x,
    bedMinY = min.y,
    bedMaxX = max!!.x,
    bedMaxY = max.y,
    towerWidth = w,
    towerDepth = d,
)
primeTowerPreviewX = position.x
primeTowerPreviewY = position.y
return true
}
fun finishPrimeTowerDrag(commit:Boolean):Boolean {
if (java.lang.Double.isNaN(primeTowerPreviewX) || java.lang.Double.isNaN(primeTowerPreviewY)) return false
if (commit)
{
val cfg = SliceBeam.buildCurrentConfigObject()
val xs = vectorWithValue(configValue(cfg, "wipe_tower_x", "15"), currentPlateIndex, primeTowerPreviewX)
val ys = vectorWithValue(configValue(cfg, "wipe_tower_y", "15"), currentPlateIndex, primeTowerPreviewY)
val pending = HashMap<String, String>()
pending.put("wipe_tower_x", xs)
pending.put("wipe_tower_y", ys)
ProfileSettingsRepository.setPendingValues(ConfigObject.PROFILE_LIST_PRINT, pending)
}
primeTowerPreviewX = Double.NaN
primeTowerPreviewY = Double.NaN
primeTowerGrabOffsetX = Double.NaN
primeTowerGrabOffsetY = Double.NaN
return true
}
private val afterFrameActions = ArrayList<Runnable>()
/** Register on the GL thread; delivered on the UI thread after the frame is drawn. */
fun afterNextFrame(action: Runnable) { afterFrameActions.add(action) }
private fun initViewer() {
if (gcodeResult == null) return
viewer = GCodeViewer()
viewer!!.initGL()
viewer!!.setThemeColors()
val performance = Prefs.isPerformanceModeEnabled()
viewer!!.applyPerformanceMode(performance || Prefs.isLowRamDevice())
viewer!!.load(gcodeResult!!)
val preview = ru.ytkab0bp.slicebeam.slic3r.GCodePreviewPolicy.initial(
    viewer!!.getLayersCount(), performance || Prefs.isLowRamDevice(), gcodeResult!!.previewSelection,
    DevicePerformancePolicy.initialLayerBlockSize(Prefs.isLowRamDevice()),
)
gcodeResult!!.previewSelection = preview
gcodeResult!!.previewLayerCount = viewer!!.getLayersCount().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
if (viewer!!.getLayersCount() > 0) viewer!!.setLayersViewRange(preview.from.toLong() - 1, preview.to.toLong() - 1)
glView?.postInvalidate()
if (!viewer!!.isOptionVisible(GCodeViewer.OPTION_TYPE_SEAMS))
{
viewer!!.toggleOptionVisibility(GCodeViewer.OPTION_TYPE_SEAMS)
}
if (isModelMultiColor)
{
val pal = ru.ytkab0bp.slicebeam.utils.Prefs.getFilamentPalette()
val rgb = IntArray(pal.size)
for (i in pal.indices) rgb[i] = pal[i] and 0xFFFFFF
viewer!!.setToolColors(rgb)
viewer!!.viewType = GCodeViewer.VIEW_TYPE_TOOL
}
else
{
viewer!!.viewType = GCodeViewer.VIEW_TYPE_FEATURE
}
}
fun setGCodeViewer(result:GCodeProcessorResult?) {
this.isViewerEnabled = result != null
if (result !== this.gcodeResult)
{
if (result != null) {
    // Retain the editable model, but release its rebuildable GPU meshes before
    // allocating the G-code viewer. Inactive-plate metadata stays available.
    clearAllCommittedOverlays()
    for (mesh in glModels) mesh.release()
    glModels.clear()
    for (meshes in inactivePlateGlModels) {
        for (mesh in meshes) mesh.release()
        meshes.clear()
    }
}
if (viewer != null)
{
viewer!!.release()
viewer = null
}
}
this.gcodeResult = result
}
@Synchronized fun clearMeasurePoints() {
measurePointA = null
measurePointB = null
}
init{
this.glView = glView
}
override fun onSurfaceCreated(gl:GL10, config:EGLConfig) {}
override fun onSurfaceChanged(gl:GL10, width:Int, height:Int) {
if (width <= 0 || height <= 0) return
viewportWidth = width
viewportHeight = height
if (bed != null)
{
onDestroy()
}
onCreate()
glViewport(0, 0, width, height)
updateProjection()
}
fun updateProjection() {
if (bed == null || !bed!!.isValid()) return
// Any external camera position change comes through here â€” invalidate cached view matrix.
 camera.invalidate()
val aspectRatio = viewportWidth.toFloat() / viewportHeight
val invZoom = 1f / camera.zoom
if (Prefs.isOrthoProjectionEnabled())
{
val min = bed!!.getVolumeMin()!!
val max = bed!!.getVolumeMax()!!
// The diagonal also fits the isometric bed in a narrow viewport, regardless of bed origin.
val scale = (kotlin.math.hypot(max.x - min.x, max.y - min.y) / 2.0 + 10.0) * invZoom
val ratioHorizontal = if (aspectRatio > 1) aspectRatio else 1f
val ratioVertical = if (aspectRatio < 1) 1f / aspectRatio else 1f
DoubleMatrix.orthoM(projectionMatrix, 0, (-scale * ratioHorizontal).toDouble(), (scale * ratioHorizontal).toDouble(), (-scale * ratioVertical).toDouble(), (scale * ratioVertical).toDouble(), NEAR_PLANE.toDouble(), FAR_PLANE.toDouble())
}
else
{
val aspectCorrection = if (viewportWidth > viewportHeight) 1.0 / aspectRatio.toDouble() else 1.0
DoubleMatrix.perspectiveM(projectionMatrix, 0, FOV.toDouble() * invZoom.toDouble() * aspectCorrection, aspectRatio.toDouble(), NEAR_PLANE.toDouble(), FAR_PLANE.toDouble())
}
}
@JvmOverloads fun setSelectedObject(selectedObject:Int, showActionBar:Boolean = true):Boolean {
if (this.model == null && selectedObject != -1) return false
if (this.model != null && (selectedObject < -1 || selectedObject >= this.model!!.objectsCount)) return false
val sameSingle = (this.selectedObject == selectedObject
&& selectedObjects.size == (if (selectedObject == -1) 0 else 1)
&& (selectedObject == -1 || selectedObjects.contains(selectedObject)))
if (sameSingle) return false
this.selectedObject = selectedObject
selectedObjects.clear()
if (selectedObject != -1) selectedObjects.add(selectedObject)
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent(showActionBar))
return true
}
fun isSelected(i:Int):Boolean {
return selectedObjects.contains(i)
}
/** Clear object-specific controls before replacing the active build-plate model. */
fun clearSelectionForPlateSwitch() {
val selection = ObjectTapSelectionPolicy.clearForPlateSwitch(selectedObject, selectedObjects)
selectedObject = selection.primaryIndex
selectedObjects.clear()
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent(showActionBar = false))
}
/**
 * Make {@code i} the primary (gizmo) object when a drag begins. If {@code i} is already part of a
 * multi-selection, the whole selection is preserved so the drag moves the entire group; otherwise
 * this collapses to a single selection like a normal tap.
 */
 fun focusForDrag(i:Int):Boolean {
if (this.model == null || i < 0 || i >= this.model!!.objectsCount) return false
if (selectedObjects.size > 1 && selectedObjects.contains(i))
{
val changed = selectedObject != i
selectedObject = i
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
if (changed) SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent(showActionBar = false))
return true
}
return setSelectedObject(i, showActionBar = false)
}
/** Select every object on the bed. The first object becomes the primary (gizmo) selection. */
 fun selectAllObjects():Boolean {
if (this.model == null || this.model!!.objectsCount == 0) return false
selectedObjects.clear()
for (i in 0 until this.model!!.objectsCount) selectedObjects.add(i)
selectedObject = 0
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
return true
}
fun invalidateGlModel(i:Int) {
if (this.model == null) return
if (i < glModels.size)
{
val glModel = glModels.get(i)
glModel.reset()
glModel.initFrom(this.model!!, i)
}
invalidateCommittedOverlays(i)
}
fun invalidateSelectionObject() {
if (selectionModel != null)
{
selectionModel!!.reset()
}
}
fun resetGlModels() {
if (this.model == null) return
clearAllCommittedOverlays()
while (glModels.size > this.model!!.objectsCount) glModels.removeAt(glModels.lastIndex).release()
for (i in 0 until this.model!!.objectsCount)
{
if (i >= glModels.size) continue
val glModel = glModels.get(i)
glModel.reset()
glModel.initFrom(this.model!!, i)
}
}
/** Release mesh buffers that can be rebuilt from the retained native model. */
 fun releaseMeshResources() {
for (glModel in glModels) glModel.release()
glModels.clear()
for (plateModels in inactivePlateGlModels)
{
for (glModel in plateModels) glModel.release()
plateModels.clear()
}
// Keep plate references and offsets: returning from a picker must rebuild the
// cheap GPU meshes without losing the other plates from the viewport.
}
/**
 * Replace the set of inactive plates shown around the active one. Must be called on the
 * GL thread. Pass empty lists (or null) to clear. GLModels are built lazily during draw.
 */
 fun setInactivePlates(models:List<Model>, offsets:List<DoubleArray>) {
for (list in inactivePlateGlModels)
{
for (gm in list) gm.release()
}
inactivePlateGlModels.clear()
inactivePlateModels.clear()
inactivePlateOffsets.clear()
inactivePlateSimplePreview = null
if (models == null || offsets == null) return
var p = 0
while (p < models.size && p < offsets.size)
{
if (models.get(p) == null) {
p++
continue
}
inactivePlateModels.add(models.get(p))
inactivePlateOffsets.add(offsets.get(p))
inactivePlateGlModels.add(ArrayList())
p++
}
}
fun invalidateFlattenMode():Boolean {
if (isInFlattenMode)
{
setInFlattenMode(true)
return true
}
return false
}
fun resetFlattenMode():Boolean {
if (isInFlattenMode)
{
setInFlattenMode(false)
return true
}
return false
}
fun setInFlattenMode(inFlattenMode:Boolean) {
isInFlattenMode = inFlattenMode
var i = 0
val c = flattenPlanes.size
while (i < c)
{
flattenPlanes.get(i).release()
i++
}
flattenPlanes.clear()
if (isInFlattenMode)
{
val planes = this.model!!.createFlattenPlanes(selectedObject)
flattenPlanes.addAll(planes)
}
}
override fun onDrawFrame(gl:GL10) {
if (backgroundModel == null) return // Not initialized yet
val dt = Math.min(System.currentTimeMillis() - lastDraw, 16)
lastDraw = System.currentTimeMillis()
// Fetch view matrix once per frame â€” getViewModelMatrix() is lazy/cached in Camera
 val viewMatrix = camera.getViewModelMatrix()
glEnable(GL_CULL_FACE)
glCullFace(GL_BACK)
glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
glDisable(GL_DEPTH_TEST)
var shader = shadersManager!!.get(GLShadersManager.SHADER_BACKGROUND)
shader.startUsing()
shader.setUniformColor("top_color", cachedBgTop)
shader.setUniformColor("bottom_color", cachedBgBottom)
backgroundModel!!.render()
shader.stopUsing()
glEnable(GL_DEPTH_TEST)
val bottom = if (Prefs.isOrthoProjectionEnabled()) camera.getDirForward().z > 0 else camera.getDirToBed().z > 0
if (lastConfigUid != SliceBeam.CONFIG_UID)
{
configureBed()
}
if (bed!!.isValid())
{
bed!!.render(shadersManager!!, bottom, viewMatrix, projectionMatrix, 1f / camera.zoom)
// Multi-plate grid: draw the other plates' beds at their offsets (edit mode only).
 if (viewer == null && !isViewerEnabled)
{
for (p in 0 until inactivePlateOffsets.size)
{
val off = inactivePlateOffsets.get(p)
System.arraycopy(viewMatrix, 0, plateViewMatrix, 0, 16)
DoubleMatrix.translateM(plateViewMatrix, 0, (off[0]).toDouble(), (off[1]).toDouble(), (0).toDouble())
bed!!.render(shadersManager!!, bottom, plateViewMatrix, projectionMatrix, 1f / camera.zoom, false)
}
}
}
drawPrimeTowerPreview(viewMatrix)
drawMeasurePoints()
if (isViewerEnabled)
{
if (viewer == null)
{
initViewer()
}
if (viewer != null)
{
viewer!!.render(viewMatrix, projectionMatrix)
}
}
// Models on inactive plates: plain dimmed render, no selection/paint/gizmo handling.
 if (viewer == null && !isViewerEnabled && !inactivePlateModels.isEmpty())
{
val simplePreview = DevicePerformancePolicy.useSimpleInactivePlatePreview(
    Prefs.isPerformanceModeEnabled(), Prefs.isLowRamDevice()
)
if (inactivePlateSimplePreview != simplePreview) {
    for (models in inactivePlateGlModels) {
        for (mesh in models) mesh.release()
        models.clear()
    }
    inactivePlateSimplePreview = simplePreview
}
shader = shadersManager!!.get(GLShadersManager.SHADER_GOURAUD_LIGHT)
shader.startUsing()
shader.setUniform("emission_factor", 0.05f)
shader.setUniformMatrix4fv("projection_matrix", projectionMatrix)
val palette = if (isPaintMode) paintPalette else Prefs.getFilamentPalette()
val inactiveColor = ColorUtils.blendARGB(if (palette.size > 0) palette[0] else cachedAccentColor, Color.GRAY, 0.45f)
for (p in 0 until inactivePlateModels.size)
{
val m = inactivePlateModels.get(p)
val off = inactivePlateOffsets.get(p)
val list = inactivePlateGlModels.get(p)
System.arraycopy(viewMatrix, 0, plateViewMatrix, 0, 16)
DoubleMatrix.translateM(plateViewMatrix, 0, (off[0]).toDouble(), (off[1]).toDouble(), (0).toDouble())
shader.setUniformMatrix4fv("view_model_matrix", plateViewMatrix)
DoubleMatrix.setIdentityM(modelMatrix, 0)
Slic3rUtils.calcViewNormalMatrix(plateViewMatrix, modelMatrix, normalMatrix)
shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix)
for (i in 0 until m.objectsCount)
{
val left = m.isLeftHanded(i)
if (left) glFrontFace(GL_CW)
shader.setUniform("volume_mirrored", left)
while (list.size <= i)
{
val gm = GLModel()
if (simplePreview) gm.initBoundingBox(m, list.size) else gm.initFrom(m, list.size)
list.add(gm)
}
val gm = list.get(i)
gm.setColor(inactiveColor)
gm.render()
if (left) glFrontFace(GL_CCW)
}
}
shader.stopUsing()
}
if (viewer == null && this.model != null)
{
shader = shadersManager!!.get(GLShadersManager.SHADER_GOURAUD_LIGHT)
shader.startUsing()
val color = cachedAccentColor
val hoverColor = cachedHoverColor
if (!isPaintMode) {
val currentPalette = if (previewPaletteModel === model) previewPalette ?: Prefs.getFilamentPalette()
    else Prefs.getFilamentPalette()
if (!paintPalette.contentEquals(currentPalette)) {
    paintPalette = currentPalette
    // Recolor cached geometry when the temporary 3MF palette arrives after mesh loading.
    // Rebuilding every triangle just to change a color freezes large assemblies.
    for (overlays in committedOverlays.values) for (overlay in overlays)
        overlay.color = currentPalette.getOrNull(overlay.filamentIndex) ?: fallbackFilamentColor(overlay.filamentIndex)
}
}
for (i in 0 until this.model!!.objectsCount)
{
val left = this.model!!.isLeftHanded(i)
if (left)
{
glFrontFace(GL_CW)
}
val selected = selectedObjects.contains(i)
val primary = i == selectedObject
shader.setUniform("emission_factor", 0.05f)
DoubleMatrix.setIdentityM(modelMatrix, 0)
if (selected)
{
// Live move delta applies to the whole selection (group move).
 DoubleMatrix.translateM(modelMatrix, 0, selX, selY, selZ)
// Live rotation/scale only previews on the primary object (the gizmo target).
 if (primary)
{
this.model!!.getTranslation(i, translate)
this.model!!.getRotation(i, rotate)
DoubleMatrix.translateM(modelMatrix, 0, translate.x, translate.y, translate.z)
DoubleMatrix.rotateM(modelMatrix, 0, selRotX, (1).toDouble(), (0).toDouble(), (0).toDouble())
DoubleMatrix.rotateM(modelMatrix, 0, selRotY, (0).toDouble(), (1).toDouble(), (0).toDouble())
DoubleMatrix.rotateM(modelMatrix, 0, selRotZ, (0).toDouble(), (0).toDouble(), (1).toDouble())
DoubleMatrix.scaleM(modelMatrix, 0, selScaleX, selScaleY, selScaleZ)
DoubleMatrix.translateM(modelMatrix, 0, -translate.x, -translate.y, -translate.z)
}
}
DoubleMatrix.multiplyMM(outModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
shader.setUniformMatrix4fv("view_model_matrix", outModelMatrix)
shader.setUniformMatrix4fv("projection_matrix", projectionMatrix)
Slic3rUtils.calcViewNormalMatrix(viewMatrix, modelMatrix, normalMatrix)
shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix)
shader.setUniform("volume_mirrored", left)
if (glModels.size < i + 1)
{
val glModel = GLModel()
glModel.initFrom(this.model!!, i)
glModels.add(glModel)
}
val glModel = glModels.get(i)
val hovering = glModel.isHovering || selected
// FIXME: Render is lagging out with hover progress
// if (hovering && glModel.hoverProgress < 1) {
// glModel.hoverProgress = Math.min(glModel.hoverProgress + dt / 50f, 1);
// glView!!.queueEvent(() -> glView!!.requestRender());
// } else if (!hovering && glModel.hoverProgress > 0) {
// glModel.hoverProgress = Math.max(glModel.hoverProgress - dt / 50f, 0);
// glView!!.queueEvent(() -> glView!!.requestRender());
// }
 val paintingThis = isPaintMode && i == paintObject
// While painting, show the live session overlays; otherwise the persisted committed ones.
 val overlays = if (paintingThis) paintOverlays else getCommittedOverlays(i)
val baseFilament = (this.model!!.getUsedFilaments(i).firstOrNull() ?: 1) - 1
val baseColor = paintPalette.getOrNull(baseFilament) ?: color
glModel.setColor(ColorUtils.blendARGB(baseColor, Color.WHITE, if (hovering) 0.4f else 0f))
glModel.render()
if (!overlays.isEmpty())
{
// Painted facets share the object's surface; offset to avoid z-fighting.
 glEnable(GL_POLYGON_OFFSET_FILL)
glPolygonOffset(-1f, -1f)
for (o in overlays)
{
o.model.setColor(ColorUtils.blendARGB(o.color, Color.WHITE, if (hovering) 0.4f else 0f))
o.model.render()
}
glPolygonOffset(0f, 0f)
glDisable(GL_POLYGON_OFFSET_FILL)
}
if (left)
{
glFrontFace(GL_CCW)
}
if (selected)
{
shader.stopUsing()
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
glLineWidth((ViewUtils.dp(1.5f)).toFloat())
flat.startUsing()
flat.setUniformMatrix4fv("view_model_matrix", outModelMatrix)
flat.setUniformMatrix4fv("projection_matrix", projectionMatrix)
if (selectionModel == null)
{
selectionModel = GLModel()
}
selectionModel!!.initBoundingBox(this.model!!, i)
selectionModel!!.setColor(hoverColor)
selectionModel!!.render()
flat.stopUsing()
if (i == selectedObject && cutPlaneVisible)
{
drawCutPlane()
drawCutConnectors()
}
shader.startUsing()
}
if (isInFlattenMode)
{
shader.stopUsing()
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
flat.startUsing()
glEnable(GL_BLEND)
flat.setUniformMatrix4fv("view_model_matrix", outModelMatrix)
flat.setUniformMatrix4fv("projection_matrix", projectionMatrix)
for (plane in flattenPlanes)
{
val hoveringPlane = plane.isHovering
val clr = ColorUtils.blendARGB(hoverColor, color, (if (hoveringPlane) 1 else 0).toFloat())
plane.setColor(ColorUtils.setAlphaComponent(clr, ((Color.alpha(clr) * 0.75f)).toInt()))
plane.render()
}
glDisable(GL_BLEND)
flat.stopUsing()
shader.startUsing()
}
}
shader.stopUsing()
}
glDisable(GL_DEPTH_TEST)
glDisable(GL_CULL_FACE)
if (afterFrameActions.isNotEmpty()) {
    val ready = afterFrameActions.toList()
    afterFrameActions.clear()
    for (action in ready) glView?.postOnAnimation(action)
}
}
private fun rotatedCutPlaneVector(x:Double, y:Double, z:Double, rotX:Double, rotY:Double):Vec3d {
val m = DoubleArray(16)
val `in` = doubleArrayOf(x, y, z, 0.0)
val out = DoubleArray(4)
DoubleMatrix.setIdentityM(m, 0)
DoubleMatrix.rotateM(m, 0, Math.toDegrees(rotY), 0.0, 1.0, 0.0)
DoubleMatrix.rotateM(m, 0, Math.toDegrees(rotX), 1.0, 0.0, 0.0)
DoubleMatrix.multiplyMV(out, 0, m, 0, `in`, 0)
return Vec3d(out[0], out[1], out[2]).normalize()
}
private fun raycastCutPlane(x:Float, y:Float):Vec3d? {
val z:Float
val rotX:Float
val rotY:Float
val min = Vec3d()
val max = Vec3d()
synchronized (this) {
if (!cutPlaneVisible) return null
z = cutPlaneZ
rotX = cutPlaneRotX
rotY = cutPlaneRotY
min.set(cutPlaneMin.x, cutPlaneMin.y, cutPlaneMin.z)
max.set(cutPlaneMax.x, cutPlaneMax.y, cutPlaneMax.z)
}
val point = Slic3rUtils.unproject(camera.getViewModelMatrix(), projectionMatrix, viewportWidth, viewportHeight, (x).toDouble(), (y).toDouble())
var direction = camera.getDirForward().clone()
if (!Prefs.isOrthoProjectionEnabled())
{
direction = point.clone().add(camera.position.clone().negate()).normalize()
}
val planePoint = Vec3d(0.0, 0.0, (z).toDouble())
val normal = rotatedCutPlaneVector(0.0, 0.0, 1.0, rotX.toDouble(), rotY.toDouble())
val denom = normal.x * direction.x + normal.y * direction.y + normal.z * direction.z
if (Math.abs(denom) < 1e-8) return null
val t = ((planePoint.x - point.x) * normal.x + (planePoint.y - point.y) * normal.y + (planePoint.z - point.z) * normal.z) / denom
if (t < 0) return null
val hit = point.clone().add(direction.clone().multiply(t))
val cutMatrix = DoubleArray(16)
val inverse = DoubleArray(16)
val local = DoubleArray(4)
DoubleMatrix.setIdentityM(cutMatrix, 0)
DoubleMatrix.translateM(cutMatrix, 0, 0.0, 0.0, (z).toDouble())
DoubleMatrix.rotateM(cutMatrix, 0, Math.toDegrees((rotY).toDouble()), 0.0, 1.0, 0.0)
DoubleMatrix.rotateM(cutMatrix, 0, Math.toDegrees((rotX).toDouble()), 1.0, 0.0, 0.0)
if (!DoubleMatrix.invertM(inverse, 0, cutMatrix, 0)) return null
DoubleMatrix.multiplyMV(local, 0, inverse, 0, doubleArrayOf(hit.x, hit.y, hit.z, 1.0), 0)
val padding = 20.0
if (local[0] < min.x - padding || local[0] > max.x + padding || local[1] < min.y - padding || local[1] > max.y + padding)
{
return null
}
return hit
}
private fun tryAddCutConnector(x:Float, y:Float):Boolean {
val enabled:Boolean
val radius:Float
val height:Float
val type:Int
synchronized (this) {
enabled = cutConnectorPlacement
radius = cutConnectorRadius
height = cutConnectorHeight
type = cutConnectorType
}
if (!enabled || this.model == null || selectedObject < 0 || selectedObject >= this.model!!.objectsCount) return false
val hit = raycastCutPlane(x, y)
if (hit == null) return false
this.model!!.addConnector(selectedObject, hit, radius, height, type, (cutPlaneRotX).toDouble(), (cutPlaneRotY).toDouble())
val preview = CutConnectorPreview()
preview.position = hit
preview.radius = radius
preview.height = height
preview.type = type
preview.rotX = cutPlaneRotX.toDouble()
preview.rotY = cutPlaneRotY.toDouble()
synchronized (this) {
var list = cutConnectorPreviews.get(selectedObject)
if (list == null)
{
list = ArrayList()
cutConnectorPreviews.put(selectedObject, list)
}
list.add(preview)
cutConnectorsDirty = true
}
return true
}
private fun addLine(vertices:MutableList<Float>, a:Vec3d, b:Vec3d) {
vertices.add((a.x).toFloat())
vertices.add((a.y).toFloat())
vertices.add((a.z).toFloat())
vertices.add((b.x).toFloat())
vertices.add((b.y).toFloat())
vertices.add((b.z).toFloat())
}
private fun drawCutConnectors() {
var previews:List<CutConnectorPreview> = emptyList()
val dirty:Boolean
synchronized (this) {
val current = cutConnectorPreviews.get(selectedObject)
if (current == null || current.isEmpty()) return
previews = ArrayList(current)
dirty = cutConnectorsDirty
cutConnectorsDirty = false
}
if (dirty || cutConnectorsVBO == -1 || cutConnectorsVAO == -1)
{
val vertices = ArrayList<Float>()
val segments = 32
for (c in previews)
{
val bx = rotatedCutPlaneVector(1.0, 0.0, 0.0, c.rotX, c.rotY)
val by = rotatedCutPlaneVector(0.0, 1.0, 0.0, c.rotX, c.rotY)
val bn = rotatedCutPlaneVector(0.0, 0.0, 1.0, c.rotX, c.rotY)
val half = bn.clone().multiply(c.height * 0.5)
val bottomCenter = c.position.clone().add(half.clone().negate())
val topCenter = c.position.clone().add(half)
var firstBottom:Vec3d? = null
var firstTop:Vec3d? = null
var prevBottom:Vec3d? = null
var prevTop:Vec3d? = null
for (s in 0..segments)
{
val a = 2.0 * Math.PI * s / segments
val radial = bx.clone().multiply(Math.cos(a) * c.radius).add(by.clone().multiply(Math.sin(a) * c.radius))
val bottom = bottomCenter.clone().add(radial)
val top = topCenter.clone().add(radial.clone())
if (s == 0)
{
firstBottom = bottom
firstTop = top
}
else
{
addLine(vertices, prevBottom!!, bottom)
addLine(vertices, prevTop!!, top)
if (s % 8 == 0) addLine(vertices, bottom, top)
}
prevBottom = bottom
prevTop = top
}
if (firstBottom != null && firstTop != null)
{
addLine(vertices, firstBottom!!, firstTop!!)
addLine(vertices, c.position.clone().add(bx.clone().multiply(-c.radius.toDouble())), c.position.clone().add(bx.clone().multiply(c.radius.toDouble())))
addLine(vertices, c.position.clone().add(by.clone().multiply(-c.radius.toDouble())), c.position.clone().add(by.clone().multiply(c.radius.toDouble())))
}
}
cutConnectorsVertexCount = vertices.size / 3
if (cutConnectorsVertexCount == 0) return
if (cutConnectorsVertexBuffer == null || cutConnectorsVertexBuffer!!.capacity() < vertices.size)
{
val bb = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
bb.order(java.nio.ByteOrder.nativeOrder())
cutConnectorsVertexBuffer = bb.asFloatBuffer()
}
cutConnectorsVertexBuffer!!.clear()
for (f in vertices) cutConnectorsVertexBuffer!!.put(f)
cutConnectorsVertexBuffer!!.position(0)
if (cutConnectorsVBO == -1)
{
val buffers = IntArray(1)
glGenBuffers(1, buffers, 0)
cutConnectorsVBO = buffers[0]
}
glBindBuffer(GL_ARRAY_BUFFER, cutConnectorsVBO)
glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, cutConnectorsVertexBuffer, GL_STATIC_DRAW)
glBindBuffer(GL_ARRAY_BUFFER, 0)
if (cutConnectorsVAO == -1)
{
val vaos = IntArray(1)
glGenVertexArrays(1, vaos, 0)
cutConnectorsVAO = vaos[0]
}
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
val posLoc = flat.getAttribLocation("v_position")
glBindVertexArray(cutConnectorsVAO)
glBindBuffer(GL_ARRAY_BUFFER, cutConnectorsVBO)
glEnableVertexAttribArray(posLoc)
glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 3 * 4, 0)
glBindVertexArray(0)
glBindBuffer(GL_ARRAY_BUFFER, 0)
}
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
flat.startUsing()
flat.setUniformMatrix4fv("view_model_matrix", camera.getViewModelMatrix())
flat.setUniformMatrix4fv("projection_matrix", projectionMatrix)
flat.setUniform4f("uniform_color", 1.0f, 0.65f, 0.10f, 0.95f)
glLineWidth((ViewUtils.dp(2.0f)).toFloat())
glBindVertexArray(cutConnectorsVAO)
glDrawArrays(GL_LINES, 0, cutConnectorsVertexCount)
glBindVertexArray(0)
flat.stopUsing()
}
private fun drawCutPlane() {
val z:Float
val rotX:Float
val rotY:Float
val min = Vec3d()
val max = Vec3d()
val dirty:Boolean
synchronized (this) {
z = cutPlaneZ
rotX = cutPlaneRotX
rotY = cutPlaneRotY
min.set(cutPlaneMin.x, cutPlaneMin.y, cutPlaneMin.z)
max.set(cutPlaneMax.x, cutPlaneMax.y, cutPlaneMax.z)
dirty = cutPlaneDirty
cutPlaneDirty = false
}
if (dirty || cutPlaneVBO == -1 || cutPlaneVAO == -1)
{
val padding = 20.0f // mm
val minX = (min.x).toFloat() - padding
val maxX = (max.x).toFloat() + padding
val minY = (min.y).toFloat() - padding
val maxY = (max.y).toFloat() + padding
val vertices = floatArrayOf(minX, minY, 0.0f, maxX, minY, 0.0f, maxX, maxY, 0.0f, minX, maxY, 0.0f)
if (cutPlaneVertexBuffer == null)
{
val bb = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
bb.order(java.nio.ByteOrder.nativeOrder())
cutPlaneVertexBuffer = bb.asFloatBuffer()
}
cutPlaneVertexBuffer!!.clear()
cutPlaneVertexBuffer!!.put(vertices)
cutPlaneVertexBuffer!!.position(0)
if (cutPlaneVBO == -1)
{
val buffers = IntArray(1)
glGenBuffers(1, buffers, 0)
cutPlaneVBO = buffers[0]
}
glBindBuffer(GL_ARRAY_BUFFER, cutPlaneVBO)
glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, cutPlaneVertexBuffer, GL_STATIC_DRAW)
glBindBuffer(GL_ARRAY_BUFFER, 0)
if (cutPlaneVAO == -1)
{
val vaos = IntArray(1)
glGenVertexArrays(1, vaos, 0)
cutPlaneVAO = vaos[0]
}
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
val posLoc = flat.getAttribLocation("v_position")
glBindVertexArray(cutPlaneVAO)
glBindBuffer(GL_ARRAY_BUFFER, cutPlaneVBO)
glEnableVertexAttribArray(posLoc)
glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 3 * 4, 0)
glBindVertexArray(0)
glBindBuffer(GL_ARRAY_BUFFER, 0)
}
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
flat.startUsing()
System.arraycopy(modelMatrix, 0, cutPlaneModelMatrix, 0, 16)
DoubleMatrix.translateM(cutPlaneModelMatrix, 0, 0.0, 0.0, (z).toDouble())
DoubleMatrix.rotateM(cutPlaneModelMatrix, 0, Math.toDegrees((rotY).toDouble()), 0.0, 1.0, 0.0)
DoubleMatrix.rotateM(cutPlaneModelMatrix, 0, Math.toDegrees((rotX).toDouble()), 1.0, 0.0, 0.0)
val viewMatrix = camera.getViewModelMatrix()
DoubleMatrix.multiplyMM(cutPlaneOutModelMatrix, 0, viewMatrix, 0, cutPlaneModelMatrix, 0)
flat.setUniformMatrix4fv("view_model_matrix", cutPlaneOutModelMatrix)
flat.setUniformMatrix4fv("projection_matrix", projectionMatrix)
flat.setUniform4f("uniform_color", 1.0f, 1.0f, 1.0f, 0.3f)
val blendEnabled = glIsEnabled(GL_BLEND)
glEnable(GL_BLEND)
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
glDepthMask(false)
val cullEnabled = glIsEnabled(GL_CULL_FACE)
glDisable(GL_CULL_FACE)
glBindVertexArray(cutPlaneVAO)
glDrawArrays(GL_TRIANGLE_FAN, 0, 4)
glBindVertexArray(0)
glDepthMask(true)
if (!blendEnabled)
{
glDisable(GL_BLEND)
}
if (cullEnabled)
{
glEnable(GL_CULL_FACE)
}
flat.stopUsing()
}
private fun drawPrimeTowerPreview(viewMatrix:DoubleArray) {
if (isViewerEnabled || bed == null || !bed!!.isValid()) return
val cfg = SliceBeam.buildCurrentConfigObject()
if (!isPrimeTowerEnabled(cfg)) return
val x = towerX(cfg)
val y = towerY(cfg)
val w = towerWidth(cfg)
val d = towerDepth(cfg, w)
val h = primeTowerHeight
if (primeTowerModel == null)
{
primeTowerModel = GLModel()
}
if (!primeTowerModel!!.isInitialized() || Math.abs(primeTowerWidth - w) > 0.01 || Math.abs(primeTowerDepth - d) > 0.01)
{
primeTowerModel!!.reset()
primeTowerModel!!.initBox(w.toFloat(), d.toFloat(), h.toFloat())
primeTowerWidth = w
primeTowerDepth = d
}
var shader = shadersManager!!.get(GLShadersManager.SHADER_GOURAUD_LIGHT)
shader.startUsing()
shader.setUniform("emission_factor", 0.45f)
shader.setUniformMatrix4fv("projection_matrix", projectionMatrix)
DoubleMatrix.setIdentityM(modelMatrix, 0)
DoubleMatrix.translateM(modelMatrix, 0, x, y, (0).toDouble())
DoubleMatrix.multiplyMM(outModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
shader.setUniformMatrix4fv("view_model_matrix", outModelMatrix)
Slic3rUtils.calcViewNormalMatrix(viewMatrix, modelMatrix, normalMatrix)
shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix)
shader.setUniform("volume_mirrored", false)
val blend = glIsEnabled(GL_BLEND)
glEnable(GL_BLEND)
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
primeTowerModel!!.setColor(ColorUtils.setAlphaComponent(Color.YELLOW, if (java.lang.Double.isNaN(primeTowerPreviewX)) 150 else 225))
primeTowerModel!!.render()
if (!blend) glDisable(GL_BLEND)
shader.stopUsing()
}
private fun drawMeasurePoints() {
if (!this.isInMeasureMode) return
if (measurePointModel == null)
{
measurePointModel = GLModel()
measurePointModel!!.initBox(3.0f, 3.0f, 3.0f)
}
val viewMatrix = camera.getViewModelMatrix()
var shader = shadersManager!!.get(GLShadersManager.SHADER_GOURAUD_LIGHT)
shader.startUsing()
shader.setUniform("emission_factor", 0.6f)
shader.setUniformMatrix4fv("projection_matrix", projectionMatrix)
shader.setUniform("volume_mirrored", false)
val blend = glIsEnabled(GL_BLEND)
glEnable(GL_BLEND)
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
if (measurePointA != null)
{
DoubleMatrix.setIdentityM(modelMatrix, 0)
DoubleMatrix.translateM(modelMatrix, 0, measurePointA!!.x - 1.5, measurePointA!!.y - 1.5, measurePointA!!.z - 1.5)
DoubleMatrix.multiplyMM(outModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
shader.setUniformMatrix4fv("view_model_matrix", outModelMatrix)
Slic3rUtils.calcViewNormalMatrix(viewMatrix, modelMatrix, normalMatrix)
shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix)
measurePointModel!!.setColor(Color.RED)
measurePointModel!!.render()
}
if (measurePointB != null)
{
DoubleMatrix.setIdentityM(modelMatrix, 0)
DoubleMatrix.translateM(modelMatrix, 0, measurePointB!!.x - 1.5, measurePointB!!.y - 1.5, measurePointB!!.z - 1.5)
DoubleMatrix.multiplyMM(outModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
shader.setUniformMatrix4fv("view_model_matrix", outModelMatrix)
Slic3rUtils.calcViewNormalMatrix(viewMatrix, modelMatrix, normalMatrix)
shader.setUniformMatrix3fv("view_normal_matrix", normalMatrix)
measurePointModel!!.setColor(Color.GREEN)
measurePointModel!!.render()
}
if (!blend) glDisable(GL_BLEND)
shader.stopUsing()
if (measurePointA != null && measurePointB != null)
{
val lineVertices = floatArrayOf((measurePointA!!.x).toFloat(), (measurePointA!!.y).toFloat(), (measurePointA!!.z).toFloat(), (measurePointB!!.x).toFloat(), (measurePointB!!.y).toFloat(), (measurePointB!!.z).toFloat())
val buffer = java.nio.ByteBuffer.allocateDirect(lineVertices.size * 4)
.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
buffer.put(lineVertices).position(0)
val flat = shadersManager!!.get(GLShadersManager.SHADER_FLAT)
flat.startUsing()
flat.setUniformMatrix4fv("view_model_matrix", camera.getViewModelMatrix())
flat.setUniformMatrix4fv("projection_matrix", projectionMatrix)
flat.setUniform4f("uniform_color", 0.0f, 0.8f, 1.0f, 1.0f)
glLineWidth((ViewUtils.dp(4.0f)).toFloat())
val posLoc = flat.getAttribLocation("position")
glEnableVertexAttribArray(posLoc)
glVertexAttribPointer(posLoc, 3, GL_FLOAT, false, 0, buffer)
glDrawArrays(GL_LINES, 0, 2)
glDisableVertexAttribArray(posLoc)
flat.stopUsing()
}
}
fun deleteObject(i:Int):Boolean {
if (this.model == null) return false
ru.ytkab0bp.slicebeam.utils.DebugUtils.assertTrue(i >= 0 && i < this.model!!.objectsCount)
this.model!!.deleteObject(i)
if (glModels.size > i)
{
glModels.removeAt(i).release()
}
// Keep the multi-selection consistent: drop the deleted index and shift higher ones down.
 val shifted = LinkedHashSet<Int>()
for (idx in selectedObjects)
{
if (idx == i) continue
shifted.add(if (idx > i) idx - 1 else idx)
}
selectedObjects.clear()
selectedObjects.addAll(shifted)
if (i == selectedObject)
{
selectedObject = -1
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
}
else if (selectedObject > i)
{
selectedObject--
}
if (this.model!!.objectsCount == 0)
{
this.model!!.release()
model = null
selectedObjects.clear()
}
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
return true
}
/**
 * Batch-fill the bed with copies of the selected object.
 * Uses bounding box math to estimate capacity, adds all copies at once,
 * then arranges a single time â€” much faster than the old one-at-a-time approach.
 */
 fun fillBedWithSelectedObject():Int {
if (this.model == null || bed == null || !bed!!.isValid() || isViewerEnabled || selectedObject == -1) return 0
val maxAttempts = FillBedPlanner.copyAttemptsForLimit(this.model!!.objectsCount, MAX_FILL_BED_OBJECTS)
if (maxAttempts == 0) return 0
// Calculate how many copies can fit using bounding box math
 val estimatedCapacity = estimateBedCapacity(selectedObject)
var copiesToAdd = Math.min(Math.max(estimatedCapacity - this.model!!.objectsCount, 0), maxAttempts)
if (copiesToAdd <= 0) copiesToAdd = maxAttempts // Fallback if estimate fails
val sourceObject = selectedObject
val sourceModel = Model()
var added = 0
try
{
sourceModel.addObject(this.model!!, sourceObject)
// Batch-add all copies first
 for (i in 0 until copiesToAdd)
{
this.model!!.addObject(sourceModel, 0)
added++
}
this.model!!.resetBoundingBox()
// Single arrange pass for all copies
 if (!bed!!.arrange(this.model!!))
{
// Arrange failed â€” remove copies that couldn't be placed
 // by trimming from the end until arrange succeeds
 while (added > 0)
{
this.model!!.deleteObject(this.model!!.objectsCount - 1)
added--
this.model!!.resetBoundingBox()
if (bed!!.arrange(this.model!!)) break
}
}
}
finally
{
sourceModel.release()
}
if (added > 0)
{
this.model!!.resetBoundingBox()
resetGlModels()
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
}
return added
}
/**
 * Batch-fill version that replaces the old step-by-step approach.
 * Adds all copies at once then arranges once, rather than arrange per copy.
 */
 fun fillBedWithSelectedObjectStep():Int {
// Delegate to the batch method â€” no more step-by-step needed
 return fillBedWithSelectedObject()
}
/**
 * Estimate how many copies of an object can fit on the bed
 * based on bounding box dimensions.
 */
 private fun estimateBedCapacity(objectIndex:Int):Int {
val bedMin = bed!!.getVolumeMin()
val bedMax = bed!!.getVolumeMax()
val bedWidth = bedMax!!.x - bedMin!!.x
val bedDepth = bedMax!!.y - bedMin!!.y
val objMin = Vec3d()
val objMax = Vec3d()
this.model!!.getBoundingBoxApprox(objectIndex, objMin, objMax)
val objWidth = objMax.x - objMin.x
val objDepth = objMax.y - objMin.y
if (objWidth <= 0 || objDepth <= 0) return MAX_FILL_BED_OBJECTS
// Add spacing between objects â€” must match the arrange min_obj_distance (6mm gap)
 val spacing = 6.0
val cols = Math.max(1, ((bedWidth + spacing) / (objWidth + spacing)).toInt())
val rows = Math.max(1, ((bedDepth + spacing) / (objDepth + spacing)).toInt())
return cols * rows
}
/**
 * Duplicate the currently selected object (single copy).
 */
 fun duplicateSelectedObject():Int {
if (this.model == null || bed == null || !bed!!.isValid() || isViewerEnabled || selectedObject == -1) return 0
val sourceModel = Model()
try
{
sourceModel.addObject(this.model!!, selectedObject)
this.model!!.addObject(sourceModel, 0)
this.model!!.resetBoundingBox()
bed!!.arrange(this.model!!)
this.model!!.resetBoundingBox()
resetGlModels()
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
return 1
}
finally
{
sourceModel.release()
}
}
@JvmOverloads fun beginPaint(objIdx:Int, mode:Int = PAINT_MODE_COLOR) {
if (this.model == null || objIdx < 0 || objIdx >= this.model!!.objectsCount) return
// A paint session copies facet state and overlays. Refuse the high-memory path even if
// another caller skipped the UI check; the user may explicitly simplify first.
if (LargeModelPick.needsPreciseSurfaceSimplification(this.model!!.getTriangleCount(objIdx))) return
endPaintInternal(false)
// Ensure the object's GLModel + raycast data exist (built lazily in onDrawFrame otherwise).
 while (glModels.size <= objIdx)
{
val gm = GLModel()
gm.initFrom(this.model!!, glModels.size)
glModels.add(gm)
}
paintModeKind = mode
paintSession = PaintSession(this.model!!, objIdx, mode)
if (!paintSession!!.isValid()) {
paintSession = null
return
}
paintObject = objIdx
isPaintMode = true
paintPalette = Prefs.getFilamentPalette()
rebuildPaintOverlays()
}
/** Overlay color for a painted state in the current paint mode. */
 private fun paintStateColor(f:Int):Int {
if (paintModeKind == PAINT_MODE_COLOR)
    return if ((f - 1) < paintPalette.size) paintPalette[f - 1] else cachedAccentColor
if (paintModeKind == PAINT_MODE_FUZZY) return -0xc07301 // fuzzy = blue
return if (f == 2) -0x18b3c4 else -0xd1338f // block = red, enforce = green
}
fun endPaint(commit:Boolean) {
endPaintInternal(commit)
}
private fun endPaintInternal(commit:Boolean) {
if (paintSession != null)
{
if (commit) paintSession!!.commit()
paintSession!!.end()
paintSession = null
}
for (o in paintOverlays) o.model.release()
paintOverlays.clear()
if (paintObject != -1) invalidateCommittedOverlays(paintObject) // rebuild persisted view from committed data
paintObject = -1
isPaintMode = false
}
/** Set object i's base filament (1-based) and refresh its persisted colors. */
 fun setObjectBaseFilament(i:Int, filament:Int) {
if (this.model == null) return
this.model!!.setExtruder(i, filament)
invalidateCommittedOverlays(i)
}
fun setObjectUniformFilament(i: Int, filament: Int) {
if (this.model == null) return
this.model!!.setUniformFilament(i, filament)
invalidateCommittedOverlays(i)
}
fun paintAt(x:Float, y:Float) {
if (paintSession == null || paintObject < 0 || paintObject >= glModels.size) return
val gl = glModels.get(paintObject)
gl.raycaster.raycast(this, raycastHits, x, y)
val hit = preciseSurfaceHit()?.position ?: return
val h = doubleArrayOf(hit.x, hit.y, hit.z)
if (paintTool == PAINT_TOOL_BUCKET)
{
paintSession!!.bucket(h, -1, paintFilament, true, bucketAngleThreshold)
}
else if (paintTool == PAINT_TOOL_HEIGHT)
{
val halfW = heightBandWidth / 2.0
paintSession!!.height(hit.z - halfW, hit.z + halfW, paintFilament)
}
else
{
paintSession!!.brush(h, -1, paintBrushRadius, paintFilament, camera.position.asDoubleArray(), isBrushSphere)
}
// Don't commit on every drag event (expensive) â€” the live session overlays show the paint,
 // and endPaint() commits to the model. This keeps fast drags from painting sparsely.
 rebuildPaintOverlays()
}
/** A bounding-box hit can select an object, but must never place paint or a surface tool. */
private fun preciseSurfaceHit(): GLModel.HitResult? {
val hit = raycastHits.firstOrNull() ?: return null
if (!hit.approximate) return hit
val now = android.os.SystemClock.elapsedRealtime()
if (lastApproximateSurfaceWarningAt == 0L || now - lastApproximateSurfaceWarningAt >= 5_000L) {
lastApproximateSurfaceWarningAt = now
ViewUtils.postOnMainThread {
android.widget.Toast.makeText(SliceBeam.INSTANCE, R.string.LargeModelPreciseSurfaceUnavailable,
    android.widget.Toast.LENGTH_LONG).show()
}
}
return null
}
fun heightPaint(zMin:Double, zMax:Double) {
if (paintSession == null) return
paintSession!!.height(zMin, zMax, paintFilament)
paintSession!!.commit()
rebuildPaintOverlays()
}
fun clearPaint() {
if (paintSession == null) return
paintSession!!.clear()
paintSession!!.commit()
rebuildPaintOverlays()
}
private fun rebuildPaintOverlays() {
for (o in paintOverlays) o.model.release()
paintOverlays.clear()
if (paintSession == null) return
// Color mode covers the full filament palette; enforce/block modes only use states 1 and 2.
 val n = if (paintModeKind == PAINT_MODE_COLOR) Math.max(paintPalette.size, 1) else 2
for (f in 1..n)
{
val gl = GLModel()
val tris = paintSession!!.buildOverlay(gl, f)
if (tris > 0)
{
val o = PaintOverlay()
o.model = gl
o.color = paintStateColor(f)
paintOverlays.add(o)
}
else
{
gl.release()
}
}
}
private fun getCommittedOverlays(i:Int):List<PaintOverlay> {
var list = committedOverlays.get(i)
if (list == null)
{
list = ArrayList()
if (this.model != null && (this.model!!.hasPaint(i) || this.model!!.getUsedFilaments(i).size > 1))
{
val pal = paintPalette
// A model painted on desktop can use more filaments than the local palette has
 // slots for; cover every painted filament so nothing renders as unpainted.
 val maxPaint = this.model!!.getUsedFilaments(i).maxOrNull() ?: 1
val n = Math.min(Math.max(Math.max(pal.size, maxPaint), 1), Prefs.MAX_FILAMENT_COLORS)
for (f in 1..n)
{
val gl = GLModel()
val tris = this.model!!.buildPaintOverlay(i, gl, f)
if (tris > 0)
{
val o = PaintOverlay()
o.model = gl
o.color = if ((f - 1) < pal.size) pal[f - 1] else fallbackFilamentColor(f - 1)
o.filamentIndex = f - 1
list.add(o)
}
else
{
gl.release()
}
}
}
committedOverlays.put(i, list)
}
return list
}
private fun invalidateCommittedOverlays(i:Int) {
val list = committedOverlays.remove(i)
if (list != null) for (o in list) o.model.release()
}
private fun clearAllCommittedOverlays() {
for (l in committedOverlays.values)
    for (o in l) o.model.release()
committedOverlays.clear()
}
fun raycastObjectIndex(x:Float, y:Float):Int {
if (this.model == null) return -1
var minDistance = Double.MAX_VALUE
var j = -1
var i = 0
val c = this.model!!.objectsCount
while (i < c)
{
if (i >= glModels.size) {
i++
continue
}
val glModel = glModels.get(i)
glModel.raycaster.raycast(this, raycastHits, x, y)
val hovered = !raycastHits.isEmpty()
if (hovered)
{
val distance = raycastHits.get(0).position.distance(camera.position)
if (distance < minDistance)
{
minDistance = distance
j = i
}
}
i++
}
return j
}
fun onClick(x:Float, y:Float):Boolean {
if (this.model == null || isViewerEnabled) return false
if (isInEmbossMode)
{
var targetObj = selectedObject
if (targetObj == -1)
{
targetObj = raycastObjectIndex(x, y)
}
if (targetObj != -1 && targetObj < glModels.size)
{
val glModel = glModels.get(targetObj)
glModel.raycaster.raycast(this, raycastHits, x, y)
val preciseHit = preciseSurfaceHit()
if (preciseHit != null)
{
val position = preciseHit.position
val normal = preciseHit.normal
SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.EmbossSurfaceClickedEvent(position, normal))
return true
}
}
return false
}
if (this.isInMeasureMode)
{
var targetObj = selectedObject
if (targetObj == -1)
{
targetObj = raycastObjectIndex(x, y)
}
if (targetObj != -1 && targetObj < glModels.size)
{
val glModel = glModels.get(targetObj)
glModel.raycaster.raycast(this, raycastHits, x, y)
val preciseHit = preciseSurfaceHit()
if (preciseHit != null)
{
val hitPoint = preciseHit.position
if (measurePointA == null)
{
measurePointA = hitPoint
}
else if (measurePointB == null)
{
measurePointB = hitPoint
}
else
{
measurePointA = hitPoint
measurePointB = null
}
SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.MeasurePointsChangedEvent(measurePointA, measurePointB))
return true
}
}
return false
}
if (tryAddCutConnector(x, y)) return true
val j = raycastObjectIndex(x, y)
if (isInFlattenMode && (j == selectedObject || j == -1))
{
var minPlane = -1
var minDistancePlane = Double.MAX_VALUE
var i = 0
val c = flattenPlanes.size
while (i < c)
{
val glModel = flattenPlanes.get(i)
glModel.raycaster.raycast(this, raycastHits, x, y)
var minDistanceRay = Double.MAX_VALUE
if (!raycastHits.isEmpty())
{
for (res in raycastHits)
{
val distance = res.position.distance(camera.position)
if (distance < minDistanceRay)
{
minDistanceRay = distance
}
}
}
if (minDistanceRay < minDistancePlane)
{
minDistancePlane = minDistanceRay
minPlane = i
}
i++
}
if (minPlane != -1)
{
val glModel = flattenPlanes.get(minPlane)
this.model!!.flattenRotate(selectedObject, glModel)
this.model!!.ensureOnBed(selectedObject)
invalidateGlModel(selectedObject)
var k = 0
val l = flattenPlanes.size
while (k < l)
{
flattenPlanes.get(k).release()
k++
}
flattenPlanes.clear()
selectedObject = -1
selectedObjects.clear()
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
return true
}
return false
}
val selection = ObjectTapSelectionPolicy.apply(selectedObject, selectedObjects, j)
val render = selection.changed
if (!render) return false
selectedObject = selection.primaryIndex
// A tap on another object replaces a multi-selection; empty space clears it.
 selectedObjects.clear()
selectedObjects.addAll(selection.selectedIndices)
if (render)
{
if (isInFlattenMode)
{
setInFlattenMode(false)
}
if (selectedObject == -1)
{
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
}
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
}
return render
}
fun hover(x:Float, y:Float):Boolean {
if (this.model == null || isViewerEnabled) return false
var render = false
var minDistance = Double.MAX_VALUE
var minModel:GLModel? = null
run({ var i = 0
val c = this.model!!.objectsCount
while (i < c)
{
if (i >= glModels.size) {
i++
continue
}
val glModel = glModels.get(i)
glModel.raycaster.raycast(this, raycastHits, x, y)
val hovered = !raycastHits.isEmpty()
if (hovered)
{
val distance = raycastHits.get(0).position.distance(camera.position)
if (distance < minDistance)
{
minDistance = distance
minModel = glModel
}
}
i++
} })
run({ var i = 0
val c = this.model!!.objectsCount
while (i < c)
{
if (i >= glModels.size) {
i++
continue
}
val glModel = glModels.get(i)
val hovered = minModel === glModel
if (glModel.isHovering && !hovered)
{
glModel.isHovering = false
render = true
}
else if (!glModel.isHovering && hovered)
{
glModel!!.isHovering = true
render = true
}
i++
} })
if (isInFlattenMode)
{
var minPlane = -1
var minDistancePlane = Double.MAX_VALUE
run({ var i = 0
val c = flattenPlanes.size
while (i < c)
{
val glModel = flattenPlanes.get(i)
glModel.raycaster.raycast(this, raycastHits, x, y)
var minDistanceRay = Double.MAX_VALUE
if (!raycastHits.isEmpty())
{
for (res in raycastHits)
{
val distance = res.position.distance(camera.position)
if (distance < minDistanceRay)
{
minDistanceRay = distance
}
}
}
if (minDistanceRay < minDistancePlane)
{
minDistancePlane = minDistanceRay
minPlane = i
}
i++
} })
if (minPlane != -1)
{
for (i in 0 until flattenPlanes.size)
{
flattenPlanes.get(i).isHovering = i == minPlane
}
render = true
}
else
{
for (i in 0 until flattenPlanes.size)
{
if (flattenPlanes.get(i).isHovering)
{
flattenPlanes.get(i).isHovering = false
render = true
}
}
}
}
return render
}
fun stopHover():Boolean {
if (this.model == null) return false
var render = false
var i = 0
val c = this.model!!.objectsCount
while (i < c)
{
if (i >= glModels.size) {
i++
continue
}
val glModel = glModels.get(i)
if (glModel.isHovering)
{
glModel.isHovering = false
render = true
}
i++
}
return render
}
fun setSelectionRotation(x:Double, y:Double, z:Double) {
selRotX = x
selRotY = y
selRotZ = z
}
fun setSelectionScale(x:Double, y:Double, z:Double) {
selScaleX = x
selScaleY = y
selScaleZ = z
}
fun setSelectionTranslation(x:Double, y:Double, z:Double) {
selX = x
selY = y
selZ = z
}
/** Commit a translation to every selected object (used for group drag-move). */
 fun translateSelectedObjects(dx:Double, dy:Double, dz:Double) {
if (this.model == null) return
for (idx in selectedObjects)
{
if (idx >= 0 && idx < this.model!!.objectsCount)
{
this.model!!.translate(idx, dx, dy, dz)
invalidateGlModel(idx)
}
}
selZ = 0.0
selY = selZ
selX = selY
}
/** Move the current selection so its combined footprint is centered on the bed. */
 fun centerSelectionOnBed() {
if (this.model == null || bed == null || !bed!!.isValid() || selectedObjects.isEmpty()) return
val bedMin = bed!!.getVolumeMin()
val bedMax = bed!!.getVolumeMax()
val bedCx = (bedMin!!.x + bedMax!!.x) / 2.0
val bedCy = (bedMin!!.y + bedMax!!.y) / 2.0
var minX = Double.POSITIVE_INFINITY
var minY = Double.POSITIVE_INFINITY
var maxX = Double.NEGATIVE_INFINITY
var maxY = Double.NEGATIVE_INFINITY
for (idx in selectedObjects)
{
if (idx < 0 || idx >= this.model!!.objectsCount) continue
this.model!!.getBoundingBoxExact(idx, bbMin, bbMax)
minX = Math.min(minX, bbMin.x)
minY = Math.min(minY, bbMin.y)
maxX = Math.max(maxX, bbMax.x)
maxY = Math.max(maxY, bbMax.y)
}
if (minX > maxX) return
val dx = bedCx - (minX + maxX) / 2.0
val dy = bedCy - (minY + maxY) / 2.0
for (idx in selectedObjects)
{
if (idx >= 0 && idx < this.model!!.objectsCount)
{
this.model!!.translate(idx, dx, dy, (0).toDouble())
invalidateGlModel(idx)
}
}
selZ = 0.0
selY = selZ
selX = selY
}
/** Delete every selected object. Returns how many were removed. */
 fun deleteSelectedObjects():Int {
if (this.model == null || selectedObjects.isEmpty()) return 0
val indices = ArrayList<Int>(selectedObjects)
Collections.sort(indices, Collections.reverseOrder())
var removed = 0
for (idx in indices)
{
if (this.model == null) break
if (idx >= 0 && idx < this.model!!.objectsCount)
{
this.model!!.deleteObject(idx)
if (glModels.size > idx) glModels.removeAt(idx).release()
removed++
}
}
selectedObjects.clear()
selectedObject = -1
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
if (this.model != null && this.model!!.objectsCount == 0)
{
this.model!!.release()
model = null
}
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
return removed
}
/** Remove every object from the bed. Returns how many were removed. */
 fun deleteAllObjects():Int {
if (this.model == null) return 0
val removed = this.model!!.objectsCount
for (i in 0 until glModels.size) glModels.get(i).release()
glModels.clear()
this.model!!.release()
model = null
selectedObjects.clear()
selectedObject = -1
selZ = 0.0
selY = selZ
selX = selY
selRotZ = 0.0
selRotY = selRotZ
selRotX = selRotY
selScaleZ = 1.0
selScaleY = selScaleZ
selScaleX = selScaleY
if (isInFlattenMode) setInFlattenMode(false)
SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
return removed
}
private fun configureBed() {
try
{
lastConfigUid = SliceBeam.CONFIG_UID
SliceBeam.genCurrentConfig()
bed!!.configure(SliceBeam.getCurrentConfigFile())
bedConfigurationFailed = !bed!!.isValid()
glView?.postInvalidate()
}
catch (e:Exception) {
Log.e("GLRenderer", "Failed to update config", e)
bedConfigurationFailed = true
glView?.postInvalidate()
}
}
fun refreshThemeColors() {
cachedAccentColor = ThemesRepo.getColor(android.R.attr.colorAccent)
cachedHoverColor = ThemesRepo.getColor(R.attr.modelHoverColor)
cachedBgTop = ThemesRepo.getColor(R.attr.backgroundColorTop)
cachedBgBottom = ThemesRepo.getColor(R.attr.backgroundColorBottom)
}
private fun onCreate() {
bedConfigurationFailed = false
bed = Bed3D()
configureBed()
refreshThemeColors()
backgroundModel = GLModel()
backgroundModel!!.initBackgroundTriangles()
shadersManager = GLShadersManager()
if (!bed!!.isValid()) return
if (cameraIsDirty)
{
val min = bed!!.getVolumeMin()
val max = bed!!.getVolumeMax()
val center = min!!.center(max!!)
camera.origin.set(center)
camera.origin.z = 0.0
camera.position.x = center.x - center.z * 2
camera.position.y = center.y - center.z * 2
camera.position.z = min!!.z + Math.sqrt(center.z * center.z * 8)
cameraIsDirty = false
}
if (isViewerEnabled)
{
initViewer()
}
}
fun onDestroy() {
endPaintInternal(false)
clearAllCommittedOverlays()
clearAllCutConnectorPreviews()
if (shadersManager != null)
{
shadersManager!!.clearShaders()
shadersManager = null
}
if (backgroundModel != null)
{
backgroundModel!!.release()
backgroundModel = null
}
if (selectionModel != null)
{
selectionModel!!.release()
selectionModel = null
}
if (primeTowerModel != null)
{
primeTowerModel!!.release()
primeTowerModel = null
}
if (bed != null)
{
bed!!.release()
bed = null
}
if (viewer != null)
{
viewer!!.release()
viewer = null
}
if (measurePointModel != null)
{
measurePointModel!!.release()
measurePointModel = null
}
for (i in 0 until glModels.size)
{
glModels.get(i).release()
}
glModels.clear()
isInFlattenMode = false
for (i in 0 until flattenPlanes.size)
{
flattenPlanes.get(i).release()
}
flattenPlanes.clear()
if (cutPlaneVBO != -1)
{
glDeleteBuffers(1, intArrayOf(cutPlaneVBO), 0)
cutPlaneVBO = -1
}
if (cutPlaneVAO != -1)
{
glDeleteVertexArrays(1, intArrayOf(cutPlaneVAO), 0)
cutPlaneVAO = -1
}
if (cutConnectorsVBO != -1)
{
glDeleteBuffers(1, intArrayOf(cutConnectorsVBO), 0)
cutConnectorsVBO = -1
}
if (cutConnectorsVAO != -1)
{
glDeleteVertexArrays(1, intArrayOf(cutConnectorsVAO), 0)
cutConnectorsVAO = -1
}
}
companion object {
private val FOV = 60f
private val NEAR_PLANE = 10f
private val FAR_PLANE = 1000f
private val MAX_FILL_BED_OBJECTS = 256
// --- Multi-color painting ---
 @JvmField
 val PAINT_TOOL_BRUSH = 0
@JvmField
val PAINT_TOOL_BUCKET = 1
@JvmField
val PAINT_TOOL_HEIGHT = 2
private fun parseDouble(value:String, fallback:Double):Double {
if (value == null) return fallback
try
{
return java.lang.Double.parseDouble(value.trim())
}
catch (e:Exception) {
return fallback
}
}
private fun formatDouble(value:Double):String {
if (Math.abs(value - Math.rint(value)) < 0.0001) return java.lang.String.valueOf((Math.rint(value)).toInt())
return Regex("\\.$").replace(Regex("0+$").replace(String.format(java.util.Locale.US, "%.3f", value), ""), "")
}
private fun vectorValue(value:String, index:Int, fallback:Double):String {
if (value == null || value.trim().isEmpty()) return formatDouble(fallback)
val parts = value.split("[,;]")
val i = Math.max(0, Math.min(index, parts.size - 1))
return parts[i].trim()
}
private fun vectorWithValue(value: String?, index: Int, replacement: Double): String {
    val parts = value?.takeIf { it.isNotBlank() }?.split(Regex("[,;]")) ?: emptyList()
    val count = maxOf(index + 1, parts.size, 1)
    val values = MutableList(count) { i ->
        parts.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            ?: parts.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "15"
    }
    values[index] = formatDouble(replacement)
    return values.joinToString(",")
}

@JvmField
val PAINT_MODE_COLOR = 0
@JvmField
val PAINT_MODE_SUPPORT = 1
@JvmField
val PAINT_MODE_SEAM = 2
@JvmField
val PAINT_MODE_FUZZY = 3
// Distinct color for a painted filament the local palette has no slot for. Spreads hues by
 // the golden angle so adjacent filament indices stay visually separable.
 private fun fallbackFilamentColor(index:Int):Int {
val hue = (index * 137.508f) % 360f
return Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.95f))
}
}
}
