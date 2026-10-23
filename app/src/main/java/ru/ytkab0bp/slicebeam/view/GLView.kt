package ru.ytkab0bp.slicebeam.view
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Region
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLException
import android.opengl.GLSurfaceView
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import java.nio.IntBuffer
import java.util.ArrayList
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.LongClickTranslationEvent
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.theme.IThemeView
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.utils.ViewUtils
class GLView(context:Context):GLSurfaceView(context), IThemeView {
lateinit var renderer: GLRenderer
private var lastX:Float = 0.toFloat()
private var lastY:Float = 0.toFloat()
private var lastLength:Float = 0.toFloat()
private var touchSlop:Int = 0
private var fromTwoPointers:Boolean = false
private var onePointerGesture:Boolean = false
private var twoPointerGesture:Boolean = false
private var longClickGesture:Boolean = false
private var longClickMoved:Boolean = false
private var isScaling:Boolean = false
private var fillBedInProgress:Boolean = false
private var primeTowerGesture:Boolean = false
private var primeTowerMoved:Boolean = false
private var primeTowerDownX:Float = 0f
private var primeTowerDownY:Float = 0f
var onPrimeTowerTap:(()->Unit)? = null
private var longClickTargetObject = -1
private val tapGestureGate = TapGestureGate()
// When true and the renderer is in paint mode, a single finger paints instead of orbiting.
 var isPaintBrushActive:Boolean = false
private var tempVec = Vec3d()
private var longClickOffset = Vec3d()
private var longClickTranslation = Vec3d()
private var longClickHitResults = ArrayList<GLModel.HitResult>()
private var lastActionTime = System.currentTimeMillis()
// A few devices report a very short accessibility long-press threshold; keep ordinary presses from opening actions.
private val longPressTimeoutMs = maxOf(ViewConfiguration.getLongPressTimeout(), 500).toLong()
private val longClick = Runnable { if (longClickTargetObject == -1 || renderer.model == null || renderer.bed == null) return@Runnable
tapGestureGate.invalidate()
val renderScale = Prefs.getRenderScale()
renderer.bed!!.raycaster.raycast(renderer, longClickHitResults, lastX * renderScale, lastY * renderScale)
if (!longClickHitResults.isEmpty())
{
performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
longClickGesture = true
longClickMoved = false
longClickTranslation.set((0).toDouble(), (0).toDouble(), (0).toDouble())
if (renderer.focusForDrag(longClickTargetObject))
{
requestRender()
}
val result = longClickHitResults.get(0)
renderer.model!!.getTranslation(longClickTargetObject, tempVec)
longClickOffset.x = result.position.x - tempVec.x
longClickOffset.y = result.position.y - tempVec.y
} }
private var path = Path()
private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
private var xferPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private var invalidBedText = TextPaint(Paint.ANTI_ALIAS_FLAG)
private var invalidBedDescriptionLayout: StaticLayout? = null
private var lastScale:Float = 0.toFloat()
private fun paintAtScreen(x:Float, y:Float) {
val renderScale = Prefs.getRenderScale()
queueEvent({ renderer.paintAt(x * renderScale, y * renderScale)
requestRender() })
}
fun interface FillBedCallback {
fun onFillBedComplete(addedCopies:Int)
}
init{
xferPaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))
setEGLContextClientVersion(3)
// Keeping the GLES context avoids rebuilding shaders, textures and model
 // buffers whenever Android briefly pauses the activity (app switch,
 // permission dialog, screen off). This noticeably reduces return-time
 // stutter for larger plates without increasing per-frame work.
 setPreserveEGLContextOnPause(true)
