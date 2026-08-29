package com.oryareach.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.home.dailyMessageIndex
import com.oryareach.core.domain.pregnancy.PregnancyProgress
import com.oryareach.core.ui.theme.NightPalette
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    onNavigateToShopping: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        text?.let(actions::onImportJson)
    }

    Scaffold(modifier = modifier.fillMaxSize().safeDrawingPadding()) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = actions::onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                if (!uiState.hasDueDate) {
                    NoDueDateCard(actions = actions)
                } else {
                    MoonCountdown(uiState = uiState, actions = actions)

                    Text(
                        text = dailyMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    uiState.progress?.let { progress -> WeeklyInfoCard(progress = progress) }

                    BudgetSummaryCard(uiState = uiState, onClick = onNavigateToShopping)

                    if (uiState.openTaskCount > 0) {
                        OpenTasksCard(count = uiState.openTaskCount, onClick = onNavigateToTasks)
                    }

                    TextButton(onClick = actions::onEditDueDate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.home_edit_due_date))
                    }
                }

                TextButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !uiState.importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_import_from_web))
                }
            }
        }
    }

    uiState.importResult?.let { result ->
        AlertDialog(
            onDismissRequest = actions::onDismissImportResult,
            confirmButton = {
                TextButton(onClick = actions::onDismissImportResult) { Text(stringResource(R.string.home_pick_confirm)) }
            },
            title = {
                Text(
                    stringResource(
                        if (result is ImportResult.Success) R.string.home_import_done else R.string.home_import_failed,
                    ),
                )
            },
            text = {
                if (result is ImportResult.Success) {
                    Text(stringResource(R.string.home_import_summary, result.taskCount, result.shoppingCount, result.dateCount))
                } else {
                    Text(stringResource(R.string.home_import_failed_body))
                }
            },
        )
    }

    if (uiState.bookOfLoveVisible) {
        val tips = androidx.compose.ui.res.stringArrayResource(R.array.home_book_of_love_tips)
        AlertDialog(
            onDismissRequest = actions::onDismissBookOfLove,
            confirmButton = {
                TextButton(onClick = actions::onDismissBookOfLove) { Text(stringResource(R.string.home_pick_confirm)) }
            },
            icon = {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp).align(Alignment.Bottom),
                    )
                }
            },
            title = { Text(stringResource(R.string.home_book_of_love_title)) },
            text = { Text(tips.random()) },
        )
    }

    if (uiState.sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissSheet, sheetState = sheetState) {
            DueDateForm(uiState = uiState, actions = actions)
        }
    }

    if (uiState.datePickerVisible) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.editingLastPeriodDate?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = actions::onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { actions.onLastPeriodChange(it.toLocalDate()) }
                }) { Text(stringResource(R.string.home_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDatePicker) { Text(stringResource(R.string.home_pick_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun dailyMessage(): String {
    val messages = androidx.compose.ui.res.stringArrayResource(R.array.home_daily_messages)
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val index = dailyMessageIndex(today, messages.size).coerceIn(0, messages.size - 1)
    return messages[index]
}

@Composable
private fun NoDueDateCard(actions: HomeActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_no_due_date_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.home_no_due_date_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = actions::onEditDueDate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_save))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MoonCountdown(uiState: HomeUiState, actions: HomeActions) {
    val progress = uiState.progress ?: return
    val backgroundColor = androidx.compose.runtime.remember {
        androidx.compose.animation.Animatable(NightPalette.sky)
    }
    var glitchFrame by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor.value, RoundedCornerShape(28.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    scope.launch {
                        // Quick flicker between the two "story world" tones, then settle back —
                        // a Split Fiction nod (the game's sci-fi/fantasy split-screen worlds).
                        backgroundColor.animateTo(NightPalette.glitchWorldOne, tween(70))
                        backgroundColor.animateTo(NightPalette.glitchWorldTwo, tween(70))
                        backgroundColor.animateTo(NightPalette.glitchWorldOne, tween(70))
                        backgroundColor.animateTo(NightPalette.sky, tween(150))
                    }
                    scope.launch {
                        // The moon itself glitches — sliced horizontal bands jittering
                        // sideways plus an RGB channel split — before snapping back, the
                        // same "reality tearing" look the game cuts between its two worlds
                        // with, not just a color flicker.
                        repeat(GLITCH_FRAME_COUNT) { frame ->
                            glitchFrame = frame + 1
                            delay(GLITCH_FRAME_MS)
                        }
                        glitchFrame = 0
                    }
                    actions.onMoonLongPress()
                },
            )
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonCanvas(moonFraction = progress.moonFraction, glitchFrame = glitchFrame, modifier = Modifier.size(160.dp))

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (progress.hasArrived) {
                uiState.babyName?.let { stringResource(R.string.home_arrived_title_named, it) }
                    ?: stringResource(R.string.home_arrived_title)
            } else {
                stringResource(R.string.home_week_progress, progress.week, progress.dayOfWeek)
            },
            style = MaterialTheme.typography.titleLarge,
            color = NightPalette.text,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (progress.hasArrived) {
                stringResource(R.string.home_arrived_subtitle)
            } else if (progress.daysLeft == 1) {
                stringResource(R.string.home_days_left_one)
            } else {
                stringResource(R.string.home_days_left_other, progress.daysLeft)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = NightPalette.textMuted,
            textAlign = TextAlign.Center,
        )

        if (!progress.hasArrived) {
            WeeklyFruitAndAnimal(week = progress.week)
        }
    }
}

@Composable
private fun WeeklyFruitAndAnimal(week: Int) {
    val fruitNames = androidx.compose.ui.res.stringArrayResource(R.array.home_weekly_fruit_names)
    val fruitEmoji = androidx.compose.ui.res.stringArrayResource(R.array.home_weekly_fruit_emoji)
    val animalNames = androidx.compose.ui.res.stringArrayResource(R.array.home_weekly_animal_names)
    val animalEmoji = androidx.compose.ui.res.stringArrayResource(R.array.home_weekly_animal_emoji)
    val index = week - 4
    if (index !in fruitNames.indices) return

    Spacer(Modifier.height(10.dp))
    Text(
        text = "${fruitEmoji[index]} " +
            stringResource(R.string.home_fruit_size, fruitNames[index]) +
            "  •  ${animalEmoji[index]} " +
            stringResource(R.string.home_animal_like, animalNames[index]),
        style = MaterialTheme.typography.bodySmall,
        color = NightPalette.text,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MoonCanvas(moonFraction: Float, glitchFrame: Int = 0, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = size.minDimension / 200f
        val center = Offset(100f, 100f) * scale
        val radius = 76f * scale

        fun drawMoonBody(alpha: Float = 1f, tint: Color? = null) {
            drawCircle(color = NightPalette.moonDim.copy(alpha = alpha), radius = radius, center = center)

            val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = center, radius = radius)) }
            clipPath(path) {
                val fillY = (200f - moonFraction * 200f) * scale
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(NightPalette.glowStart, NightPalette.glowMid, NightPalette.glowEnd),
                        center = Offset(center.x, center.y * 0.85f),
                        radius = radius * 1.3f,
                    ),
                    topLeft = Offset(0f, fillY),
                    size = androidx.compose.ui.geometry.Size(size.width, (size.height - fillY).coerceAtLeast(0f)),
                    alpha = alpha,
                )
                if (tint != null) {
                    drawCircle(color = tint.copy(alpha = alpha * 0.4f), radius = radius, center = center, blendMode = BlendMode.Screen)
                }
            }
        }

        if (glitchFrame == 0) {
            drawMoonBody()
        } else {
            val random = kotlin.random.Random(glitchFrame * GLITCH_SEED_MULTIPLIER)
            val maxShift = 12.dp.toPx()

            // Chromatic-aberration ghosts either side of the real moon.
            translate(left = -maxShift * 0.6f) { drawMoonBody(alpha = 0.45f, tint = Color(0xFF00E5FF)) }
            translate(left = maxShift * 0.6f) { drawMoonBody(alpha = 0.45f, tint = Color(0xFFFF2ECC)) }

            // The real moon, torn into horizontal bands each jittering sideways.
            val bandCount = 6
            val bandHeight = size.height / bandCount
            repeat(bandCount) { band ->
                val dx = (random.nextFloat() - 0.5f) * maxShift * 2f
                clipRect(top = band * bandHeight, bottom = (band + 1) * bandHeight) {
                    translate(left = dx) { drawMoonBody() }
                }
            }
        }

        drawCircle(color = NightPalette.moonRim, radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
    }
}

