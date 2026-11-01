package io.github.patience

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.UUID
import java.util.concurrent.Executors

data class Note(val id: String, val text: String, val updatedAt: Long) {
    val title: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "新会话"
}

internal data class Notebook(val notes: List<Note>, val activeId: String?)

internal class NotesDatabase(context: Context, name: String = "notes.db") :
    SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY NOT NULL, body TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE workspace (id INTEGER PRIMARY KEY CHECK(id=1), active_id TEXT)")
        db.execSQL("INSERT INTO workspace(id, active_id) VALUES (1, NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No migration from $oldVersion to $newVersion")
    }

    fun read(): Notebook {
        val db = readableDatabase
        val notes = db.rawQuery("SELECT id, body, updated_at FROM notes ORDER BY updated_at DESC, id", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Note(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
            }
        }
        val active = db.rawQuery("SELECT active_id FROM workspace WHERE id=1", null).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        return Notebook(notes, active?.takeIf { id -> notes.any { it.id == id } })
    }

    fun save(note: Note) {
        writableDatabase.insertWithOnConflict("notes", null, ContentValues().apply {
            put("id", note.id)
            put("body", note.text)
            put("updated_at", note.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Unable to save note" } }
    }

    fun select(id: String?) {
        writableDatabase.execSQL("UPDATE workspace SET active_id=? WHERE id=1", arrayOf(id))
    }

    fun delete(id: String, activeId: String?) = transaction {
        delete("notes", "id=?", arrayOf(id))
        execSQL("UPDATE workspace SET active_id=? WHERE id=1", arrayOf(activeId))
    }

    fun replace(notebook: Notebook) = transaction {
        delete("notes", null, null)
        notebook.notes.forEach { save(it) }
        select(notebook.activeId)
    }

    private fun transaction(block: SQLiteDatabase.() -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

class NotesModel(application: Application) : AndroidViewModel(application) {
    private val database = NotesDatabase(application)
    private val writer = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var notes by mutableStateOf(emptyList<Note>())
        private set
    var activeId by mutableStateOf<String?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    private fun load() = write("无法读取本地记录") {
        val notebook = database.read()
        val restored = notebook.notes.ifEmpty {
            listOf(Note(UUID.randomUUID().toString(), "", System.currentTimeMillis()).also(database::save))
        }
        val selected = notebook.activeId ?: restored.first().id
        if (selected != notebook.activeId) database.select(selected)
        main.post {
            notes = restored
            activeId = selected
            ready = true
            error = null
        }
    }

    fun create() {
        if (!ready) return
        val note = Note(UUID.randomUUID().toString(), "", System.currentTimeMillis())
        notes = listOf(note) + notes
        activeId = note.id
        write {
            database.save(note)
            database.select(note.id)
        }
    }

    fun select(id: String?) {
        activeId = id
        write { database.select(id) }
    }

    fun ensureActive() {
        if (!ready || activeId != null) return
        if (notes.isEmpty()) create() else select(notes.first().id)
    }

    fun edit(id: String, text: String) {
        val previous = notes.first { it.id == id }
        if (previous.text == text) return // Selection and IME composition changes are not edits.
        val note = previous.copy(text = text, updatedAt = System.currentTimeMillis())
        notes = notes.map { if (it.id == id) note else it }.sortedByDescending { it.updatedAt }
        // FIFO writes have no debounce window; switching notes cannot cancel a pending save.
        write { database.save(note) }
    }

    fun delete(id: String) {
        notes = notes.filterNot { it.id == id }
        if (activeId == id) activeId = notes.firstOrNull()?.id
        val selected = activeId
        write { database.delete(id, selected) }
    }

    fun retry() {
        if (!ready) { load(); return }
        val snapshot = Notebook(notes, activeId)
        write {
            database.replace(snapshot)
            main.post { error = null }
        }
    }

    private fun write(message: String = "更改尚未保存", operation: () -> Unit) {
        writer.execute {
            try {
                operation()
            } catch (failure: Exception) {
                Log.e("Patience", message, failure)
                main.post { error = message }
            }
        }
    }

    fun flush() {
        // Lifecycle barrier: all text received before onStop is committed before returning.
        writer.submit {}.get()
    }

    override fun onCleared() {
        writer.execute { database.close() }
        writer.shutdown()
    }
}
