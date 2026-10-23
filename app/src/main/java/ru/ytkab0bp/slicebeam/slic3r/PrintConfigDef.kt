package ru.ytkab0bp.slicebeam.slic3r

import androidx.annotation.Keep
import androidx.core.util.Pair
import java.lang.reflect.Field

/** Holds the native option catalog and the app's small Orca-specific presentation overrides. */
@Keep
class PrintConfigDef @Keep constructor() {
    @JvmField
    var options: MutableMap<String, ConfigOptionDef> = HashMap()

    @Keep
    fun addOption(key: String, def: ConfigOptionDef) {
        options[key] = def
    }

    companion object {
        @JvmField
        var SKIP_DEFAULT_OPTIONS: List<String> = listOf(
            "tilt_up_initial_speed",
            "tilt_up_finish_speed",
            "tilt_down_initial_speed",
            "tilt_down_finish_speed",
            "tower_speed",
            // If absent, the native bridge selects Orkan's established PEI/high-temp default.
            "curr_bed_type",
        ) + listOf("cool_plate", "eng_plate", "textured_plate", "textured_cool_plate", "supertack_plate")
            .flatMap { listOf("${it}_temp", "${it}_temp_initial_layer") }

        private var instance: PrintConfigDef? = null
        private val clzMap = HashMap<String, Class<*>>()
        private val fieldMap = HashMap<Pair<Class<*>, String>, Field>()
        private val valueMap = HashMap<Pair<Class<*>, String>, Any>()

        private val orcaLabels = linkedMapOf(
            // Wall / perimeter renames
            "perimeters" to "Wall loops",
            "extra_perimeters" to "Extra walls",
            "perimeter_generator" to "Wall generator",
            "avoid_crossing_perimeters" to "Avoid crossing walls",
            "overhangs" to "Detect overhang walls",
            "thin_walls" to "Detect thin walls",
            // Layer height
            "first_layer_height" to "Initial layer height",
            // Top/bottom shells
            "top_solid_layers" to "Top shell layers",
            "bottom_solid_layers" to "Bottom shell layers",
            "top_solid_min_thickness" to "Top shell thickness",
            "bottom_solid_min_thickness" to "Bottom shell thickness",
            "top_fill_pattern" to "Top surface pattern",
            "bottom_fill_pattern" to "Bottom surface pattern",
            // Infill
            "fill_density" to "Sparse infill density",
            "fill_pattern" to "Sparse infill pattern",
            "fill_angle" to "Sparse infill direction",
            "infill_overlap" to "Infill/wall overlap",
            "bridge_flow_ratio" to "Bridge flow",
            // Line widths
            "extrusion_width" to "Line width",
            "first_layer_extrusion_width" to "Initial layer line width",
            "perimeter_extrusion_width" to "Inner wall line width",
            "external_perimeter_extrusion_width" to "Outer wall line width",
            "infill_extrusion_width" to "Sparse infill line width",
            "solid_infill_extrusion_width" to "Inner solid infill line width",
            "top_infill_extrusion_width" to "Top surface line width",
            "support_material_extrusion_width" to "Support line width",
            "bridge_line_width" to "Bridge line width",
            // Speed
            "perimeter_speed" to "Inner wall speed",
            "small_perimeter_speed" to "Small perimeter speed",
            "external_perimeter_speed" to "Outer wall speed",
            "infill_speed" to "Sparse infill speed",
            "solid_infill_speed" to "Internal solid infill speed",
            "top_solid_infill_speed" to "Top surface speed",
            "gap_fill_speed" to "Gap infill speed",
            "first_layer_speed" to "Initial layer speed",
            "first_layer_speed_over_raft" to "Initial layer speed over raft",
            "ironing_speed" to "Ironing speed",
            // Ironing
            "ironing_flowrate" to "Ironing flow",
            // Support
            "support_material" to "Enable support",
            "support_material_threshold" to "Threshold angle",
            "support_material_buildplate_only" to "On build plate only",
            "support_material_contact_distance" to "Top Z distance",
            "support_material_bottom_contact_distance" to "Bottom Z distance",
            "support_material_xy_spacing" to "XY separation between object and support",
            "support_material_pattern" to "Support base pattern",
            "support_material_spacing" to "Support base pattern spacing",
            "support_material_interface_layers" to "Top interface layers",
            "support_material_bottom_interface_layers" to "Bottom interface layers",
            "support_material_interface_spacing" to "Interface pattern spacing",
            // Skirt / brim
            "skirts" to "Skirt loops",
            "brim_separation" to "Brim-object gap",
            // Special modes
            "spiral_vase" to "Spiral vase",
            "xy_size_compensation" to "XY size compensation",
            "elefant_foot_compensation" to "Elephant foot compensation",
            // Prime tower
            "enable_prime_tower" to "Enable prime tower",
            "wipe_tower" to "Enable prime tower",
            "wipe_tower_x" to "Prime tower X",
            "wipe_tower_y" to "Prime tower Y",
            "wipe_tower_width" to "Prime tower width",
            "prime_tower_width" to "Prime tower width",
            "prime_tower_brim_width" to "Prime tower brim width",
            "flush_volumes_matrix" to "Flush volumes",
            "flush_multiplier" to "Flush multiplier",
            "single_extruder_multi_material_priming" to "Prime all filaments",
        )

        @JvmStatic
        @Keep
        @Synchronized
        fun resolveEnum(className: String, value: String): Any? {
            val normalizedClassName = className.replace('/', '.')
            val enumClass = clzMap.getOrPut(normalizedClassName) {
                try {
                    Class.forName(normalizedClassName)
                } catch (error: ClassNotFoundException) {
                    throw RuntimeException(error)
                }
            }
            val key = Pair(enumClass, value)
            valueMap[key]?.let { return it }

            val field = fieldMap.getOrPut(key) {
                try {
                    enumClass.getDeclaredField(value).apply { isAccessible = true }
                } catch (error: NoSuchFieldException) {
                    throw RuntimeException(error)
                }
            }
            return try {
                field.get(null).also { resolved ->
                    if (resolved != null) valueMap[key] = resolved
                }
            } catch (error: IllegalAccessException) {
                throw RuntimeException(error)
            }
        }

        @JvmStatic
        @Synchronized
        fun getInstance(): PrintConfigDef {
            instance?.let { return it }

            val loadResult = OrcaConfigBridge.loadPrintConfigDefinition()
            val definition = loadResult.definition ?: throw IllegalStateException(
                loadResult.errorMessage,
                loadResult.cause,
            )
            applyOrcaLabels(definition)
            instance = definition
            return definition
        }

        private fun applyOrcaLabels(def: PrintConfigDef) {
            for ((key, label) in orcaLabels) {
                def.options[key]?.label = label
            }
            def.options["bridge_line_width"]?.tooltip =
                "Bridge line width in mm or % of the nozzle diameter. Maximum: nozzle diameter. 0 uses the internal solid infill width."

            val brimTypeOption = def.options["brim_type"] ?: return
            val originalValues = brimTypeOption.enumValues ?: return
            val originalLabels = brimTypeOption.enumLabels ?: return
            if (originalValues.any { it == "auto_brim" }) return

            brimTypeOption.enumValues = arrayOf("auto_brim", *originalValues)
            brimTypeOption.enumLabels = arrayOf("Auto", *originalLabels)
            brimTypeOption.defaultValue = "auto_brim"
        }
    }
}
