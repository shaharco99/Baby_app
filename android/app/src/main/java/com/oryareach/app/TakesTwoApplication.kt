package com.oryareach.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.oryareach.app.di.appModule
import com.oryareach.app.lock.AutoLockController
import com.oryareach.core.database.reminder.FeedingReminderRefresher
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
import kotlinx.coroutines.launch

class TakesTwoApplication : Application(), KoinComponent {

    private val autoLockController: AutoLockController by inject()
    private val feedingReminders: FeedingReminderRefresher by inject()

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

        // Off the main thread and not awaited: nothing on screen depends on it, and a device
        // with no workspace open yet is a no-op that the next launch redoes.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { feedingReminders.refresh() }
    }
}
