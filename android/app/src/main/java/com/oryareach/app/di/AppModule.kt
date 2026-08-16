package com.oryareach.app.di

import com.oryareach.app.lock.AutoLockController
import com.oryareach.app.notifications.WorkManagerReminderScheduler
import com.oryareach.app.sync.WorkManagerSyncTrigger
import com.oryareach.core.settings.ReminderScheduler
import com.oryareach.core.settings.SettingsPreferences
import com.oryareach.core.database.DatabaseFactory
import com.oryareach.core.database.DatabasePassphrase
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.CycleEntryRepository
import com.oryareach.core.database.repository.CycleRepository
import com.oryareach.core.database.repository.DocumentRepository
import com.oryareach.core.database.repository.FolderRepository
import com.oryareach.core.database.repository.ConflictRepository
import com.oryareach.core.database.repository.ImportantDateRepository
import com.oryareach.core.database.repository.SearchRepository
import com.oryareach.core.database.repository.ShoppingItemRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.database.sync.RoomSyncStore
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
import com.oryareach.feature.pairing.PairingViewModel
import com.oryareach.feature.tasks.TasksViewModel
import com.oryareach.feature.cycle.CycleViewModel
import com.oryareach.feature.update.UpdateViewModel
import com.oryareach.feature.shopping.ShoppingViewModel
import com.oryareach.feature.dates.DatesViewModel
import com.oryareach.feature.home.HomeViewModel
import com.oryareach.feature.folders.FoldersViewModel
import com.oryareach.feature.settings.SettingsViewModel
import com.oryareach.feature.search.SearchViewModel
import com.oryareach.feature.calendar.CalendarViewModel
import com.oryareach.feature.conflicts.ConflictsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.android.ext.koin.androidContext
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
val appModule = module {

    single { SessionState() }
    single<com.oryareach.core.security.SessionController> { get<SessionState>() }
    single { SettingsPreferences(androidContext()) }
    single { AutoLockController(session = get(), preferences = get()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext()) }

    // Consumed by :core:network, which must not depend on the session type.
    single(workspaceIdQualifier) { { get<SessionState>().workspaceId } }

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

    single<WorkspaceKeyProvider> { get<SessionState>().keyProvider() }
    single { RecordCodec(keys = get()) }

    single<SyncStore> {
        RoomSyncStore(
            database = get(),
            codec = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }

    single { SyncEngine(store = get(), remote = get()) }

    single { DeviceIdentity(get()) }

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
    single { ShoppingItemRepository(database = get(), syncTrigger = get()) }
    single { ImportantDateRepository(database = get(), syncTrigger = get()) }
    single { AppSettingsRepository(database = get(), syncTrigger = get()) }
    single { FolderRepository(database = get(), syncTrigger = get()) }
    single { DocumentRepository(database = get(), syncTrigger = get(), blobStore = get(), keys = get()) }
    single { SearchRepository(database = get()) }
    single { ConflictRepository(database = get(), codec = get()) }

    viewModel { AuthViewModel(auth = get()) }
    viewModel {
        PairingViewModel(
            workspaces = get(),
            identity = get(),
            onWorkspaceOpened = { workspaceId, key ->
                get<SessionState>().open(workspaceId, key)
                get<SyncTrigger>().syncNow()
            },
        )
    }
    viewModel {
        TasksViewModel(
            repository = get(),
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
        ShoppingViewModel(
            repository = get(),
            documents = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        DatesViewModel(
            repository = get(),
            auth = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        HomeViewModel(
            settingsRepository = get(),
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
        )
    }
    viewModel {
        SearchViewModel(
            repository = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel {
        CalendarViewModel(
            tasks = get(),
            importantDates = get(),
            cycles = get(),
            syncEngine = get(),
            workspaceId = { get<SessionState>().workspaceId },
        )
    }
    viewModel { ConflictsViewModel(repository = get()) }
}
