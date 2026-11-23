package com.digitalsignage.tv.core.common.result

sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Error(val exception: AppException) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (AppException) -> Unit): AppResult<T> {
        if (this is Error) action(exception)
        return this
    }
}

sealed class AppException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    data class NetworkError(
        val httpCode: Int? = null,
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException(message, cause)

    data class TimeoutError(
        override val message: String = "Tiempo de espera agotado",
        override val cause: Throwable? = null
    ) : AppException(message, cause)

    data class ServerError(
        val code: Int,
        override val message: String,
        val errorBody: String? = null
    ) : AppException(message)

    data class UnauthorizedError(
        override val message: String = "Credenciales del dispositivo no autorizadas o revocadas"
    ) : AppException(message)

    data class DiskStorageError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException(message, cause)

    data class IntegrityVerificationError(
        val expectedHash: String,
        val computedHash: String,
        override val message: String = "La verificación SHA-256 del archivo no coincide"
    ) : AppException(message)

    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppException(message, cause)

    data class UnknownError(
        override val message: String = "Error inesperado del sistema",
        override val cause: Throwable? = null
    ) : AppException(message, cause)
}
