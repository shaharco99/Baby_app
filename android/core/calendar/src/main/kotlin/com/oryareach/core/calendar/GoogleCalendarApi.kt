package com.oryareach.core.calendar

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import java.net.URLEncoder

/**
 * `events.list`/`calendarList.list` over plain REST (see build.gradle.kts's doc comment for why
 * Ktor rather than the Google Java API client). Every request restricts `fields` to exactly
 * date+title data — nothing else is ever requested or parsed, per phase 1's free/busy-only
 * decision.
 */
class GoogleCalendarApi(
    private val tokenProvider: GoogleAccessTokenProvider,
    private val client: HttpClient = defaultClient(),
) : CalendarEventSource {

    override suspend fun fetchCalendarList(): Result<List<GoogleCalendarListEntry>> = runCatching {
        val token = requireToken()
        val response: CalendarListResponseDto = client.get("$BASE_URL/users/me/calendarList") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("minAccessRole", "reader")
            parameter("fields", "items(id,summary,primary)")
        }.body()

        response.items.map {
            GoogleCalendarListEntry(id = it.id, summary = it.summary.ifBlank { it.id }, primary = it.primary)
        }
    }

    override suspend fun fetchEvents(
        calendarIds: List<String>,
        from: LocalDate,
        to: LocalDate,
    ): Result<List<GoogleCalendarEvent>> = runCatching {
        val token = requireToken()
        val timeZone = TimeZone.currentSystemDefault()
        val timeMin = Instant.parse("${from}T00:00:00Z").toString()
        val timeMax = Instant.parse("${to.plusOneDay()}T00:00:00Z").toString()

        calendarIds.flatMap { calendarId ->
            val response: EventsResponseDto = client.get(
                "$BASE_URL/calendars/${calendarId.urlEncode()}/events",
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("singleEvents", "true")
                parameter("orderBy", "startTime")
                parameter("timeMin", timeMin)
                parameter("timeMax", timeMax)
                parameter("maxResults", "2500")
                parameter("fields", "items(id,summary,start,end)")
            }.body()

            response.items.mapNotNull { it.toDomain(calendarId, timeZone) }
        }
    }

    private suspend fun requireToken(): String =
        tokenProvider.currentAccessToken()
            ?: error("Not connected to Google Calendar — GoogleAccessTokenProvider returned no token")

    private companion object {
        const val BASE_URL = "https://www.googleapis.com/calendar/v3"

        fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

private fun LocalDate.plusOneDay(): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() + 1)

@Serializable
private data class CalendarListResponseDto(val items: List<CalendarListItemDto> = emptyList())

@Serializable
private data class CalendarListItemDto(
    val id: String,
    val summary: String = "",
    val primary: Boolean = false,
)

@Serializable
private data class EventsResponseDto(val items: List<EventDto> = emptyList())

@Serializable
private data class EventDto(
    val id: String,
    val summary: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
)

@Serializable
private data class EventDateTimeDto(
    val date: String? = null,
    val dateTime: String? = null,
)

private fun EventDto.toDomain(calendarId: String, timeZone: TimeZone): GoogleCalendarEvent? {
    val startDto = start ?: return null
    val endDto = end ?: startDto
    val allDay = startDto.date != null

    val (startAt, endAt) = if (allDay) {
        val startDate = LocalDate.parse(requireNotNull(startDto.date))
        val endDate = endDto.date?.let(LocalDate::parse) ?: startDate
        LocalDateTime(startDate, LocalTime(0, 0)) to LocalDateTime(endDate, LocalTime(0, 0))
    } else {
        val startInstant = startDto.dateTime?.let(Instant::parse) ?: return null
        val endInstant = endDto.dateTime?.let(Instant::parse) ?: startInstant
        startInstant.toLocalDateTime(timeZone) to endInstant.toLocalDateTime(timeZone)
    }

    return GoogleCalendarEvent(
        id = id,
        calendarId = calendarId,
        title = summary.orEmpty(),
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
    )
}
