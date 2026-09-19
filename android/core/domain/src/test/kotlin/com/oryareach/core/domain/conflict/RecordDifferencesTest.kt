package com.oryareach.core.domain.conflict

import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import org.junit.Test

class RecordDifferencesTest {

    private val utc = TimeZone.UTC

    @Test
    fun `lists only the fields that differ, one line per side`() {
        val local = """{"id":"f1","feedType":"FORMULA","amountMl":22,"note":"0000"}"""
        val server = """{"id":"f1","feedType":"FORMULA","amountMl":11,"note":"0000"}"""

        val diff = recordDifferences(local, server, utc)

        diff.local shouldBe listOf("amount ml: 22")
        diff.server shouldBe listOf("amount ml: 11")
    }

    @Test
    fun `a field missing on one side reads as empty`() {
        val local = """{"id":"f1","note":"left early"}"""
        val server = """{"id":"f1"}"""

        val diff = recordDifferences(local, server, utc)

        diff.local shouldBe listOf("note: left early")
        diff.server shouldBe listOf("note: —")
    }

    @Test
    fun `epoch millis fields read as a date and time`() {
        val local = """{"endedAtEpochMillis":1789822800000}"""
        val server = """{"endedAtEpochMillis":null}"""

        val diff = recordDifferences(local, server, utc)

        diff.local shouldBe listOf("ended at: 19.09 13:00")
        diff.server shouldBe listOf("ended at: —")
    }

    @Test
    fun `booleans read as yes and no`() {
        val diff = recordDifferences("""{"hadUrine":true}""", """{"hadUrine":false}""", utc)

        diff.local shouldBe listOf("had urine: yes")
        diff.server shouldBe listOf("had urine: no")
    }

    @Test
    fun `identical records have no differences`() {
        val same = """{"id":"t1","title":"Pack the bag","done":false}"""

        val diff = recordDifferences(same, same, utc)

        diff.local shouldBe emptyList()
        diff.server shouldBe emptyList()
    }

    @Test
    fun `unparseable input degrades to no differences instead of throwing`() {
        val diff = recordDifferences("not json", """{"a":1}""", utc)

        diff.local shouldBe emptyList()
        diff.server shouldBe emptyList()
    }
}
