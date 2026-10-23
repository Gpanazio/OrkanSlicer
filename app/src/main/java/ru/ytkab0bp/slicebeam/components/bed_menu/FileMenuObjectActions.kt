package ru.ytkab0bp.slicebeam.components.bed_menu

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.view.GLView
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import java.util.Locale

/** Kotlin-owned object selection, duplication, filament and plate actions for the live File menu. */
class FileMenuObjectActions(private val fragment: BedFragment) {
    /** All native reads happen on GL before immutable dialog values reach the main thread. */
    private fun withTarget(view: View, expected: ModelSelectionSnapshot?, allowEmpty: Boolean = false,
                           show: (GLView, ModelSelectionSnapshot, Array<CharSequence>, Int) -> Unit) {
        val viewport = fragment.glView ?: return
        viewport.queueEvent {
            val renderer = viewport.renderer
            if (expected != null && !renderer.isSelectionSnapshotCurrent(expected)) return@queueEvent
            val snapshot = expected ?: renderer.captureSelectionSnapshot(allowEmpty) ?: return@queueEvent
            val names = Array<CharSequence>(snapshot.objectCount) { index ->
                snapshot.model.getObjectName(index) ?: view.context.getString(R.string.MenuFileObjectNumber, index + 1)
            }
            val tool = if (snapshot.primaryObject >= 0) snapshot.model.getExtruder(snapshot.primaryObject) - 1 else -1
            ViewUtils.postOnMainThread {
                if (fragment.glView === viewport && fragment.getContext() != null) show(viewport, snapshot, names, tool)
            }
        }
    }

    private fun edit(viewport: GLView, snapshot: ModelSelectionSnapshot, record: Boolean = true,
                     action: () -> Unit, complete: () -> Unit = {}) {
        viewport.queueEvent {
            if (!viewport.renderer.isSelectionSnapshotCurrent(snapshot)) return@queueEvent
            action()
            viewport.requestRender()
            if (record) fragment.recordModelEditOnGl()
            ViewUtils.postOnMainThread {
                complete()
            }
        }
    }