renderer = GLRenderer(this)
setRenderer(renderer)
setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
touchSlop = ViewConfiguration.get(context).getScaledTouchSlop()
setWillNotDraw(false)
}
@JvmOverloads fun arrange(onComplete:Runnable? = null) {
if (renderer.model == null) return
queueEvent({ renderer.bed!!.arrange(renderer.model!!)
renderer.resetGlModels()
requestRender()
if (onComplete != null) ViewUtils.postOnMainThread(onComplete) })
}
fun fillBedWithSelectedModel(callback:FillBedCallback) {
if (renderer.model == null || fillBedInProgress) return
fillBedInProgress = true
queueEvent({ val addedCopies = renderer.fillBedWithSelectedObject()
requestRender()
ViewUtils.postOnMainThread({ fillBedInProgress = false
if (callback != null)
{
callback.onFillBedComplete(addedCopies)
} }) })
}
fun duplicateSelectedModel(onComplete:Runnable) {
duplicateSelectedModel(1, onComplete)
}
fun duplicateSelectedModel(copies:Int, onComplete:Runnable) {
queueEvent({ val count = Math.max(1, Math.min(copies, 50))
for (i in 0 until count)
{
renderer.duplicateSelectedObject()
}
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
fun selectModel(objectIndex:Int, onComplete:Runnable) {
queueEvent({ renderer.setSelectedObject(objectIndex)
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
fun deleteSelectedModel(onComplete:Runnable) {
queueEvent({ val sel = renderer.selectedObject
if (sel != -1)
{
renderer.deleteObject(sel)
}
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
fun selectAllModels(onComplete:Runnable) {
queueEvent({ renderer.selectAllObjects()
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
fun centerSelectedOnBed(onComplete:Runnable) {
queueEvent({ renderer.centerSelectionOnBed()
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
fun deleteAllModels(onComplete:Runnable) {
queueEvent({ renderer.deleteAllObjects()
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
private val previewBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
fun drawOverlay(canvas:Canvas, toBitmap:Boolean) {
val rad = ViewUtils.dp(16)
val offsetX = getTranslationX()
val offsetY = -getTranslationY()
if (toBitmap)
{
paint.setColor(ThemesRepo.getColor(android.R.attr.windowBackground))
canvas.drawRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, paint)
canvas.drawRoundRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, (rad).toFloat(), (rad).toFloat(), xferPaint)
}
else
{
path.rewind()
path.addRoundRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, (rad).toFloat(), (rad).toFloat(), Path.Direction.CW)
canvas.save()
canvas.clipPath(path, Region.Op.DIFFERENCE)
canvas.drawColor(ThemesRepo.getColor(android.R.attr.windowBackground))
canvas.restore()
val previewResult = renderer.gcodeResult
val preview = previewResult?.previewSelection
if (previewResult != null && preview != null && previewResult.previewLayerCount > 0) {
    val badge = context.getString(R.string.GCodePreviewBadge, preview.from, preview.to, previewResult.previewLayerCount)
    val margin = ViewUtils.dp(8).toFloat()
    val padding = ViewUtils.dp(6).toFloat()
    val available = (width - margin * 2 - padding * 2).coerceAtLeast(1f)
    previewBadgePaint.textSize = ViewUtils.dp(13).toFloat()
    val measured = previewBadgePaint.measureText(badge)
    if (measured > available) previewBadgePaint.textSize *= available / measured
    val metrics = previewBadgePaint.fontMetrics
    val badgeWidth = previewBadgePaint.measureText(badge) + padding * 2
    previewBadgePaint.color = ThemesRepo.getColor(android.R.attr.windowBackground)
    canvas.drawRoundRect(margin, margin, margin + badgeWidth,
        margin + metrics.descent - metrics.ascent + padding * 2, padding, padding, previewBadgePaint)
    previewBadgePaint.color = ThemesRepo.getColor(android.R.attr.textColorPrimary)
    canvas.drawText(badge, margin + padding, margin + padding - metrics.ascent, previewBadgePaint)
}
if (renderer.bedConfigurationFailed)
{
paint.setColor(ThemesRepo.getColor(android.R.attr.windowBackground))
val size = ViewUtils.dp(200)
canvas.drawRect((0).toFloat(), (getHeight() - size) / 2f, (getWidth()).toFloat(), (getHeight() + size) / 2f, paint)
val angle = Math.toRadians((60).toDouble())
val stableWidth = ViewUtils.dp(16)
val lineHeight = ViewUtils.dp(16)
val lineWidth = ViewUtils.dp(16 + ((32 * Math.sin(angle))).toFloat())
val linePath = Path()
linePath.moveTo((0).toFloat(), (0).toFloat())
linePath.lineTo((stableWidth).toFloat(), (0).toFloat())
linePath.lineTo((lineWidth).toFloat(), (lineHeight).toFloat())
linePath.lineTo((lineWidth - stableWidth).toFloat(), (lineHeight).toFloat())
linePath.lineTo((0).toFloat(), (0).toFloat())
linePath.close()
paint.setColor(ThemesRepo.getColor(android.R.attr.colorAccent))
// Keep the invalid-bed warning static. The old moving stripes called
 // invalidate() forever, defeating RENDERMODE_WHEN_DIRTY on idle devices.
 var x = -lineWidth
while (x < getWidth())
{
canvas.save()
canvas.translate((x).toFloat(), (getHeight() - size) / 2f)
canvas.drawPath(linePath, paint)
canvas.translate((stableWidth).toFloat(), (0).toFloat())
val alpha = paint.getAlpha()
paint.setAlpha((alpha * 0.5f).toInt())
canvas.drawPath(linePath, paint)
canvas.restore()
paint.setAlpha(alpha)
x += stableWidth * 2
}
x = getWidth() + lineWidth
while (x >= -lineWidth)
{
canvas.save()
canvas.translate((x).toFloat(), (getHeight() + size) / 2f - lineHeight)
canvas.drawPath(linePath, paint)
canvas.translate((-stableWidth).toFloat(), (0).toFloat())
val alpha = paint.getAlpha()
paint.setAlpha((alpha * 0.5f).toInt())
canvas.drawPath(linePath, paint)
canvas.restore()
paint.setAlpha(alpha)
x -= stableWidth * 2
}
if (invalidBedDescriptionLayout == null)
{
invalidBedText.setTextSize((ViewUtils.dp(16)).toFloat())
invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorSecondary))
invalidBedText.setTypeface(Typeface.DEFAULT)
invalidBedDescriptionLayout = StaticLayout(getContext().getString(R.string.BedConfigurationErrorDesc), invalidBedText, getWidth() - ViewUtils.dp(32), Layout.Alignment.ALIGN_CENTER, (1).toFloat(), (0).toFloat(), false)
}
val realTextSize = ViewUtils.dp(22)
val padding = ViewUtils.dp(12)
val totalHeight = realTextSize + invalidBedDescriptionLayout!!.getHeight()
invalidBedText.setTextSize((ViewUtils.dp(18)).toFloat())
invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorPrimary))
invalidBedText.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM))
val errString = getContext().getString(R.string.BedConfigurationError)
canvas.drawText(errString, 0, errString.length, (getWidth() - invalidBedText.measureText(errString)) / 2f, getHeight() / 2f - totalHeight / 2f + realTextSize - padding / 2f, invalidBedText)
invalidBedText.setTextSize((ViewUtils.dp(16)).toFloat())
invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorSecondary))
invalidBedText.setTypeface(Typeface.DEFAULT)
canvas.save()
canvas.translate((getWidth() - invalidBedDescriptionLayout!!.getWidth()) / 2f, getHeight() / 2f + totalHeight / 2f - invalidBedDescriptionLayout!!.getHeight() + padding / 2f)
invalidBedDescriptionLayout!!.draw(canvas)
canvas.restore()
}
}
}
override fun draw(canvas:Canvas) {
super.draw(canvas)
drawOverlay(canvas, false)
}
fun snapshotBitmap():Bitmap {
val w = getWidth()
val h = getHeight()
val bitmapBuffer = IntArray(w * h)
val bitmapSource = IntArray(w * h)
val intBuffer = IntBuffer.wrap(bitmapBuffer)
intBuffer.position(0)
try
{
GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, intBuffer)
var offset1:Int
var offset2:Int
for (i in 0 until h)
{
offset1 = i * w
offset2 = (h - i - 1) * w
for (j in 0 until w)
{
val texturePixel = bitmapBuffer[offset1 + j]
val blue = (texturePixel shr 16) and 0xff
val red = (texturePixel shl 16) and 0x00ff0000
val pixel = (texturePixel and -0xff0100) or red or blue
bitmapSource[offset2 + j] = pixel
}
}
}
catch (e:GLException) {
throw RuntimeException(e)
}
return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
}
protected override fun onConfigurationChanged(newConfig:Configuration) {
super.onConfigurationChanged(newConfig)
touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop()
}
private fun calcStartFocus(e:MotionEvent) {
lastX = (e.getX(0) + e.getX(1)) / 2f
lastY = (e.getY(0) + e.getY(1)) / 2f
val x = e.getX(0) - e.getX(1)
val y = e.getY(0) - e.getY(1)
lastLength = (Math.sqrt((x * x + y * y).toDouble())).toFloat()
}
override fun onHoverEvent(event:MotionEvent):Boolean {
if (if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) renderer.stopHover() else renderer.hover(event.getX() * Prefs.getRenderScale(), event.getY() * Prefs.getRenderScale()))
{
queueEvent(Runnable { requestRender() })
}
return super.onHoverEvent(event)
}
@SuppressLint("ClickableViewAccessibility")
override fun onTouchEvent(e:MotionEvent):Boolean {
val deltaMs = System.currentTimeMillis() - lastActionTime
lastActionTime = System.currentTimeMillis()
val action = e.getActionMasked()
if (action == MotionEvent.ACTION_CANCEL) {
tapGestureGate.invalidate()
onePointerGesture = false
}
if (e.getPointerCount() > 2)
{
tapGestureGate.invalidate()
removeCallbacks(longClick)
longClickGesture = false
longClickMoved = false
longClickTargetObject = -1
fromTwoPointers = false
isScaling = false
twoPointerGesture = false
onePointerGesture = false
return true
}
// Paint mode: a single finger paints; two fingers still orbit/zoom via the normal path.
 if ((renderer.isPaintMode && isPaintBrushActive && e.getPointerCount() == 1
&& (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)))
{
tapGestureGate.invalidate()
paintAtScreen(e.getX(), e.getY())
return true
}
if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
{
    tapGestureGate.begin(e.getPointerCount(), e.getX(0), e.getY(0), e.getDownTime())
if (action == MotionEvent.ACTION_DOWN) onePointerGesture = false
if (e.getPointerCount() == 2)
{
if (primeTowerGesture)
{
renderer.finishPrimeTowerDrag(false)
primeTowerGesture = false
primeTowerMoved = false
requestRender()
}
removeCallbacks(longClick)
longClickGesture = false
calcStartFocus(e)
fromTwoPointers = true
}
else
{
lastX = e.getX()
lastY = e.getY()
longClickTargetObject = -1
longClickMoved = false
val renderScale = Prefs.getRenderScale()
if (renderer.tryStartPrimeTowerDrag(lastX * renderScale, lastY * renderScale))
{
tapGestureGate.invalidate()
removeCallbacks(longClick)
primeTowerGesture = true
primeTowerMoved = false
primeTowerDownX = lastX
primeTowerDownY = lastY
requestRender()
return true
}
val j = renderer.raycastObjectIndex(lastX * renderScale, lastY * renderScale)
if (renderer.gcodeResult == null && j != -1)
{
longClickTargetObject = j
postDelayed(longClick, longPressTimeoutMs)
}
}
return true
}
if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL)
{
removeCallbacks(longClick)
if (primeTowerGesture)
{
tapGestureGate.invalidate()
val openExactPosition = action == MotionEvent.ACTION_UP && !primeTowerMoved
renderer.finishPrimeTowerDrag(action != MotionEvent.ACTION_CANCEL && primeTowerMoved)
primeTowerGesture = false
primeTowerMoved = false
onePointerGesture = false
requestRender()
if (openExactPosition) onPrimeTowerTap?.invoke()
return true
}
if (longClickGesture)
{
tapGestureGate.invalidate()
if (longClickMoved)
{
queueEvent({
// Commit the drag to the whole selection (group move when multiple are selected).
 renderer.translateSelectedObjects(longClickTranslation.x, longClickTranslation.y, (0).toDouble())
requestRender()
SliceBeam.EVENT_BUS.fireEvent(LongClickTranslationEvent(longClickTranslation.x, longClickTranslation.y, false)) })
}
// A long press is reserved for picking up and moving a model. Opening a dialog here
// makes ordinary touches on the build plate feel like repeated taps. Model actions
// remain available from the explicit, visible "Mehr" control.
longClickGesture = false
longClickMoved = false
longClickTargetObject = -1
onePointerGesture = false
return true
}
longClickGesture = false
if (fromTwoPointers)
{
tapGestureGate.invalidate()
if (e.getPointerCount() == 1 || action == MotionEvent.ACTION_CANCEL)
{
fromTwoPointers = false
isScaling = false
twoPointerGesture = false
lastActionTime = 0
}
return true
}
if (e.getPointerCount() == 1)
{
val dispatchTap = tapGestureGate.finish(
    pointerCount = e.getPointerCount(),
    moved = onePointerGesture || tapGestureGate.movedBeyondTouchSlop(
        e.getX(0), e.getY(0), touchSlop,
    ),
    cancelled = action == MotionEvent.ACTION_CANCEL,
    eventTimeMs = e.getEventTime(),
    x = e.getX(0),
    y = e.getY(0),
    duplicateWindowMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
    duplicateRadiusPx = touchSlop * 2f,
     maxTapDurationMs = longPressTimeoutMs,
)
if (dispatchTap)
{
ru.ytkab0bp.slicebeam.render.GlEditDispatch.click(
    { action -> queueEvent { action() } }, e.getX(), e.getY(), Prefs.getRenderScale(),
    renderer::onClick, ::requestRender,
)
}
lastX = e.getX(0)
lastY = e.getY(0)
onePointerGesture = false
}
// TODO: Rotate with inertia?
 return true
}
if (action == MotionEvent.ACTION_MOVE)
{
if (primeTowerGesture && e.getPointerCount() == 1)
{
if (!primeTowerMoved)
{
val deltaX = e.getX() - primeTowerDownX
val deltaY = e.getY() - primeTowerDownY
if (deltaX * deltaX + deltaY * deltaY <= touchSlop.toFloat() * touchSlop.toFloat())
{
lastX = e.getX()
lastY = e.getY()
return true
}
primeTowerMoved = true
performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}
val renderScale = Prefs.getRenderScale()
if (renderer.dragPrimeTower(e.getX() * renderScale, e.getY() * renderScale))
{
requestRender()
}
lastX = e.getX()
lastY = e.getY()
return true
}
if (e.getPointerCount() == 2)
{
val x = (e.getX(0) + e.getX(1)) / 2f
val y = (e.getY(0) + e.getY(1)) / 2f
val lenX = e.getX(0) - e.getX(1)
val lenY = e.getY(0) - e.getY(1)
val len = (Math.sqrt((lenX * lenX + lenY * lenY).toDouble())).toFloat()
val distanceX = lastX - x
val distanceY = lastY - y
if (deltaMs > 128)
{
isScaling = false
twoPointerGesture = false
}
var startingGesture = false
if (!isScaling && !twoPointerGesture)
{
if (Math.abs(distanceX) < touchSlop && Math.abs(distanceY) < touchSlop && Math.abs(len - lastLength) > touchSlop * 1.5f)
{
isScaling = true
startingGesture = true
}
else if (Math.sqrt((distanceX * distanceX + distanceY * distanceY).toDouble()) >= touchSlop)
{
twoPointerGesture = true
startingGesture = true
}
}
if (isScaling)
{
val delta = len - lastLength
lastLength = len
if (!startingGesture)
{
renderer.camera.zoom(delta / touchSlop * Prefs.getCameraSensitivity())
renderer.updateProjection()
requestRender()
}
lastX = x
lastY = y
}
else if (twoPointerGesture)
{
if (!startingGesture)
{
val mode = Prefs.getCameraControlMode()
if (mode == Prefs.CAMERA_CONTROL_MODE_ROTATE_MOVE || mode == Prefs.CAMERA_CONTROL_MODE_MOVE_ONLY)
{
renderer.camera.move(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity())
}
else
{
renderer.camera.rotateAround((distanceX / touchSlop * Prefs.getCameraSensitivity()).toDouble(), (distanceY / touchSlop * Prefs.getCameraSensitivity()).toDouble())
}
requestRender()
}
lastX = x
lastY = y
}
}
else if (!fromTwoPointers)
{
val distanceX = lastX - e.getX()
val distanceY = lastY - e.getY()
var startingGesture = false
if (!onePointerGesture)
{
if (tapGestureGate.movedBeyondTouchSlop(e.getX(), e.getY(), touchSlop))
{
onePointerGesture = true
tapGestureGate.invalidate()
startingGesture = true
removeCallbacks(longClick)
}
}
if (onePointerGesture)
{
if (!startingGesture)
{
if (longClickGesture)
{
longClickMoved = true
val renderScale = Prefs.getRenderScale()
renderer.model!!.getTranslation(renderer.selectedObject, tempVec)
renderer.bed!!.raycaster.raycast(renderer, longClickHitResults, e.getX() * renderScale, e.getY() * renderScale)
if (!longClickHitResults.isEmpty())
{
val result = longClickHitResults.get(0)
longClickTranslation.x = result.position.x - tempVec.x - longClickOffset.x
longClickTranslation.y = result.position.y - tempVec.y - longClickOffset.y
renderer.setSelectionTranslation(longClickTranslation.x, longClickTranslation.y, (0).toDouble())
SliceBeam.EVENT_BUS.fireEvent(LongClickTranslationEvent(longClickTranslation.x, longClickTranslation.y, true))
}
requestRender()
}
else
{
val mode = Prefs.getCameraControlMode()
if (mode == Prefs.CAMERA_CONTROL_MODE_ROTATE_MOVE)
{
renderer.camera.rotateAround((distanceX / touchSlop * Prefs.getCameraSensitivity()).toDouble(), (distanceY / touchSlop * Prefs.getCameraSensitivity()).toDouble())
}
else
{
renderer.camera.move(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity())
}
requestRender()
}
}
lastX = e.getX()
lastY = e.getY()
}
}
}
return true
}
private fun applyScale() {
val w = getWidth()
val h = getHeight()
lastScale = Prefs.getRenderScale()
val realScale = Math.round(w * lastScale) / w.toFloat()
getHolder().setFixedSize((w * realScale).toInt(), (h * realScale).toInt())
}
protected override fun onSizeChanged(w:Int, h:Int, oldw:Int, oldh:Int) {
super.onSizeChanged(w, h, oldw, oldh)
if (w != 0 && h != 0)
{
applyScale()
}
}
protected override fun onAttachedToWindow() {
super.onAttachedToWindow()
if (getWidth() > 0 && getHeight() > 0 && lastScale != Prefs.getRenderScale())
{
applyScale()
}
}
override fun surfaceCreated(holder:SurfaceHolder) {
super.surfaceCreated(holder)
requestRender()
}
override fun surfaceChanged(holder:SurfaceHolder, format:Int, w:Int, h:Int) {
super.surfaceChanged(holder, format, w, h)
requestRender()
}
override fun surfaceDestroyed(holder:SurfaceHolder) {
super.surfaceDestroyed(holder)
renderer.onDestroy()
}
override fun onApplyTheme() {
requestRender()
}
companion object {
private val FILL_BED_STEP_DELAY_MS = 16L
}
}
