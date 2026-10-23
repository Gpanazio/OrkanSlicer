package ru.ytkab0bp.slicebeam.components.bed_menu

import android.content.Context
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.UnfoldMenu
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.ThemeAwareComposeView
import ru.ytkab0bp.slicebeam.utils.DoubleMatrix
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class TransformScaleValues(val x: Double, val y: Double, val z: Double)

internal fun resolveTransformScaleValues(
    currentX: Double,
    currentY: Double,
    currentZ: Double,
    newValue: Double,
    editX: Boolean,
    editY: Boolean,
    editZ: Boolean,
    linked: Boolean,
): TransformScaleValues = TransformScaleValues(
    x = if (editX || linked) newValue else currentX,
    y = if (editY || linked) newValue else currentY,
    z = if (editZ || linked) newValue else currentZ,
)

private data class TransformScaleDimensions(val x: Double, val y: Double, val z: Double) {
    fun forAxis(axis: TransformScaleAxis): Double = when (axis) {
        TransformScaleAxis.X -> x
        TransformScaleAxis.Y -> y
        TransformScaleAxis.Z -> z
    }
}

/** Compose-based, responsive scale editor for the selected model. */
class TransformScaleMenu(private val bed: BedFragment) : UnfoldMenu() {
    private val rulerValues = mutableStateOf(TransformScaleRulerValues(100, 100, 100))
    private val actualScale = mutableStateOf(TransformScaleValues(1.0, 1.0, 1.0))
    private val dimensionsMm = mutableStateOf(TransformScaleDimensions(0.0, 0.0, 0.0))
    private val linkedState = mutableStateOf(Prefs.isScaleLinked())
    private val hasSelection = mutableStateOf(false)
    private val manualAxis = mutableStateOf<TransformScaleAxis?>(null)
    private val manualText = mutableStateOf("")
    private val manualInMillimeters = mutableStateOf(Prefs.isScaleInputInMM())
    private var linkOffsets = TransformScalePolicy.linkOffsets(rulerValues.value)
    private var selectedObjectForScale = -1
    private var startedScrollObject = -1
    private var eventBusRegistered = false
    private var selectionSnapshot: ModelSelectionSnapshot? = null
    private var selectionLoadGeneration = 0

    init {
        fragment = bed
    }

    private fun setLinked(linked: Boolean) {
        if (linkedState.value == linked) return
        linkedState.value = linked
        Prefs.setScaleLinked(linked)
        linkOffsets = TransformScalePolicy.linkOffsets(rulerValues.value)
    }

    private fun setRulerValue(axis: TransformScaleAxis, value: Int) {
        val next = TransformScalePolicy.moveRuler(
            current = rulerValues.value,
            axis = axis,
            newValue = value,
            linked = linkedState.value,
            offsets = linkOffsets,
        )
        rulerValues.value = next
        actualScale.value = TransformScaleValues(next.x / 100.0, next.y / 100.0, next.z / 100.0)
        scaleVisual(actualScale.value)
    }

