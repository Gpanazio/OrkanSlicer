package ru.ytkab0bp.slicebeam.ui.settings

import ru.ytkab0bp.slicebeam.R

/** Printer-profile groups; per-extruder keys are rendered once for each native extruder. */
object OrcaPrinterSettingsCatalog {
    @JvmStatic
    fun leadingCategories(): List<OrcaSettingsCategory> = listOf(
        OrcaSettingsCategory(
            "General", R.drawable.printer_outline_28, listOf(
                section("Size and coordinates", "printable_area", "printable_height", "z_offset", "curr_bed_type"),
                section("Capabilities", "single_extruder_multi_material", "high_current_on_filament_swap"),
                section("Firmware", "gcode_flavor", "silent_mode", "fan_kickstart", "fan_speedup_time", "fan_speedup_overhangs", "machine_load_filament_time", "machine_unload_filament_time"),
                section("Advanced", "use_relative_e_distances", "use_firmware_retraction"),
            ),
        ),
        OrcaSettingsCategory(
            "Custom G-code", R.drawable.settings_outline_28, listOf(
                section("Start G-code", "machine_start_gcode"),
                section("End G-code", "machine_end_gcode"),
                section("Before layer change G-code", "before_layer_change_gcode"),
                section("After layer change G-code", "layer_change_gcode"),
                section("Tool change G-code", "change_filament_gcode"),
                section("Printing by object G-code", "printing_by_object_gcode"),
                section("Color change G-code", "change_extrusion_role_gcode"),
                section("Pause Print G-code", "machine_pause_gcode"),
                section("Template Custom G-code", "template_custom_gcode"),
            ),
        ),
        OrcaSettingsCategory(
            "Machine limits", R.drawable.note_pen_outline_96, listOf(
                section("General", "emit_machine_limits_to_gcode"),
                section("Acceleration limits", "machine_max_acceleration_x", "machine_max_acceleration_y", "machine_max_acceleration_z", "machine_max_acceleration_e", "machine_max_acceleration_extruding", "machine_max_acceleration_retracting"),
                section("Maximum speeds", "machine_max_speed_x", "machine_max_speed_y", "machine_max_speed_z", "machine_max_speed_e"),
                section("Jerk limits", "machine_max_jerk_x", "machine_max_jerk_y", "machine_max_jerk_z", "machine_max_jerk_e", "machine_max_junction_deviation"),
                section("Minimum feedrates", "machine_min_extruding_rate", "machine_min_travel_rate"),
            ),
        ),
    )

    @JvmStatic
    fun extruderSections(): List<OrcaSettingsSection> = listOf(
        section("Size", "nozzle_diameter"),
        section("Preview", "extruder_colour"),
        section("Layer height limits", "min_layer_height", "max_layer_height"),
        section("Position (for multi-extruder printers)", "extruder_offset"),
        section("Travel lift", "z_hop", "z_hop_types", "retract_lift_enforce", "travel_slope"),
        section("Only lift", "retract_lift_above", "retract_lift_below"),
        section("Retraction", "retraction_length", "retraction_speed", "deretraction_speed", "retract_restart_extra", "retraction_minimum_travel", "retract_when_changing_layer", "wipe", "retract_before_wipe"),
        section("Retraction for cutting (long retraction when cut)", "long_retractions_when_cut", "retraction_distances_when_cut"),
        section("Retraction when tool is disabled (advanced settings for multi-extruder setups)", "retract_length_toolchange", "retract_restart_extra_toolchange"),
    )

    @JvmStatic
    fun trailingCategories(): List<OrcaSettingsCategory> = listOf(
        OrcaSettingsCategory("Notes", R.drawable.note_pen_outline_96, listOf(section("Notes", "printer_notes"))),
        OrcaSettingsCategory("Physical Printer", R.drawable.power_socket_outline_28, listOf(section("Print Host upload", "host_type", "print_host", "printhost_apikey"))),
    )

    @JvmStatic
    fun fixedKeys(): List<String> = (leadingCategories() + trailingCategories())
        .flatMap { category -> category.sections.flatMap { it.keys } }

    @JvmStatic
    fun hasUniqueFixedKeys(): Boolean = fixedKeys().size == fixedKeys().distinct().size

    @JvmStatic
    fun hasUniqueExtruderKeys(): Boolean = extruderSections().flatMap { it.keys }.let { it.size == it.distinct().size }

    private fun section(title: String, vararg keys: String) = OrcaSettingsSection(title, keys.toList())
}
