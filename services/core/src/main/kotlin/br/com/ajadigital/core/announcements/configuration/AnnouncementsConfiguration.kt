package br.com.ajadigital.core.announcements.configuration

import br.com.ajadigital.core.announcements.application.ports.`in`.CreateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.GetAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListCalendarEventsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardUpcomingEventsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.UpdateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.WithdrawAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.usecases.CreateAnnouncementUseCase
import br.com.ajadigital.core.announcements.application.usecases.GetAnnouncementUseCase
import br.com.ajadigital.core.announcements.application.usecases.ListAnnouncementsUseCase
import br.com.ajadigital.core.announcements.application.usecases.ListCalendarEventsUseCase
import br.com.ajadigital.core.announcements.application.usecases.ListDashboardAnnouncementsUseCase
import br.com.ajadigital.core.announcements.application.usecases.ListDashboardUpcomingEventsUseCase
import br.com.ajadigital.core.announcements.application.usecases.UpdateAnnouncementUseCase
import br.com.ajadigital.core.announcements.application.usecases.WithdrawAnnouncementUseCase
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementReceiptCodecOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementRequestFingerprintOutputPort

@Configuration
class AnnouncementsConfiguration {
    @Bean
    fun createAnnouncement(
        store: AnnouncementStoreOutputPort,
        fingerprints: AnnouncementRequestFingerprintOutputPort,
        idempotency: EncodedIdempotencyStoreOutputPort,
        receiptCodec: AnnouncementReceiptCodecOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CreateAnnouncementInputPort = CreateAnnouncementUseCase(store, fingerprints, idempotency, receiptCodec, transactionRunner)

    @Bean
    fun updateAnnouncement(
        store: AnnouncementStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): UpdateAnnouncementInputPort = UpdateAnnouncementUseCase(store, transactionRunner)

    @Bean
    fun withdrawAnnouncement(
        store: AnnouncementStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): WithdrawAnnouncementInputPort = WithdrawAnnouncementUseCase(store, transactionRunner)

    @Bean
    fun getAnnouncement(store: AnnouncementStoreOutputPort): GetAnnouncementInputPort = GetAnnouncementUseCase(store)

    @Bean
    fun listAnnouncements(store: AnnouncementStoreOutputPort): ListAnnouncementsInputPort = ListAnnouncementsUseCase(store)

    @Bean
    fun listCalendarEvents(store: AnnouncementStoreOutputPort): ListCalendarEventsInputPort = ListCalendarEventsUseCase(store)

    @Bean
    fun listDashboardAnnouncements(store: AnnouncementStoreOutputPort): ListDashboardAnnouncementsInputPort =
        ListDashboardAnnouncementsUseCase(store)

    @Bean
    fun listDashboardUpcomingEvents(store: AnnouncementStoreOutputPort): ListDashboardUpcomingEventsInputPort =
        ListDashboardUpcomingEventsUseCase(store)
}
