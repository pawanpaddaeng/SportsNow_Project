package com.example.cpen321application.game

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

// games starting within this many minutes get a "Starts in 45 min" pill
private const val STARTS_SOON_MINUTES = 120L

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val hourFormat = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
private val hourAmPmFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val amPmFormat = DateTimeFormatter.ofPattern("a", Locale.getDefault())
private val dayTimeFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())

internal fun String.capitalized() = replaceFirstChar { it.titlecase(Locale.getDefault()) }

// a time from the server in the phone's time zone, null if it isn't an ISO 8601 time
internal fun parseZoned(text: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? = try {
    Instant.parse(text).atZone(zone)
} catch (e: DateTimeParseException) {
    null
}

internal fun parseInstant(text: String): Instant? = try {
    Instant.parse(text)
} catch (e: DateTimeParseException) {
    null
}

// "Fri, Oct 17" for a server time, or the text itself if it can't be read
internal fun formatDay(text: String): String = parseZoned(text)?.format(dayFormat) ?: text

// "Sat, Oct 18 · 3:00 PM" for a server time, or the text itself if it can't be read
internal fun formatDayTime(text: String): String = parseZoned(text)?.format(dayTimeFormat) ?: text

// "Full", "1 spot left" or "4 spots left"
fun spotsLeftLabel(spotsLeft: Int): String = when (spotsLeft) {
    0 -> "Full"
    1 -> "1 spot left"
    else -> "$spotsLeft spots left"
}

/*  "Fri, Oct 17 · 7:00 – 9:00 PM", "Sun, Oct 19 · 10:00 AM – 12:00 PM" or just the start
    when there is no end time. AM/PM is only repeated when it changes
 */
internal fun formatGameTime(start: ZonedDateTime, end: ZonedDateTime?): String {
    val startText = "${start.format(dayFormat)} · "
    return when {
        end == null -> startText + start.format(hourAmPmFormat)
        end.toLocalDate() != start.toLocalDate() ->
            "$startText${start.format(hourAmPmFormat)} – ${end.format(dayFormat)} · ${end.format(hourAmPmFormat)}"
        start.format(amPmFormat) == end.format(amPmFormat) ->
            "$startText${start.format(hourFormat)} – ${end.format(hourAmPmFormat)}"
        else -> "$startText${start.format(hourAmPmFormat)} – ${end.format(hourAmPmFormat)}"
    }
}

// "250 m" under a kilometre (to the nearest 50 m), otherwise "1.4 km"
internal fun formatDistance(metres: Int): String =
    if (metres < 1000) {
        "${((metres / 50.0).roundToInt() * 50).coerceAtLeast(50)} m"
    } else {
        String.format(Locale.getDefault(), "%.1f km", metres / 1000.0)
    }

// "Today · 5:30 PM", "Tomorrow · 9:00 AM" or "Sat, Oct 18 · 3:00 PM"
internal fun formatStartTime(start: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val startDay = start.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (startDay) {
        today -> "Today · ${start.atZone(zone).format(hourAmPmFormat)}"
        today.plusDays(1) -> "Tomorrow · ${start.atZone(zone).format(hourAmPmFormat)}"
        else -> start.atZone(zone).format(dayTimeFormat)
    }
}

// "Starts in 45 min" or "Starts in 1 h 30 min" for games starting soon, null otherwise
internal fun formatStartsIn(start: Instant, now: Instant): String? {
    val minutes = Duration.between(now, start).toMinutes()
    if (minutes < 0 || minutes > STARTS_SOON_MINUTES) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "Starts in $minutes min"
        rest == 0L -> "Starts in $hours h"
        else -> "Starts in $hours h $rest min"
    }
}

// "just now", "5 min ago", "2h ago", "yesterday" or "3 days ago"
internal fun formatRequestedAgo(createdAt: Instant, now: Instant): String {
    val minutes = Duration.between(createdAt, now).toMinutes().coerceAtLeast(0)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min ago"
        days < 1 -> "${hours}h ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}
