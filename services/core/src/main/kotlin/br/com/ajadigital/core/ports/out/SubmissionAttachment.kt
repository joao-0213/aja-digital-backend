package br.com.ajadigital.core.ports.out



data class SubmissionAttachment(val kind: String, val name: String, val fileId: String? = null, val url: String? = null)
