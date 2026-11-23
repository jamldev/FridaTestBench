package com.example.fridatestbench.storage

import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataOperationsTest {

    @Test
    fun readsEmptyDatabaseBeforeFirstWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("local-data-", ".db", context.cacheDir)
        try {
            val isolatedContext = object : ContextWrapper(context) {
                override fun openOrCreateDatabase(
                    name: String,
                    mode: Int,
                    factory: SQLiteDatabase.CursorFactory?,
                ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(file, factory)
            }
            val operations = LocalDataOperations(isolatedContext)

            assertEquals(emptyList<String>(), operations.readPayloadsFromDatabase())

            val payloads = listOf("first", "second")
            operations.writePayloadsToDatabase(payloads)
            assertEquals(payloads, operations.readPayloadsFromDatabase())
        } finally {
            SQLiteDatabase.deleteDatabase(file)
        }
    }
}
