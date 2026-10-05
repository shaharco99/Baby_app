package com.oryareach.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService

/**
 * Woken by the Data Layer when the phone puts new times, even with the app closed, so the tile
 * moves with them. The manifest filter limits it to the timers path.
 */
class TimersListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(TimersTileService::class.java)
    }
}
