/*
 * Copyright 2026, Lawnchair contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package app.lawnchair.util

import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.ItemInfo

/**
 * Converts Launcher3 model items into a read-only compaction proposal.
 *
 * Do not use this as a database mutation API. The caller must provide a stable
 * snapshot of one screen, and must revalidate it before any future write.
 */
object WorkspaceCompactionAdapter {
    data class Proposal(
        val plan: WorkspaceCompactionPlanner.Plan,
        val itemsById: Map<Long, ItemInfo>,
    )

    fun preview(
        screenId: Int,
        columns: Int,
        rows: Int,
        items: Collection<ItemInfo>,
    ): Proposal? {
        if (screenId < 0) return null
        // Reject incomplete screen snapshots: caller must provide all items on this
        // screen, not only movable icons, to prevent collisions with widgets/folders.
        val screenItems = items.filter {
            it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                it.screenId == screenId
        }
        val byId = screenItems.associateBy { it.id.toLong() }
        if (byId.size != screenItems.size) return null

        val inputs = screenItems.map {
            WorkspaceCompactionPlanner.Item(
                id = it.id.toLong(),
                cellX = it.cellX,
                cellY = it.cellY,
                spanX = it.spanX,
                spanY = it.spanY,
                movable = it.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                    it.spanX == 1 && it.spanY == 1,
            )
        }
        val plan = WorkspaceCompactionPlanner.calculate(columns, rows, inputs) ?: return null
        return Proposal(plan, byId)
    }

    /** Check item identity and current model coordinates before committing a plan. */
    fun stillMatches(proposal: Proposal): Boolean =
        proposal.plan.moves.all { move ->
            val item = proposal.itemsById[move.id] ?: return@all false
            item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                item.cellX == move.from.x && item.cellY == move.from.y
        }
}