    private fun scaleVisual(target: TransformScaleValues) {
        val glView = bed.glView ?: return
        val snapshot = selectionSnapshot ?: return
        val index = snapshot.primaryObject
        if (index < 0) return
        startedScrollObject = index
        glView.queueEvent {
            val renderer = glView.renderer
            if (!renderer.isSelectionSnapshotCurrent(snapshot)) return@queueEvent
            val model = snapshot.model

            runCatching {
                val current = Vec3d()
                val rotation = Vec3d()
                model.getScale(index, current)
                val scaleX = current.x
                val scaleY = current.y
                val scaleZ = current.z

                model.getRotation(index, rotation)
                val matrix = DoubleArray(16)
                DoubleMatrix.setIdentityM(matrix, 0)
                DoubleMatrix.rotateM(matrix, 0, Math.toDegrees(rotation.x), 1.0, 0.0, 0.0)
                DoubleMatrix.rotateM(matrix, 0, Math.toDegrees(rotation.y), 0.0, 1.0, 0.0)
                DoubleMatrix.rotateM(matrix, 0, Math.toDegrees(rotation.z), 0.0, 0.0, 1.0)

                val projectedScale = doubleArrayOf(scaleX, scaleY, scaleZ, 1.0)
                DoubleMatrix.multiplyMV(projectedScale, 0, matrix, 0, projectedScale, 0)
                val sx = abs(projectedScale[0] / projectedScale[3])
                val sy = abs(projectedScale[1] / projectedScale[3])
                val sz = abs(projectedScale[2] / projectedScale[3])
                val dx = 1.0 / sx * target.x
                val dy = 1.0 / sy * target.y
                val dz = 1.0 / sz * target.z
                if (listOf(dx, dy, dz).all { it.isFinite() && it > 0.0 }) {
                    renderer.setSelectionScale(dx, dy, dz)
                    glView.requestRender()
                }
            }
        }
    }

    private fun commitScale() {
        val glView = bed.glView ?: return
        val snapshot = selectionSnapshot ?: return
        val index = snapshot.primaryObject
        if (index < 0 || (selectedObjectForScale >= 0 && index != selectedObjectForScale)) return
        val target = actualScale.value
        if (listOf(target.x, target.y, target.z).any { !it.isFinite() || it <= 0.0 }) return
        startedScrollObject = -1

        glView.queueEvent {
            val activeRenderer = glView.renderer
            if (!activeRenderer.isSelectionSnapshotCurrent(snapshot)) return@queueEvent
            val model = snapshot.model
            runCatching {
                val rotation = Vec3d()
                model.getRotation(index, rotation)
                val matrix = DoubleArray(16)
                DoubleMatrix.setIdentityM(matrix, 0)
                DoubleMatrix.rotateM(matrix, 0, Math.toDegrees(rotation.x), 1.0, 0.0, 0.0)
                DoubleMatrix.rotateM(matrix, 0, Math.toDegrees(rotation.y), 0.0, 1.0, 0.0)
                DoubleMatrix.rotateM(matrix, 0, Math.toDegrees(rotation.z), 0.0, 0.0, 1.0)
                val values = doubleArrayOf(target.x, target.y, target.z, 1.0)
                DoubleMatrix.multiplyMV(values, 0, matrix, 0, values, 0)
                val dx = abs(values[0] / values[3])
                val dy = abs(values[1] / values[3])
                val dz = abs(values[2] / values[3])

                if (listOf(dx, dy, dz).all { it.isFinite() && it > 0.0 }) {
                    model.scale(index, dx, dy, dz)
                    model.ensureOnBed(index)
                    activeRenderer.invalidateSelectionObject()
                    activeRenderer.setSelectionScale(1.0, 1.0, 1.0)
                    activeRenderer.invalidateGlModel(index)
                    glView.requestRender()
                    bed.recordModelEditOnGl()
                    ViewUtils.postOnMainThread { loadSelectionValues() }
                }
            }
        }
        glView.requestRender()
    }

    private fun openManualEditor(axis: TransformScaleAxis) {
        val inMillimeters = Prefs.isScaleInputInMM()
        val currentFactor = actualScale.value.forAxis(axis)
        val axisSize = dimensionsMm.value.forAxis(axis)
        val value = if (inMillimeters) currentFactor * axisSize else currentFactor * 100.0
        manualInMillimeters.value = inMillimeters
        manualText.value = String.format(Locale.ROOT, "%.2f", value)
        manualAxis.value = axis
    }

