package app.lawnchair.util

import com.android.launcher3.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCompactionAdapterTest {
    private val desktop = LauncherSettings.Favorites.CONTAINER_DESKTOP
    private val app = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
    private val folder = LauncherSettings.Favorites.ITEM_TYPE_FOLDER

    private fun icon(id: Long, screen: Int, x: Int, y: Int) =
        WorkspaceCompactionAdapter.SnapshotItem(id, screen, desktop, app, x, y, 1, 1)

    @Test fun screensArePlannedIndependently() {
        val screens = listOf(
            WorkspaceCompactionAdapter.Screen(0, 4, 2),
            WorkspaceCompactionAdapter.Screen(1, 3, 2),
        )
        val snapshot = listOf(icon(1, 0, 3, 0), icon(2, 1, 2, 1))
        val batch = WorkspaceCompactionAdapter.previewAll(screens, snapshot)!!
        assertEquals(2, batch.moveCount)
        assertEquals(setOf(0, 1), batch.reverseMoves().keys)
        assertTrue(WorkspaceCompactionAdapter.stillMatches(batch, snapshot))
        assertFalse(WorkspaceCompactionAdapter.stillMatches(batch, snapshot.dropLast(1)))
    }

    @Test fun fixedObstacleChangesInvalidateSnapshot() {
        val screens = listOf(WorkspaceCompactionAdapter.Screen(0, 4, 2))
        val folderItem = WorkspaceCompactionAdapter.SnapshotItem(
            5, 0, desktop, folder, 0, 0, 1, 1,
        )
        val snapshot = listOf(folderItem, icon(1, 0, 3, 1))
        val batch = WorkspaceCompactionAdapter.previewAll(screens, snapshot)!!
        assertFalse(WorkspaceCompactionAdapter.stillMatches(
            batch, listOf(folderItem.copy(cellX = 1), icon(1, 0, 3, 1)),
        ))
    }

    @Test fun unknownScreenAndDuplicateIdsAreRejected() {
        val screens = listOf(WorkspaceCompactionAdapter.Screen(0, 4, 2))
        assertNull(WorkspaceCompactionAdapter.previewAll(screens, listOf(icon(1, 1, 0, 0))))
        assertNull(WorkspaceCompactionAdapter.previewAll(
            screens, listOf(icon(1, 0, 0, 0), icon(1, 0, 1, 0)),
        ))
    }
}
