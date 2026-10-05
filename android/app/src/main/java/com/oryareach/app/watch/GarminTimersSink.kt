package com.oryareach.app.watch

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.oryareach.core.watch.WatchTimers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GarminTimers"
private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
private const val INIT_TIMEOUT_MILLIS = 10_000L

/**
 * The same clocks as the Wear OS watch, sent to the Connect IQ app in `garmin/` through Garmin
 * Connect. A message is the flat [WatchTimers.toLongs] map — timestamps only, as everywhere else.
 *
 * Quiet without Garmin Connect installed (most installs): nothing is initialised, and the SDK's
 * own "install Garmin Connect" dialog is off.
 */
class GarminTimersSink(private val context: Context) {
    private val connectIq: ConnectIQ by lazy { ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS) }
    private val app = IQApp(GARMIN_APP_ID)
    private val initLock = Mutex()
    private var ready = false

    suspend fun put(timers: WatchTimers) {
        if (!garminConnectInstalled() || !ensureReady()) return
        try {
            val devices: List<IQDevice> = connectIq.connectedDevices.orEmpty()
            val message = timers.toLongs()
            devices.forEach { device ->
                connectIq.sendMessage(device, app, message) { _, _, status ->
                    if (status != ConnectIQ.IQMessageStatus.SUCCESS) Log.i(TAG, "Garmin send: $status")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "No Garmin to update: ${e.message}")
        }
    }

    /** Initialises the SDK once per process; the binding needs the main thread. */
    private suspend fun ensureReady(): Boolean = initLock.withLock {
        if (ready) return@withLock true
        val result = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            connectIq.initialize(
                context,
                false,
                object : ConnectIQ.ConnectIQListener {
                    override fun onSdkReady() {
                        result.complete(true)
                    }

                    override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                        Log.i(TAG, "Garmin SDK unavailable: $status")
                        result.complete(false)
                    }

                    override fun onSdkShutDown() {
                        ready = false
                    }
                },
            )
        }
        ready = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { result.await() } ?: false
        ready
    }

    private fun garminConnectInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(GARMIN_CONNECT_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        /** garmin/manifest.xml's application id. */
        const val GARMIN_APP_ID = "f53d782328444403832deafca93e4bdc"
    }
}
