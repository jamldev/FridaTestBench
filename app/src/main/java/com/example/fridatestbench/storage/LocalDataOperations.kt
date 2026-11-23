package com.example.fridatestbench.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import java.io.File

data class LocalDataSnapshot(
    val fileName: String,
    val fileWrittenValue: String,
    val fileReadValue: String,
    val preferencesName: String,
    val preferenceKey: String,
    val preferenceReadValue: String?,
    val databaseName: String,
    val databaseReadValues: List<String>,
)

/** Performs explicit local file, SharedPreferences, and SQLite round-trips for tracing. */
class LocalDataOperations(private val context: Context) {

    fun runRoundTrips(payload: String, url: String): LocalDataSnapshot {
        writePayloadToFile(payload)
        val fileReadValue = readPayloadFromFile()

        saveUrlToPreferences(url)
        val preferenceReadValue = readUrlFromPreferences()

        writePayloadsToDatabase(DATABASE_PAYLOADS)
        val databaseReadValues = readPayloadsFromDatabase()

        return LocalDataSnapshot(
            fileName = PAYLOAD_FILE_NAME,
            fileWrittenValue = payload,
            fileReadValue = fileReadValue,
            preferencesName = PREFERENCES_NAME,
            preferenceKey = URL_PREFERENCE_KEY,
            preferenceReadValue = preferenceReadValue,
            databaseName = DATABASE_NAME,
            databaseReadValues = databaseReadValues,
        )
    }

    fun writePayloadToFile(payload: String) {
        payloadFile().writeText(payload, Charsets.UTF_8)
    }

    fun readPayloadFromFile(): String {
        return payloadFile().readText(Charsets.UTF_8)
    }

    fun saveUrlToPreferences(url: String) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(URL_PREFERENCE_KEY, url) }
    }

    fun readUrlFromPreferences(): String? {
        return context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(URL_PREFERENCE_KEY, null)
    }

    fun writePayloadsToDatabase(payloads: List<String>) {
        withPayloadDatabase { database ->
            database.beginTransaction()
            try {
                database.delete("payloads", null, null)
                payloads.forEachIndexed { index, payload ->
                    val values = ContentValues().apply {
                        put("id", index + 1)
                        put("value", payload)
                    }
                    database.insertOrThrow("payloads", null, values)
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    fun readPayloadsFromDatabase(): List<String> {
        return withPayloadDatabase { database ->
            database.query("payloads", arrayOf("value"), null, null, null, null, "id ASC").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
        }
    }

    private inline fun <T> withPayloadDatabase(operation: (SQLiteDatabase) -> T): T {
        return context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS payloads (id INTEGER PRIMARY KEY, value TEXT NOT NULL)",
            )
            operation(database)
        }
    }

    private fun payloadFile(): File = File(context.filesDir, PAYLOAD_FILE_NAME)

    private companion object {
        const val PAYLOAD_FILE_NAME = "dcl_payload.txt"
        const val PREFERENCES_NAME = "dcl_testbench_preferences"
        const val URL_PREFERENCE_KEY = "saved_url"
        const val DATABASE_NAME = "dcl_testbench.db"
        val DATABASE_PAYLOADS = listOf(
            "NSO group pegasus payload",
            "Secret polymorphic Mossad payload",
            "Encrypted CIA access key",
        )
    }
}
