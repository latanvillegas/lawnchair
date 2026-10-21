/*
 * Copyright 2026, Lawnchair contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package app.lawnchair.util

/**
 * Pure, side-effect-free workspace compaction planner.
 *
 * The caller supplies model-space coordinates for ONE desktop screen and its
 * actual grid dimensions. The planner does not write the launcher database.
 * Every occupied cell is considered, including widgets, folders and unknown
 * item types. Only explicitly movable single-cell app icons may be relocated.
 */
object WorkspaceCompactionPlanner {
    data class Item(
        val id: Long,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val movable: Boolean,
    )

    data class Position(val x: Int, val y: Int)

    data class Move(val id: Long, val from: Position, val to: Position)

    data class Plan(
        val moves: List<Move>,
        val originalPositions: Map<Long, Position>,
    ) {
        /** True only when each target has a matching reversible source. */
        fun isReversible(): Boolean = moves.all { originalPositions[it.id] == it.from }

        /** Reverse moves in reverse order for safe higher-layer undo orchestration. */
        fun reverseMoves(): List<Move> = moves.asReversed().map { Move(it.id, it.to, it.from) }

        /** Coordinates to restore if a higher layer needs to undo the plan. */
        fun undoPositions(): Map<Long, Position> = originalPositions
    }

    /**
     * Returns null for invalid, overlapping, out-of-bounds or duplicate data.
     * Returns an empty plan if there is nothing to compact.
     *
     * Movable icons retain their original row-major relative order.
     */
    fun calculate(columns: Int, rows: Int, items: List<Item>): Plan? {
        if (columns <= 0 || rows <= 0) return null
        val occupied = BooleanArray(columns * rows)
        val ids = HashSet<Long>()

        for (item in items) {
            if (!ids.add(item.id)) return null
            if (item.cellX < 0 || item.cellY < 0 ||
                item.spanX <= 0 || item.spanY <= 0 ||
                item.cellX.toLong() + item.spanX > columns ||
                item.cellY.toLong() + item.spanY > rows
            ) return null
            if (item.movable && (item.spanX != 1 || item.spanY != 1)) return null

            for (y in item.cellY until item.cellY + item.spanY) {
                for (x in item.cellX until item.cellX + item.spanX) {
                    val index = y * columns + x
                    if (occupied[index]) return null
                    occupied[index] = true
                }
            }
        }

        // Release movable cells, leaving every other element as an obstacle.
        val movable = items.filter { it.movable }
            .sortedWith(compareBy<Item>({ it.cellY }, { it.cellX }, { it.id }))
        for (item in movable) occupied[item.cellY * columns + item.cellX] = false

        val moves = ArrayList<Move>()
        val originals = LinkedHashMap<Long, Position>()
        var cursor = 0
        for (item in movable) {
            while (cursor < occupied.size && occupied[cursor]) cursor++
            if (cursor == occupied.size) return null
            val destination = Position(cursor % columns, cursor / columns)
            val original = Position(item.cellX, item.cellY)
            if (original != destination) {
                originals[item.id] = original
                moves.add(Move(item.id, original, destination))
            }
            occupied[cursor] = true
            cursor++
        }
        return Plan(moves, originals).takeIf { it.isReversible() }
    }
}
