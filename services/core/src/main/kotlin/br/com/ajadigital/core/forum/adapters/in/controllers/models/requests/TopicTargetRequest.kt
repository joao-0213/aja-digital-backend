package br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class TopicTargetRequest(val type: String, val id: String)
