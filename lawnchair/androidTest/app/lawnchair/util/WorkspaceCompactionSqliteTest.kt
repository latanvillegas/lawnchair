package app.lawnchair.util

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated SQLite transaction fault-injection tests.
 * Uses an in-memory database, never the user's Launcher3 favorites database.
 * This is a prerequisite, not a substitute, for integration tests using ModelDbController.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceCompactionSqliteTest {
    private fun database(): SQLiteDatabase =
        SQLiteDatabase.create(null).apply {
            execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY, cellX INTEGER NOT NULL)")
            execSQL("INSERT INTO favorites (_id, cellX) VALUES (1, 2), (2, 4)")
        }

    private fun cell(db: SQLiteDatabase, id: Int): Int =
        db.rawQuery("SELECT cellX FROM favorites WHERE _id=?", arrayOf(id.toString())).use {
            check(it.moveToFirst())
            it.getInt(0)
        }

    @Test fun successfulTransactionPersistsAllMoves() {
        database().use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE favorites SET cellX=0 WHERE _id=1")
                db.execSQL("UPDATE favorites SET cellX=1 WHERE _id=2")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            assertEquals(0, cell(db, 1))
            assertEquals(1, cell(db, 2))
        }
    }

    @Test fun interruptedTransactionRollsBackAllMoves() {
        database().use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE favorites SET cellX=0 WHERE _id=1")
                // Simulates failure before transaction is marked successful.
            } finally {
                db.endTransaction()
            }
            assertEquals(2, cell(db, 1))
            assertEquals(4, cell(db, 2))
        }
    }

    @Test fun staleExpectedPositionCausesRollback() {
        database().use { db ->
            db.beginTransaction()
            try {
                val first = db.compileStatement(
                    "UPDATE favorites SET cellX=0 WHERE _id=1 AND cellX=2"
                ).executeUpdateDelete()
                val stale = db.compileStatement(
                    "UPDATE favorites SET cellX=1 WHERE _id=2 AND cellX=99"
                ).executeUpdateDelete()
                if (first == 1 && stale == 1) db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            assertEquals(2, cell(db, 1))
            assertEquals(4, cell(db, 2))
        }
    }

    @Test fun committedUndoRestoresOriginalCoordinates() {
        database().use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE favorites SET cellX=0 WHERE _id=1")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.beginTransaction()
            try {
                db.execSQL("UPDATE favorites SET cellX=2 WHERE _id=1 AND cellX=0")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            assertEquals(2, cell(db, 1))
        }
    }
}
