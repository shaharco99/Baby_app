package com.oryareach.core.watch

import io.kotest.matchers.shouldBe
import org.junit.Test

class WatchCommandTest {

    @Test
    fun `every command survives the wire`() {
        val all = listOf(
            WatchCommand.LogFeed,
            WatchCommand.PauseNursing,
            WatchCommand.ResumeNursing,
            WatchCommand.StopNursing,
        ) + NursingSide.entries.map { WatchCommand.StartNursing(it) }

        all.forEach { WatchCommand.decode(it.encode()) shouldBe it }
    }

    @Test
    fun `unknown bytes are not a command`() {
        WatchCommand.decode("delete-everything".toByteArray()) shouldBe null
        WatchCommand.decode("start-nursing:UP".toByteArray()) shouldBe null
        WatchCommand.decode(ByteArray(0)) shouldBe null
    }

    @Test
    fun `every result survives the wire`() {
        CommandResult.entries.forEach { CommandResult.decode(it.encode()) shouldBe it }
        CommandResult.decode("nope".toByteArray()) shouldBe null
    }
}
