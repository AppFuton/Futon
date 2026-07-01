package io.github.landwarderer.futon.core.util

import android.os.SystemClock
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ForwardingSource
import okio.Source

class BandwidthLimitedSource(
    delegate: Source,
    private val limiter: BandwidthLimiter
) : ForwardingSource(delegate) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read > 0) {
            limiter.take(read)
        }
        return read
    }
}

class BandwidthLimiter(private val bytesPerSecondProvider: () -> Int) {
    private var lastTime = SystemClock.elapsedRealtime()
    private var availableBytes = 0L

    fun take(bytes: Long) {
        val limit = bytesPerSecondProvider()
        if (limit <= 0) return

        var delayMs = 0L
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastTime
            availableBytes += (elapsed * limit) / 1000
            if (availableBytes > limit) availableBytes = limit.toLong()
            lastTime = now

            availableBytes -= bytes
            if (availableBytes < 0) {
                delayMs = (-availableBytes * 1000) / limit
            }
        }

        if (delayMs > 0) {
            runBlocking {
                kotlinx.coroutines.delay(delayMs)
            }
            synchronized(this) {
                lastTime = SystemClock.elapsedRealtime()
            }
        }
    }
}
