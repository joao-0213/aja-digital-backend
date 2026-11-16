package br.com.ajadigital.core.shared.adapters.`in`.web.requests

internal fun requestId(value: String): String = value.also { require(it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))) }
