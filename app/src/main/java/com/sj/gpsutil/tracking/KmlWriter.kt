package com.sj.gpsutil.tracking

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

class KmlWriter(outputStream: OutputStream) {
    private val writer = BufferedWriter(OutputStreamWriter(outputStream))
    private var closed = false

    fun writeHeader() {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        writer.newLine()
        writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
        writer.newLine()
        writer.write("<Document>")
        writer.newLine()
        writer.write("<name>SJGpsUtil Track</name>")
        writer.newLine()
        writer.flush()
    }

    fun appendSample(sample: TrackingSample) {
        val altitude = sample.altitudeMeters ?: 0.0
        val speed = sample.speedKmph?.let { "%.2f".format(it) } ?: ""
        val bearing = sample.bearingDegrees?.let { "%.1f".format(it) } ?: ""
        val accuracy = sample.accuracyMeters?.let { "%.1f".format(it) } ?: ""
        val verticalAccuracy = sample.verticalAccuracyMeters?.let { "%.1f".format(it) } ?: ""
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sample.timestampMillis))

        writer.write("<Placemark>")
        writer.newLine()
        writer.write("<name></name>")
        writer.newLine()
        writer.write("<ExtendedData>")
        writer.newLine()
        writer.write("<Data name=\"timestamp\"><value>$timestamp</value></Data>")
        writer.newLine()
        writer.write("<Data name=\"speedKmph\"><value>$speed</value></Data>")
        writer.newLine()
        writer.write("<Data name=\"bearingDegrees\"><value>$bearing</value></Data>")
        writer.newLine()
        writer.write("<Data name=\"accuracyMeters\"><value>$accuracy</value></Data>")
        writer.newLine()
        writer.write("<Data name=\"verticalAccuracyMeters\"><value>$verticalAccuracy</value></Data>")
        writer.newLine()
        writer.write("</ExtendedData>")
        writer.newLine()
        writer.write("<Point>")
        writer.newLine()
        writer.write("<coordinates>${sample.longitude},${sample.latitude},$altitude</coordinates>")
        writer.newLine()
        writer.write("</Point>")
        writer.newLine()
        writer.write("</Placemark>")
        writer.newLine()
        writer.flush()
    }

    fun close() {
        if (closed) return
        writer.write("</Document>")
        writer.newLine()
        writer.write("</kml>")
        writer.newLine()
        writer.flush()
        writer.close()
        closed = true
    }
}
