package br.com.ajadigital.core.application.identity



class ProfileRevisionConflict(val currentRevision: Int) : RuntimeException()
