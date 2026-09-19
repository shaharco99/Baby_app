package com.oryareach.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.oryareach.app.di.SessionState
import com.oryareach.app.di.appModule
import com.oryareach.app.lock.AutoLockController
import com.oryareach.app.notifications.ReminderAlarms
import com.oryareach.core.database.reminder.FeedingReminderRefresher
import com.oryareach.core.database.reminder.PumpReminderRefresher
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
    private val feedingReminders: FeedingReminderRefresher by inject()
    private val pumpReminders: PumpReminderRefresher by inject()
    private val session: SessionState by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@TakesTwoApplication)
            modules(appModule, networkModule)
        }

        // A safety net for changes made on the other device while this one was idle. A sync
        // with no workspace open is a fast no-op, so scheduling this unconditionally is fine.
        SyncWorker.schedulePeriodic(this)

        // Tracks whole-app foreground/background, not any one Activity's lifecycle — a
        // rotation or multi-window change stops/restarts an Activity without the app actually
        // leaving the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLockController)

        ReminderAlarms.dropLegacyWork(this)

        // Re-derive the pending feed and pump alarms from the database each time a workspace
        // opens. Not at process start: the workspace is still locked then, so the refreshers
        // would find no workspace and do nothing. Off the main thread and never awaited.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            session.workspaceIdFlow.filterNotNull().collect {
                feedingReminders.refresh()
                pumpReminders.refresh()
            }
        }
    }
}
