package ru.ytkab0bp.slicebeam.config

import java.util.Locale

/** Orca stores line width as millimetres or as a percentage of nozzle diameter. */
object LineWidthConversion {
    val keys = setOf("line_width", "initial_layer_line_width", "inner_wall_line_width",
        "outer_wall_line_width", "sparse_infill_line_width", "internal_solid_infill_line_width",
        "top_surface_line_width", "support_line_width", "bridge_line_width")

    /** Orca rejects bridge widths above the nozzle diameter even if other lines may be wider. */
    fun maxPercent(key: String): Double = if (key == "bridge_line_width") 100.0 else 300.0

    fun nozzle(raw: String?): Double? = raw?.substringBefore(';')?.substringBefore(',')?.trim()
        ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    fun percent(raw: String, nozzle: Double): Double? {
        val value = raw.trim().removeSuffix("%").trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return if (raw.trim().endsWith('%')) value else value / nozzle * 100.0
    }

    fun millimetres(percent: Double, nozzle: Double): Double = percent * nozzle / 100.0

    fun percentFromMillimetres(mm: Double, nozzle: Double): Double = mm / nozzle * 100.0

    fun formatPercent(value: Double): String = format(value, 2)
    fun formatMillimetres(value: Double): String = format(value, 3)

    private fun format(value: Double, digits: Int): String =
        String.format(Locale.ROOT, "%.${digits}f", value).trimEnd('0').trimEnd('.')
}
