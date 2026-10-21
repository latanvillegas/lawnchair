/*
 * Copyright 2026, Lawnchair contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package app.lawnchair.util

import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.ItemInfo

/**
 * Immutable snapshots and read-only plans for one or more workspace screens.
 *
 * The caller MUST obtain a complete, consistent model snapshot on the model
 * thread, including fixed items. This adapter cannot prove snapshot completeness.
 * Do not persist moves from this adapter without transactional writer support.
 */
object WorkspaceCompactionAdapter {
    data class SnapshotItem(
        val id: Long,
        val screenId: Int,
        val container: Int,
        val itemType: Int,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
    )

    data class Screen(val id: Int, val columns: Int, val rows: Int)

    data class Proposal(
        val screen: Screen,
        val plan: WorkspaceCompactionPlanner.Plan,
        val snapshot: List<SnapshotItem>,
    )

    data class Batch(val proposals: List<Proposal>) {
        val moveCount: Int get() = proposals.sumOf { it.plan.moves.size }

        fun reverseMoves(): Map<Int, List<WorkspaceCompactionPlanner.Move>> =
            proposals.associate { it.screen.id to it.plan.reverseMoves() }
    }

    fun capture(items: Iterable<ItemInfo>): List<SnapshotItem> = items.map {
        SnapshotItem(
            id = it.id.toLong(),
            screenId = it.screenId,
            container = it.container,
            itemType = it.itemType,
            cellX = it.cellX,
            cellY = it.cellY,
            spanX = it.spanX,
            spanY = it.spanY,
        )
    }

    /**
     * Plans each screen independently; no moves between screens, profiles or dock.
     * Rejects duplicate screen IDs, item IDs, invalid geometry or collisions.
     */
    fun previewAll(screens: List<Screen>, snapshot: List<SnapshotItem>): Batch? {
        if (screens.map { it.id }.toSet().size != screens.size) return null
        if (snapshot.map { it.id }.toSet().size != snapshot.size) return null
        val desktop = LauncherSettings.Favorites.CONTAINER_DESKTOP
        val screenIds = screens.map { it.id }.toSet()
        if (snapshot.any { it.container == desktop && it.screenId !in screenIds }) return null
        val proposals = ArrayList<Proposal>()
        for (screen in screens) {
            if (screen.id < 0 || screen.columns <= 0 || screen.rows <= 0) return null
            val screenItems = snapshot.filter { it.container == desktop && it.screenId == screen.id }
            val inputs = screenItems.map {
                WorkspaceCompactionPlanner.Item(
                    id = it.id,
                    cellX = it.cellX,
                    cellY = it.cellY,
                    spanX = it.spanX,
                    spanY = it.spanY,
                    movable = it.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                        it.spanX == 1 && it.spanY == 1,
                )
            }
            val plan = WorkspaceCompactionPlanner.calculate(
                screen.columns, screen.rows, inputs,
            ) ?: return null
            proposals.add(Proposal(screen, plan, screenItems))
        }
        return Batch(proposals)
    }

    /**
     * Fail closed if ANY item changed, including fixed obstacles and other
     * screens. The caller must recapture on the model thread immediately before
     * committing. This is not an atomic compare-and-swap.
     */
    fun stillMatches(batch: Batch, current: List<SnapshotItem>): Boolean {
        val original = batch.proposals.flatMap { it.snapshot }
        val desktop = LauncherSettings.Favorites.CONTAINER_DESKTOP
        val currentDesktop = current.filter { it.container == desktop }
        if (currentDesktop.size != original.size) return false
        return original.toSet() == currentDesktop.toSet()
    }
}
