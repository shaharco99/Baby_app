package com.oryareach.wear

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.oryareach.core.watch.CommandResult
import com.oryareach.core.watch.WatchCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val ANSWER_TIMEOUT_MILLIS = 10_000L

/**
 * Sends [command] to the phone and waits for its answer.
 *
 * Null means the phone could not be reached or did not answer in time — the caller says so; the
 * command may or may not have landed, and the clocks, which the phone republishes after any
 * change, are what tells.
 */
internal suspend fun sendToPhone(context: Context, command: WatchCommand): CommandResult? {
    val client = Wearable.getMessageClient(context)
    val answer = CompletableDeferred<CommandResult>()
    // Listening before sending: the phone can answer faster than the send call returns.
    val listener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path == CommandResult.PATH) CommandResult.decode(event.data)?.let(answer::complete)
    }
    client.addListener(listener)
    return try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) return null
        nodes.forEach { client.sendMessage(it.id, WatchCommand.PATH, command.encode()).await() }
        withTimeoutOrNull(ANSWER_TIMEOUT_MILLIS) { answer.await() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } finally {
        client.removeListener(listener)
    }
}
