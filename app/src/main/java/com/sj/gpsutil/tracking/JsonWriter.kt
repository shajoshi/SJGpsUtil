package com.sj.gpsutil.tracking

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class JsonWriter(outputStream: OutputStream) : TrackWriter {
    private val writer = BufferedWriter(OutputStreamWriter(outputStream))
    private var closed = false
    private var firstDataItem = true
    private var startMillis: Long? = null

    private val uuid = UUID.randomUUID().toString()
    private val localZone = ZoneId.systemDefault()
    private val timezoneOffsetMillis: Int = java.util.TimeZone.getDefault().rawOffset

    override fun writeHeader() {
        // Header is deferred until first sample so we can set times based on first point.
    }

    override fun appendSample(sample: TrackingSample) {
        if (closed) return

        if (startMillis == null) {
            startMillis = sample.timestampMillis
            val name = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(localZone)
                .format(Instant.ofEpochMilli(sample.timestampMillis))

            val utcTime = DateTimeFormatter.ISO_INSTANT
                .format(Instant.ofEpochMilli(sample.timestampMillis))
            val localTime = DateTimeFormatter.ISO_INSTANT
                .format(Instant.ofEpochMilli(sample.timestampMillis).atZone(localZone).toInstant())

            writer.write("{\n")
            writer.write("  \"gpslogger2path\": {\n")
            writer.write("    \"meta\": {\n")
            writer.write("      \"roundtrip\": false,\n")
            writer.write("      \"imported\": false,\n")
            writer.write("      \"commute\": false,\n")
            writer.write("      \"uuid\": \"$uuid\",\n")
            writer.write("      \"name\": \"$name\",\n")
            writer.write("      \"utctime\": \"$utcTime\",\n")
            writer.write("      \"localtime\": \"$localTime\",\n")
            writer.write("      \"timezoneoffset\": $timezoneOffsetMillis,\n")
            writer.write("      \"ts\": ${sample.timestampMillis}\n")
            writer.write("    },\n")
            writer.write("    \"data\": [\n")
            firstDataItem = true
        }

        val base = startMillis ?: sample.timestampMillis
        val tsOffset = (sample.timestampMillis - base).coerceAtLeast(0L)

        val sats = sample.satelliteCount ?: 0
        val acc = sample.accuracyMeters ?: 0f
        val course = sample.bearingDegrees ?: 0f
        val speed = sample.speedKmph ?: 0.0
        val alt = sample.altitudeMeters ?: 0.0

        if (!firstDataItem) {
            writer.write(",\n")
        }
        firstDataItem = false

        writer.write("      {\n")
        writer.write("        \"gps\": {\n")
        writer.write("          \"ts\": $tsOffset,\n")
        writer.write("          \"lat\": ${sample.latitude},\n")
        writer.write("          \"lon\": ${sample.longitude},\n")
        writer.write("          \"sats\": $sats,\n")
        writer.write("          \"acc\": $acc,\n")
        writer.write("          \"course\": $course,\n")
        writer.write("          \"speed\": $speed,\n")
        writer.write("          \"climbPPM\": 0,\n")
        writer.write("          \"climb\": 0,\n")
        writer.write("          \"salt\": $alt,\n")
        writer.write("          \"alt\": $alt\n")
        writer.write("        }\n")
        writer.write("      }")
        writer.flush()
    }

    override fun close(totalDistanceMeters: Double?) {
        if (closed) return

        if (startMillis == null) {
            writer.write("{\n  \"gpslogger2path\": {\n    \"meta\": {\n      \"uuid\": \"$uuid\"\n    },\n    \"data\": []\n  }\n}\n")
            writer.flush()
            writer.close()
            closed = true
            return
        }

        writer.write("\n    ]")
        totalDistanceMeters?.let { meters ->
            val km = meters / 1000.0
            writer.write(",\n    \"summary\": {\n")
            writer.write("      \"totalDistanceMeters\": ${"%.1f".format(meters)},\n")
            writer.write("      \"totalDistanceKm\": ${"%.3f".format(km)}\n")
            writer.write("    }")
        }
        writer.write("\n  }\n")
        writer.write("}\n")
        writer.flush()
        writer.close()
        closed = true
    }
}