    private fun toggleManualUnits() {
        val axis = manualAxis.value ?: return
        val nextIsMm = !manualInMillimeters.value
        val oldValue = manualText.value.replace(',', '.').toDoubleOrNull()
        val axisSize = dimensionsMm.value.forAxis(axis)
        val converted = if (oldValue != null && axisSize.isFinite() && axisSize > 0.0) {
            if (nextIsMm) oldValue / 100.0 * axisSize else oldValue / axisSize * 100.0
        } else oldValue
        manualInMillimeters.value = nextIsMm
        Prefs.setScaleInputInMM(nextIsMm)
        if (converted != null && converted.isFinite()) manualText.value = String.format(Locale.ROOT, "%.2f", converted)
    }

    private fun manualScaleFactor(): Double? {
        val axis = manualAxis.value ?: return null
        return TransformScalePolicy.scaleFactor(
            manualText.value,
            manualInMillimeters.value,
            dimensionsMm.value.forAxis(axis),
        )
    }

    private fun confirmManualEditor() {
        val axis = manualAxis.value ?: return
        val factor = manualScaleFactor() ?: return
        val current = actualScale.value
        val next = resolveTransformScaleValues(
            currentX = current.x,
            currentY = current.y,
            currentZ = current.z,
            newValue = factor,
            editX = axis == TransformScaleAxis.X,
            editY = axis == TransformScaleAxis.Y,
            editZ = axis == TransformScaleAxis.Z,
            linked = linkedState.value,
        )
        actualScale.value = next
        rulerValues.value = TransformScaleRulerValues(
            TransformScalePolicy.roundedPercent(next.x),
            TransformScalePolicy.roundedPercent(next.y),
            TransformScalePolicy.roundedPercent(next.z),
        )
        if (linkedState.value) linkOffsets = TransformScalePolicy.linkOffsets(rulerValues.value)
        scaleVisual(next)
        commitScale()
        manualAxis.value = null
    }

    private fun loadSelectionValues() {
        val view = bed.glView ?: return
        val generation = ++selectionLoadGeneration
        view.queueEvent {
            val snapshot = view.renderer.captureSelectionSnapshot()
            val scale = Vec3d()
            val minimum = Vec3d()
            val maximum = Vec3d()
            if (snapshot != null) {
                view.renderer.setSelectionScale(1.0, 1.0, 1.0)
                snapshot.model.getScale(snapshot.primaryObject, scale)
                snapshot.model.getBoundingBoxExact(snapshot.primaryObject, minimum, maximum)
            }
            ViewUtils.postOnMainThread {
                if (generation != selectionLoadGeneration || bed.glView !== view || !eventBusRegistered) return@postOnMainThread
                selectionSnapshot = snapshot
                hasSelection.value = snapshot != null
                selectedObjectForScale = snapshot?.primaryObject ?: -1
                startedScrollObject = -1
                if (snapshot != null) {
                    fun originalSize(low: Double, high: Double, factor: Double) = abs(high - low) / abs(factor).coerceAtLeast(1e-12)
                    actualScale.value = TransformScaleValues(scale.x, scale.y, scale.z)
                    val next = TransformScaleRulerValues(TransformScalePolicy.roundedPercent(scale.x),
                        TransformScalePolicy.roundedPercent(scale.y), TransformScalePolicy.roundedPercent(scale.z))
                    rulerValues.value = next
                    linkOffsets = TransformScalePolicy.linkOffsets(next)
                    dimensionsMm.value = TransformScaleDimensions(originalSize(minimum.x, maximum.x, scale.x),
                        originalSize(minimum.y, maximum.y, scale.y), originalSize(minimum.z, maximum.z, scale.z))
                }
            }
        }
    }

