package br.com.ajadigital.core.shared.adapters.`in`.web.requests

internal fun requestIdempotencyKey(value: String?): String = value?.also { require(it.length in 8..128) } ?: throw IllegalArgumentException()
