package com.stainedhead.paperclipmobile.android.designsystem

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import kotlin.math.abs

private const val DRAGGED_ALPHA = 0.92f
private const val HALF = 2

/** What a row of a [ReorderableColumn] gets: the modifier that makes its handle draggable, and the one-step moves. */
class RowControls(
    /** Long-press and drag on this to move the row. */
    val handle: Modifier,
    /** Moves the row one place up; null for the first row. For accessibility actions. */
    val moveUp: (() -> Unit)?,
    /** Moves the row one place down; null for the last row. For accessibility actions. */
    val moveDown: (() -> Unit)?,
)

/**
 * The order of a short list while it is being changed, and the drag in progress. The list the person sees follows the
 * finger at once; the new order is handed to [commit] when the row is dropped (or at once for a one-step move), and the
 * shared core decides whether it is accepted: the next list it sends replaces this one.
 */
@Stable
internal class ReorderController<T>(
    initial: List<T>,
    private val key: (T) -> String,
    private val commit: (List<T>) -> Unit,
) {
    private var settled = initial
    private val heights = HashMap<String, Int>()
    var order by mutableStateOf(initial)
        private set
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun measured(
        key: String,
        height: Int,
    ) {
        heights[key] = height
    }

    fun move(
        from: Int,
        to: Int,
    ) {
        if (from !in order.indices || to !in order.indices || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        handOn()
    }

    fun start(key: String) {
        draggingKey = key
        offset = 0f
    }

    fun drag(delta: Float) {
        val dragged = draggingKey ?: return
        offset += delta
        while (swapOnce(dragged)) {
            // Swapping can leave the row past the next one too.
        }
    }

    fun finish() {
        if (draggingKey != null && order != settled) handOn()
        draggingKey = null
        offset = 0f
    }

    private fun handOn() {
        settled = order
        commit(order)
    }

    /** Swaps the dragged row with its neighbour once it is more than half way over it. */
    private fun swapOnce(dragged: String): Boolean {
        val index = order.indexOfFirst { key(it) == dragged }
        val neighbour = if (offset > 0) index + 1 else index - 1
        val height = order.getOrNull(neighbour)?.let { heights[key(it)] } ?: return false
        if (abs(offset) <= height / HALF) return false
        order = order.toMutableList().apply { add(neighbour, removeAt(index)) }
        offset += if (neighbour > index) -height else height
        return true
    }
}

/**
 * A column whose rows can be dragged into a new order by a long press on their handle, with one-step moves for
 * accessibility actions. Only for short lists (the person's factories): every row is composed.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onReordered: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    reorderRejected: Boolean = false,
    row: @Composable (item: T, controls: RowControls) -> Unit,
) {
    val commit by rememberUpdatedState(onReordered)
    val controller = remember(items) { ReorderController(items, keyOf) { commit(it) } }
    Column(modifier) {
        for ((index, item) in controller.order.withIndex()) {
            val itemKey = keyOf(item)
            key(itemKey) {
                val dragging = controller.draggingKey == itemKey
                val controls =
                    RowControls(
                        handle =
                            Modifier.pointerInput(itemKey) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { controller.start(itemKey) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        controller.drag(amount.y)
                                    },
                                    onDragEnd = controller::finish,
                                    onDragCancel = controller::finish,
                                )
                            },
                        moveUp = if (index > 0) ({ controller.move(index, index - 1) }) else null,
                        moveDown =
                            if (index <
                                controller.order.lastIndex
                            ) {
                                ({ controller.move(index, index + 1) })
                            } else {
                                null
                            },
                    )
                Box(
                    Modifier
                        .onSizeChanged { controller.measured(itemKey, it.height) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) controller.offset else 0f }
                        .alpha(if (dragging) DRAGGED_ALPHA else 1f),
                ) { row(item, controls) }
            }
        }
    }
}
