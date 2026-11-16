package br.com.ajadigital.core.shared.adapters.`in`.web.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class OrderItemRequest(val id: String, val expectedRevision: Int)
