package app.hypertrophy.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.designsystem.component.PhoenixIgnitionButton
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import app.hypertrophy.feature.workout.BuilderExercise
import app.hypertrophy.feature.workout.WorkoutBuilderViewModel
import java.time.LocalDate

@Composable
fun WorkoutBuilderRoute(
    viewModel: WorkoutBuilderViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val builder by viewModel.builder.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val selected by viewModel.selectedExercise.collectAsStateWithLifecycle()
    val modelFile by viewModel.modelFile.collectAsStateWithLifecycle()

    WorkoutBuilderScreen(
        exercises = exercises,
        builder = builder,
        schedule = schedule,
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
        onSaveTemplate = viewModel::saveTemplate,
        onAssignDay = viewModel::assignDay,
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
    schedule: List<app.hypertrophy.core.data.local.ScheduledWorkoutEntity>,
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
    onSaveTemplate: (String) -> Unit,
    onAssignDay: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var search by remember { mutableStateOf("") }
    var activeMuscle by remember { mutableStateOf<String?>(null) }
    var activeEquipment by remember { mutableStateOf<String?>(null) }
    var templateTitle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        if (overdueCount > 0) {
            RescheduleBanner(overdueCount, onShiftWeek, onMergeToday)
            Spacer(modifier = Modifier.height(12.dp))
        }

        WeeklyStrip(schedule = schedule, onAssignDay = onAssignDay)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                onQueryChange(it)
            },
            label = { Text("Search exercises") },
            modifier = Modifier.fillMaxWidth(),
        )

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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(exercises, key = { it.id }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    onClick = { onExerciseClick(exercise) },
                    onAdd = { onAddExercise(exercise) },
                )
            }
        }

        if (builder.isNotEmpty()) {
            Text(
                text = "BUILDER",
                style = MaterialTheme.typography.labelLarge,
                color = colors.emberOrange,
            )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = templateTitle,
                    onValueChange = { templateTitle = it },
                    label = { Text("Template name") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                PhoenixIgnitionButton(
                    text = "Save",
                    onClick = {
                        onSaveTemplate(templateTitle)
                        templateTitle = ""
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
    schedule: List<app.hypertrophy.core.data.local.ScheduledWorkoutEntity>,
    onAssignDay: (Long, Boolean) -> Unit,
) {
    val colors = LocalPhoenixColors.current
    val today = remember { LocalDate.now().toEpochDay() }
    val days = remember(schedule) {
        if (schedule.isNotEmpty()) {
            schedule.sortedBy { it.scheduledDay }.take(7)
        } else {
            (0L..6L).map { offset ->
                app.hypertrophy.core.data.local.ScheduledWorkoutEntity(
                    id = "preview-$offset",
                    userId = "",
                    templateId = null,
                    title = "Assign",
                    scheduledDay = today + offset,
                    isRestDay = offset == 2L || offset == 5L,
                    createdAt = 0L,
                )
            }
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it.id }) { day ->
            val label = LocalDate.ofEpochDay(day.scheduledDay).dayOfWeek.name.take(3)
            val isRest = day.isRestDay
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRest) colors.elevated else colors.surface,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(76.dp)
                    .clickable { onAssignDay(day.scheduledDay, false) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = if (isRest) "REST" else "LIFT",
                        style = PhoenixMonoTextStyle.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = if (isRest) colors.solarGold else colors.textPrimary,
                    )
                }
            }
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
        modifier = Modifier.width(200.dp),
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
            Icon(
                Icons.Filled.Remove,
                contentDescription = "Decrease $label",
                tint = colors.textPrimary,
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
