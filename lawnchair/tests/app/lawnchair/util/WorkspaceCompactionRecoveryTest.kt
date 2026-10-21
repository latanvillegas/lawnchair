package app.lawnchair.util

import com.android.launcher3.LauncherSettings.Favorites
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceCompactionRecoveryTest {
    private fun item(x: Int) = WorkspaceCompactionAdapter.SnapshotItem(
        1L, 0, Favorites.CONTAINER_DESKTOP, Favorites.ITEM_TYPE_APPLICATION,
        x, 0, 1, 1,
    )
    private val before = listOf(item(2))
    private val after = listOf(item(0))
    private val record = WorkspaceCompactionJournal.Record(before, after)

    @Test fun crashBeforeSqliteCommitKeepsOriginalLayout() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.BEFORE_WRITE,
            WorkspaceCompactionTransaction.classifyRecovery(record, before),
        )
    }

    @Test fun crashAfterSqliteCommitRetainsUndo() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.COMMITTED_UNDO_AVAILABLE,
            WorkspaceCompactionTransaction.classifyRecovery(record, after),
        )
    }

    @Test fun laterDesktopEditMustBlockUndo() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(record, listOf(item(1))),
        )
    }

    @Test fun duplicateIdsMustBlockUndo() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(record, listOf(item(0), item(0))),
        )
    }
}
