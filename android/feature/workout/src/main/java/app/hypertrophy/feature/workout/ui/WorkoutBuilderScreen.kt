package app.hypertrophy.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.WorkoutTemplateEntity
import app.hypertrophy.core.data.plan.WorkoutPlanCodec
import app.hypertrophy.core.designsystem.component.PhoenixIgnitionButton
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import app.hypertrophy.feature.workout.BuilderExercise
import app.hypertrophy.feature.workout.WorkoutBuilderViewModel
import java.time.LocalDate

@Composable
fun WorkoutBuilderRoute(
    onStartWorkout: (String) -> Unit,
    viewModel: WorkoutBuilderViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val builder by viewModel.builder.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val selected by viewModel.selectedExercise.collectAsStateWithLifecycle()
    val modelFile by viewModel.modelFile.collectAsStateWithLifecycle()
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val editingTemplateId by viewModel.editingTemplateId.collectAsStateWithLifecycle()
    val startWorkoutId by viewModel.startWorkoutId.collectAsStateWithLifecycle()

    LaunchedEffect(startWorkoutId) {
        val workoutId = startWorkoutId ?: return@LaunchedEffect
        viewModel.consumeStartWorkout()
        onStartWorkout(workoutId)
    }

    WorkoutBuilderScreen(
        exercises = exercises,
        builder = builder,
        templates = templates,
        schedule = schedule,
        draftTitle = draftTitle,
        isEditingTemplate = editingTemplateId != null,
        overdueCount = banner?.overdueIds?.size ?: 0,
        onShiftWeek = viewModel::applyShiftWeekPlusOneDay,
        onMergeToday = viewModel::applyMergeKeyCompoundToday,
        onQueryChange = viewModel::setQuery,
        onMuscleFilter = viewModel::setMuscle,
        onEquipmentFilter = viewModel::setEquipment,
        onExerciseClick = viewModel::selectExercise,
        onAddExercise = viewModel::addExercise,
        onRemoveExercise = viewModel::removeExercise,
        onSetsChange = viewModel::updateSets,
        onRepsChange = viewModel::updateReps,
        onWeightChange = viewModel::updateWeight,
        onSubstitute = viewModel::substituteExercise,
        onTitleChange = viewModel::setDraftTitle,
        onNewDraft = viewModel::newDraft,
        onSaveTemplate = viewModel::saveTemplate,
        onStartNow = viewModel::startBuilderToday,
        onLoadTemplate = viewModel::loadTemplate,
        onStartTemplate = viewModel::startTemplateToday,
        onDeleteTemplate = viewModel::deleteTemplate,
        onScheduleDay = viewModel::scheduleDay,
    )

    selected?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            modelFile = modelFile,
            onDismiss = viewModel::clearSelection,
        )
    }
}

