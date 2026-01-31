package com.sj.gpsutil.tracking

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

class KmlWriter(outputStream: OutputStream) : TrackWriter {
    private val writer = BufferedWriter(OutputStreamWriter(outputStream))
    private var closed = false
    private val trackEntries = StringBuilder()
    private val lineStringCoordinates = StringBuilder()

    override fun writeHeader() {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        writer.newLine()
        writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">")
        writer.newLine()
        writer.write("<Document>")
        writer.newLine()
        writer.write("<name>SJGpsUtil Track</name>")
        writer.newLine()
        writer.flush()
    }

    override fun appendSample(sample: TrackingSample) {
        val altitude = sample.altitudeMeters ?: 0.0
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sample.timestampMillis))

        val speed = sample.speedKmph?.let { "%.2f".format(it) } ?: ""
        val bearing = sample.bearingDegrees?.let { "%.1f".format(it) } ?: ""
        val accuracy = sample.accuracyMeters?.let { "%.1f".format(it) } ?: ""
        val verticalAccuracy = sample.verticalAccuracyMeters?.let { "%.1f".format(it) } ?: ""
        val accelXMean = sample.accelXMean?.let { "%.3f".format(it) } ?: ""
        val accelYMean = sample.accelYMean?.let { "%.3f".format(it) } ?: ""
        val accelZMean = sample.accelZMean?.let { "%.3f".format(it) } ?: ""
        val accelMagMax = sample.accelMagnitudeMax?.let { "%.3f".format(it) } ?: ""
        val accelRMS = sample.accelRMS?.let { "%.3f".format(it) } ?: ""

        trackEntries.append("<when>$timestamp</when>\n")
        trackEntries.append("<gx:coord>${sample.longitude} ${sample.latitude} $altitude</gx:coord>\n")

        writer.write("<Placemark>")
        writer.newLine()
        writer.write("<name></name>")
        writer.newLine()
        writer.write("<TimeStamp><when>$timestamp</when></TimeStamp>")
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
        if (sample.accelXMean != null) {
            writer.write("<Data name=\"accelXMean\"><value>$accelXMean</value></Data>")
            writer.newLine()
            writer.write("<Data name=\"accelYMean\"><value>$accelYMean</value></Data>")
            writer.newLine()
            writer.write("<Data name=\"accelZMean\"><value>$accelZMean</value></Data>")
            writer.newLine()
            writer.write("<Data name=\"accelMagnitudeMax\"><value>$accelMagMax</value></Data>")
            writer.newLine()
            writer.write("<Data name=\"accelRMS\"><value>$accelRMS</value></Data>")
            writer.newLine()
        }
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

        if (lineStringCoordinates.isNotEmpty()) {
            lineStringCoordinates.append(' ')
        }
        lineStringCoordinates.append("${sample.longitude},${sample.latitude},$altitude")
        writer.flush()
    }

    override fun close(totalDistanceMeters: Double?) {
        if (closed) return

        writer.write("<Placemark>")
        writer.newLine()
        writer.write("<name>Track</name>")
        writer.newLine()
        writer.write("<MultiGeometry>")
        writer.newLine()
        writer.write("<gx:Track>")
        writer.newLine()
        writer.write(trackEntries.toString())
        writer.write("</gx:Track>")
        writer.newLine()
        writer.write("<LineString>")
        writer.newLine()
        writer.write("<tessellate>1</tessellate>")
        writer.newLine()
        writer.write("<coordinates>${lineStringCoordinates}</coordinates>")
        writer.newLine()
        writer.write("</LineString>")
        writer.newLine()
        writer.write("</MultiGeometry>")
        writer.newLine()
        writer.write("</Placemark>")
        writer.newLine()

        totalDistanceMeters?.let { meters ->
            val km = meters / 1000.0
            writer.write("<Placemark>")
            writer.newLine()
            writer.write("<name>Summary</name>")
            writer.newLine()
            writer.write("<ExtendedData>")
            writer.newLine()
            writer.write("<Data name=\"totalDistanceMeters\"><value>${"%.1f".format(meters)}</value></Data>")
            writer.newLine()
            writer.write("<Data name=\"totalDistanceKm\"><value>${"%.3f".format(km)}</value></Data>")
            writer.newLine()
            writer.write("</ExtendedData>")
            writer.newLine()
            writer.write("</Placemark>")
            writer.newLine()
        }

        writer.write("</Document>")
        writer.newLine()
        writer.write("</kml>")
        writer.newLine()
        writer.flush()
        writer.close()
        closed = true
    }
}
