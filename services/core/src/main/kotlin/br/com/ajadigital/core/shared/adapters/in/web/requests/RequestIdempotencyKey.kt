package br.com.ajadigital.core.shared.adapters.`in`.web.requests

internal fun requestIdempotencyKey(value: String?): String = value?.also { require(it.length in MIN_KEY_LENGTH..MAX_KEY_LENGTH) } ?: throw IllegalArgumentException()

private const val MIN_KEY_LENGTH = 8
private const val MAX_KEY_LENGTH = 128
