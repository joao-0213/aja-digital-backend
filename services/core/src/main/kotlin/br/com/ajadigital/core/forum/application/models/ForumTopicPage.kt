package br.com.ajadigital.core.forum.application.models

data class ForumTopicPage(val items: List<ForumTopicView>, val page: Int, val pageSize: Int, val total: Int)
