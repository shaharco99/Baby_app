package com.oryareach.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.oryareach.app.di.SessionState
import com.oryareach.app.di.appModule
import com.oryareach.app.lock.AutoLockController
import com.oryareach.app.notifications.ReminderAlarms
import com.oryareach.app.push.PushConfig
import com.oryareach.app.push.PushRegistrar
import com.oryareach.core.database.reminder.FeedingReminderRefresher
import com.oryareach.core.security.DeviceIdentity
import com.oryareach.core.database.reminder.PumpReminderRefresher
import com.oryareach.core.database.reminder.VitaminReminderRefresher
import com.oryareach.app.sync.ForegroundSyncController
import com.oryareach.app.sync.SyncWorker
import com.oryareach.core.network.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.logger.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class TakesTwoApplication : Application(), KoinComponent {

    private val autoLockController: AutoLockController by inject()
    private val foregroundSync: ForegroundSyncController by inject()
    private val feedingReminders: FeedingReminderRefresher by inject()
    private val pumpReminders: PumpReminderRefresher by inject()
    private val vitaminReminders: VitaminReminderRefresher by inject()
    private val session: SessionState by inject()
    private val pushRegistrar: PushRegistrar by inject()
    private val identity: DeviceIdentity by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@TakesTwoApplication)
            modules(appModule, networkModule)
        }

        // Brought up before anything registers a token. A no-op when this build has no Firebase
        // project configured, which is the normal state of a fork or a fresh clone.
        PushConfig.initialize(this)

        // A safety net for changes made on the other device while this one was idle. A sync
        // with no workspace open is a fast no-op, so scheduling this unconditionally is fine.
        SyncWorker.schedulePeriodic(this)

        // Tracks whole-app foreground/background, not any one Activity's lifecycle — a
        // rotation or multi-window change stops/restarts an Activity without the app actually
        // leaving the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLockController)
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundSync)

        ReminderAlarms.dropLegacyWork(this)

        // Re-derive the pending feed and pump alarms from the database each time a workspace
        // opens. Not at process start: the workspace is still locked then, so the refreshers
        // would find no workspace and do nothing. Off the main thread and never awaited.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            session.workspaceIdFlow.filterNotNull().collect { workspaceId ->
                // Persist which workspace this device belongs to, every time one opens.
                //
                // It used to be written only by the pairing flow, which a paired device never
                // runs again — so on an install that paired before background sync needed it,
                // `DeviceIdentity.workspaceId` stayed null forever. That is the value the
                // Keystore fallback in AppModule reads when SessionState is empty, so a
                // push-woken app pulled its partner's changes and then dropped them on the
                // floor: `RoomSyncStore.applyRemote` returns early without a workspace, the
                // reminder was re-derived from a database that had not changed, and the alarm
                // stayed wrong. Writing it here heals those installs on their next launch.
                identity.workspaceId = workspaceId

                feedingReminders.refresh()
                pumpReminders.refresh()
                vitaminReminders.refresh()
                // The same moment is when this device becomes wakeable: it now belongs to a
                // workspace, so the partner's phone has somewhere to send its wake-up.
                pushRegistrar.onWorkspaceOpened(workspaceId)
            }
        }
    }
}
