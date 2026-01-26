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
        writer.write("<gpx version=\"1.1\" creator=\"Tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
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
        writer.write("</trkpt>")
        writer.newLine()
        writer.flush()
    }

    override fun close() {
        if (closed) return
        writer.write("</trkseg>")
        writer.newLine()
        writer.write("</trk>")
        writer.newLine()
        writer.write("</gpx>")
        writer.newLine()
        writer.flush()
        writer.close()
        closed = true
    }
}
