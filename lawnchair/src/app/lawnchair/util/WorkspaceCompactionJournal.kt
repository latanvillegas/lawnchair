/*
 * Copyright 2026, Lawnchair contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package app.lawnchair.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable single-operation undo journal.
 *
 * Persist BEFORE changing the database; the caller reconciles the journal
 * against actual SQLite rows after a crash. commit() is synchronous, and
 * failure MUST prevent the database write. Never trust stored coordinates
 * without comparing the complete desktop snapshot.
 */
object WorkspaceCompactionJournal {
    private const val FILE = "workspace_compaction_undo"
    private const val KEY = "pending"
    private const val VERSION = 2

    private fun encode(items: List<WorkspaceCompactionAdapter.SnapshotItem>): JSONArray =
        JSONArray().also { array ->
            items.forEach { item ->
                array.put(JSONArray().apply {
                    put(item.id)
                    put(item.screenId)
                    put(item.container)
                    put(item.itemType)
                    put(item.cellX)
                    put(item.cellY)
                    put(item.spanX)
                    put(item.spanY)
                })
            }
        }

    private fun decode(array: JSONArray): List<WorkspaceCompactionAdapter.SnapshotItem> =
        (0 until array.length()).map { index ->
            val item = array.getJSONArray(index)
            require(item.length() == 8)
            WorkspaceCompactionAdapter.SnapshotItem(
                id = item.getLong(0),
                screenId = item.getInt(1),
                container = item.getInt(2),
                itemType = item.getInt(3),
                cellX = item.getInt(4),
                cellY = item.getInt(5),
                spanX = item.getInt(6),
                spanY = item.getInt(7),
            )
        }

    enum class Phase { COMPACTION, UNDO_INTENT }

    data class Record(
        val before: List<WorkspaceCompactionAdapter.SnapshotItem>,
        val after: List<WorkspaceCompactionAdapter.SnapshotItem>,
        val phase: Phase = Phase.COMPACTION,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Synchronous persistence barrier. False means do not modify SQLite. */
    fun save(context: Context, record: Record): Boolean {
        val json = JSONObject().apply {
            put("version", VERSION)
            put("phase", record.phase.name)
            put("before", encode(record.before))
            put("after", encode(record.after))
        }
        return prefs(context).edit().putString(KEY, json.toString()).commit()
    }

    fun load(context: Context): Record? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val version = json.getInt("version")
            require(version == 1 || version == VERSION)
            val before = decode(json.getJSONArray("before"))
            val after = decode(json.getJSONArray("after"))
            require(before.map { it.id }.toSet().size == before.size)
            require(after.map { it.id }.toSet().size == after.size)
            require(before.map { it.id }.toSet() == after.map { it.id }.toSet())
            Record(before, after, if (version == 1) Phase.COMPACTION else Phase.valueOf(json.getString("phase")))
        } catch (_: Exception) {
            null // Malformed journals are not automatically overwritten or applied.
        }
    }

    /** Persist undo intent before starting the SQLite restoration. */
    fun markUndoIntent(context: Context, expected: Record): Boolean {
        val current = load(context) ?: return false
        if (current.before != expected.before || current.after != expected.after) return false
        if (current.phase == Phase.UNDO_INTENT) return true
        return save(context, current.copy(phase = Phase.UNDO_INTENT))
    }

    fun exists(context: Context): Boolean = prefs(context).contains(KEY)

    fun clear(context: Context): Boolean = prefs(context).edit().remove(KEY).commit()
}
