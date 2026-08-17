package com.oryareach.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.oryareach.app.di.SessionState
import com.oryareach.app.lock.LockRoute
import com.oryareach.core.ui.nav.MoonNavItem
import com.oryareach.core.ui.nav.MoonNavigationDrawer
import com.oryareach.core.ui.nav.MoonTopBar
import kotlinx.coroutines.launch
import com.oryareach.feature.calendar.CalendarEffect
import com.oryareach.feature.calendar.CalendarScreen
import com.oryareach.feature.calendar.CalendarViewModel
import com.oryareach.feature.conflicts.ConflictsScreen
import com.oryareach.feature.conflicts.ConflictsViewModel
import com.oryareach.feature.search.SearchEffect
import com.oryareach.feature.search.SearchScreen
import com.oryareach.feature.search.SearchViewModel
import com.oryareach.feature.settings.SettingsEffect
import com.oryareach.feature.settings.SettingsScreen
import com.oryareach.feature.settings.SettingsViewModel
import com.oryareach.core.network.auth.AuthState
import com.oryareach.feature.auth.AuthEffect
import com.oryareach.feature.auth.AuthScreen
import com.oryareach.feature.auth.AuthViewModel
import com.oryareach.feature.cycle.CycleScreen
import com.oryareach.feature.cycle.CycleViewModel
import com.oryareach.feature.pairing.PairingEffect
import com.oryareach.feature.pairing.PairingScreen
import com.oryareach.feature.pairing.PairingViewModel
import com.oryareach.feature.tasks.TasksScreen
import com.oryareach.feature.tasks.TasksViewModel
import com.oryareach.feature.update.UpdateDialog
import com.oryareach.feature.update.UpdateEffect
import com.oryareach.feature.update.UpdateViewModel
import com.oryareach.feature.shopping.ShoppingScreen
import com.oryareach.feature.shopping.ShoppingViewModel
import com.oryareach.feature.home.HomeScreen
import com.oryareach.feature.home.HomeViewModel
import com.oryareach.feature.folders.FoldersScreen
import com.oryareach.feature.folders.FoldersViewModel
import androidx.compose.ui.platform.LocalContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Chooses the screen from what the device actually has, rather than from navigation history.
 *
 * Signed out shows auth; signed in without a workspace key shows pairing; both present shows
 * the app. Deriving it this way means the user cannot land back on a screen that no longer
 * applies by pressing back, and a sign-out anywhere unwinds correctly on its own.
 */
@Composable
fun SaharApp(authState: AuthState) {
    val session: SessionState = koinInject()
    val workspaceId by session.workspaceIdFlow.collectAsStateWithLifecycle()
    val locked by session.lockedFlow.collectAsStateWithLifecycle()

    when {
        authState == AuthState.Unknown -> Unit

        authState == AuthState.SignedOut -> AuthRoute()

        workspaceId == null -> PairingRoute()

        // Checked before the unlocked check below: a deliberate lock must not fall through to
        // pairing, which would silently re-derive the key from the device's own Keystore-sealed
        // copy with no prompt at all — see SessionState.lock()'s doc comment.
        locked -> LockRoute()

        !session.isUnlocked -> PairingRoute()

        else -> {
            HomeRoute()
            ConflictHost()
        }
    }

    UpdateHost()
}

/** Not modal like [UpdateHost]'s mandatory case — a stuck conflict is important but never
 * urgent enough to block the screen. Re-surfaces whenever the conflict count changes (a new
 * one arrives, or the list shrinks from a resolution) even if the user dismissed it before;
 * disappears entirely once [ConflictsViewModel.uiState] has no conflicts left. */
@Composable
private fun ConflictHost(viewModel: ConflictsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable(uiState.conflicts.size) { mutableStateOf(false) }

    if (uiState.hasConflicts && !dismissed) {
        ConflictsScreen(uiState = uiState, actions = viewModel, onDismiss = { dismissed = true })
    }
}

/**
 * Hosted at the app root, not per-screen: a mandatory update must be able to interrupt the
 * user regardless of which tab or auth state they are in.
 */
@Composable
private fun UpdateHost(viewModel: UpdateViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is UpdateEffect.OpenRelease ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))

                    is UpdateEffect.LaunchInstallConfirmation ->
                        context.startActivity(effect.intent)
                }
            }
        }
    }

    if (uiState.visible) {
        UpdateDialog(uiState = uiState, actions = viewModel)
    }
}

private enum class HomeTab { Home, Tasks, Shopping, Folders, Cycle, Calendar, Search, Settings }

/**
 * A plain tab switch, not `navigation-compose`: two peer screens with no back-stack semantics
 * between them don't need a `NavHost`. Real navigation arrives with folders/documents in a
 * later milestone, once there is something to navigate *into*.
 */
