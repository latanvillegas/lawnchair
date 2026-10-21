/*
 * Copyright 2026, Lawnchair contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package app.lawnchair.util

import android.content.ContentValues
import android.content.Context
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.util.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transactional persistence for an already-reviewed compaction proposal.
 *
 * Disabled for UI until geometry, profiles and rollback have been exercised on
 * devices. The caller must supply a snapshot from the model executor and a
 * complete screen set. Both commit and undo compare ALL desktop DB rows within
 * the same SQLite transaction as the updates.
 */
object WorkspaceCompactionTransaction {
    data class Receipt(
        val before: List<WorkspaceCompactionAdapter.SnapshotItem>,
        val after: List<WorkspaceCompactionAdapter.SnapshotItem>,
        val moves: Map<Long, WorkspaceCompactionPlanner.Position>,
        val reverse: Map<Long, WorkspaceCompactionPlanner.Position>,
    )

    private val inFlight = AtomicBoolean(false)

    enum class RecoveryState {
        NONE, BEFORE_WRITE, COMMITTED_UNDO_AVAILABLE, DIVERGED_OR_CORRUPT
    }

    /**
     * Inspect persistent state without changing any user layout.
     * BEFORE_WRITE can arise when the process dies after journaling but
     * before SQLite commits. Never silently clear a divergent journal.
     */
    fun recoveryState(context: Context): RecoveryState {
        if (!WorkspaceCompactionJournal.exists(context)) return RecoveryState.NONE
        val record = WorkspaceCompactionJournal.load(context)
            ?: return RecoveryState.DIVERGED_OR_CORRUPT
        return try {
            classifyRecovery(record, readDesktop(context))
        } catch (_: Exception) {
            RecoveryState.DIVERGED_OR_CORRUPT
        }
    }

    internal fun classifyRecovery(
        record: WorkspaceCompactionJournal.Record,
        current: List<WorkspaceCompactionAdapter.SnapshotItem>,
    ): RecoveryState {
        if (record.before.map { it.id }.toSet().size != record.before.size ||
            record.after.map { it.id }.toSet().size != record.after.size ||
            current.map { it.id }.toSet().size != current.size
        ) return RecoveryState.DIVERGED_OR_CORRUPT
        val actual = current.toSet()
        return when {
            actual == record.after.toSet() && current.size == record.after.size ->
                RecoveryState.COMMITTED_UNDO_AVAILABLE
            actual == record.before.toSet() && current.size == record.before.size ->
                RecoveryState.BEFORE_WRITE
            else -> RecoveryState.DIVERGED_OR_CORRUPT
        }
    }

    private val projection = arrayOf(
        Favorites._ID, Favorites.SCREEN, Favorites.CONTAINER, Favorites.ITEM_TYPE,
        Favorites.CELLX, Favorites.CELLY, Favorites.SPANX, Favorites.SPANY,
    )

