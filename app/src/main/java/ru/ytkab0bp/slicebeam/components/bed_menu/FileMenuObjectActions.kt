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
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import java.util.Locale

/** Kotlin-owned object selection, duplication, filament and plate actions for the live File menu. */
class FileMenuObjectActions(private val fragment: BedFragment) {
    private fun hasSelection(): Boolean =
        fragment.glView!!.renderer.selectedObject != -1 && fragment.glView!!.renderer.model != null

    fun showCopyMoveDialog(view: View) {
        val context = view.context
        BeamAlertDialogBuilder(context)
            .setTitle(R.string.MenuFileCopyMove)
            .setItems(
                arrayOf<CharSequence>(
                    context.getString(R.string.MenuFileDuplicateCount),
                    context.getString(R.string.MenuFileMoveToPlate),
                ),
            ) { _, which ->
                if (which == 0) showDuplicateDialog(view) else showMoveToPlateDialog(view)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showObjectsDialog(view: View) {
        val model = fragment.currentModel
        val count = model?.objectsCount ?: 0
        if (count == 0) {
            Toast.makeText(view.context, R.string.MenuFileObjectsEmpty, Toast.LENGTH_SHORT).show()
            return
        }

        val objects = Array<CharSequence>(count) { index ->
            model?.getObjectName(index)
                ?: view.context.getString(R.string.MenuFileObjectNumber, index + 1)
        }
        val selected = fragment.glView!!.renderer.selectedObject
        BeamAlertDialogBuilder(view.context)
            .setTitle(R.string.MenuFileObjects)
            .setSingleChoiceItems(objects, selected) { dialog, which ->
                dialog.dismiss()
                fragment.glView!!.selectModel(which) {
                    SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showSelectedObjectActions(view: View) {
        if (!hasSelection()) return
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
                    0 -> showDuplicateDialog(view)
                    1 -> showMoveToPlateDialog(view)
                    2 -> showObjectFilamentSlotDialog(view)
                    3 -> fragment.glView!!.centerSelectedOnBed(fragment::updateModel)
                    4 -> {
                        fragment.glView!!.arrange()
                        fragment.updateModel()
                    }
                    5 -> fragment.splitSelectedObject()
                    6 -> fragment.glView!!.deleteSelectedModel(fragment::updateModel)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Assigns a whole object to an ACE/slicer tool; fine-grained color painting stays separate. */
    fun showObjectFilamentSlotDialog(view: View) {
        if (!hasSelection()) return
        val slots: List<FilamentSlot> = Prefs.getFilamentSlots()
        if (slots.isEmpty()) return

        val selected = fragment.glView!!.renderer.selectedObject
        val model = fragment.currentModel ?: return
        val choices = Array<CharSequence>(minOf(4, slots.size)) { "" }
        val currentTool = model.getExtruder(selected) - 1
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
                fragment.glView!!.queueEvent {
                    fragment.glView!!.renderer.setObjectUniformFilament(selected, filament)
                    fragment.glView!!.requestRender()
                    ViewUtils.postOnMainThread {
                        fragment.updateModel()
                        Toast.makeText(
                            view.context,
                            view.context.getString(R.string.ModelContextFilamentSlotApplied, which),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showDuplicateDialog(view: View) {
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
                fragment.glView!!.duplicateSelectedModel(copies) {
                    fragment.updateModel()
                    Toast.makeText(
                        view.context,
                        view.context.resources.getQuantityString(R.plurals.MenuFileDuplicatedCount, copies, copies),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showMoveToPlateDialog(view: View) {
        val plateCount = fragment.plateCount
        if (plateCount < 2) {
            Toast.makeText(view.context, R.string.MenuFileMoveNoOtherPlate, Toast.LENGTH_SHORT).show()
            return
        }

        val current = fragment.currentPlateIndex
        val targets = (0 until plateCount).filter { it != current }
        val labels = targets.map { view.context.getString(R.string.PlateNumber, (it + 1).toString()) }
        BeamAlertDialogBuilder(view.context)
            .setTitle(R.string.MenuFileMoveToPlate)
            .setItems(labels.toTypedArray()) { _, which ->
                val target = targets[which]
                fragment.moveSelectedObjectToPlate(target) {
                    Toast.makeText(
                        view.context,
                        view.context.getString(R.string.MenuFileMovedToPlate, (target + 1).toString()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
