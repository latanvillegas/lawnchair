package app.lawnchair.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCompactionPlannerTest {
    private val planner = WorkspaceCompactionPlanner

    @Test fun sparseGridCompactsInReadingOrder() {
        val plan = planner.calculate(4, 2, listOf(
            WorkspaceCompactionPlanner.Item(1, 3, 0, 1, 1, true),
            WorkspaceCompactionPlanner.Item(2, 2, 1, 1, 1, true),
        ))!!
        assertEquals(listOf(
            WorkspaceCompactionPlanner.Position(0, 0),
            WorkspaceCompactionPlanner.Position(1, 0),
        ), plan.moves.map { it.to })
        assertTrue(plan.isReversible())
        assertEquals(plan.moves.size, plan.reverseMoves().size)
    }

    @Test fun fixedWidgetsAndFoldersStayOccupied() {
        val plan = planner.calculate(4, 2, listOf(
            WorkspaceCompactionPlanner.Item(9, 0, 0, 2, 1, false),
            WorkspaceCompactionPlanner.Item(8, 2, 0, 1, 1, false),
            WorkspaceCompactionPlanner.Item(1, 3, 1, 1, 1, true),
        ))!!
        assertEquals(WorkspaceCompactionPlanner.Position(3, 0), plan.moves.single().to)
    }

    @Test fun collisionIsRejected() {
        assertNull(planner.calculate(3, 3, listOf(
            WorkspaceCompactionPlanner.Item(1, 0, 0, 2, 2, false),
            WorkspaceCompactionPlanner.Item(2, 1, 1, 1, 1, true),
        )))
    }

    @Test fun outOfBoundsAndDuplicateIdsAreRejected() {
        assertNull(planner.calculate(4, 4, listOf(
            WorkspaceCompactionPlanner.Item(1, 4, 0, 1, 1, true),
        )))
        assertNull(planner.calculate(4, 4, listOf(
            WorkspaceCompactionPlanner.Item(1, 0, 0, 1, 1, true),
            WorkspaceCompactionPlanner.Item(1, 1, 0, 1, 1, true),
        )))
    }

    @Test fun alreadyCompactedIsNoOp() {
        val plan = planner.calculate(2, 2, listOf(
            WorkspaceCompactionPlanner.Item(1, 0, 0, 1, 1, true),
            WorkspaceCompactionPlanner.Item(2, 1, 0, 1, 1, true),
        ))
        assertNotNull(plan)
        assertTrue(plan!!.moves.isEmpty())
        assertFalse(plan.reverseMoves().isNotEmpty())
    }
}
