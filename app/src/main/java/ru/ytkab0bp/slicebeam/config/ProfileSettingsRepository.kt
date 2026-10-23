package ru.ytkab0bp.slicebeam.config

import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight, immutable state exposed to profile screens. */
data class ProfileSettingsUiState(
    val activeProfileType: Int = -1,
    val activeProfileTitle: String = "",
    val pendingCount: Int = 0,
    val revision: Long = 0,
)

/**
 * Owns staged edits shared by profile screens and the native slicing configuration.
 * Saved preset objects remain owned by Slic3rConfigWrapper; mutable drafts never leave this class.
 */
object ProfileSettingsRepository {
    private val printerDraft = ConfigObject()
    private val processDraft = ConfigObject()
    private val filamentDraft = ConfigObject()
    private val _uiState = MutableStateFlow(ProfileSettingsUiState())

    /** Read-only observable state; mutable ConfigObject instances are never exposed through UI state. */
    @JvmStatic
    val uiState: StateFlow<ProfileSettingsUiState> = _uiState.asStateFlow()

    /** Binds the currently visible profile to the shared status presented by profile screens. */
    @JvmStatic
    @Synchronized
    fun bindActiveProfile(profileType: Int, title: String?) {
        val normalizedTitle = title.orEmpty()
        val count = if (profileType == -1) 0 else pendingCountFor(profileType)
        val current = _uiState.value
        if (current.activeProfileType == profileType &&
            current.activeProfileTitle == normalizedTitle && current.pendingCount == count) return
        _uiState.value = ProfileSettingsUiState(profileType, normalizedTitle, count, current.revision + 1)
    }

    private fun publishDraftChange() {
        val current = _uiState.value
        _uiState.value = current.copy(
            pendingCount = if (current.activeProfileType == -1) 0 else pendingCountFor(current.activeProfileType),
            revision = current.revision + 1,
        )
    }

    @Synchronized fun notifyProjectChanged() = publishDraftChange()

    private fun draftFor(profileType: Int): ConfigObject = when (profileType) {
        ConfigObject.PROFILE_LIST_PRINTER -> printerDraft
        ConfigObject.PROFILE_LIST_PRINT -> processDraft
        ConfigObject.PROFILE_LIST_FILAMENT -> filamentDraft
        else -> throw IllegalArgumentException("Unsupported profile type: $profileType")
    }

    /** Returns a defensive, read-only copy for summaries and view rendering. */
    @JvmStatic
    @Synchronized
    fun pendingEntries(profileType: Int): Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(if (TemporaryProjectSettings.owns(profileType))
            TemporaryProjectSettings.edits(profileType) else draftFor(profileType).values))

    @JvmStatic
    @Synchronized
    fun pendingCountFor(profileType: Int): Int = if (TemporaryProjectSettings.owns(profileType))
        TemporaryProjectSettings.edits(profileType).size else draftFor(profileType).values.size

    @JvmStatic
    @Synchronized
    fun valueFor(
        profileType: Int,
        baseProfile: ConfigObject,
        key: String,
        includePending: Boolean,
    ): String? {
        val draft = draftFor(profileType)
        if (TemporaryProjectSettings.ownsKey(profileType, key)) {
            TemporaryProjectSettings.valueFor(profileType, key, includePending)?.let { return it }
            if (profileType == ConfigObject.PROFILE_LIST_FILAMENT) return TemporaryProjectSettings.scalarForUi(
                key, if (includePending && draft.has(key)) draft.get(key) else baseProfile.get(key),
            )
        }
        return if (includePending && draft.has(key)) draft.get(key) else baseProfile.get(key)
    }

    /** Stages an edited value or removes the staged override when it matches the base value. */
    @JvmStatic
    @Synchronized
    fun setPendingValue(
        profileType: Int,
        key: String,
        baseValue: String?,
        editedValue: String,
    ) {
        if (TemporaryProjectSettings.ownsKey(profileType, key)) {
            TemporaryProjectSettings.setValue(profileType, key, editedValue)
            publishDraftChange()
            return
        }
        val draft = draftFor(profileType)
        if (baseValue == editedValue) draft.remove(key) else draft.put(key, editedValue)
        if (profileType == ConfigObject.PROFILE_LIST_FILAMENT && key == "pressure_advance" &&
            editedValue.toDoubleOrNull()?.let { it > 0.0 } == true) {
            // A positive value in a disabled preset would otherwise be metadata only.
            draft.put("enable_pressure_advance", "1")
        }
        publishDraftChange()
    }

    /** Merges a batch of already-filtered edits, such as Smart Slice recommendations. */
    @JvmStatic
    @Synchronized
    fun setPendingValues(profileType: Int, values: Map<String, String>) {
        if (TemporaryProjectSettings.owns(profileType)) {
            values.forEach { (key, value) -> TemporaryProjectSettings.setValue(profileType, key, value) }
            publishDraftChange()
            return
        }
        draftFor(profileType).values.putAll(values)
        publishDraftChange()
    }

    @JvmStatic
    @Synchronized
    fun clearDraft(profileType: Int) {
        if (TemporaryProjectSettings.owns(profileType)) {
            TemporaryProjectSettings.resetEdits(profileType)
            publishDraftChange()
            return
        }
        draftFor(profileType).values.clear()
        publishDraftChange()
    }

    /** Commits staged values into the selected profile and clears that profile's pending edits. */
    @JvmStatic
    @Synchronized
    fun commitDraft(profileType: Int, baseProfile: ConfigObject) {
        if (TemporaryProjectSettings.owns(profileType)) return
        val draft = draftFor(profileType)
        baseProfile.values.putAll(draft.values)
        draft.values.clear()
        publishDraftChange()
    }

    /** Overlays one profile's staged edits onto a temporary configuration snapshot. */
    @JvmStatic
    @Synchronized
    fun applyPendingFor(profileType: Int, target: ConfigObject) {
        target.values.putAll(draftFor(profileType).values)
        if (TemporaryProjectSettings.owns(profileType)) TemporaryProjectSettings.applyProfileTo(profileType, target)
    }

    /** Adds all unsaved profile edits to a temporary config sent to the slicer. */
    @JvmStatic
    @Synchronized
    fun applyPendingTo(target: ConfigObject) {
        target.values.putAll(printerDraft.values)
        target.values.putAll(processDraft.values)
        target.values.putAll(filamentDraft.values)
    }

    @JvmStatic
    @Synchronized
    fun clearAllDrafts() {
        printerDraft.values.clear()
        processDraft.values.clear()
        filamentDraft.values.clear()
        val current = _uiState.value
        _uiState.value = current.copy(pendingCount = 0, revision = current.revision + 1)
    }
}
