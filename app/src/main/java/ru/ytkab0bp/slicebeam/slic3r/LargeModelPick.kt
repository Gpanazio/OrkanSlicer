package ru.ytkab0bp.slicebeam.slic3r

import ru.ytkab0bp.slicebeam.utils.Vec3d
import kotlin.math.abs

/** Cheap selection for dense models; never changes the geometry used for slicing. */
internal object LargeModelPick {
    const val TRIANGLE_LIMIT = 20_000

    fun needsPreciseSurfaceSimplification(triangleCount: Int): Boolean = triangleCount > TRIANGLE_LIMIT

    /** Leave headroom because decimators may return slightly more facets than requested. */
    fun suggestedKeepRatio(triangleCount: Int): Float =
        if (needsPreciseSurfaceSimplification(triangleCount))
            (TRIANGLE_LIMIT * 0.8f / triangleCount).coerceIn(0.000001f, 0.99f)
        else 1f

    fun hit(origin: Vec3d, direction: Vec3d, min: Vec3d, max: Vec3d): GLModel.HitResult? {
        val starts = doubleArrayOf(origin.x, origin.y, origin.z)
        val rays = doubleArrayOf(direction.x, direction.y, direction.z)
        val lows = doubleArrayOf(min.x, min.y, min.z)
        val highs = doubleArrayOf(max.x, max.y, max.z)
        if (!starts.all(Double::isFinite) || !rays.all(Double::isFinite) ||
            !lows.all(Double::isFinite) || !highs.all(Double::isFinite)) return null

        var near = Double.NEGATIVE_INFINITY
        var far = Double.POSITIVE_INFINITY
        var nearAxis = -1
        var farAxis = -1
        var nearSign = 0.0
        var farSign = 0.0
        for (axis in 0..2) {
            // The approximate model bounds can be flat (for example, a lithophane).
            val low = lows[axis] - 0.5
            val high = highs[axis] + 0.5
            if (low > high) return null
            val ray = rays[axis]
            if (abs(ray) < 1e-12) {
                if (starts[axis] < low || starts[axis] > high) return null
                continue
            }
            val entry = (if (ray > 0) low else high) - starts[axis]
            val exit = (if (ray > 0) high else low) - starts[axis]
            val tNear = entry / ray
            val tFar = exit / ray
            if (tNear > near) {
                near = tNear
                nearAxis = axis
                nearSign = if (ray > 0) -1.0 else 1.0
            }
            if (tFar < far) {
                far = tFar
                farAxis = axis
                farSign = if (ray > 0) 1.0 else -1.0
            }
            if (near > far) return null
        }
        val distance = if (near >= 0) near else far
        val axis = if (near >= 0) nearAxis else farAxis
        val sign = if (near >= 0) nearSign else farSign
        if (!distance.isFinite() || distance < 0 || axis < 0) return null
        val normal = Vec3d()
        when (axis) {
            0 -> normal.x = sign
            1 -> normal.y = sign
            else -> normal.z = sign
        }
        return GLModel.HitResult(
            Vec3d(origin.x + direction.x * distance, origin.y + direction.y * distance, origin.z + direction.z * distance),
            normal,
            approximate = true,
        )
    }
}