private const val GLITCH_FRAME_COUNT = 9
private const val GLITCH_FRAME_MS = 50L
private const val GLITCH_SEED_MULTIPLIER = 7919

@Composable
private fun WeeklyInfoCard(progress: PregnancyProgress) {
    if (progress.hasArrived) return
    val info = androidx.compose.ui.res.stringArrayResource(R.array.home_weekly_info)
    val index = progress.week - 1
    if (index !in info.indices) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_weekly_info_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = info[index],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSummaryCard(uiState: HomeUiState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    R.string.home_budget_spent_of_estimated,
                    uiState.budgetSpent,
                    uiState.budgetEstimated,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.home_budget_paid_by_us, uiState.budgetSpentByUs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_budget_gifts, uiState.budgetSpentByOthers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenTasksCard(count: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_open_tasks, count), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DueDateForm(uiState: HomeUiState, actions: HomeActions) {
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = actions::onOpenDatePicker, modifier = Modifier.fillMaxWidth()) {
            Text(uiState.editingLastPeriodDate?.toString() ?: stringResource(R.string.home_due_date_field))
        }

        OutlinedTextField(
            value = uiState.editingBabyName,
            onValueChange = actions::onBabyNameChange,
            label = { Text(stringResource(R.string.home_baby_name_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.editingPartnerOneName,
            onValueChange = actions::onPartnerOneNameChange,
            label = { Text(stringResource(R.string.home_partner_one_name_field)) },
            placeholder = { Text(stringResource(R.string.home_partner_one_name_default)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.editingPartnerTwoName,
            onValueChange = actions::onPartnerTwoNameChange,
            label = { Text(stringResource(R.string.home_partner_two_name_field)) },
            placeholder = { Text(stringResource(R.string.home_partner_two_name_default)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = actions::onSubmit,
            enabled = uiState.canSubmitForm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_save))
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun LocalDate.toUtcMillis(): Long =
    Instant.parse("${this}T00:00:00Z").toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    OrYareachTheme {
        HomeScreen(
            uiState = HomeUiState(
                dueDate = LocalDate(2026, 12, 25),
                progress = PregnancyProgress(daysLeft = 60, week = 30, dayOfWeek = 3, hasArrived = false, moonFraction = 0.75f),
                openTaskCount = 3,
                budgetEstimated = 4000.0,
                budgetSpent = 1200.0,
            ),
            actions = NoopHomeActions,
        )
    }
}

private object NoopHomeActions : HomeActions {
    override fun onEditDueDate() = Unit
    override fun onDismissSheet() = Unit
    override fun onOpenDatePicker() = Unit
    override fun onDismissDatePicker() = Unit
    override fun onLastPeriodChange(value: LocalDate) = Unit
    override fun onBabyNameChange(value: String) = Unit
    override fun onPartnerOneNameChange(value: String) = Unit
    override fun onPartnerTwoNameChange(value: String) = Unit
    override fun onSubmit() = Unit
    override fun onImportJson(json: String) = Unit
    override fun onDismissImportResult() = Unit
    override fun onRefresh() = Unit
    override fun onMoonLongPress() = Unit
    override fun onDismissBookOfLove() = Unit
}