@Composable
private fun HomeRoute() {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var highlightId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeviceManagement by rememberSaveable { mutableStateOf(false) }
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MoonNavigationDrawer(
                headerTitle = stringResource(com.oryareach.core.ui.R.string.app_drawer_title),
                headerSubtitle = stringResource(com.oryareach.core.ui.R.string.app_drawer_subtitle),
                items = listOf(
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.home.R.string.home_title),
                        selectedIcon = Icons.Filled.Home,
                        unselectedIcon = Icons.Outlined.Home,
                        selected = tab == HomeTab.Home,
                        onClick = { tab = HomeTab.Home; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.tasks.R.string.tasks_title),
                        selectedIcon = Icons.Filled.CheckCircle,
                        unselectedIcon = Icons.Outlined.CheckCircle,
                        selected = tab == HomeTab.Tasks,
                        onClick = { tab = HomeTab.Tasks; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.shopping.R.string.shopping_title),
                        selectedIcon = Icons.Filled.ShoppingCart,
                        unselectedIcon = Icons.Outlined.ShoppingCart,
                        selected = tab == HomeTab.Shopping,
                        onClick = { tab = HomeTab.Shopping; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.folders.R.string.folders_title),
                        selectedIcon = Icons.Filled.Folder,
                        unselectedIcon = Icons.Outlined.Folder,
                        selected = tab == HomeTab.Folders,
                        onClick = { tab = HomeTab.Folders; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.cycle.R.string.cycle_title),
                        selectedIcon = Icons.Filled.WaterDrop,
                        unselectedIcon = Icons.Outlined.WaterDrop,
                        selected = tab == HomeTab.Cycle,
                        onClick = { tab = HomeTab.Cycle; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.calendar.R.string.calendar_title),
                        selectedIcon = Icons.Filled.DateRange,
                        unselectedIcon = Icons.Outlined.DateRange,
                        selected = tab == HomeTab.Calendar,
                        onClick = { tab = HomeTab.Calendar; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.search.R.string.search_title),
                        selectedIcon = Icons.Filled.Search,
                        unselectedIcon = Icons.Outlined.Search,
                        selected = tab == HomeTab.Search,
                        onClick = { tab = HomeTab.Search; drawerScope.launch { drawerState.close() } },
                    ),
                    MoonNavItem(
                        label = stringResource(com.oryareach.feature.settings.R.string.settings_title),
                        selectedIcon = Icons.Filled.Settings,
                        unselectedIcon = Icons.Outlined.Settings,
                        selected = tab == HomeTab.Settings,
                        onClick = { tab = HomeTab.Settings; drawerScope.launch { drawerState.close() } },
                    ),
                ),
            )
        },
    ) {
    Scaffold(
        topBar = {
            MoonTopBar(
                title = stringResource(com.oryareach.core.ui.R.string.app_drawer_title),
                onMenuClick = { drawerScope.launch { drawerState.open() } },
                menuContentDescription = stringResource(com.oryareach.core.ui.R.string.app_drawer_open),
            )
        },
    ) { padding ->
        when (tab) {
            HomeTab.Home -> HomeTabRoute(modifier = androidx.compose.ui.Modifier.padding(padding))
            HomeTab.Tasks -> TasksRoute(
                modifier = androidx.compose.ui.Modifier.padding(padding),
                highlightId = highlightId,
                onHighlightConsumed = { highlightId = null },
            )
            HomeTab.Shopping -> ShoppingRoute(
                modifier = androidx.compose.ui.Modifier.padding(padding),
                highlightId = highlightId,
                onHighlightConsumed = { highlightId = null },
            )
            HomeTab.Folders -> FoldersRoute(modifier = androidx.compose.ui.Modifier.padding(padding))
            HomeTab.Cycle -> CycleRoute(modifier = androidx.compose.ui.Modifier.padding(padding))
            HomeTab.Calendar -> CalendarRoute(
                modifier = androidx.compose.ui.Modifier.padding(padding),
                onNavigateToTab = { destination, recordId -> tab = destination; highlightId = recordId },
            )
            HomeTab.Search -> SearchRoute(
                modifier = androidx.compose.ui.Modifier.padding(padding),
                onNavigateToTab = { destination, recordId -> tab = destination; highlightId = recordId },
            )
            HomeTab.Settings -> if (showDeviceManagement) {
                DeviceManagementRoute(
                    onBack = { showDeviceManagement = false },
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                )
            } else {
                SettingsRoute(
                    onManageDevices = { showDeviceManagement = true },
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                )
            }
        }
    }
    }
}

@Composable
private fun TasksRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    viewModel: TasksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TasksScreen(
        uiState = uiState,
        actions = viewModel,
        modifier = modifier,
        highlightId = highlightId,
        onHighlightConsumed = onHighlightConsumed,
    )
}

