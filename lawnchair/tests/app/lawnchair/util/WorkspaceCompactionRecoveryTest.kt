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
    @Test fun interruptedUndoBeforeSqliteRestorationStillAllowsUndo() {
        val intent = record.copy(phase = WorkspaceCompactionJournal.Phase.UNDO_INTENT)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.COMMITTED_UNDO_AVAILABLE,
            WorkspaceCompactionTransaction.classifyRecovery(intent, after),
        )
    }

    @Test fun interruptedUndoAfterSqliteRestorationNeedsCleanup() {
        val intent = record.copy(phase = WorkspaceCompactionJournal.Phase.UNDO_INTENT)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.UNDONE_PENDING_CLEANUP,
            WorkspaceCompactionTransaction.classifyRecovery(intent, before),
        )
    }

    @Test fun interruptedUndoWithLaterEditMustNotClearJournal() {
        val intent = record.copy(phase = WorkspaceCompactionJournal.Phase.UNDO_INTENT)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(intent, listOf(item(1))),
        )
    }

    @Test fun unchangedBeforeAndAfterMustNotOfferUndo() {
        val identical = WorkspaceCompactionJournal.Record(before, before)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.BEFORE_WRITE,
            WorkspaceCompactionTransaction.classifyRecovery(identical, before),
        )
    }

    @Test fun removedDesktopItemMustBlockRecovery() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(record, emptyList()),
        )
    }

    @Test fun insertedDesktopItemMustBlockRecovery() {
        val extra = item(3).copy(id = 2L)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(record, after + extra),
        )
    }

    @Test fun changedItemTypeMustBlockRecovery() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(
                record, listOf(after.single().copy(itemType = Favorites.ITEM_TYPE_FOLDER)),
            ),
        )
    }

    @Test fun changedScreenMustBlockRecovery() {
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(
                record, listOf(after.single().copy(screenId = 1)),
            ),
        )
    }

    @Test fun reorderedSnapshotDoesNotChangeRecoveryClassification() {
        val secondBefore = item(4).copy(id = 2L)
        val secondAfter = item(1).copy(id = 2L)
        val multi = WorkspaceCompactionJournal.Record(before + secondBefore, after + secondAfter)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.COMMITTED_UNDO_AVAILABLE,
            WorkspaceCompactionTransaction.classifyRecovery(multi, listOf(secondAfter, after.single())),
        )
    }

    @Test fun duplicateIdsInJournalMustBlockRecovery() {
        val corrupt = WorkspaceCompactionJournal.Record(before + before, after + after)
        assertEquals(
            WorkspaceCompactionTransaction.RecoveryState.DIVERGED_OR_CORRUPT,
            WorkspaceCompactionTransaction.classifyRecovery(corrupt, after),
        )
    }

}
