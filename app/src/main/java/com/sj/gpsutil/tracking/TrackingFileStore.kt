package com.sj.gpsutil.tracking

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.sj.gpsutil.data.TrackingSettings
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val KML_MIME_TYPE = "application/vnd.google-earth.kml+xml"

class TrackingFileStore(private val context: Context) {
    data class TrackFileHandle(
        val uri: Uri,
        val outputStream: OutputStream
    )

    fun createTrackOutputStream(settings: TrackingSettings): TrackFileHandle {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        val filename = "track_$timestamp.kml"
        val folderUri = settings.folderUri?.let { Uri.parse(it) }

        return if (folderUri != null) {
            createInFolder(folderUri, filename)
        } else {
            createInDownloads(filename)
        }
    }

    private fun createInDownloads(filename: String): TrackFileHandle {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, KML_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create file in Downloads")
        val outputStream = resolver.openOutputStream(uri)
            ?: error("Unable to open output stream for Downloads file")
        return TrackFileHandle(uri, outputStream)
    }

    private fun createInFolder(folderUri: Uri, filename: String): TrackFileHandle {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("Invalid folder Uri")
        val file = folder.createFile(KML_MIME_TYPE, filename)
            ?: error("Unable to create file in selected folder")
        val outputStream = context.contentResolver.openOutputStream(file.uri)
            ?: error("Unable to open output stream for folder file")
        return TrackFileHandle(file.uri, outputStream)
    }
}
