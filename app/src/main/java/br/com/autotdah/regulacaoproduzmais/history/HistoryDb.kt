package br.com.autotdah.regulacaoproduzmais.history

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.autotdah.regulacaoproduzmais.data.Codecs
import br.com.autotdah.regulacaoproduzmais.data.Json
import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.HistoryEvent
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.SessionSummary

/** Histórico estruturado em SQLite (local, sem rede). */
class HistoryDb(context: Context) : SQLiteOpenHelper(context, "historico.db", null, 1), HistoryRepository {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sessions(
                id TEXT PRIMARY KEY, started INTEGER, ended INTEGER, status TEXT NOT NULL,
                planned INTEGER NOT NULL, executed INTEGER NOT NULL, completed INTEGER NOT NULL,
                interrupted INTEGER NOT NULL, pauses INTEGER NOT NULL, steps TEXT NOT NULL,
                updated INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE events(
                id TEXT PRIMARY KEY, session_id TEXT NOT NULL, type TEXT NOT NULL,
                wall INTEGER NOT NULL, step_index INTEGER, detail TEXT)""",
        )
        db.execSQL("CREATE INDEX idx_events_session ON events(session_id, wall)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun upsertSession(summary: SessionSummary) {
        val cv = ContentValues().apply {
            put("id", summary.id)
            put("started", summary.startedWallMs)
            put("ended", summary.endedWallMs)
            put("status", summary.status.name)
            put("planned", summary.plannedMs)
            put("executed", summary.executedMs)
            put("completed", summary.stepsCompleted)
            put("interrupted", summary.stepsInterrupted)
            put("pauses", summary.pauses)
            put("steps", Json.write(summary.steps.map { Codecs.step(it) }))
            put("updated", summary.updatedWallMs)
        }
        writableDatabase.insertWithOnConflict("sessions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun insertEvents(events: List<HistoryEvent>) {
        if (events.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (e in events) {
                val cv = ContentValues().apply {
                    put("id", e.id)
                    put("session_id", e.sessionId)
                    put("type", e.type.name)
                    put("wall", e.wallMs)
                    put("step_index", e.stepIndex)
                    put("detail", e.detail)
                }
                db.insertWithOnConflict("events", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun sessions(): List<SessionSummary> {
        val out = mutableListOf<SessionSummary>()
        readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY COALESCE(started, updated) DESC", null).use { c ->
            while (c.moveToNext()) {
                try {
                    out += SessionSummary(
                        id = c.str("id"),
                        startedWallMs = c.longOrNull("started"),
                        endedWallMs = c.longOrNull("ended"),
                        status = SessionStatus.valueOf(c.str("status")),
                        plannedMs = c.long("planned"),
                        executedMs = c.long("executed"),
                        stepsCompleted = c.long("completed").toInt(),
                        stepsInterrupted = c.long("interrupted").toInt(),
                        pauses = c.long("pauses").toInt(),
                        steps = Codecs.steps(Json.parse(c.str("steps")) as List<*>),
                        updatedWallMs = c.long("updated"),
                    )
                } catch (_: Exception) {
                    // Linha ilegível é ignorada na listagem, sem apagar dados.
                }
            }
        }
        return out
    }

    override fun events(sessionId: String): List<HistoryEvent> {
        val out = mutableListOf<HistoryEvent>()
        readableDatabase.rawQuery(
            "SELECT * FROM events WHERE session_id = ? ORDER BY wall, rowid", arrayOf(sessionId),
        ).use { c ->
            while (c.moveToNext()) {
                val type = EventType.entries.firstOrNull { it.name == c.str("type") } ?: continue
                out += HistoryEvent(c.str("id"), c.str("session_id"), type, c.long("wall"), c.longOrNull("step_index")?.toInt(), c.strOrNull("detail"))
            }
        }
        return out
    }

    override fun clear() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("events", null, null)
            db.delete("sessions", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.idx(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.str(name: String): String = getString(idx(name))
    private fun Cursor.strOrNull(name: String): String? = idx(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(name: String): Long = getLong(idx(name))
    private fun Cursor.longOrNull(name: String): Long? = idx(name).let { if (isNull(it)) null else getLong(it) }
}
