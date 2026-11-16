package br.com.ajadigital.core.announcements.configuration

import br.com.ajadigital.core.announcements.domain.ports.`in`.ManageAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.announcements.domain.usecases.ManageAnnouncements
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AnnouncementsConfiguration {
    @Bean
    fun manageAnnouncements(
        store: AnnouncementStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): ManageAnnouncementsUseCase = ManageAnnouncements(store, mapper, idempotency, transactionRunner)
}
