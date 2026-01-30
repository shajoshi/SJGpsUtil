package com.sj.gpsutil.tracking

interface TrackWriter {
    fun writeHeader()
    fun appendSample(sample: TrackingSample)
    fun close(totalDistanceMeters: Double? = null)
}
