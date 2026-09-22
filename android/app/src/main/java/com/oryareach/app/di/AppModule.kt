package com.oryareach.app.di

import com.oryareach.app.lock.AutoLockController
import com.oryareach.app.notifications.AlarmFeedingReminderScheduler
import com.oryareach.app.notifications.AlarmPumpReminderScheduler
import com.oryareach.app.notifications.AlarmVitaminReminderScheduler
import com.oryareach.app.notifications.WorkManagerReminderScheduler
import com.oryareach.app.push.PushRegistrar
import com.oryareach.app.sync.WorkManagerSyncTrigger
import com.oryareach.core.calendar.CalendarEventSource
import com.oryareach.core.calendar.GoogleAccessTokenProvider
import com.oryareach.core.calendar.GoogleCalendarApi
import com.oryareach.core.calendar.GoogleCalendarSyncRepository
import com.oryareach.core.security.GoogleCalendarAuthManager
import com.oryareach.core.security.GoogleCalendarAuthManagerImpl
import com.oryareach.core.security.GoogleCalendarTokenStore
import com.oryareach.core.settings.FeedingReminderScheduler
import com.oryareach.core.settings.PumpReminderScheduler
import com.oryareach.core.settings.VitaminReminderScheduler
import com.oryareach.core.settings.ReminderScheduler
import com.oryareach.core.settings.SettingsPreferences
import com.oryareach.core.database.DatabaseFactory
import com.oryareach.core.database.DatabasePassphrase
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.reminder.FeedingReminderRefresher
import com.oryareach.core.database.reminder.PumpReminderRefresher
import com.oryareach.core.database.reminder.VitaminReminderRefresher
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.CycleEntryRepository
import com.oryareach.core.database.repository.CycleRepository
import com.oryareach.core.database.repository.DocumentRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.database.repository.PumpSessionRepository
import com.oryareach.core.database.repository.VitaminDoseRepository
import com.oryareach.core.database.repository.FolderRepository
import com.oryareach.core.database.repository.ConflictRepository
import com.oryareach.core.database.repository.ImportantDateRepository
import com.oryareach.core.database.repository.SearchRepository
import com.oryareach.core.database.repository.ShoppingItemRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.database.sync.RoomSyncStore
import com.oryareach.core.network.di.pushDeviceIdQualifier
import com.oryareach.core.network.di.workspaceIdQualifier
import com.oryareach.core.security.KeystoreDatabasePassphrase
import com.oryareach.core.security.LocalDataWiper
import com.oryareach.core.sync.RecordCodec
import com.oryareach.core.sync.SyncEngine
import com.oryareach.core.sync.SyncStore
import com.oryareach.core.sync.SyncTrigger
import com.oryareach.core.security.DeviceIdentity
import com.oryareach.core.sync.WorkspaceKeyProvider
import com.oryareach.core.update.ReleaseChecker
import com.oryareach.core.update.UpdateDownloader
import com.oryareach.core.update.UpdateInstaller
import com.oryareach.core.update.UpdateState
import com.oryareach.core.update.VersionManager
import com.oryareach.feature.auth.AuthViewModel
import com.oryareach.feature.auth.ResetPasswordViewModel
import com.oryareach.feature.pairing.PairingViewModel
import com.oryareach.feature.tasks.TasksViewModel
import com.oryareach.feature.cycle.CycleViewModel
import com.oryareach.feature.feeding.FeedingViewModel
import com.oryareach.feature.pumping.PumpingViewModel
import com.oryareach.feature.update.UpdateViewModel
import com.oryareach.feature.shopping.ShoppingViewModel
import com.oryareach.feature.home.HomeViewModel
import com.oryareach.feature.folders.FoldersViewModel
import com.oryareach.feature.settings.SettingsViewModel
import com.oryareach.feature.search.SearchViewModel
import com.oryareach.feature.calendar.CalendarViewModel
import com.oryareach.feature.conflicts.ConflictsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Wiring for the sync stack.
 *
 * The workspace id and the workspace key are supplied as lambdas rather than values: neither
 * exists until the user has signed in and the device has been paired, and the key disappears
 * again when the app locks.
 *
 * Everything here is lazy. The database is not opened, and no network client is built, until
 * something actually needs them — so the app starts even when Supabase is unconfigured.
 */
/**
 * The workspace this device belongs to, for work that runs with no one looking.
 *
 * [SessionState] only knows while the app is open, and a push wakes a brand-new process with an
 * empty one. Everything on the background path has to fall back to the Keystore-sealed copy or
 * it quietly does nothing: the sync store drops what it pulled, and the reminder refreshers
 * return before touching the alarm — which is exactly how a woken phone kept ringing at the old
 * time while every other part of the chain reported success.
 *
 * One function rather than the same expression written out at each call site, because the first
 * three were fixed and the two that actually move the alarm were missed.
 */
