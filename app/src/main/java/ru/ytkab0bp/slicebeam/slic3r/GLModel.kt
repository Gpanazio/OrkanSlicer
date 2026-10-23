package ru.ytkab0bp.slicebeam.slic3r

import android.graphics.Color
import ru.ytkab0bp.slicebeam.render.Camera
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.utils.DebugUtils
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import java.util.ArrayList

open class GLModel internal constructor(
    @JvmField var pointer: Long,
) {
    private var raycasterCache: MeshRaycaster? = null
    private var approximatePickBounds: Pair<Vec3d, Vec3d>? = null

    @JvmField var hoverProgress: Float = 0f
    @JvmField var isHovering: Boolean = false

    constructor() : this(Native.glmodel_create())

    constructor(model: Model) : this() {
        initFrom(model)
    }

    open fun initFrom(model: Model) {
        clearPicking()
        Native.glmodel_init_from_model(pointer, model.pointer)
    }

    open fun initFrom(model: Model, i: Int) {
        updatePickBounds(model, i)
        Native.glmodel_init_from_model_object(pointer, model.pointer, i)
    }

    private fun updatePickBounds(model: Model, i: Int) {
        clearPicking()
        approximatePickBounds = if (LargeModelPick.needsPreciseSurfaceSimplification(model.getTriangleCount(i))) {
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxApprox(i, min, max)
            min to max
        } else null
    }

    /** Build this model from facets painted with the given filament in a paint session (mesh-local coords). */
    open fun initFromPaint(paintSessionPtr: Long, filamentIdx: Int): Int {
        clearPicking()
        return Native.glmodel_init_from_paint(pointer, paintSessionPtr, filamentIdx)
    }

    open fun setColor(color: Int) {
        Native.glmodel_set_color(
            pointer,
            Color.red(color) / 0xFF.toFloat(),
            Color.green(color) / 0xFF.toFloat(),
            Color.blue(color) / 0xFF.toFloat(),
            Color.alpha(color) / 0xFF.toFloat(),
        )
    }

    open fun stilizedArrow(tipRadius: Float, tipLength: Float, stemRadius: Float, stemLength: Float) {
        clearPicking()
        Native.glmodel_stilized_arrow(pointer, tipRadius, tipLength, stemRadius, stemLength)
    }

    open fun initBackgroundTriangles() {
        clearPicking()
        Native.glmodel_init_background_triangles(pointer)
    }

    /** Build a simple rectangular prism with origin at the front-left-bottom corner. */
    open fun initBox(width: Float, depth: Float, height: Float) {
        clearPicking()
        Native.glmodel_init_box(pointer, width, depth, height)
    }

    open fun initBoundingBox(model: Model, i: Int) {
        clearPicking()
        Native.glmodel_init_bounding_box(pointer, model.pointer, i)
    }

    open fun isInitialized(): Boolean = Native.glmodel_is_initialized(pointer)

    open fun isEmpty(): Boolean = Native.glmodel_is_empty(pointer)

    open fun render() {
        Native.glmodel_render(pointer)
    }

    open fun reset() {
        Native.glmodel_reset(pointer)
        clearPicking()
    }

    open fun release() {
        if (pointer != 0L) {
            Native.glmodel_release(pointer)
            pointer = 0L
        }
        clearPicking()
    }

    private fun clearPicking() {
        raycasterCache = null
        approximatePickBounds = null
    }

    open val raycaster: MeshRaycaster
        get() {
        var current = raycasterCache
        if (current == null) {
            // AABBMesh builds face-neighbour data for every triangle. Dense meshes
            // can exceed a million facets and crash the process on this tap path.
            if (approximatePickBounds == null) Native.glmodel_init_raycast_data(pointer)
            current = MeshRaycaster()
            raycasterCache = current
        }
        return current
    }

    inner class MeshRaycaster {
        fun raycast(renderer: GLRenderer?, list: ArrayList<HitResult>?, x: Float, y: Float) {
            DebugUtils.assertTrue(renderer != null)
            DebugUtils.assertTrue(list != null)
            val safeRenderer = renderer!!
            val safeList = list!!
            safeList.clear()

            val camera: Camera = safeRenderer.camera
            val point = Slic3rUtils.unproject(
                camera.getViewModelMatrix(),
                safeRenderer.projectionMatrix,
                safeRenderer.viewportWidth,
                safeRenderer.viewportHeight,
                x.toDouble(),
                y.toDouble(),
            )
            var direction = camera.getDirForward().clone()
            if (!Prefs.isOrthoProjectionEnabled()) {
                direction = point.clone().add(camera.position.clone().negate()).normalize()
            }
            approximatePickBounds?.let { (min, max) ->
                LargeModelPick.hit(point, direction, min, max)?.let(safeList::add)
                return
            }
            val hits = Native.glmodel_raycast_closest_hit(pointer, point.asDoubleArray(), direction.asDoubleArray())
            safeList.ensureCapacity(hits.size / 6)
            var i = 0
            while (i < hits.size) {
                safeList.add(
                    HitResult(
                        Vec3d(hits[i], hits[i + 1], hits[i + 2]),
                        Vec3d(hits[i + 3], hits[i + 4], hits[i + 5]),
                    ),
                )
                i += 6
            }
        }
    }

    open class HitResult(
        @JvmField val position: Vec3d,
        @JvmField val normal: Vec3d,
        /** True for a selection-box hit; unsuitable for edits needing a real surface point. */
        @JvmField val approximate: Boolean = false,
    )
}