    override fun onCreateView(ctx: Context, portrait: Boolean): View {
        val themedView = ThemeAwareComposeView(ctx)
        themedView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        themedView.setContent {
            BeamComposeTheme(themeRevision = themedView.themeRevision) {
                val values = rulerValues.value
                val actual = actualScale.value
                val sizes = dimensionsMm.value
                val linked = linkedState.value
                val selected = hasSelection.value
                TransformScaleEditorContent(
                    values = values,
                    scale = actual,
                    dimensionsMm = sizes,
                    enabled = selected,
                    linked = linked,
                    portrait = portrait,
                    xColor = Color(ThemesRepo.getColor(R.attr.xTrackColor)),
                    yColor = Color(ThemesRepo.getColor(R.attr.yTrackColor)),
                    zColor = Color(ThemesRepo.getColor(R.attr.zTrackColor)),
                    manualAxis = manualAxis.value,
                    manualValue = manualText.value,
                    manualInMillimeters = manualInMillimeters.value,
                    onManualAxisChanged = { manualAxis.value = it },
                    onManualValueChanged = { manualText.value = it },
                    onToggleManualUnits = ::toggleManualUnits,
                    onConfirmManual = ::confirmManualEditor,
                    onRulerChanged = ::setRulerValue,
                    onCommitScale = ::commitScale,
                    onOpenManual = ::openManualEditor,
                    onLinkedChanged = ::setLinked,
                    onBack = ::dismiss,
                )
            }
        }
        return themedView
    }

    override fun getRequestedSize(into: FrameLayout, portrait: Boolean): Int =
        if (portrait) ViewUtils.dp(52) + ViewUtils.dp(64) + ViewUtils.dp(80 + 24) * 3 + ViewUtils.dp(12)
        else (into.width * 0.5f).toInt()

    override fun onCreate() {
        super.onCreate()
        SliceBeam.EVENT_BUS.registerListener(this)
        eventBusRegistered = true
        loadSelectionValues()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (eventBusRegistered) SliceBeam.EVENT_BUS.unregisterListener(this)
        eventBusRegistered = false
        selectionLoadGeneration++
        selectionSnapshot = null
        stopScroll()
    }

    private fun stopScroll() {
        val view = bed.glView ?: return
        view.queueEvent { view.renderer.setSelectionScale(1.0, 1.0, 1.0); view.requestRender() }
        startedScrollObject = -1
    }

    @EventHandler(runOnMainThread = true)
    fun onObjectsListChanged(event: ObjectsListChangedEvent) { stopScroll(); loadSelectionValues() }

    @EventHandler(runOnMainThread = true)
    fun onSelectedObjectChanged(event: SelectedObjectChangedEvent) {
        stopScroll()
        loadSelectionValues()
    }

    private fun TransformScaleValues.forAxis(axis: TransformScaleAxis): Double = when (axis) {
        TransformScaleAxis.X -> x
        TransformScaleAxis.Y -> y
        TransformScaleAxis.Z -> z
    }
}

