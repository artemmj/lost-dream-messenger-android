package com.example.messenger.data

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * DRF отвечает либо {"detail": "..."}, либо полями валидации {"phone": ["..."]}.
 * Текст 429 сервер отдаёт по-английски — показываем как есть, он и так объясняет срок.
 */
fun Throwable.displayMessage(): String {
    val http = this as? HttpException
    if (http == null) {
        return when (this) {
            is IOException -> "Нет соединения с сервером"
            else -> message ?: "Ошибка"
        }
    }

    val body = runCatching {
        http.response()?.errorBody()?.string()?.let { errorJson.parseToJsonElement(it) as? JsonObject }
    }.getOrNull()

    body?.get("detail")?.let { return (it as? JsonPrimitive)?.text() ?: "Ошибка (${http.code()})" }

    // Валидация полей: берём первое сообщение первого поля
    val fieldMessage = body?.values?.firstNotNullOfOrNull { value ->
        when (value) {
            is JsonArray -> value.firstOrNull()?.let { (it as? JsonPrimitive)?.text() }
            is JsonPrimitive -> value.text()
            else -> null
        }
    }
    return fieldMessage ?: when (http.code()) {
        401 -> "Сессия истекла, войдите заново"
        403 -> "Недостаточно прав"
        404 -> "Не найдено"
        else -> "Ошибка (${http.code()})"
    }
}

private fun JsonPrimitive.text(): String? = contentOrNull?.takeIf { it.isNotBlank() }