@Composable
fun WorkoutBuilderScreen(
    exercises: List<ExerciseEntity>,
    builder: List<BuilderExercise>,
    templates: List<WorkoutTemplateEntity>,
    schedule: List<ScheduledWorkoutEntity>,
    draftTitle: String,
    isEditingTemplate: Boolean,
    overdueCount: Int,
    onShiftWeek: () -> Unit,
    onMergeToday: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleFilter: (String?) -> Unit,
    onEquipmentFilter: (String?) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onAddExercise: (ExerciseEntity) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onSetsChange: (String, Int) -> Unit,
    onRepsChange: (String, Int) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    onSubstitute: (ExerciseEntity) -> Unit,
    onTitleChange: (String) -> Unit,
    onNewDraft: () -> Unit,
    onSaveTemplate: () -> Unit,
    onStartNow: () -> Unit,
    onLoadTemplate: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onScheduleDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var search by remember { mutableStateOf("") }
    var activeMuscle by remember { mutableStateOf<String?>(null) }
    var activeEquipment by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
    ) {
        if (overdueCount > 0) {
            item {
                RescheduleBanner(
                    overdueCount = overdueCount,
                    onShiftWeek = onShiftWeek,
                    onMergeToday = onMergeToday,
                )
            }
        }

        item {
            WeeklyStrip(schedule = schedule, onScheduleDay = onScheduleDay)
        }

        if (templates.isNotEmpty()) {
            item {
                TemplatesSection(
                    templates = templates,
                    onLoad = onLoadTemplate,
                    onStart = onStartTemplate,
                    onDelete = onDeleteTemplate,
                )
            }
        }

        item {
            BuilderSection(
                builder = builder,
                draftTitle = draftTitle,
                isEditingTemplate = isEditingTemplate,
                onTitleChange = onTitleChange,
                onNewDraft = onNewDraft,
                onSaveTemplate = onSaveTemplate,
                onStartNow = onStartNow,
                onRemoveExercise = onRemoveExercise,
                onSetsChange = onSetsChange,
                onRepsChange = onRepsChange,
                onWeightChange = onWeightChange,
                onSubstitute = onSubstitute,
            )
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    onQueryChange(it)
                },
                label = { Text("Search exercises") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { activeMuscle = null; onMuscleFilter(null) },
                        label = { Text("All muscles") },
                    )
                }
                items(ExerciseCatalog.muscleGroups) { muscle ->
                    AssistChip(
                        onClick = {
                            activeMuscle = if (activeMuscle == muscle) null else muscle
                            onMuscleFilter(activeMuscle)
                        },
                        label = { Text(muscle.replace('_', ' ')) },
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { activeEquipment = null; onEquipmentFilter(null) },
                        label = { Text("Any equipment") },
                    )
                }
                items(ExerciseCatalog.equipmentTypes) { equipment ->
                    AssistChip(
                        onClick = {
                            activeEquipment = if (activeEquipment == equipment) null else equipment
                            onEquipmentFilter(activeEquipment)
                        },
                        label = { Text(equipment) },
                    )
                }
            }
        }

        items(exercises, key = { it.id }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                onClick = { onExerciseClick(exercise) },
                onAdd = { onAddExercise(exercise) },
            )
        }
    }
}

@Composable
private fun RescheduleBanner(
    overdueCount: Int,
    onShiftWeek: () -> Unit,
    onMergeToday: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.elevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$overdueCount uncompleted session${if (overdueCount == 1) "" else "s"} from earlier this week",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShiftWeek) {
                    Text("Shift Week +1 Day", color = colors.emberOrange)
                }
                TextButton(onClick = onMergeToday) {
                    Text("Merge Key Compound Lifts Today", color = colors.solarGold)
                }
            }
        }
    }
}

