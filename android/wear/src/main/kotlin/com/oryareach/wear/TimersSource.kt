package com.oryareach.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.oryareach.core.watch.WatchTimers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The phone's latest [WatchTimers], then every change to them.
 *
 * The Data Layer keeps the last item the phone put, on the watch, across restarts and while the
 * phone is out of range — so opening the app with the phone away still shows the last clocks,
 * ticking from the times they were given.
 *
 * Emits null until anything has ever arrived.
 */
internal fun watchTimers(context: Context): Flow<WatchTimers?> = callbackFlow {
    val client = Wearable.getDataClient(context)
    val listener = DataClient.OnDataChangedListener { events ->
        events.forEach { event ->
            if (event.dataItem.uri.path != WatchTimers.PATH) return@forEach
            trySend(if (event.type == DataEvent.TYPE_DELETED) null else event.dataItem.toTimers())
        }
    }
    client.addListener(listener)

    try {
        val items = client.dataItems.await()
        val stored = try {
            items.firstOrNull { it.uri.path == WatchTimers.PATH }?.toTimers()
        } finally {
            items.release()
        }
        if (stored != null) trySend(stored)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // No Data Layer on this watch: keep waiting rather than fail; nothing else to show.
    }

    awaitClose { client.removeListener(listener) }
}

private fun DataItem.toTimers(): WatchTimers = WatchTimers.fromDataMap(DataMapItem.fromDataItem(this).dataMap)
