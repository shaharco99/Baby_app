package com.oryareach.core.domain.conflict

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/** What differs between the two versions of a conflicted record, as one line per field. */
data class RecordDifferences(val local: List<String>, val server: List<String>)

/**
 * Compares two serialized versions of the same record field by field.
 *
 * A conflict card that only shows each side's title is useless when the title is the one thing
 * both sides agree on — two edits of a feed's amount both read "0000" if that's the note. This is
 * what tells them apart.
 *
 * Field names come straight from the model and are only de-camelCased, not translated: the
 * database layer that calls this has no resources, and "amount ml: 22" is readable either way.
 */
fun recordDifferences(localJson: String, serverJson: String, zone: TimeZone): RecordDifferences {
    val local = parse(localJson) ?: return RecordDifferences(emptyList(), emptyList())
    val server = parse(serverJson) ?: return RecordDifferences(emptyList(), emptyList())

    val keys = (local.keys + server.keys).filterNot { it in IGNORED }.sorted()
    val differing = keys.filter { (local[it] ?: JsonNull) != (server[it] ?: JsonNull) }

    return RecordDifferences(
        local = differing.map { line(it, local[it], zone) },
        server = differing.map { line(it, server[it], zone) },
    )
}

private val IGNORED = setOf("id")

private fun parse(json: String): JsonObject? =
    runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()

private fun line(key: String, value: JsonElement?, zone: TimeZone): String {
    val isMoment = key.endsWith("EpochMillis")
    val label = key.removeSuffix("EpochMillis").humanized()
    return "$label: ${value.readable(isMoment, zone)}"
}

private fun JsonElement?.readable(isMoment: Boolean, zone: TimeZone): String {
    if (this == null || this is JsonNull) return EMPTY
    if (this !is JsonPrimitive) return toString()
    if (isMoment) longOrNull?.let { return momentLabel(it, zone) }
    booleanOrNull?.let { return if (it) "yes" else "no" }
    return content.ifBlank { EMPTY }
}

private const val EMPTY = "—"

private fun String.humanized(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").lowercase()

private fun momentLabel(epochMillis: Long, zone: TimeZone): String {
    val moment = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "%02d.%02d %02d:%02d".format(moment.day, moment.month.ordinal + 1, moment.hour, moment.minute)
}