private fun Scope.backgroundWorkspaceId(): String? =
    get<SessionState>().workspaceId ?: get<DeviceIdentity>().workspaceId

val appModule = module {

    single { SessionState() }
    single<com.oryareach.core.security.SessionController> { get<SessionState>() }
    single { SettingsPreferences(androidContext()) }
    single { AutoLockController(session = get(), preferences = get()) }
    single {
        com.oryareach.app.sync.ForegroundSyncController(
            session = get(),
            poll = { com.oryareach.app.sync.SyncWorker.pollNow(androidContext()) },
            refreshReminders = {
                get<FeedingReminderRefresher>().refresh()
                get<PumpReminderRefresher>().refresh()
                get<VitaminReminderRefresher>().refresh()
            },
        )
    }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext()) }
    single<FeedingReminderScheduler> { AlarmFeedingReminderScheduler(androidContext()) }
    single<PumpReminderScheduler> { AlarmPumpReminderScheduler(androidContext()) }
    single<VitaminReminderScheduler> { AlarmVitaminReminderScheduler(androidContext()) }

    // Consumed by :core:network, which must not depend on the session type.
    // The Keystore fallback matters here too: a push arrives with no open session, and a null
    // workspace id would make the pull a no-op.
    single(workspaceIdQualifier) {
        val scope = this
        { scope.backgroundWorkspaceId() }
    }

    single<DatabasePassphrase> { KeystoreDatabasePassphrase(androidContext()) }

    single { DatabaseFactory.create(androidContext(), get()) }
    single<LocalDataWiper> { RoomLocalDataWiper(androidContext(), get()) }
    single { get<OrYareachDatabase>().taskDao() }
    single { get<OrYareachDatabase>().menstrualCycleDao() }
    single { get<OrYareachDatabase>().cycleEntryDao() }
    single { get<OrYareachDatabase>().shoppingItemDao() }
    single { get<OrYareachDatabase>().importantDateDao() }
    single { get<OrYareachDatabase>().appSettingsDao() }
    single { get<OrYareachDatabase>().folderDao() }
    single { get<OrYareachDatabase>().documentDao() }
    single { get<OrYareachDatabase>().syncOperationDao() }
    single { get<OrYareachDatabase>().syncStateDao() }
    single { get<OrYareachDatabase>().cachedCalendarEventDao() }

    /**
     * The workspace key, with a Keystore-backed fallback for background work.
     *
     * [SessionState] holds the key only while the app is open and unlocked, which is correct for
     * everything on screen. It is not enough for sync: a push arrives while the app is closed,
     * and without a key the pulled records cannot be decrypted, so the reminder cannot be
     * re-derived and the alarm stays wrong — the bug this whole path exists to fix.
     *
     * The fallback reads the same Keystore-sealed copy the pairing screen already re-derives
     * from on every launch. It does not weaken the lock as much as it first appears: the
     * SQLCipher passphrase is likewise Keystore-sealed with no user authentication required
     * (see KeystoreDatabasePassphrase), so anything running as this app could already read the
     * database. The lock is, and always was, a lock on the UI rather than on the data at rest.
     * What it does change is that background sync now proceeds while the app is locked, which is
     * deliberate — see docs/architecture/012-push-wake-up.md.
     */
    single<WorkspaceKeyProvider> {
        val session = get<SessionState>()
        val identity = get<DeviceIdentity>()
        WorkspaceKeyProvider { session.keyProvider().current() ?: identity.workspaceKey() }
    }
    single { RecordCodec(keys = get()) }

    single<SyncStore> {
        RoomSyncStore(
            database = get(),
            codec = get(),
            // Same fallback, same reason: a closed app has no SessionState to read from.
            workspaceId = { backgroundWorkspaceId() },
        )
    }

    single { SyncEngine(store = get(), remote = get(), wakeUp = get()) }

    single { DeviceIdentity(get()) }

    // Push: waking the partner's device after a write, and being woken by theirs. The Supabase
    // half is bound in networkModule, which is the only module that can see the client.
    single(pushDeviceIdQualifier) { { get<DeviceIdentity>().pushDeviceId } }
    single { PushRegistrar(context = androidContext(), identity = get(), tokens = get()) }

    // Google Calendar (phase 1, docs/specs/03-google-calendar-integration.md) — entirely
    // separate from the workspace pairing/session above: a device-local Google credential that
    // never syncs to Supabase. See GoogleCalendarTokenStore's doc comment.
    single { GoogleCalendarTokenStore(androidContext()) }
    single<GoogleCalendarAuthManager> { GoogleCalendarAuthManagerImpl(androidContext(), get()) }
    // Same cross-module lambda seam as `workspaceIdQualifier` above: `:core:calendar` depends on
    // a token-supplying function, not on `:core:security`'s concrete auth manager.
    single<GoogleAccessTokenProvider> { GoogleAccessTokenProvider { get<GoogleCalendarAuthManager>().currentAccessToken() } }
    single<CalendarEventSource> { GoogleCalendarApi(get()) }
    single { GoogleCalendarSyncRepository(database = get(), source = get()) }

    single { VersionManager(androidContext()) }
    single { ReleaseChecker(versionManager = get()) }
    single { UpdateDownloader(androidContext()) }
    single { UpdateInstaller(androidContext()) }
    single { UpdateState(androidContext()) }
    viewModel { UpdateViewModel(checker = get(), downloader = get(), installer = get(), state = get()) }

    single<SyncTrigger> { WorkManagerSyncTrigger(androidContext()) }
    single { TaskRepository(database = get(), syncTrigger = get()) }
    single { CycleRepository(database = get(), syncTrigger = get()) }
    single { CycleEntryRepository(database = get(), syncTrigger = get()) }
    single { BabyRepository(database = get(), syncTrigger = get()) }
    single { FeedingEntryRepository(database = get(), syncTrigger = get(), reminders = get()) }
    single { PumpSessionRepository(database = get(), syncTrigger = get(), reminders = get()) }
    single { VitaminDoseRepository(database = get(), syncTrigger = get()) }
    single { ShoppingItemRepository(database = get(), syncTrigger = get()) }
    single { ImportantDateRepository(database = get(), syncTrigger = get()) }
    single { AppSettingsRepository(database = get(), syncTrigger = get()) }
    single { FolderRepository(database = get(), syncTrigger = get()) }
    single { DocumentRepository(database = get(), syncTrigger = get(), blobStore = get(), keys = get()) }
    single { SearchRepository(database = get()) }
    single {
        val scope = this
        FeedingReminderRefresher(
            babies = get(),
            feeds = get(),
            settings = get(),
            scheduler = get(),
            workspaceId = { scope.backgroundWorkspaceId() },
        )
    }
    single {
        val scope = this
        VitaminReminderRefresher(
            babies = get(),
            vitamins = get(),
            settings = get(),
            scheduler = get(),
            workspaceId = { scope.backgroundWorkspaceId() },
        )
    }
    single {
        val scope = this
        PumpReminderRefresher(
            sessions = get(),
            settings = get(),
            scheduler = get(),
            workspaceId = { scope.backgroundWorkspaceId() },
        )
    }
    single { ConflictRepository(database = get(), codec = get()) }

    viewModel { AuthViewModel(auth = get()) }
    viewModel { ResetPasswordViewModel(auth = get()) }
    viewModel {
        PairingViewModel(
            workspaces = get(),
            identity = get(),
            auth = get(),
            session = get(),
            localDataWiper = get(),
            onWorkspaceOpened = { workspaceId, key ->
                get<SessionState>().open(workspaceId, key)
                get<SyncTrigger>().syncNow()
            },
        )
    }
    viewModel {
        TasksViewModel(
            repository = get(),
            settingsRepository = get(),
            documents = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        CycleViewModel(
            repository = get(),
            entryRepository = get(),
            documents = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        FeedingViewModel(
            repository = get(),
            babyRepository = get(),
            settingsRepository = get(),
            vitaminRepository = get(),
            vitaminReminders = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        PumpingViewModel(
            repository = get(),
            settingsRepository = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        ShoppingViewModel(
            repository = get(),
            settingsRepository = get(),
            documents = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        HomeViewModel(
            settingsRepository = get(),
            babyRepository = get(),
            feedingRepository = get(),
            pumpRepository = get(),
            taskRepository = get(),
            shoppingRepository = get(),
            importantDateRepository = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        FoldersViewModel(
            repository = get(),
            documents = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        SettingsViewModel(
            preferences = get(),
            reminders = get(),
            identity = get(),
            session = get(),
            auth = get(),
            localDataWiper = get(),
            pushTokens = get(),
            googleCalendarAuth = get(),
            googleCalendarSync = get(),
            babies = get(),
            appSettings = get(),
            feedingReminders = get(),
            pumpReminders = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        SearchViewModel(
            repository = get(),
            workspaceId = { get<SessionState>().workspaceId },
            syncEngine = get(),
        )
    }
    viewModel {
        CalendarViewModel(
            tasks = get(),
            importantDates = get(),
            cycles = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
            googleCalendarSync = get(),
            settingsPreferences = get(),
            auth = get(),
        )
    }
    viewModel { ConflictsViewModel(repository = get()) }
}
