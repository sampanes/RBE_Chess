package com.ratherbeembed.rbe_chess.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ratherbeembed.rbe_chess.chess.GameTextExport
import java.io.File

class GameExportStore(private val context: Context) {
    fun save(export: GameTextExport): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(export)
        } else {
            saveToAppExternalDocuments(export)
        }

    private fun saveToDownloads(export: GameTextExport): String {
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/RBE Chess"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, export.fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create export file")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(export.text.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open export file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return "$relativePath/${export.fileName}"
    }

    private fun saveToAppExternalDocuments(export: GameTextExport): String {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        check(dir.exists() || dir.mkdirs()) { "Could not create export directory" }
        val file = File(dir, export.fileName)
        file.writeText(export.text, Charsets.UTF_8)
        return file.absolutePath
    }
}
