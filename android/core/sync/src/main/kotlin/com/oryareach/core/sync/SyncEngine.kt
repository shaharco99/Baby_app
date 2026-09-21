package com.oryareach.core.sync

import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult

/**
 * Drains the local outbox to the server, then applies whatever the server has that is newer.
 *
 * Push runs before pull deliberately: pulling first would overwrite a local edit that has not
 * been sent yet, and the user would watch their own change disappear.
 *
 * The engine never resolves a conflict itself. A rejected write is marked and surfaced, so a
 * person decides which version survives — silently picking last-write-wins is how shared
 * edits get lost.
 */
class SyncEngine(
    private val store: SyncStore,
    private val remote: RecordRemoteDataSource,
    private val wakeUp: PartnerWakeUp = PartnerWakeUp.None,
    private val pushBatchSize: Int = DEFAULT_PUSH_BATCH,
    private val pullBatchSize: Int = DEFAULT_PULL_BATCH,
) {

    suspend fun sync(): AppResult<SyncOutcome> = try {
        val push = push()
        // Before the pull, and only when something actually landed on the server: the partner's
        // device should start syncing while this one is still finishing, and a cycle that
        // pushed nothing has nothing to wake anyone for.
        if (push.pushed > 0) wakeUp.wakePartners()
        val pull = pull()
        AppResult.Success(
            SyncOutcome(
                pushed = push.pushed,
                pulled = pull,
                conflicts = push.conflicts,
                failures = push.failures,
            ),
        )
    } catch (e: Exception) {
        // The whole cycle is best-effort: a failure leaves the outbox intact, so the next run
        // picks up exactly where this one stopped.
        AppResult.Failure(AppError.Unexpected(e.message ?: e::class.simpleName.orEmpty()))
    }

    private suspend fun push(): PushSummary {
        var pushed = 0
        var conflicts = 0
        var failures = 0

        while (true) {
            val batch = store.pendingChanges(pushBatchSize)
            if (batch.isEmpty()) break

            val results = remote.push(batch)

            for (result in results) {
                when (result) {
                    is PushResult.Applied -> {
                        store.markSynced(result.recordId, result.newVersion)
                        pushed++
                    }

                    is PushResult.Conflict -> {
                        store.markConflict(result.recordId, result.server)
                        conflicts++
                    }

                    is PushResult.Failed -> {
                        store.recordFailure(result.recordId, result.error)
                        failures++
                    }
                }
            }

            // Anything not applied stays queued, so a full batch of failures would otherwise
            // spin forever. Stop and let the next scheduled run retry with backoff.
            if (results.none { it is PushResult.Applied }) break
            if (batch.size < pushBatchSize) break
        }

        return PushSummary(pushed, conflicts, failures)
    }

    private suspend fun pull(): Int {
        var pulled = 0
        var cursor = store.pullCursor()

        while (true) {
            val batch = remote.pull(cursor, pullBatchSize)
            if (batch.isEmpty()) break

            store.applyRemote(batch)
            pulled += batch.size

            val newest = batch.maxOf { it.updatedAt }
            // A cursor that fails to advance means the server keeps returning the same page;
            // continuing would loop forever.
            if (cursor != null && newest <= cursor) break
            cursor = newest
            store.savePullCursor(newest)

            if (batch.size < pullBatchSize) break
        }

        return pulled
    }

    private data class PushSummary(val pushed: Int, val conflicts: Int, val failures: Int)

    private companion object {
        const val DEFAULT_PUSH_BATCH = 50
        const val DEFAULT_PULL_BATCH = 200
    }
}
