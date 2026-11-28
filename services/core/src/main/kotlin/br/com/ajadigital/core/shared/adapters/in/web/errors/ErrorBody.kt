package br.com.ajadigital.core.shared.adapters.`in`.web.errors

import java.util.UUID

internal fun errorBody(code: String, message: String) = mapOf("error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()))
