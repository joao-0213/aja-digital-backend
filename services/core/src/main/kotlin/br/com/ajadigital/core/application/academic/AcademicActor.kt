package br.com.ajadigital.core.application.academic

import java.time.Instant


data class AcademicActor(val id: String, val role: String, val active: Boolean, val emailConfirmedAt: Instant?, val sessionTokenHash: String)
