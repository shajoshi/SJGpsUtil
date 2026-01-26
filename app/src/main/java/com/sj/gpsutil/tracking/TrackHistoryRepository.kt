package com.sj.gpsutil.tracking

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sj.gpsutil.data.TrackingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TrackHistoryRepository(private val context: Context) {
    suspend fun listTracks(settings: TrackingSettings): List<TrackFileInfo> = withContext(Dispatchers.IO) {
        val entries = collectEntries(settings)
        entries.sortedByDescending { it.lastModified }
            .map { entry ->
                TrackFileInfo(
                    name = entry.name,
                    timestampMillis = if (entry.lastModified > 0) entry.lastModified else System.currentTimeMillis(),
                    distanceKm = null,
                    extension = entry.extension,
                    uri = entry.documentFile?.uri,
                    filePath = entry.file?.absolutePath
                )
            }
    }

    fun resolveFolderLabel(settings: TrackingSettings): String {
        val folderUri = settings.folderUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (folderUri != null) {
            val doc = DocumentFile.fromTreeUri(context, folderUri)
            val displayPath = folderUri.toDisplayPath()
            val name = doc?.name
            return when {
                !displayPath.isNullOrBlank() -> displayPath
                !name.isNullOrBlank() -> name!!
                else -> folderUri.toString()
            }
        }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return downloads?.absolutePath ?: "Downloads"
    }

    private fun Uri.toDisplayPath(): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(this)
            val parts = docId.split(":", limit = 2)
            val type = parts.firstOrNull() ?: return null
            val relativePath = parts.getOrNull(1)
            val base = when {
                type.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory().absolutePath
                else -> null
            } ?: return null
            if (relativePath.isNullOrBlank()) base else "$base/${relativePath.trimStart('/')}"
        }.getOrNull()
    }

    private fun collectEntries(settings: TrackingSettings): List<FileEntry> {
        val folderUri = settings.folderUri?.let { Uri.parse(it) }
        return if (folderUri != null) {
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
            folder.listFiles()
                .filter { it.isFile && it.name.isTrackFileName() }
                .map { doc ->
                    FileEntry(
                        name = doc.name.orEmpty(),
                        extension = doc.name.orEmpty().substringAfterLast('.', "").lowercase(),
                        lastModified = doc.lastModified(),
                        documentFile = doc
                    )
                }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.listFiles()?.filter { it.isFile && it.name.isTrackFileName() }?.map { file ->
                FileEntry(
                    name = file.name,
                    extension = file.extension.lowercase(),
                    lastModified = file.lastModified(),
                    file = file
                )
            } ?: emptyList()
        }
    }

    private fun String?.isTrackFileName(): Boolean {
        if (this.isNullOrBlank()) return false
        val lower = lowercase()
        return lower.startsWith("track_") && (lower.endsWith(".kml") || lower.endsWith(".gpx") || lower.endsWith(".json"))
    }

    private fun computeDistanceKm(entry: FileEntry): Double? {
        val coords = when (entry.extension.lowercase()) {
            "kml" -> parseKml(entry)
            "gpx" -> parseGpx(entry)
            "json" -> parseJson(entry)
            else -> emptyList()
        }
        if (coords.size < 2) return 0.0
        var distanceMeters = 0.0
        for (i in 1 until coords.size) {
            distanceMeters += haversineMeters(coords[i - 1], coords[i])
        }
        return distanceMeters / 1000.0
    }

    private fun parseKml(entry: FileEntry): List<Pair<Double, Double>> {
        val text = entry.readText(context) ?: return emptyList()
        val coords = mutableListOf<Pair<Double, Double>>()
        val gxRegex = Regex("<gx:coord>(.*?)</gx:coord>")
        gxRegex.findAll(text).forEach { matchResult ->
            val parts = matchResult.groupValues[1].trim().split(" ")
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lat != null && lon != null) coords.add(lat to lon)
            }
        }
        val coordRegex = Regex("<coordinates>(.*?)</coordinates>", RegexOption.DOT_MATCHES_ALL)
        coordRegex.findAll(text).forEach { matchResult ->
            val block = matchResult.groupValues[1]
            block.split(Regex("\\s+")).forEach { coordinate ->
                val parts = coordinate.split(",")
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull()
                    val lat = parts[1].toDoubleOrNull()
                    if (lat != null && lon != null) coords.add(lat to lon)
                }
            }
        }
        return coords
    }

    private fun parseGpx(entry: FileEntry): List<Pair<Double, Double>> {
        val text = entry.readText(context) ?: return emptyList()
        val regex = Regex("""<trkpt[^>]*lat=\"([^\"]+)\"[^>]*lon=\"([^\"]+)\"""")
        return regex.findAll(text).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lon = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) lat to lon else null
        }.toList()
    }

    private fun parseJson(entry: FileEntry): List<Pair<Double, Double>> {
        val text = entry.readText(context) ?: return emptyList()
        return runCatching {
            val root = JSONObject(text)
            val dataArray = root.optJSONObject("gpslogger2path")?.optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until dataArray.length()) {
                    val gps = dataArray.optJSONObject(i)?.optJSONObject("gps") ?: continue
                    val lat = gps.optDouble("lat")
                    val lon = gps.optDouble("lon")
                    if (!lat.isNaN() && !lon.isNaN()) add(lat to lon)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun haversineMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(a.first)
        val lat2 = Math.toRadians(b.first)
        val deltaLat = Math.toRadians(b.first - a.first)
        val deltaLon = Math.toRadians(b.second - a.second)
        val sinLat = sin(deltaLat / 2).pow(2)
        val sinLon = sin(deltaLon / 2).pow(2)
        val cosLat = cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(sinLat + cosLat * sinLon), sqrt(1 - (sinLat + cosLat * sinLon)))
        return earthRadius * c
    }

    private fun FileEntry.readText(context: Context): String? {
        return when {
            documentFile != null -> context.contentResolver.openInputStream(documentFile.uri)?.use { it.readAllText() }
            file != null -> file.inputStream().use { it.readAllText() }
            else -> null
        }
    }

    private fun InputStream.readAllText(): String {
        val reader = BufferedReader(InputStreamReader(this))
        val builder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            builder.append(line).append('\n')
        }
        return builder.toString()
    }

    data class TrackFileInfo(
        val name: String,
        val timestampMillis: Long,
        val distanceKm: Double?,
        val extension: String,
        val uri: Uri? = null,
        val filePath: String? = null
    )

    private data class FileEntry(
        val name: String,
        val extension: String,
        val lastModified: Long,
        val documentFile: DocumentFile? = null,
        val file: File? = null
    )
}