    private fun readDesktop(context: Context): List<WorkspaceCompactionAdapter.SnapshotItem> {
        val db = LauncherAppState.getInstance(context).model.modelDbController
        val result = ArrayList<WorkspaceCompactionAdapter.SnapshotItem>()
        db.query(
            projection,
            Favorites.CONTAINER + "=?",
            arrayOf(Favorites.CONTAINER_DESKTOP.toString()),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    WorkspaceCompactionAdapter.SnapshotItem(
                        id = cursor.getLong(0),
                        screenId = cursor.getInt(1),
                        container = cursor.getInt(2),
                        itemType = cursor.getInt(3),
                        cellX = cursor.getInt(4),
                        cellY = cursor.getInt(5),
                        spanX = cursor.getInt(6),
                        spanY = cursor.getInt(7),
                    ),
                )
            }
        }
        return result
    }

    private fun apply(
        context: Context,
        expected: List<WorkspaceCompactionAdapter.SnapshotItem>,
        target: Map<Long, WorkspaceCompactionPlanner.Position>,
    ): Boolean {
        val model = LauncherAppState.getInstance(context).model
        if (target.isEmpty() || expected.map { it.id }.toSet().size != expected.size) return false
        val proposed = expected.map { item ->
            target[item.id]?.let { item.copy(cellX = it.x, cellY = it.y) } ?: item
        }
        if (proposed.any { it.cellX < 0 || it.cellY < 0 || it.spanX <= 0 || it.spanY <= 0 }) return false
        // Validate complete occupancy, including the footprint of fixed widgets.
        val occupied = HashSet<Triple<Int, Int, Int>>()
        for (item in proposed) {
            if (item.container != Favorites.CONTAINER_DESKTOP) continue
            for (y in item.cellY until item.cellY + item.spanY) {
                for (x in item.cellX until item.cellX + item.spanX) {
                    if (!occupied.add(Triple(item.screenId, x, y))) return false
                }
            }
        }
        var committed = false
        try {
            model.modelDbController.newTransaction().use { transaction ->
                // Compare all desktop items, not just the icons being moved.
                val actual = readDesktop(context)
                if (actual.size != expected.size || actual.toSet() != expected.toSet()) {
                    return false
                }
                for ((id, destination) in target) {
                    val original = expected.firstOrNull { it.id == id } ?: return false
                    if (original.container != Favorites.CONTAINER_DESKTOP ||
                        original.itemType != Favorites.ITEM_TYPE_APPLICATION
                    ) return false
                    val values = ContentValues().apply {
                        put(Favorites.CELLX, destination.x)
                        put(Favorites.CELLY, destination.y)
                    }
                    val updated = model.modelDbController.update(
                        values,
                        Favorites._ID + "=? AND " + Favorites.CONTAINER + "=? AND " +
                            Favorites.SCREEN + "=? AND " + Favorites.CELLX + "=? AND " +
                            Favorites.CELLY + "=?",
                        arrayOf(
                            id.toString(), Favorites.CONTAINER_DESKTOP.toString(),
                            original.screenId.toString(), original.cellX.toString(),
                            original.cellY.toString(),
                        ),
                    )
                    if (updated != 1) return false
                }
                transaction.commit()
            }
            // endTransaction() has completed successfully at this point.
            committed = true
        } catch (_: Exception) {
            return false
        } finally {
            if (committed) model.forceReload()
        }
        return committed
    }

    /** Executes on MODEL_EXECUTOR. Callback also runs on MODEL_EXECUTOR. */
    fun commit(
        context: Context,
        result: WorkspaceCompactionModelReader.Result,
        callback: (Receipt?) -> Unit,
    ) {
        if (!inFlight.compareAndSet(false, true)) {
            callback(null)
            return
        }
        val appContext = context.applicationContext
        Executors.MODEL_EXECUTOR.execute {
            try {
                val model = LauncherAppState.getInstance(appContext).model
                val before = result.preview.proposals.flatMap { it.snapshot }
                val moves = result.preview.proposals.flatMap { it.plan.moves }
                val target = moves.associate { it.id to it.to }
                if (!model.isModelLoaded() || model.lastLoadId != result.loadId ||
                    !WorkspaceCompactionAdapter.stillMatches(result.preview, result.snapshot) ||
                    moves.size != target.size
                ) {
                    callback(null)
                    return@execute
                }
                if (target.isEmpty()) {
                    callback(null)
                    return@execute
                }
                val reverse = moves.associate { it.id to it.from }
                val after = before.map { item ->
                    val destination = target[item.id]
                    if (destination == null) item
                    else item.copy(cellX = destination.x, cellY = destination.y)
                }
                // Never overwrite an unresolved journal from a previous operation.
                if (WorkspaceCompactionJournal.exists(appContext) ||
                    !WorkspaceCompactionJournal.save(
                        appContext, WorkspaceCompactionJournal.Record(before, after),
                    )
                ) {
                    callback(null)
                    return@execute
                }
                if (!apply(appContext, before, target)) {
                    // SQLite rolled back or rejected the update. Only remove
                    // the journal when the original state is still present.
                    if (recoveryState(appContext) == RecoveryState.BEFORE_WRITE) {
                        WorkspaceCompactionJournal.clear(appContext)
                    }
                    callback(null)
                    return@execute
                }
                callback(Receipt(before, after, target, reverse))
            } catch (_: Exception) {
                callback(null)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /**
     * Reconcile a crash after journal persistence but before the SQLite write.
     * Never erase a committed undo receipt or an ambiguous journal.
     * Callback runs on MODEL_EXECUTOR.
     */
    fun discardUnappliedJournal(context: Context, callback: (Boolean) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) {
            callback(false)
            return
        }
        val appContext = context.applicationContext
        Executors.MODEL_EXECUTOR.execute {
            try {
                val cleared = recoveryState(appContext) == RecoveryState.BEFORE_WRITE &&
                    WorkspaceCompactionJournal.clear(appContext)
                callback(cleared)
            } catch (_: Exception) {
                callback(false)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /**
     * Returns a durable undo receipt only when the database still exactly
     * matches the journal's post-compaction state. The caller must run this
     * on MODEL_EXECUTOR. An ambiguous or partially applied journal is refused.
     */
    fun recover(context: Context): Receipt? {
        val record = WorkspaceCompactionJournal.load(context) ?: return null
        val current = readDesktop(context)
        if (current.size != record.after.size ||
            current.toSet() != record.after.toSet()
        ) return null
        val previous = record.before.associateBy { it.id }
        val changed = record.after.filter { previous[it.id] != it }
        if (changed.isEmpty()) return null
        val reverse = changed.associate { it.id to
            WorkspaceCompactionPlanner.Position(
                previous.getValue(it.id).cellX, previous.getValue(it.id).cellY,
            )
        }
        val moves = changed.associate { it.id to
            WorkspaceCompactionPlanner.Position(it.cellX, it.cellY)
        }
        return Receipt(record.before, record.after, moves, reverse)
    }

    /** Undo is conditional: refuses to overwrite any subsequent desktop edits. */
    fun undo(context: Context, receipt: Receipt, callback: (Boolean) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) {
            callback(false)
            return
        }
        val appContext = context.applicationContext
        Executors.MODEL_EXECUTOR.execute {
            try {
                val stored = WorkspaceCompactionJournal.load(appContext)
                if (stored == null || stored.before != receipt.before ||
                    stored.after != receipt.after
                ) {
                    callback(false)
                    return@execute
                }
                val restored = apply(appContext, receipt.after, receipt.reverse)
                if (restored) WorkspaceCompactionJournal.clear(appContext)
                callback(restored)
            } catch (_: Exception) {
                callback(false)
            } finally {
                inFlight.set(false)
            }
        }
    }
}
