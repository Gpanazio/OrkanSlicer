package ru.ytkab0bp.slicebeam.config

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ProfileSettingsRepositoryTest {
    @Before
    fun setUp() {
        ProfileSettingsRepository.clearAllDrafts()
        ProfileSettingsRepository.bindActiveProfile(-1, null)
    }

    @After
    fun tearDown() = ProfileSettingsRepository.clearAllDrafts()

    @Test
    fun pendingValueOverridesReadWithoutChangingSavedProfileUntilCommit() {
        val processProfile = ConfigObject("process").apply { put("layer_height", "0.20") }
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_PRINT, "layer_height", "0.20", "0.24",
        )

        assertEquals("0.24", ProfileSettingsRepository.valueFor(
            ConfigObject.PROFILE_LIST_PRINT, processProfile, "layer_height", true,
        ))
        assertEquals("0.20", processProfile.get("layer_height"))

        ProfileSettingsRepository.commitDraft(ConfigObject.PROFILE_LIST_PRINT, processProfile)
        assertEquals("0.24", processProfile.get("layer_height"))
        processProfile.put("layer_height", "0.28")
        assertEquals("0.28", ProfileSettingsRepository.valueFor(
            ConfigObject.PROFILE_LIST_PRINT, processProfile, "layer_height", true,
        ))
    }

    @Test
    fun revertingToSavedValueRemovesThePendingOverride() {
        val filamentProfile = ConfigObject("filament").apply { put("pressure_advance", "0.035") }
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_FILAMENT, "pressure_advance", "0.035", "0.08",
        )
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_FILAMENT, "pressure_advance", "0.035", "0.035",
        )

        assertEquals("0.035", ProfileSettingsRepository.valueFor(
            ConfigObject.PROFILE_LIST_FILAMENT, filamentProfile, "pressure_advance", true,
        ))
        assertEquals("0.035", filamentProfile.get("pressure_advance"))
    }

    @Test
    fun pendingValuesStayInTheirOwningProfileAndOnlyOverlaySlicerConfigOnRequest() {
        val printerProfile = ConfigObject("printer").apply { put("z_offset", "0.12") }
        val filamentProfile = ConfigObject("filament").apply { put("pressure_advance", "0.035") }
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_PRINTER, "z_offset", "0.12", "0.18",
        )
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_FILAMENT, "pressure_advance", "0.035", "0.08",
        )

        assertNull(ProfileSettingsRepository.valueFor(
            ConfigObject.PROFILE_LIST_PRINT, ConfigObject(), "pressure_advance", true,
        ))
        val sliceConfig = ConfigObject()
        ProfileSettingsRepository.applyPendingTo(sliceConfig)
        assertEquals("0.18", sliceConfig.get("z_offset"))
        assertEquals("0.08", sliceConfig.get("pressure_advance"))
        assertEquals("0.12", printerProfile.get("z_offset"))
        assertEquals("0.035", filamentProfile.get("pressure_advance"))
    }

    @Test
    fun observableStateTracksActiveProfileAndUnsavedValueCount() {
        val profile = ConfigObject("Balanced")
        ProfileSettingsRepository.bindActiveProfile(ConfigObject.PROFILE_LIST_PRINT, profile.title)
        val before = ProfileSettingsRepository.uiState.value.revision

        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_PRINT, "layer_height", null, "0.20",
        )
        val edited = ProfileSettingsRepository.uiState.value
        assertEquals(ConfigObject.PROFILE_LIST_PRINT, edited.activeProfileType)
        assertEquals("Balanced", edited.activeProfileTitle)
        assertEquals(1, edited.pendingCount)
        assertTrue(edited.revision > before)

        ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)
        assertEquals(0, ProfileSettingsRepository.uiState.value.pendingCount)
    }

    @Test
    fun rebindingToAnotherProfileRefreshesItsOwnPendingCount() {
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_PRINTER, "z_offset", null, "0.10",
        )
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_FILAMENT, "pressure_advance", null, "0.04",
        )

        ProfileSettingsRepository.bindActiveProfile(ConfigObject.PROFILE_LIST_FILAMENT, "PLA")
        assertEquals("PLA", ProfileSettingsRepository.uiState.value.activeProfileTitle)
        assertEquals(2, ProfileSettingsRepository.uiState.value.pendingCount)
    }

    @Test fun editingPositiveFilamentPressureAdvanceEnablesItsGcodeCommand() {
        val profile = ConfigObject("PLA").apply {
            put("pressure_advance", "0.02")
            put("enable_pressure_advance", "0")
        }
        ProfileSettingsRepository.setPendingValue(ConfigObject.PROFILE_LIST_FILAMENT,
            "pressure_advance", "0.02", "0.051")
        val slice = ConfigObject(profile)
        ProfileSettingsRepository.applyPendingTo(slice)
        assertEquals("0.051", slice["pressure_advance"])
        assertEquals("1", slice["enable_pressure_advance"])

        ProfileSettingsRepository.setPendingValue(ConfigObject.PROFILE_LIST_FILAMENT,
            "enable_pressure_advance", "0", "0")
        val disabled = ConfigObject(profile)
        ProfileSettingsRepository.applyPendingTo(disabled)
        assertEquals("0", disabled["enable_pressure_advance"])
    }

    @Test
    fun pendingEntriesAreSnapshotsAndBatchUpdatesPublishState() {
        ProfileSettingsRepository.bindActiveProfile(ConfigObject.PROFILE_LIST_PRINT, "Balanced")
        val before = ProfileSettingsRepository.uiState.value.revision
        ProfileSettingsRepository.setPendingValues(
            ConfigObject.PROFILE_LIST_PRINT,
            mapOf("layer_height" to "0.20", "wall_loops" to "3"),
        )
        val snapshot = ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINT)

        assertEquals(2, snapshot.size)
        assertEquals(2, ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINT))
        assertEquals(2, ProfileSettingsRepository.uiState.value.pendingCount)
        assertTrue(ProfileSettingsRepository.uiState.value.revision > before)
        try {
            (snapshot as MutableMap<String, String>)["layer_height"] = "0.30"
        } catch (_: UnsupportedOperationException) {
            // The repository intentionally returns an immutable snapshot.
        }
        assertEquals("0.20", ProfileSettingsRepository.valueFor(
            ConfigObject.PROFILE_LIST_PRINT, ConfigObject(), "layer_height", true,
        ))
        assertFalse(ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINT).isEmpty())
    }

    @Test
    fun printerSnapshotOverlaysOnlyItsOwnPendingValues() {
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_PRINTER, "print_host", "old.local", "new.local",
        )
        ProfileSettingsRepository.setPendingValue(
            ConfigObject.PROFILE_LIST_PRINT, "layer_height", null, "0.20",
        )
        val printerSnapshot = ConfigObject("printer").apply { put("print_host", "old.local") }

        ProfileSettingsRepository.applyPendingFor(ConfigObject.PROFILE_LIST_PRINTER, printerSnapshot)

        assertEquals("new.local", printerSnapshot.get("print_host"))
        assertNull(printerSnapshot.get("layer_height"))
    }
}
