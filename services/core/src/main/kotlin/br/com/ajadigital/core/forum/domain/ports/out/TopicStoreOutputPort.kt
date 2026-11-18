package br.com.ajadigital.core.forum.domain.ports.out
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.forum.domain.models.*
import br.com.ajadigital.core.forum.domain.models.TopicDraft



interface TopicStoreOutputPort {
    fun authorizeAndFindReplay(request: AuthorizeAndFindTopicReplayDto): Map<String, Any?>?
    fun authorizeReply(request: AuthorizeTopicReplyDto)
    fun create(request: CreateTopicRecordDto): Map<String, Any?>
    fun topics(request: ListStoredTopicsDto): AcademicPage
    fun dashboardTopics(request: ListDashboardStoredTopicsDto): List<Map<String, Any?>>
    fun topic(request: GetStoredTopicDto): Map<String, Any?>?
    fun reply(request: CreateTopicReplyRecordDto): Map<String, Any?>?
    fun read(request: MarkTopicReadRecordDto): Map<String, Any?>?
    fun solution(request: MarkTopicSolutionRecordDto): Map<String, Any?>?
    fun withdraw(request: WithdrawTopicRecordDto): Map<String, Any?>?
    fun vote(request: VoteTopicReplyRecordDto): Map<String, Any?>?
    fun withdrawReply(request: WithdrawTopicReplyRecordDto): Map<String, Any?>?
}
