/*
 * Copyright 2026, Lawnchair contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package app.lawnchair.util

import android.content.Context
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.util.Executors
import java.util.function.Consumer

/**
 * Reads the complete in-memory workspace item map on Launcher3's model executor.
 *
 * Does not write to the database. Callbacks are invoked on MODEL_EXECUTOR,
 * not the main thread. Screen geometry must be provided by the caller.
 */
object WorkspaceCompactionModelReader {
    data class Result(
        val loadId: Int,
        val snapshot: List<WorkspaceCompactionAdapter.SnapshotItem>,
        val preview: WorkspaceCompactionAdapter.Batch,
    )

    fun read(
        context: Context,
        screens: List<WorkspaceCompactionAdapter.Screen>,
        callback: (Result?) -> Unit,
    ) {
        val model = LauncherAppState.getInstance(context.applicationContext).model
        model.loadAsync(Consumer { dataModel ->
            // LauncherModel.loadAsync dispatches this callback on MODEL_EXECUTOR.
            if (dataModel == null || !model.isModelLoaded()) {
                callback(null)
                return@Consumer
            }
            val loadId = model.lastLoadId
            // Copy *every* item first, not only the movable icons, while on
            // the model thread. Fixed desktop items are needed for occupancy.
            val snapshot = WorkspaceCompactionAdapter.capture(
                dataModel.itemsIdMap.toList(),
            )
            val preview = WorkspaceCompactionAdapter.previewAll(screens, snapshot)
            callback(
                if (preview != null && model.isModelLoaded() && model.lastLoadId == loadId) {
                    Result(loadId, snapshot, preview)
                } else {
                    null
                },
            )
        })
    }

    /**
     * Schedule a fresh snapshot and compare the entire desktop immediately
     * before any eventual write. This is a validation barrier, NOT a lock or
     * an atomic database transaction.
     */
    fun revalidate(
        context: Context,
        previous: Result,
        callback: (Boolean) -> Unit,
    ) {
        val model = LauncherAppState.getInstance(context.applicationContext).model
        Executors.MODEL_EXECUTOR.execute {
            if (!model.isModelLoaded() || model.lastLoadId != previous.loadId) {
                callback(false)
                return@execute
            }
            model.loadAsync(Consumer { dataModel ->
                if (dataModel == null || !model.isModelLoaded() ||
                    model.lastLoadId != previous.loadId
                ) {
                    callback(false)
                    return@Consumer
                }
                val current = WorkspaceCompactionAdapter.capture(dataModel.itemsIdMap.toList())
                callback(WorkspaceCompactionAdapter.stillMatches(previous.preview, current))
            })
        }
    }
}
