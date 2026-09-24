package com.example.messenger.ui

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Сервер отдаёт ISO-8601 с смещением (`created_at.isoformat()`); на кривом значении
 * показываем пустую строку вместо падения кадра.
 */
private fun parse(iso: String?): ZonedDateTime? =
    iso?.let {
        runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
    }

fun formatTime(iso: String?): String =
    parse(iso)?.toLocalTime()?.format(TIME) ?: ""

/** В списке чатов: время сегодня — часы, иначе день недели или дату */
fun formatListStamp(iso: String?): String {
    val moment = parse(iso) ?: return ""
    val today = LocalDate.now()
    return when {
        moment.toLocalDate() == today -> moment.toLocalTime().format(TIME)
        moment.toLocalDate() == today.minusDays(1) -> "Вчера"
        moment.year == today.year ->
            moment.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " +
                moment.dayOfMonth + " " +
                moment.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

        else -> moment.toLocalDate().format(DATE)
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
