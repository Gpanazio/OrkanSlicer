package ru.ytkab0bp.slicebeam.ui.settings

import ru.ytkab0bp.slicebeam.R

/** Presentation groups for process-profile settings. Option metadata still comes from Orca's native schema. */
data class OrcaSettingsSection(val title: String, val keys: List<String>)

data class OrcaSettingsCategory(
    val title: String,
    val iconResId: Int,
    val sections: List<OrcaSettingsSection>,
)

object OrcaProcessSettingsCatalog {
    @JvmStatic
    fun categories(): List<OrcaSettingsCategory> = listOf(
        OrcaSettingsCategory(
            "Layers and perimeters", R.drawable.print_layers_28, listOf(
                section("Layer height", "layer_height", "initial_layer_print_height"),
                section("Line width", "line_width", "initial_layer_line_width", "inner_wall_line_width", "outer_wall_line_width", "sparse_infill_line_width", "internal_solid_infill_line_width", "top_surface_line_width", "support_line_width"),
                section("Walls", "wall_loops", "alternate_extra_wall", "detect_thin_wall"),
                section("Seam", "seam_position", "staggered_inner_seams", "seam_gap", "wipe_speed", "role_based_wipe_speed"),
                section("Scarf joint seam", "seam_slope_type", "seam_slope_conditional", "scarf_angle_threshold", "scarf_overhang_threshold", "seam_slope_start_height", "seam_slope_entire_loop", "seam_slope_min_length", "seam_slope_steps", "seam_slope_inner_walls", "scarf_joint_speed", "scarf_joint_flow_ratio"),
                section("Precision", "slice_closing_radius", "resolution", "enable_arc_fitting", "elefant_foot_compensation", "elefant_foot_compensation_layers", "precise_z_height", "xy_hole_compensation", "xy_contour_compensation", "hole_to_polyhole", "hole_to_polyhole_threshold", "hole_to_polyhole_twisted"),
                section("Ironing", "ironing_type", "ironing_pattern", "ironing_flow", "ironing_spacing", "ironing_angle", "ironing_angle_fixed", "ironing_inset", "ironing_expansion"),
                section("Wall generator", "wall_generator", "wall_transition_angle", "wall_transition_filter_deviation", "wall_transition_length", "wall_distribution_count", "min_bead_width", "min_feature_size"),
                section("Walls and surfaces", "extra_perimeters_on_overhangs", "precise_outer_wall", "ensure_vertical_shell_thickness", "reduce_crossing_wall", "max_travel_detour_distance", "only_one_wall_top", "only_one_wall_first_layer", "wall_sequence", "wall_direction", "gap_fill_target", "reduce_infill_retraction", "is_infill_first"),
                section("Overhangs", "detect_overhang_wall", "overhang_reverse", "overhang_reverse_threshold", "overhang_reverse_internal_only", "overhang_flow_ratio"),
                section("Bridging", "bridge_line_width", "bridge_flow", "bridge_density", "thick_bridges", "bridge_angle", "internal_bridge_angle", "relative_bridge_angle", "dont_filter_internal_bridges", "thick_internal_bridges", "enable_extra_bridge_layer", "counterbore_hole_bridging"),
            ),
        ),
        OrcaSettingsCategory(
            "Top/bottom layers", R.drawable.print_layers_28, listOf(
                section("Top/bottom layers", "top_shell_layers", "top_shell_thickness", "bottom_shell_layers", "bottom_shell_thickness", "top_surface_pattern", "bottom_surface_pattern", "internal_solid_infill_pattern", "top_surface_density", "bottom_surface_density", "min_width_top_surface"),
            ),
        ),
        OrcaSettingsCategory(
            "Infill", R.drawable.print_infill_28, listOf(
                section("Infill", "sparse_infill_density", "sparse_infill_pattern", "fill_multiline", "infill_direction", "solid_infill_direction", "infill_anchor", "infill_anchor_max", "infill_combination", "infill_combination_max_layer_height", "minimum_sparse_infill_area", "infill_wall_overlap", "detect_narrow_internal_solid_infill", "gyroid_optimized"),
                section("Flush into infill", "flush_into_infill"),
                section("Lightning infill", "lightning_overhang_angle", "lightning_prune_angle", "lightning_straightening_angle"),
                section("Wall flow ratio", "outer_wall_flow_ratio", "inner_wall_flow_ratio", "gap_fill_flow_ratio", "first_layer_flow_ratio"),
                section("Solid and infill flow ratio", "top_solid_infill_flow_ratio", "bottom_solid_infill_flow_ratio", "internal_solid_infill_flow_ratio", "sparse_infill_flow_ratio"),
                section("Internal bridges", "internal_bridge_density", "internal_bridge_flow", "internal_bridge_speed", "internal_bridge_fan_speed"),
            ),
        ),
        OrcaSettingsCategory(
            "Skirt and brim", R.drawable.print_skirt_28, listOf(
                section("Skirt", "skirt_loops", "skirt_distance", "skirt_height", "skirt_type", "skirt_speed", "skirt_start_angle", "draft_shield", "single_loop_draft_shield", "min_skirt_length"),
                section("Brim", "brim_type", "brim_width", "brim_object_gap", "brim_ears", "brim_ears_max_angle", "brim_ears_detection_length", "combine_brims", "brim_flow_ratio"),
            ),
        ),
        OrcaSettingsCategory(
            "Support material", R.drawable.print_support_28, listOf(
                section("Support", "enable_support", "support_type", "support_threshold_angle", "support_threshold_overlap", "enforce_support_layers", "support_style", "support_top_z_distance", "support_bottom_z_distance", "support_base_pattern", "support_base_pattern_spacing", "support_angle", "support_on_build_plate_only", "support_object_xy_distance", "bridge_no_support", "support_critical_regions_only", "support_remove_small_overhang"),
                section("Raft", "raft_first_layer_density", "raft_first_layer_expansion", "raft_layers", "raft_contact_distance", "raft_expansion"),
                section("Support filament", "support_filament", "support_interface_filament"),
                section("Support flow", "support_flow_ratio", "support_interface_flow_ratio"),
                section("Support ironing", "support_ironing", "support_ironing_pattern", "support_ironing_flow", "support_ironing_spacing"),
                section("Advanced", "support_object_first_layer_gap", "max_bridge_length", "independent_support_layer_height", "support_interface_top_layers", "support_interface_bottom_layers", "support_interface_pattern", "support_interface_spacing", "support_bottom_interface_spacing", "support_interface_loop_pattern", "support_interface_not_for_body", "support_expansion"),
                section("Tree supports", "tree_support_branch_angle", "tree_support_angle_slow", "tree_support_branch_diameter", "tree_support_branch_diameter_angle", "tree_support_tip_diameter", "tree_support_branch_distance", "tree_support_top_rate", "tree_support_brim_width", "tree_support_wall_count", "tree_support_auto_brim", "tree_support_branch_angle_organic", "tree_support_branch_diameter_organic", "tree_support_branch_distance_organic"),
            ),
        ),
        OrcaSettingsCategory(
            "Speed", R.drawable.menu_orientation_rotation_28, listOf(
                section("Speed", "inner_wall_speed", "small_perimeter_speed", "outer_wall_speed", "sparse_infill_speed", "internal_solid_infill_speed", "top_surface_speed", "support_speed", "support_interface_speed", "bridge_speed", "gap_infill_speed", "ironing_speed", "overhang_1_4_speed", "overhang_2_4_speed", "overhang_3_4_speed", "overhang_4_4_speed", "travel_speed", "travel_speed_z", "initial_layer_speed", "initial_layer_infill_speed", "initial_layer_travel_speed", "max_volumetric_extrusion_rate_slope"),
                section("Overhang speed", "enable_overhang_speed", "slowdown_for_curled_perimeters"),
                section("Acceleration", "outer_wall_acceleration", "inner_wall_acceleration", "top_surface_acceleration", "internal_solid_infill_acceleration", "sparse_infill_acceleration", "bridge_acceleration", "initial_layer_acceleration", "travel_acceleration", "default_acceleration", "accel_to_decel_enable", "accel_to_decel_factor"),
                section("Jerk (XY)", "default_jerk", "outer_wall_jerk", "inner_wall_jerk", "infill_jerk", "top_surface_jerk", "initial_layer_jerk", "travel_jerk", "initial_layer_travel_jerk"),
                section("Junction deviation", "default_junction_deviation"),
                section("Resonance avoidance", "resonance_avoidance", "min_resonance_avoidance_speed", "max_resonance_avoidance_speed"),
            ),
        ),
        OrcaSettingsCategory(
            "Cooling", R.drawable.mode_fan_24, listOf(
                section("Cooling and fan", "slow_down_for_layer_cooling", "slow_down_layer_time", "slow_down_min_speed", "fan_cooling_layer_time"),
            ),
        ),
        OrcaSettingsCategory(
            "Multiple Extruders", R.drawable.slot_filament_28, listOf(
                section("Prime tower", "enable_prime_tower", "wipe_tower_x", "wipe_tower_y", "prime_tower_width", "wipe_tower_rotation_angle", "prime_tower_brim_width", "wipe_tower_bridging", "wipe_tower_cone_angle", "wipe_tower_extra_spacing", "wipe_tower_extra_flow", "wipe_tower_no_sparse_layers", "single_extruder_multi_material_priming", "prime_tower_infill_gap", "prime_tower_flat_ironing", "prime_tower_enable_framework", "prime_volume", "purge_in_prime_tower"),
                section("Filament for Features", "outer_wall_filament_id", "inner_wall_filament_id", "sparse_infill_filament_id", "internal_solid_filament_id", "top_surface_filament_id", "bottom_surface_filament_id", "wipe_tower_filament"),
                section("Flushing", "flush_into_objects", "flush_into_support", "flush_volumes_matrix", "flush_multiplier"),
                section("Ooze prevention", "ooze_prevention", "standby_temperature_delta"),
                section("Beam interlocking", "interlocking_beam", "interlocking_beam_width", "interlocking_orientation", "interlocking_beam_layer_count", "interlocking_depth", "interlocking_boundary_avoidance"),
                section("Advanced", "interface_shells", "mmu_segmented_region_max_width", "mmu_segmented_region_interlocking_depth"),
            ),
        ),
        OrcaSettingsCategory(
            "Advanced", R.drawable.settings_outline_28, listOf(
                section("Make overhangs printable", "make_overhang_printable", "make_overhang_printable_angle", "make_overhang_printable_hole_size"),
                section("Special mode", "slicing_mode", "spiral_mode", "print_sequence", "print_order", "spiral_mode_smooth", "spiral_mode_max_xy_smoothing", "spiral_starting_flow_ratio", "spiral_finishing_flow_ratio", "timelapse_type", "enable_timelapse", "enable_wrapping_detection", "extruder_clearance_radius", "extruder_clearance_height_to_rod"),
                section("Fuzzy Skin", "fuzzy_skin", "fuzzy_skin_thickness", "fuzzy_skin_point_distance", "fuzzy_skin_mode", "fuzzy_skin_first_layer", "fuzzy_skin_noise_type", "fuzzy_skin_scale", "fuzzy_skin_octaves", "fuzzy_skin_persistence"),
            ),
        ),
        OrcaSettingsCategory(
            "Output options", R.drawable.settings_outline_28, listOf(
                section("G-code output", "gcode_comments", "gcode_label_objects", "gcode_add_line_number", "exclude_object", "disable_m73", "scan_first_layer", "enable_power_loss_recovery", "filename_format"),
                section("Notes", "notes"),
                section("Profile dependencies", "compatible_printers", "compatible_printers_condition"),
            ),
        ),
    )

    @JvmStatic
    fun processKeys(): List<String> = categories().flatMap { category ->
        category.sections.flatMap { section -> section.keys }
    }

    @JvmStatic
    fun hasUniqueProcessKeys(): Boolean = processKeys().size == processKeys().distinct().size

    private fun section(title: String, vararg keys: String) = OrcaSettingsSection(title, keys.toList())
}
