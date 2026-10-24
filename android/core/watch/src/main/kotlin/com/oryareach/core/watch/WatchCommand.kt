package com.oryareach.core.watch

/** Which breast. Mirrors the phone's own side choice; kept here so the watch need not know the phone's model. */
enum class NursingSide { LEFT, RIGHT, BOTH }

/**
 * What the watch asks the phone to do, sent as a Data Layer message on [PATH].
 *
 * The reverse of [WatchTimers], and held to the same rule: nothing from the workspace crosses —
 * these are verbs only. The phone does the writing through its own repositories, so sync, the
 * feed reminder, the widget and the partner's phone all follow exactly as if the phone had been
 * tapped.
 */
sealed interface WatchCommand {
    /** A bottle feed now, no amount: the amount is filled in later on the phone. */
    data object LogFeed : WatchCommand

    data class StartNursing(val side: NursingSide) : WatchCommand

    data object PauseNursing : WatchCommand

    data object ResumeNursing : WatchCommand

    data object StopNursing : WatchCommand

    fun encode(): ByteArray = when (this) {
        LogFeed -> LOG_FEED
        is StartNursing -> "$START_NURSING:${side.name}"
        PauseNursing -> PAUSE_NURSING
        ResumeNursing -> RESUME_NURSING
        StopNursing -> STOP_NURSING
    }.toByteArray(Charsets.UTF_8)

    companion object {
        /** The message path the phone listens on. */
        const val PATH = "/command"

        private const val LOG_FEED = "log-feed"
        private const val START_NURSING = "start-nursing"
        private const val PAUSE_NURSING = "pause-nursing"
        private const val RESUME_NURSING = "resume-nursing"
        private const val STOP_NURSING = "stop-nursing"

        /** Null for anything that is not a command this build knows — a newer watch, or noise. */
        fun decode(bytes: ByteArray): WatchCommand? {
            val text = bytes.toString(Charsets.UTF_8)
            return when {
                text == LOG_FEED -> LogFeed
                text == PAUSE_NURSING -> PauseNursing
                text == RESUME_NURSING -> ResumeNursing
                text == STOP_NURSING -> StopNursing
                text.startsWith("$START_NURSING:") -> {
                    val side = NursingSide.entries.firstOrNull { it.name == text.substringAfter(':') }
                    side?.let(::StartNursing)
                }
                else -> null
            }
        }
    }
}

/** The phone's answer to one [WatchCommand], sent back on [PATH] so the watch can say what happened. */
enum class CommandResult {
    DONE,

    /** No child born yet, or no workspace on this phone: there is nowhere to write the feed. */
    NOTHING_TO_LOG,

    /** Pause/resume/stop with no breastfeed running. */
    NOT_RUNNING,
    FAILED,
    ;

    fun encode(): ByteArray = name.toByteArray(Charsets.UTF_8)

    companion object {
        const val PATH = "/command-result"

        fun decode(bytes: ByteArray): CommandResult? =
            entries.firstOrNull { it.name == bytes.toString(Charsets.UTF_8) }
    }
}