@Composable
private fun HomeTabRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState = uiState, actions = viewModel, modifier = modifier)
}

@Composable
private fun ShoppingRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    viewModel: ShoppingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingScreen(
        uiState = uiState,
        actions = viewModel,
        modifier = modifier,
        highlightId = highlightId,
        onHighlightConsumed = onHighlightConsumed,
    )
}

@Composable
private fun FoldersRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: FoldersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FoldersScreen(uiState = uiState, actions = viewModel, modifier = modifier)
}

@Composable
private fun CycleRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: CycleViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CycleScreen(uiState = uiState, actions = viewModel, modifier = modifier)
}

@Composable
private fun CalendarRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onNavigateToTab: (HomeTab, String?) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is CalendarEffect.OpenEvent ->
                        // Google-sourced events have no entityType (no in-app record to open)
                        // and no in-app tab to route to — see CalendarEvent's doc comment.
                        effect.event.entityType?.let { onNavigateToTab(it.toHomeTab(), effect.event.recordId) }
                }
            }
        }
    }

    CalendarScreen(uiState = uiState, actions = viewModel, modifier = modifier)
}

@Composable
private fun SearchRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onNavigateToTab: (HomeTab, String?) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is SearchEffect.OpenResult ->
                        onNavigateToTab(effect.result.entityType.toHomeTab(), effect.result.recordId)
                }
            }
        }
    }

    SearchScreen(uiState = uiState, actions = viewModel, modifier = modifier)
}

/** Search results only switch to the tab that owns the record — there is no per-screen
 * "scroll to this id" support yet, so this gets the user to the right area, not the exact
 * item. A documented gap, not an oversight. */
private fun com.oryareach.core.model.EntityType.toHomeTab(): HomeTab = when (this) {
    com.oryareach.core.model.EntityType.TASK -> HomeTab.Tasks
    com.oryareach.core.model.EntityType.SHOPPING_ITEM -> HomeTab.Shopping
    com.oryareach.core.model.EntityType.IMPORTANT_DATE -> HomeTab.Calendar
    com.oryareach.core.model.EntityType.FOLDER, com.oryareach.core.model.EntityType.DOCUMENT -> HomeTab.Folders
    com.oryareach.core.model.EntityType.CYCLE, com.oryareach.core.model.EntityType.CYCLE_ENTRY -> HomeTab.Cycle
    com.oryareach.core.model.EntityType.SETTINGS -> HomeTab.Settings
}

@Composable
private fun SettingsRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onManageDevices: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // Google Calendar consent needs an intent-sender launch — same
    // `StartIntentSenderResult`-launcher shape `:core:scanner`'s `DocumentScanner` uses.
    val googleCalendarLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onGoogleCalendarResolutionResult(result.resultCode, result.data)
    }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SettingsEffect.NavigateToDeviceManagement -> onManageDevices()
                    SettingsEffect.RequestNotificationPermission -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // No runtime permission needed before Android 13.
                            viewModel.onNotificationPermissionResult(true)
                        }
                    }
                    is SettingsEffect.LaunchGoogleCalendarResolution -> {
                        googleCalendarLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(effect.intentSender).build(),
                        )
                    }
                }
            }
        }
    }

    val updateViewModel: UpdateViewModel = koinViewModel()
    SettingsScreen(
        uiState = uiState,
        actions = viewModel,
        modifier = modifier,
        footer = {
            androidx.compose.material3.OutlinedButton(
                onClick = updateViewModel::onCheckNow,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(com.oryareach.feature.update.R.string.settings_check_for_updates))
            }
        },
    )
}

/**
 * Reuses [PairingViewModel]'s already-live `Ready` stage rather than rebuilding device
 * approval: there is no `NavHost`, so this and [PairingRoute] both resolving `koinViewModel()`
 * for the same class within the same Activity return the identical instance.
 */
@Composable
private fun DeviceManagementRoute(
    onBack: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: PairingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.app_back))
            }
            Text(stringResource(com.oryareach.feature.settings.R.string.settings_manage_devices))
        }
        PairingScreen(uiState = uiState, actions = viewModel)
    }
}

@Composable
private fun AuthRoute(viewModel: AuthViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    // Recomposition is driven by the auth state flow; the effect exists so a
                    // future snackbar has somewhere to hang.
                    AuthEffect.SignedIn -> Unit
                }
            }
        }
    }

    AuthScreen(uiState = uiState, actions = viewModel)
}

@Composable
private fun PairingRoute(viewModel: PairingViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PairingEffect.Completed -> Unit
                }
            }
        }
    }

    PairingScreen(uiState = uiState, actions = viewModel)
}
