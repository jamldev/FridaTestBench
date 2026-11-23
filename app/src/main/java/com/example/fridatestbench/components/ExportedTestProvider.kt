package com.example.fridatestbench.components

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Exposes one dummy file for exported-provider and URI-grant testing. */
class ExportedTestProvider : ContentProvider() {
    private lateinit var sampleFile: File
    private lateinit var sampleUri: Uri

    override fun onCreate(): Boolean {
        val appContext = context ?: return false
        sampleUri = Uri.parse("content://${appContext.packageName}.testprovider/sample.txt")
        sampleFile = File(appContext.filesDir, "exported_component_sample.txt")
        if (!sampleFile.exists()) {
            sampleFile.writeText("Exported provider test fixture\n")
        }
        return true
    }

    private fun requireSampleUri(uri: Uri) {
        if (uri != sampleUri) {
            throw FileNotFoundException("Unknown test fixture URI: $uri")
        }
    }

    override fun getType(uri: Uri): String {
        requireSampleUri(uri)
        return "text/plain"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        requireSampleUri(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> "sample.txt"
                OpenableColumns.SIZE -> sampleFile.length()
                else -> throw IllegalArgumentException("Unsupported column: $column")
            }
        }.toTypedArray()
        return MatrixCursor(columns, 1).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requireSampleUri(uri)
        val fileMode = if (mode == "w") "wt" else mode
        return ParcelFileDescriptor.open(sampleFile, ParcelFileDescriptor.parseMode(fileMode))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Use openFile to write the sample file")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Use openFile to write the sample file")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Deleting the sample file is unsupported")
}