    internal fun showCopyMoveDialog(view: View, expected: ModelSelectionSnapshot? = null) {
        withTarget(view, expected) { viewport, snapshot, objectNames, selectedTool ->
            val context = view.context
            BeamAlertDialogBuilder(context)
                .setTitle(R.string.MenuFileCopyMove)
                .setItems(
                    arrayOf<CharSequence>(
                        context.getString(R.string.MenuFileDuplicateCount),
                        context.getString(R.string.MenuFileMoveToPlate),
                    ),
                ) { _, which ->
                    if (which == 0) showDuplicateDialog(view, snapshot) else showMoveToPlateDialog(view, snapshot)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    internal fun showObjectsDialog(view: View, expected: ModelSelectionSnapshot? = null) {
        withTarget(view, expected, allowEmpty = true) { viewport, snapshot, objectNames, selectedTool ->
            val count = snapshot.objectCount
            if (count == 0) {
                Toast.makeText(view.context, R.string.MenuFileObjectsEmpty, Toast.LENGTH_SHORT).show()
                return@withTarget
            }
            val objects = objectNames
            val selected = snapshot.primaryObject
            BeamAlertDialogBuilder(view.context)
                .setTitle(R.string.MenuFileObjects)
                .setSingleChoiceItems(objects, selected) { dialog, which ->
                    dialog.dismiss()
                    edit(viewport, snapshot, record = false,
                        action = { viewport.renderer.setSelectedObject(which) },
                        complete = { SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent()) })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    internal fun showSelectedObjectActions(view: View, expected: ModelSelectionSnapshot? = null) {
        withTarget(view, expected) { viewport, snapshot, objectNames, selectedTool ->
            val context = view.context
            BeamAlertDialogBuilder(context)
                .setTitle(R.string.MenuFileObjectActions)
                .setItems(
                    arrayOf<CharSequence>(
                        context.getString(R.string.MenuFileDuplicateCount),
                        context.getString(R.string.MenuFileMoveToPlate),
                        context.getString(R.string.ModelContextSelectFilamentSlot),
                        context.getString(R.string.ModelContextCenterOnBed),
                        context.getString(R.string.MenuOrientationArrange),
                        context.getString(R.string.MenuToolbarSplit),
                        context.getString(R.string.MenuFileDelete),
                    ),
                ) { _, which ->
                    when (which) {
                        0 -> showDuplicateDialog(view, snapshot)
                        1 -> showMoveToPlateDialog(view, snapshot)
                        2 -> showObjectFilamentSlotDialog(view, snapshot)
                        3 -> edit(viewport, snapshot, action = { viewport.renderer.centerSelectionOnBed() })
                        4 -> edit(viewport, snapshot, action = {
                            viewport.renderer.bed?.arrange(snapshot.model)
                            viewport.renderer.resetGlModels()
                        })
                        5 -> fragment.splitSelectedObject(snapshot)
                        6 -> edit(viewport, snapshot, action = { viewport.renderer.deleteObject(snapshot.primaryObject) })
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /** Assigns a whole object to an ACE/slicer tool; fine-grained color painting stays separate. */
    internal fun showObjectFilamentSlotDialog(view: View, expected: ModelSelectionSnapshot? = null) {
        withTarget(view, expected) { viewport, snapshot, objectNames, selectedTool ->
            val slots: List<FilamentSlot> = Prefs.getFilamentSlots()
            if (slots.isEmpty()) return@withTarget

            val selected = snapshot.primaryObject
            val choices = Array<CharSequence>(minOf(4, slots.size)) { "" }
            val currentTool = selectedTool
            for (tool in choices.indices) {
                val slot = slots[tool]
                val label = view.context.getString(
                    R.string.ModelContextFilamentSlot,
                    tool,
                    slot.type,
                    String.format(Locale.ROOT, "#%06X", slot.color and 0xFFFFFF),
                )
                choices[tool] = if (tool == currentTool) "✓ $label" else label
            }

            BeamAlertDialogBuilder(view.context)
                .setTitle(R.string.ModelContextSelectFilamentSlot)
                .setItems(choices) { _, which ->
                val filament = which + 1 // libSlic3r object extruders are one-based.
                    edit(viewport, snapshot,
                        action = { viewport.renderer.setObjectUniformFilament(selected, filament) },
                        complete = {
                    Toast.makeText(view.context,
                                view.context.getString(R.string.ModelContextFilamentSlotApplied, which), Toast.LENGTH_SHORT).show()
                })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    internal fun showDuplicateDialog(view: View, expected: ModelSelectionSnapshot? = null) {
        withTarget(view, expected) { viewport, snapshot, objectNames, selectedTool ->
            val input = EditText(view.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText("1")
                setSelectAllOnFocus(true)
            }
            val padding = ViewUtils.dp(24)
            val container = FrameLayout(view.context).apply {
                setPadding(padding, 0, padding, 0)
                addView(
                    input,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            }

            BeamAlertDialogBuilder(view.context)
                .setTitle(R.string.MenuFileDuplicateCount)
                .setMessage(R.string.MenuFileDuplicateCountHint)
                .setView(container)
                .setPositiveButton(R.string.ModelContextDuplicate) { _, _ ->
                val copies = input.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: 1
                    edit(viewport, snapshot,
                        action = { repeat(copies) { viewport.renderer.duplicateSelectedObject() } },
                        complete = {
                    Toast.makeText(view.context,
                                view.context.resources.getQuantityString(R.plurals.MenuFileDuplicatedCount, copies, copies),
                                Toast.LENGTH_SHORT).show()
                })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    internal fun showMoveToPlateDialog(view: View, expected: ModelSelectionSnapshot? = null) {
        withTarget(view, expected) { viewport, snapshot, objectNames, selectedTool ->
            val plateCount = fragment.plateCount
            if (plateCount < 2) {
                Toast.makeText(view.context, R.string.MenuFileMoveNoOtherPlate, Toast.LENGTH_SHORT).show()
                return@withTarget
            }

            val current = fragment.currentPlateIndex
            val targets = (0 until plateCount).filter { it != current }
            val labels = targets.map { view.context.getString(R.string.PlateNumber, (it + 1).toString()) }
            BeamAlertDialogBuilder(view.context)
                .setTitle(R.string.MenuFileMoveToPlate)
                .setItems(labels.toTypedArray()) { _, which ->
                val target = targets[which]
                    fragment.moveSelectedObjectToPlate(target, Runnable {
                    Toast.makeText(
                            view.context,
                            view.context.getString(R.string.MenuFileMovedToPlate, (target + 1).toString()),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }, snapshot)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
