package br.com.ajadigital.core

import org.springframework.test.web.servlet.get
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference


class MutableTestClock : Clock() {
    private val offset = AtomicReference(Duration.ZERO)
    private val frozenInstant = AtomicReference<Instant?>(null)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = frozenInstant.get() ?: Instant.now(Clock.systemUTC()).plus(offset.get())
    fun advance(duration: Duration) {
        if (frozenInstant.get() != null) frozenInstant.updateAndGet { it?.plus(duration) }
        else offset.updateAndGet { it.plus(duration) }
    }
    fun freeze() { frozenInstant.set(instant()) }
    fun freezeAt(value: Instant) { frozenInstant.set(value) }
    fun reset() { frozenInstant.set(null); offset.set(Duration.ZERO) }
}
