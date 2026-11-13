package br.com.ajadigital.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class TopicReplyRequest(val body: String)