@Composable
private fun WeeklyStrip(
    schedule: List<ScheduledWorkoutEntity>,
    onScheduleDay: (Long) -> Unit,
) {
    val colors = LocalPhoenixColors.current
    val today = remember { LocalDate.now() }
    val monday = remember(today) { today.minusDays((today.dayOfWeek.value - 1).toLong()) }
    val days = remember(monday) { (0L..6L).map { monday.plusDays(it) } }
    val byDay = remember(schedule) { schedule.groupBy { it.scheduledDay } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "THIS WEEK",
            style = MaterialTheme.typography.labelLarge,
            color = colors.emberOrange,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.toEpochDay() }) { day ->
                val entries = byDay[day.toEpochDay()].orEmpty()
                val primary = entries.firstOrNull()
                val isToday = day == today
                val isRest = primary?.isRestDay == true
                val isDone = primary?.isCompleted == true

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDone -> colors.solarGold.copy(alpha = 0.18f)
                            isRest -> colors.elevated
                            else -> colors.surface
                        },
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(92.dp)
                        .height(108.dp)
                        .clickable { onScheduleDay(day.toEpochDay()) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (isToday) "TODAY" else day.dayOfWeek.name.take(3),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) colors.solarGold else colors.textSecondary,
                        )
                        Text(
                            text = day.dayOfMonth.toString(),
                            style = PhoenixMonoTextStyle.copy(fontSize = 18.sp, lineHeight = 20.sp),
                            color = colors.textPrimary,
                        )
                        Text(
                            text = when {
                                primary == null -> "Assign"
                                isRest -> "Rest"
                                else -> primary.title
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRest) colors.solarGold else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                entries.size > 1 -> "+${entries.size - 1} more"
                                isDone -> "Completed"
                                primary == null -> "Tap to plan"
                                else -> "Tap to replace"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDone) colors.solarGold else colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            text = "Tap a day to schedule the draft. With no exercises, a tap marks rest.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun TemplatesSection(
    templates: List<WorkoutTemplateEntity>,
    onLoad: (String) -> Unit,
    onStart: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "TEMPLATES",
            style = MaterialTheme.typography.labelLarge,
            color = colors.emberOrange,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = { it.id }) { template ->
                val plan = remember(template.exercisesJson) {
                    WorkoutPlanCodec.decode(template.exercisesJson)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    modifier = Modifier.width(250.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = template.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${plan.size} exercises | ${plan.sumOf { it.sets }} sets",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onLoad(template.id) }) {
                                Text("LOAD", color = colors.solarGold)
                            }
                            TextButton(onClick = { onStart(template.id) }) {
                                Text("START", color = colors.emberOrange)
                            }
                            TextButton(onClick = { onDelete(template.id) }) {
                                Text("DELETE", color = colors.formErrorCrimson)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderSection(
    builder: List<BuilderExercise>,
    draftTitle: String,
    isEditingTemplate: Boolean,
    onTitleChange: (String) -> Unit,
    onNewDraft: () -> Unit,
    onSaveTemplate: () -> Unit,
    onStartNow: () -> Unit,
    onRemoveExercise: (String) -> Unit,
    onSetsChange: (String, Int) -> Unit,
    onRepsChange: (String, Int) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    onSubstitute: (ExerciseEntity) -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isEditingTemplate) "EDITING TEMPLATE" else "DRAFT SESSION",
                style = MaterialTheme.typography.labelLarge,
                color = colors.emberOrange,
                modifier = Modifier.weight(1f),
            )
            if (builder.isNotEmpty() || draftTitle.isNotBlank()) {
                TextButton(onClick = onNewDraft) {
                    Text("NEW", color = colors.textSecondary)
                }
            }
        }

        if (builder.isEmpty()) {
            Text(
                text = "Add exercises below to build a session. Save it as a template, start it now, or tap a day above.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@Column
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(builder, key = { it.exercise.id }) { item ->
                BuilderCard(
                    item = item,
                    onRemove = { onRemoveExercise(item.exercise.id) },
                    onSets = { onSetsChange(item.exercise.id, it) },
                    onReps = { onRepsChange(item.exercise.id, it) },
                    onWeight = { onWeightChange(item.exercise.id, it) },
                    onSubstitute = { onSubstitute(item.exercise) },
                )
            }
        }

        OutlinedTextField(
            value = draftTitle,
            onValueChange = onTitleChange,
            label = { Text("Session title") },
            placeholder = { Text("Custom Session") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onSaveTemplate,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text(
                    text = if (isEditingTemplate) "Update template" else "Save template",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
            }
            PhoenixIgnitionButton(
                text = "Start now",
                onClick = onStartNow,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseEntity,
    onClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${exercise.primaryMuscle.replace('_', ' ')} | ${exercise.equipment}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onAdd) {
                Text("Add", color = colors.emberOrange)
            }
        }
    }
}

@Composable
private fun BuilderCard(
    item: BuilderExercise,
    onRemove: () -> Unit,
    onSets: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onWeight: (Double) -> Unit,
    onSubstitute: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    var weightDraft by remember(item.exercise.id) {
        mutableStateOf(item.targetWeightKg.toString())
    }
    LaunchedEffect(item.targetWeightKg) {
        val parsed = weightDraft.toDoubleOrNull()
        if (parsed == null || kotlin.math.abs(parsed - item.targetWeightKg) > 0.001) {
            weightDraft = item.targetWeightKg.toString()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.elevated),
        modifier = Modifier.width(210.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.exercise.name, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            StepperRow("Sets", item.sets, onSets)
            StepperRow("Reps", item.reps, onReps)
            OutlinedTextField(
                value = weightDraft,
                onValueChange = {
                    weightDraft = it
                    it.toDoubleOrNull()?.let(onWeight)
                },
                label = { Text("Target kg") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSubstitute) {
                Text("1-Tap Substitute", color = colors.emberOrange)
            }
            TextButton(onClick = onRemove) {
                Text("Remove", color = colors.formErrorCrimson)
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit) {
    val colors = LocalPhoenixColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange(value - 1) },
            modifier = Modifier.size(48.dp),
        ) {
            Text(
                text = "\u2212",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }
        Text("$value", style = PhoenixMonoTextStyle.copy(fontSize = 16.sp, lineHeight = 20.sp), color = colors.textPrimary)
        IconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Increase $label",
                tint = colors.textPrimary,
            )
        }
    }
}
