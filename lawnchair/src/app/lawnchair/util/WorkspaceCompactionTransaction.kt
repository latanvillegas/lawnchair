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
                committed = true
            }
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
                if (!apply(appContext, before, target)) {
                    callback(null)
                    return@execute
                }
                val after = before.map { item ->
                    val destination = target[item.id]
                    if (destination == null) item
                    else item.copy(cellX = destination.x, cellY = destination.y)
                }
                callback(Receipt(before, after, target, reverse))
            } finally {
                inFlight.set(false)
            }
        }
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
                callback(apply(appContext, receipt.after, receipt.reverse))
            } finally {
                inFlight.set(false)
            }
        }
    }
}
