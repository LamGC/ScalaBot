package net.lamgc.scalabot.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.spi.FilterReply
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class StdOutFilterTest {

    @Test
    fun filterTest() {
        val filter = StdOutFilter()

        for (level in listOf(
            Level.ALL,
            Level.TRACE,
            Level.DEBUG,
            Level.INFO
        )) {
            val loggingEvent = mockk<LoggingEvent> {
                every { this@mockk.level }.returns(level)
            }
            assertEquals(FilterReply.ACCEPT, filter.decide(loggingEvent))
        }

        for (level in listOf(
            Level.WARN,
            Level.ERROR
        )) {
            val loggingEvent = mockk<LoggingEvent> {
                every { this@mockk.level }.returns(level)
            }
            assertEquals(FilterReply.DENY, filter.decide(loggingEvent))
        }
    }

}