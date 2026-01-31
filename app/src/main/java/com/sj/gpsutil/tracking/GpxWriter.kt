package com.sj.gpsutil.tracking

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

class GpxWriter(outputStream: OutputStream) : TrackWriter {
    private val writer = BufferedWriter(OutputStreamWriter(outputStream))
    private var closed = false

    override fun writeHeader() {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        writer.newLine()
        writer.write("<gpx version=\"1.1\" creator=\"Tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:sj=\"http://sj.gpsutil\">")
        writer.newLine()
        writer.write("<trk>")
        writer.newLine()
        writer.write("<name>Track</name>")
        writer.newLine()
        writer.write("<trkseg>")
        writer.newLine()
        writer.flush()
    }

    override fun appendSample(sample: TrackingSample) {
        val lat = sample.latitude
        val lon = sample.longitude
        val time = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sample.timestampMillis))
        val ele = sample.altitudeMeters

        writer.write("<trkpt lat=\"$lat\" lon=\"$lon\">\n")
        if (ele != null) {
            writer.write("<ele>$ele</ele>\n")
        }
        writer.write("<time>$time</time>\n")
        sample.featureDetected?.let { feature ->
            writer.write("<name>$feature</name>\n")
        }
        if (sample.accelXMean != null) {
            writer.write("<extensions>\n")
            writer.write("<sj:accel>\n")
            writer.write("<sj:xMean>${"%.3f".format(sample.accelXMean)}</sj:xMean>\n")
            writer.write("<sj:yMean>${"%.3f".format(sample.accelYMean)}</sj:yMean>\n")
            writer.write("<sj:zMean>${"%.3f".format(sample.accelZMean)}</sj:zMean>\n")
            writer.write("<sj:magMax>${"%.3f".format(sample.accelMagnitudeMax)}</sj:magMax>\n")
            writer.write("<sj:rms>${"%.3f".format(sample.accelRMS)}</sj:rms>\n")
            val styleId = when (sample.roadQuality) {
                "smooth" -> "smoothStyle"
                "average" -> "averageStyle"
                "rough" -> "roughStyle"
                else -> null
            }
            val styleColor = when (sample.roadQuality) {
                "smooth" -> "#00FF00"
                "average" -> "#FFA500"
                "rough" -> "#FF0000"
                else -> null
            }
            styleId?.let { writer.write("<sj:styleId>$it</sj:styleId>\n") }
            styleColor?.let { writer.write("<sj:color>$it</sj:color>\n") }
            sample.roadQuality?.let {
                writer.write("<sj:roadQuality>$it</sj:roadQuality>\n")
            }
            sample.featureDetected?.let {
                writer.write("<sj:featureDetected>$it</sj:featureDetected>\n")
            }
            sample.peakCount?.let {
                writer.write("<sj:peakCount>$it</sj:peakCount>\n")
            }
            sample.stdDev?.let {
                writer.write("<sj:stdDev>${"%.3f".format(it)}</sj:stdDev>\n")
            }
            writer.write("</sj:accel>\n")
            writer.write("</extensions>\n")
        }
        writer.write("</trkpt>")
        writer.newLine()
        writer.flush()
    }

    override fun close(totalDistanceMeters: Double?) {
        if (closed) return
        writer.write("</trkseg>")
        writer.newLine()

        totalDistanceMeters?.let { meters ->
            val km = meters / 1000.0
            writer.write("<extensions>\n")
            writer.write("<sj:totalDistanceMeters>${"%.1f".format(meters)}</sj:totalDistanceMeters>\n")
            writer.write("<sj:totalDistanceKm>${"%.3f".format(km)}</sj:totalDistanceKm>\n")
            writer.write("</extensions>")
            writer.newLine()
        }

        writer.write("</trk>")
        writer.newLine()
        writer.write("</gpx>")
        writer.newLine()
        writer.flush()
        writer.close()
        closed = true
    }
}
