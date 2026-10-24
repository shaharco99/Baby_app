package com.oryareach.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService
import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Woken by the Data Layer when the phone puts new times, even with the app closed, so the tile
 * and the watch-face complication move with them. The manifest filter limits it to the timers path.
 */
class TimersListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(TimersTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, FeedComplicationService::class.java))
            .requestUpdateAll()
    }
}
