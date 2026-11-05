package com.stainedhead.paperclipmobile.android.designsystem

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The drag arithmetic of a reorderable list: when a row swaps with a neighbour and when the new order is handed on. */
class ReorderControllerTest {
    private val committed = mutableListOf<List<String>>()

    private fun controller() =
        ReorderController(listOf("a", "b", "c"), { it }) { committed += it }.also {
            for (id in listOf("a", "b", "c")) it.measured(id, ROW)
        }

    @Test
    fun `a one step move hands the new order on at once`() {
        val controller = controller()

        controller.move(2, 1)

        controller.order shouldBe listOf("a", "c", "b")
        committed shouldBe listOf(listOf("a", "c", "b"))
    }

    @Test
    fun `a move off either end or onto itself changes nothing`() {
        val controller = controller()

        controller.move(0, -1)
        controller.move(2, 3)
        controller.move(1, 1)

        controller.order shouldBe listOf("a", "b", "c")
        committed shouldBe emptyList()
    }

    @Test
    fun `a row dragged more than half over its neighbour swaps with it and keeps the rest of the drag`() {
        val controller = controller()

        controller.start("a")
        controller.drag(ROW / 2f - 1)
        controller.order shouldBe listOf("a", "b", "c")
        controller.drag(2f)

        controller.order shouldBe listOf("b", "a", "c")
        controller.offset shouldBe (ROW / 2f + 1 - ROW)
    }

    @Test
    fun `a long drag passes several rows`() {
        val controller = controller()

        controller.start("a")
        controller.drag(ROW * 2f)

        controller.order shouldBe listOf("b", "c", "a")
    }

    @Test
    fun `dragging up swaps with the row above`() {
        val controller = controller()

        controller.start("c")
        controller.drag(-(ROW * 0.75f))

        controller.order shouldBe listOf("a", "c", "b")
    }

    @Test
    fun `dropping hands the new order on once and a drop with no change hands on nothing`() {
        val controller = controller()
        controller.start("a")
        controller.drag(ROW * 1f)
        controller.finish()

        committed shouldBe listOf(listOf("b", "a", "c"))
        controller.draggingKey shouldBe null
        controller.offset shouldBe 0f

        controller.start("b")
        controller.finish()
        committed.size shouldBe 1
    }

    private companion object {
        const val ROW = 100
    }
}
