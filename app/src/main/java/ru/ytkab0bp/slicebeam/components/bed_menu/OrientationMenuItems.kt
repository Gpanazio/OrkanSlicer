package ru.ytkab0bp.slicebeam.components.bed_menu

import android.view.View
import com.google.android.material.snackbar.Snackbar
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import java.util.function.Consumer

/** Kotlin action controller for the live arrange/orient/flatten menu. */
internal class OrientationMenuItems(
    private val fragment: BedFragment,
    private val showPositionMenu: Consumer<View>,
    private val showRotationMenu: Consumer<View>,
    private val onItemsChanged: () -> Unit,
) {
    private var flattenEnabled = false

    fun createItems(): List<BedComposeMenuAction> {
        val renderer = fragment.glView!!.renderer
        val availability = modelSelectionAvailability(renderer.model != null, renderer.selectedObject)

        return listOf(
            BedComposeMenuAction(
                R.string.MenuOrientationArrange,
                R.drawable.grid_layout_outline_28,
                {
                    fragment.glView!!.arrange()
                    fragment.glView!!.queueEvent {
                        if (renderer.invalidateFlattenMode()) fragment.glView!!.requestRender()
                    }
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuOrientationArrangeFinished))
                },
                enabled = availability.canArrange,
            ),
            BedComposeMenuAction(
                R.string.MenuOrientationAutoOrient,
                R.drawable.menu_orientation_auto_28,
                {
                    val view = fragment.glView ?: return@BedComposeMenuAction
                    view.queueEvent {
                        val model = renderer.model ?: return@queueEvent
                        val selected = renderer.selectedObject
                        if (selected !in 0 until model.objectsCount) return@queueEvent
                        renderer.resetFlattenMode()
                        model.autoOrient(selected)
                        renderer.invalidateGlModel(selected)
                        view.requestRender()
                        fragment.recordModelEditOnGl()
                        ViewUtils.postOnMainThread {
                            resetFlattenCheckedState()
                            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuOrientationAutoOrientDone, Snackbar.LENGTH_SHORT))
                        }
                    }
                },
                sectionBreakBefore = true,
                enabled = availability.hasSelectedObject,
            ),
            BedComposeMenuAction(
                R.string.MenuOrientationFlatten,
                R.drawable.menu_orientation_flatten_28,
                {
                    flattenEnabled = !flattenEnabled
                    val flatten = flattenEnabled
                    val view = fragment.glView ?: return@BedComposeMenuAction
                    view.queueEvent { renderer.setInFlattenMode(flatten); view.requestRender() }
                    onItemsChanged()
                },
                selected = flattenEnabled,
                toggleable = true,
                enabled = availability.hasSelectedObject,
            ),
            BedComposeMenuAction(
                R.string.MenuOrientationPosition,
                R.drawable.menu_orientation_position_28,
                { anchor ->
                    resetFlattenIfNeeded(renderer)
                    showPositionMenu.accept(anchor)
                },
                enabled = availability.hasSelectedObject,
            ),
            BedComposeMenuAction(
                R.string.MenuOrientationRotation,
                R.drawable.menu_orientation_rotation_28,
                { anchor ->
                    resetFlattenIfNeeded(renderer)
                    showRotationMenu.accept(anchor)
                },
                enabled = availability.hasSelectedObject,
            ),
        )
    }

    fun onFlattenModeReset() = resetFlattenCheckedState()

    fun onObjectsChanged() = resetFlattenCheckedState()

    fun onSelectionChanged() = resetFlattenCheckedState()

    private fun resetFlattenIfNeeded(renderer: ru.ytkab0bp.slicebeam.render.GLRenderer) {
        val view = fragment.glView ?: return
        view.queueEvent { if (renderer.resetFlattenMode()) view.requestRender() }
        resetFlattenCheckedState()
    }

    private fun resetFlattenCheckedState() {
        if (!flattenEnabled) return
        flattenEnabled = false
        onItemsChanged()
    }
}
