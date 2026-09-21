package com.oryareach.core.sync

import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.SyncOperationType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    @Test
    fun `pushes queued changes and marks them synced`() = runTest {
        val store = FakeSyncStore(pending = mutableListOf(request("a"), request("b")))
        val remote = FakeRemote(
            pushHandler = { batch -> batch.map { PushResult.Applied(it.recordId, newVersion = 2) } },
        )

        val outcome = SyncEngine(store, remote).sync().shouldBeSuccess()

        outcome.pushed shouldBe 2
        outcome.conflicts shouldBe 0
        store.synced shouldBe mapOf("a" to 2, "b" to 2)
        store.pending.isEmpty() shouldBe true
    }

    @Test
    fun `wakes the partner's devices once something has actually been pushed`() = runTest {
        var woken = 0
        val store = FakeSyncStore(pending = mutableListOf(request("a")))
        val remote = FakeRemote(
            pushHandler = { batch -> batch.map { PushResult.Applied(it.recordId, 2) } },
        )

        SyncEngine(store, remote, wakeUp = { woken++ }).sync().shouldBeSuccess()

        woken shouldBe 1
    }

    @Test
    fun `a cycle that pushed nothing wakes nobody`() = runTest {
        var woken = 0
        val store = FakeSyncStore(pending = mutableListOf())
        val remote = FakeRemote(
            pullPages = mutableListOf(listOf(remoteRecord("a", version = 2, updatedAt = 100))),
        )

        SyncEngine(store, remote, wakeUp = { woken++ }).sync().shouldBeSuccess()

        woken shouldBe 0
    }

    @Test
    fun `a conflict is not something to wake the partner over`() = runTest {
        var woken = 0
        val store = FakeSyncStore(pending = mutableListOf(request("a")))
        val remote = FakeRemote(
            pushHandler = { batch ->
                batch.map { PushResult.Conflict(it.recordId, remoteRecord(it.recordId, 2, 100)) }
            },
        )

        SyncEngine(store, remote, wakeUp = { woken++ }).sync().shouldBeSuccess()

        woken shouldBe 0
    }

    @Test
    fun `pushes before pulling so a local edit is never overwritten`() = runTest {
        val store = FakeSyncStore(pending = mutableListOf(request("a")))
        val remote = FakeRemote(
            pushHandler = { batch -> batch.map { PushResult.Applied(it.recordId, 2) } },
            pullPages = mutableListOf(listOf(remoteRecord("a", version = 2, updatedAt = 100))),
        )

        SyncEngine(store, remote).sync().shouldBeSuccess()

        store.callOrder.first() shouldBe "push"
        assertTrue("pull must come after push", store.callOrder.indexOf("applyRemote") > 0)
    }

    @Test
    fun `a rejected write is marked as a conflict rather than resolved`() = runTest {
        val server = remoteRecord("a", version = 7, updatedAt = 500)
        val store = FakeSyncStore(pending = mutableListOf(request("a")))
        val remote = FakeRemote(
            pushHandler = { listOf(PushResult.Conflict("a", server)) },
        )

        val outcome = SyncEngine(store, remote).sync().shouldBeSuccess()

        outcome.conflicts shouldBe 1
        outcome.pushed shouldBe 0
        outcome.hasConflicts shouldBe true
        store.conflicts shouldBe mapOf("a" to server)
        // Crucially, nothing was silently overwritten.
        store.synced.isEmpty() shouldBe true
    }

    @Test
    fun `a conflict does not trigger a retry but a transient failure does`() = runTest {
        val conflicted = SyncOutcome(conflicts = 1)
        conflicted.shouldRetry shouldBe false

        val failed = SyncOutcome(failures = 1)
        failed.shouldRetry shouldBe true
    }

    @Test
    fun `a failed push stays queued for the next run`() = runTest {
        val store = FakeSyncStore(pending = mutableListOf(request("a")))
        val remote = FakeRemote(
            pushHandler = { listOf(PushResult.Failed("a", AppError.Network.Offline)) },
        )

        val outcome = SyncEngine(store, remote).sync().shouldBeSuccess()

        outcome.failures shouldBe 1
        outcome.shouldRetry shouldBe true
        store.failures shouldBe listOf("a")
        store.pending.map { it.recordId } shouldBe listOf("a")
    }

    @Test
    fun `a batch where nothing applied stops rather than spinning`() = runTest {
        // Every attempt fails and the operation stays queued; without a guard the loop would
        // request the same batch forever.
        val store = FakeSyncStore(pending = MutableList(50) { request("r$it") })
        val remote = FakeRemote(
            pushHandler = { batch -> batch.map { PushResult.Failed(it.recordId, AppError.Network.Offline) } },
        )

        SyncEngine(store, remote, pushBatchSize = 50).sync().shouldBeSuccess()

        remote.pushCalls shouldBe 1
    }

    @Test
    fun `pull applies remote records and advances the cursor`() = runTest {
        val store = FakeSyncStore()
        val remote = FakeRemote(
            pullPages = mutableListOf(
                listOf(
                    remoteRecord("x", version = 1, updatedAt = 100),
                    remoteRecord("y", version = 1, updatedAt = 250),
                ),
            ),
        )

        val outcome = SyncEngine(store, remote, pullBatchSize = 200).sync().shouldBeSuccess()

        outcome.pulled shouldBe 2
        store.applied.map { it.id } shouldBe listOf("x", "y")
        store.cursor shouldBe 250
    }

    @Test
    fun `pull pages until the server returns a short page`() = runTest {
        val store = FakeSyncStore()
        val remote = FakeRemote(
            pullPages = mutableListOf(
                listOf(remoteRecord("a", 1, 10), remoteRecord("b", 1, 20)),
                listOf(remoteRecord("c", 1, 30)),
            ),
        )

        val outcome = SyncEngine(store, remote, pullBatchSize = 2).sync().shouldBeSuccess()

        outcome.pulled shouldBe 3
        store.cursor shouldBe 30
    }

    @Test
    fun `a cursor that fails to advance stops the pull loop`() = runTest {
        val store = FakeSyncStore(cursor = 100)
        // A server that keeps returning the same page would loop forever without the guard.
        val stuck = listOf(remoteRecord("a", version = 1, updatedAt = 100))
        val remote = FakeRemote(pullPages = MutableList(10) { stuck })

        SyncEngine(store, remote, pullBatchSize = 1).sync().shouldBeSuccess()

        remote.pullCalls shouldBe 1
    }

    @Test
    fun `a thrown error leaves the outbox intact`() = runTest {
        val store = FakeSyncStore(pending = mutableListOf(request("a")))
        val remote = FakeRemote(pushHandler = { error("connection reset") })

        val result = SyncEngine(store, remote).sync()

        assertTrue("expected failure but was $result", result is AppResult.Failure)
        store.pending.map { it.recordId } shouldBe listOf("a")
        store.synced.isEmpty() shouldBe true
    }

    @Test
    fun `nothing to do is a successful no-op`() = runTest {
        val outcome = SyncEngine(FakeSyncStore(), FakeRemote()).sync().shouldBeSuccess()

        outcome shouldBe SyncOutcome()
        outcome.shouldRetry shouldBe false
    }

    // ---------------------------------------------------------------------

    private fun request(id: String) = PushRequest(
        recordId = id,
        entityType = EntityType.TASK,
        operation = SyncOperationType.UPDATE,
        ciphertext = byteArrayOf(1, 2, 3),
        baseVersion = 1,
        clientMutationId = "mut-$id",
    )

    private fun remoteRecord(id: String, version: Int, updatedAt: Long) = RemoteRecord(
        id = id,
        entityType = EntityType.TASK,
        ciphertext = byteArrayOf(9, 9),
        version = version,
        updatedAt = updatedAt,
        deletedAt = null,
    )

    private fun <T> AppResult<T>.shouldBeSuccess(): T {
        assertTrue("expected success but was $this", this is AppResult.Success)
        return (this as AppResult.Success).data
    }

    private class FakeSyncStore(
        val pending: MutableList<PushRequest> = mutableListOf(),
        var cursor: Long? = null,
    ) : SyncStore {
        val synced = mutableMapOf<String, Int>()
        val conflicts = mutableMapOf<String, RemoteRecord>()
        val failures = mutableListOf<String>()
        val applied = mutableListOf<RemoteRecord>()
        val callOrder = mutableListOf<String>()

        override suspend fun pendingChanges(limit: Int): List<PushRequest> {
            if (pending.isNotEmpty()) callOrder += "push"
            return pending.take(limit)
        }

        override suspend fun markSynced(recordId: String, version: Int) {
            synced[recordId] = version
            pending.removeAll { it.recordId == recordId }
        }

        override suspend fun markConflict(recordId: String, server: RemoteRecord) {
            conflicts[recordId] = server
            pending.removeAll { it.recordId == recordId }
        }

        override suspend fun recordFailure(recordId: String, error: AppError) {
            failures += recordId
        }

        override suspend fun applyRemote(records: List<RemoteRecord>) {
            callOrder += "applyRemote"
            applied += records
        }

        override suspend fun pullCursor(): Long? = cursor

        override suspend fun savePullCursor(cursor: Long) {
            this.cursor = cursor
        }
    }

    private class FakeRemote(
        private val pushHandler: (List<PushRequest>) -> List<PushResult> = { emptyList() },
        private val pullPages: MutableList<List<RemoteRecord>> = mutableListOf(),
    ) : RecordRemoteDataSource {
        var pushCalls = 0
        var pullCalls = 0

        override suspend fun push(requests: List<PushRequest>): List<PushResult> {
            pushCalls++
            return pushHandler(requests)
        }

        override suspend fun pull(since: Long?, limit: Int): List<RemoteRecord> {
            pullCalls++
            return if (pullPages.isEmpty()) emptyList() else pullPages.removeAt(0)
        }
    }
}
