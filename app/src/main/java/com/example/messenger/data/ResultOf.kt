package com.example.messenger.data

import kotlinx.coroutines.CancellationException

/**
 * runCatching глотает CancellationException: отмена scope'а выглядела бы как
 * сетевая ошибка. Для сетевых вызовов нужен проброс отмены.
 */
inline fun <R> resultOf(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
