package ru.ytkab0bp.slicebeam.ui.settings

import ru.ytkab0bp.slicebeam.R

/** Presentation groups for filament profiles; option definitions remain owned by Orca's schema. */
object OrcaFilamentSettingsCatalog {
    @JvmStatic
    fun categories(): List<OrcaSettingsCategory> = listOf(
        OrcaSettingsCategory(
            "Filament", R.drawable.slot_filament_28, listOf(
                section("Filament", "filament_colour", "filament_diameter", "filament_flow_ratio", "filament_density", "filament_cost"),
                section("Temperature", "idle_temperature"),
                section("Nozzle", "nozzle_temperature_initial_layer", "nozzle_temperature"),
                section("High temperature plate", "hot_plate_temp_initial_layer", "hot_plate_temp"),
                section("Cool plate", "cool_plate_temp_initial_layer", "cool_plate_temp"),
                section("Engineering plate", "eng_plate_temp_initial_layer", "eng_plate_temp"),
                section("Textured PEI plate", "textured_plate_temp_initial_layer", "textured_plate_temp"),
                section("Textured cool plate", "textured_cool_plate_temp_initial_layer", "textured_cool_plate_temp"),
                section("SuperTack plate", "supertack_plate_temp_initial_layer", "supertack_plate_temp"),
                section("Chamber", "chamber_temperature", "activate_chamber_temp_control"),
            ),
        ),
        OrcaSettingsCategory(
            "Cooling", R.drawable.mode_fan_24, listOf(
                section("Enable", "reduce_fan_stop_start_freq", "slow_down_for_layer_cooling", "dont_slow_down_outer_wall"),
                section("Fan speed", "fan_min_speed", "fan_max_speed", "enable_overhang_bridge_fan", "overhang_fan_speed", "overhang_fan_threshold", "close_fan_the_first_x_layers", "full_fan_speed_layer", "support_material_interface_fan_speed", "ironing_fan_speed"),
                section("Additional / auxiliary fan", "additional_cooling_fan_speed", "close_additional_fan_first_x_layers", "first_x_layer_fan_speed", "additional_fan_full_speed_layer", "internal_bridge_fan_speed"),
                section("Air filtration", "activate_air_filtration", "activate_air_filtration_during_print", "activate_air_filtration_on_completion", "during_print_exhaust_fan_speed", "complete_print_exhaust_fan_speed"),
                section("Cooling thresholds", "fan_cooling_layer_time", "slow_down_layer_time", "slow_down_min_speed"),
            ),
        ),
        OrcaSettingsCategory(
            "Advanced", R.drawable.settings_outline_28, listOf(
                section("Filament properties", "filament_type", "filament_soluble", "filament_is_support", "temperature_vitrification", "nozzle_temperature_range_low", "nozzle_temperature_range_high"),
                section("Pressure advance", "enable_pressure_advance", "pressure_advance", "adaptive_pressure_advance", "adaptive_pressure_advance_model", "adaptive_pressure_advance_overhangs", "adaptive_pressure_advance_bridges"),
                section("Filament change", "filament_change_length"),
                section("Print speed override", "filament_max_volumetric_speed"),
                section("Shrinkage compensation", "filament_shrink", "filament_shrinkage_compensation_z"),
                section("Wipe tower parameters", "filament_minimal_purge_on_wipe_tower"),
                section("Toolchange parameters with single extruder MM printers", "filament_loading_speed_start", "filament_loading_speed", "filament_unloading_speed_start", "filament_unloading_speed", "filament_toolchange_delay", "filament_cooling_moves", "filament_cooling_initial_speed", "filament_cooling_final_speed", "filament_stamping_loading_speed", "filament_stamping_distance", "filament_ramming_parameters"),
                section("Toolchange parameters with multi extruder MM printers", "filament_multitool_ramming", "filament_multitool_ramming_volume", "filament_multitool_ramming_flow"),
            ),
        ),
        OrcaSettingsCategory(
            "Filament Overrides", R.drawable.settings_outline_28, listOf(
                section("Ironing", "filament_ironing_flow", "filament_ironing_spacing", "filament_ironing_inset", "filament_ironing_speed"),
                section("Travel lift", "filament_z_hop", "filament_retract_lift_above", "filament_retract_lift_below"),
                section("Retraction", "filament_retraction_length", "filament_retraction_speed", "filament_deretraction_speed", "filament_retract_restart_extra", "filament_retraction_minimum_travel", "filament_retract_when_changing_layer", "filament_wipe", "filament_retract_before_wipe"),
            ),
        ),
        OrcaSettingsCategory(
            "Custom G-code", R.drawable.settings_outline_28, listOf(
                section("Start G-code", "filament_start_gcode"),
                section("End G-code", "filament_end_gcode"),
            ),
        ),
        OrcaSettingsCategory(
            "Notes", R.drawable.note_pen_outline_96, listOf(section("Notes", "filament_notes")),
        ),
        OrcaSettingsCategory(
            "Dependencies", R.drawable.wrench_outline_28, listOf(
                section("Profile dependencies", "compatible_printers", "compatible_printers_condition", "compatible_prints", "compatible_prints_condition"),
            ),
        ),
    )

    @JvmStatic
    fun filamentKeys(): List<String> = categories().flatMap { category ->
        category.sections.flatMap { section -> section.keys }
    }

    @JvmStatic
    fun hasUniqueFilamentKeys(): Boolean = filamentKeys().size == filamentKeys().distinct().size

    private fun section(title: String, vararg keys: String) = OrcaSettingsSection(title, keys.toList())
}
