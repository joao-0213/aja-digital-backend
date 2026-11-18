package br.com.ajadigital.core.curriculum.domain.policies

import br.com.ajadigital.core.curriculum.domain.exceptions.UnknownPublication


object SubjectPublications {
    fun seenAfter(current: List<String>, requested: List<String>?, published: List<String>): List<String> {
        if (requested != null && !published.containsAll(requested)) throw UnknownPublication()
        return (current + requested.orEmpty()).distinct().sorted()
    }

    fun hasUpdates(published: List<String>, seen: List<String>): Boolean =
        (published - seen.toSet()).isNotEmpty()
}
