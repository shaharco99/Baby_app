package com.oryareach.app.watch

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.oryareach.core.watch.CommandResult
import com.oryareach.core.watch.WatchCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "WatchCommand"
private const val WORK_TIMEOUT_MILLIS = 15_000L

/**
 * Woken by the Data Layer when the watch sends a [WatchCommand], even with the app closed.
 *
 * Only a watch paired to this phone and signed with the same key as this app can message it —
 * that is the Data Layer's own rule, the same one that lets the watch receive the timers.
 *
 * `onMessageReceived` runs on a worker thread, and the service may be torn down as soon as it
 * returns, so the work is done inside it rather than handed to a scope that could be cut off.
 */
class WatchCommandService : WearableListenerService(), KoinComponent {

    private val handler: WatchCommandHandler by inject()

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchCommand.PATH) return
        val command = WatchCommand.decode(event.data) ?: return
        val node = event.sourceNodeId

        runBlocking(Dispatchers.IO) {
            val result = withTimeoutOrNull(WORK_TIMEOUT_MILLIS) { handler.handle(command) } ?: CommandResult.FAILED
            try {
                Wearable.getMessageClient(this@WatchCommandService)
                    .sendMessage(node, CommandResult.PATH, result.encode())
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Could not answer the watch: ${e.message}")
            }
        }
    }
}
