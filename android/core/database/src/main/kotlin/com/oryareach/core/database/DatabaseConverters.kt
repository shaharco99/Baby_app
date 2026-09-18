package com.oryareach.core.database

import androidx.room.TypeConverter
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FlowLevel
import com.oryareach.core.model.Mood
import com.oryareach.core.model.PainLevel
import com.oryareach.core.model.Priority
import com.oryareach.core.model.RecurrenceFrequency
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingStatus
import com.oryareach.core.model.Symptom
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.model.TaskCategory
import kotlinx.serialization.json.Json

/**
 * Enums are stored by name, not ordinal: an ordinal silently remaps every stored row if a
 * constant is ever inserted in the middle of a declaration.
 */
class DatabaseConverters {

    @TypeConverter fun priorityToString(value: Priority): String = value.name
    @TypeConverter fun stringToPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter fun assigneeToString(value: Assignee?): String? = value?.name
    @TypeConverter fun stringToAssignee(value: String?): Assignee? = value?.let(Assignee::valueOf)

    @TypeConverter fun taskCategoryToString(value: TaskCategory): String = value.name
    @TypeConverter fun stringToTaskCategory(value: String): TaskCategory = TaskCategory.valueOf(value)

    @TypeConverter fun syncStatusToString(value: SyncStatus): String = value.name
    @TypeConverter fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter fun entityTypeToString(value: EntityType): String = value.wireName
    @TypeConverter fun stringToEntityType(value: String): EntityType =
        requireNotNull(EntityType.fromWireName(value)) { "unknown entity type: $value" }

    @TypeConverter fun operationToString(value: SyncOperationType): String = value.name
    @TypeConverter fun stringToOperation(value: String): SyncOperationType =
        SyncOperationType.valueOf(value)

    @TypeConverter fun shoppingCategoryToString(value: ShoppingCategory): String = value.name
    @TypeConverter fun stringToShoppingCategory(value: String): ShoppingCategory =
        ShoppingCategory.valueOf(value)

    @TypeConverter fun shoppingStatusToString(value: ShoppingStatus): String = value.name
    @TypeConverter fun stringToShoppingStatus(value: String): ShoppingStatus =
        ShoppingStatus.valueOf(value)

    @TypeConverter fun alternativesToString(value: List<ShoppingAlternative>): String =
        Json.encodeToString(value)
    @TypeConverter fun stringToAlternatives(value: String): List<ShoppingAlternative> =
        Json.decodeFromString(value)

    @TypeConverter fun flowLevelToString(value: FlowLevel?): String? = value?.name
    @TypeConverter fun stringToFlowLevel(value: String?): FlowLevel? = value?.let(FlowLevel::valueOf)

    @TypeConverter fun painLevelToString(value: PainLevel?): String? = value?.name
    @TypeConverter fun stringToPainLevel(value: String?): PainLevel? = value?.let(PainLevel::valueOf)

    @TypeConverter fun symptomsToString(value: List<Symptom>): String = Json.encodeToString(value)
    @TypeConverter fun stringToSymptoms(value: String): List<Symptom> = Json.decodeFromString(value)

    @TypeConverter fun moodToString(value: List<Mood>): String = Json.encodeToString(value)
    @TypeConverter fun stringToMood(value: String): List<Mood> = Json.decodeFromString(value)

    @TypeConverter fun recurrenceFrequencyToString(value: RecurrenceFrequency?): String? = value?.name
    @TypeConverter fun stringToRecurrenceFrequency(value: String?): RecurrenceFrequency? =
        value?.let(RecurrenceFrequency::valueOf)

    @TypeConverter fun feedTypeToString(value: FeedType): String = value.name
    @TypeConverter fun stringToFeedType(value: String): FeedType = FeedType.valueOf(value)

    @TypeConverter fun tagsToString(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter fun stringToTags(value: String): List<String> = Json.decodeFromString(value)
}