@Composable
private fun TransformScaleEditorContent(
    values: TransformScaleRulerValues,
    scale: TransformScaleValues,
    dimensionsMm: TransformScaleDimensions,
    enabled: Boolean,
    linked: Boolean,
    portrait: Boolean,
    xColor: Color,
    yColor: Color,
    zColor: Color,
    manualAxis: TransformScaleAxis?,
    manualValue: String,
    manualInMillimeters: Boolean,
    onManualAxisChanged: (TransformScaleAxis?) -> Unit,
    onManualValueChanged: (String) -> Unit,
    onToggleManualUnits: () -> Unit,
    onConfirmManual: () -> Unit,
    onRulerChanged: (TransformScaleAxis, Int) -> Unit,
    onCommitScale: () -> Unit,
    onOpenManual: (TransformScaleAxis) -> Unit,
    onLinkedChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ScaleAxisEditor(
                axis = TransformScaleAxis.X,
                title = stringResource(R.string.MenuOrientationPositionX),
                valueText = stringResource(R.string.MenuTransformScaleXValue, scale.x * 100.0, dimensionsMm.x * scale.x),
                value = values.x,
                color = xColor,
                enabled = enabled,
                onValueChanged = { onRulerChanged(TransformScaleAxis.X, it) },
                onCommit = onCommitScale,
                onEdit = { onOpenManual(TransformScaleAxis.X) },
            )
            ScaleAxisEditor(
                axis = TransformScaleAxis.Y,
                title = stringResource(R.string.MenuOrientationPositionY),
                valueText = stringResource(R.string.MenuTransformScaleYValue, scale.y * 100.0, dimensionsMm.y * scale.y),
                value = values.y,
                color = yColor,
                enabled = enabled,
                onValueChanged = { onRulerChanged(TransformScaleAxis.Y, it) },
                onCommit = onCommitScale,
                onEdit = { onOpenManual(TransformScaleAxis.Y) },
            )
            ScaleAxisEditor(
                axis = TransformScaleAxis.Z,
                title = stringResource(R.string.MenuOrientationPositionZ),
                valueText = stringResource(R.string.MenuTransformScaleZValue, scale.z * 100.0, dimensionsMm.z * scale.z),
                value = values.z,
                color = zColor,
                enabled = enabled,
                onValueChanged = { onRulerChanged(TransformScaleAxis.Z, it) },
                onCommit = onCommitScale,
                onEdit = { onOpenManual(TransformScaleAxis.Z) },
            )
            if (portrait) {
                ScaleLinkedControl(linked, enabled, onLinkedChanged)
            }
        }

        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (portrait) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 12.dp, vertical = 5.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.MenuOrientationPositionBack), fontSize = 15.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(0.8f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.MenuOrientationPositionBack), maxLines = 1, fontSize = 14.sp)
                }
                ScaleLinkedControl(linked, enabled, onLinkedChanged, Modifier.weight(1.2f))
            }
        }
    }

    if (manualAxis != null) {
        val axisSize = dimensionsMm.forAxis(manualAxis)
        val dialogTitle = stringResource(R.string.ScaleManualDialogTitle)
        val fieldLabel = stringResource(if (manualInMillimeters) R.string.ScaleManualMillimeters else R.string.ScaleManualPercent)
        val toggleLabel = stringResource(R.string.MenuTransformScaleMM)
        val canConfirm = TransformScalePolicy.scaleFactor(manualValue, manualInMillimeters, axisSize) != null
        TransformScaleManualDialog(
            title = dialogTitle,
            value = manualValue,
            fieldLabel = fieldLabel,
            unitToggleLabel = toggleLabel,
            inMillimeters = manualInMillimeters,
            canConfirm = canConfirm,
            onValueChanged = onManualValueChanged,
            onToggleUnits = onToggleManualUnits,
            onConfirm = onConfirmManual,
            onDismiss = { onManualAxisChanged(null) },
        )
    }
}

@Composable
private fun ScaleAxisEditor(
    axis: TransformScaleAxis,
    title: String,
    valueText: String,
    value: Int,
    color: Color,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    onCommit: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.72f else 0.35f))
                .clickable(enabled = enabled, onClick = onEdit)
                .semantics { contentDescription = "$title, $valueText" }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                valueText,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("✎", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
        }
        SteppedRuler(
            key = "scale-${axis.name}",
            title = title,
            stateDescription = valueText,
            value = value,
            color = color,
            enabled = enabled,
            min = TransformScalePolicy.MIN_PERCENT,
            max = Int.MAX_VALUE,
            onValueChanged = onValueChanged,
            onCommit = onCommit,
        )
    }
}

@Composable
private fun ScaleLinkedControl(
    linked: Boolean,
    enabled: Boolean,
    onLinkedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onLinkedChanged(!linked) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.MenuTransformScaleLink),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
        )
        Switch(checked = linked, enabled = enabled, onCheckedChange = onLinkedChanged)
    }
}

@Composable
private fun TransformScaleManualDialog(
    title: String,
    value: String,
    fieldLabel: String,
    unitToggleLabel: String,
    inMillimeters: Boolean,
    canConfirm: Boolean,
    onValueChanged: (String) -> Unit,
    onToggleUnits: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(fieldLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleUnits).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(unitToggleLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = inMillimeters, onCheckedChange = { onToggleUnits() })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
