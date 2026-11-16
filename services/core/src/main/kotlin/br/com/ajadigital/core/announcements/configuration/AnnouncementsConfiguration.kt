package br.com.ajadigital.core.announcements.configuration

import br.com.ajadigital.core.announcements.domain.ports.`in`.CreateAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.GetAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.ListAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.ListCalendarEventsUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardUpcomingEventsUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.UpdateAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.WithdrawAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.announcements.domain.usecases.CreateAnnouncement
import br.com.ajadigital.core.announcements.domain.usecases.GetAnnouncement
import br.com.ajadigital.core.announcements.domain.usecases.ListAnnouncements
import br.com.ajadigital.core.announcements.domain.usecases.ListCalendarEvents
import br.com.ajadigital.core.announcements.domain.usecases.ListDashboardAnnouncements
import br.com.ajadigital.core.announcements.domain.usecases.ListDashboardUpcomingEvents
import br.com.ajadigital.core.announcements.domain.usecases.UpdateAnnouncement
import br.com.ajadigital.core.announcements.domain.usecases.WithdrawAnnouncement
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AnnouncementsConfiguration {
    @Bean
    fun createAnnouncement(
        store: AnnouncementStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CreateAnnouncementUseCase = CreateAnnouncement(store, mapper, idempotency, transactionRunner)

    @Bean
    fun updateAnnouncement(
        store: AnnouncementStore,
        transactionRunner: TransactionRunner,
    ): UpdateAnnouncementUseCase = UpdateAnnouncement(store, transactionRunner)

    @Bean
    fun withdrawAnnouncement(
        store: AnnouncementStore,
        transactionRunner: TransactionRunner,
    ): WithdrawAnnouncementUseCase = WithdrawAnnouncement(store, transactionRunner)

    @Bean
    fun getAnnouncement(store: AnnouncementStore): GetAnnouncementUseCase = GetAnnouncement(store)

    @Bean
    fun listAnnouncements(store: AnnouncementStore): ListAnnouncementsUseCase = ListAnnouncements(store)

    @Bean
    fun listCalendarEvents(store: AnnouncementStore): ListCalendarEventsUseCase = ListCalendarEvents(store)

    @Bean
    fun listDashboardAnnouncements(store: AnnouncementStore): ListDashboardAnnouncementsUseCase =
        ListDashboardAnnouncements(store)

    @Bean
    fun listDashboardUpcomingEvents(store: AnnouncementStore): ListDashboardUpcomingEventsUseCase =
        ListDashboardUpcomingEvents(store)
}
