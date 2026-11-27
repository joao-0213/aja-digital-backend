package br.com.ajadigital.core.forum.application.ports.out
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic
import br.com.ajadigital.core.forum.application.models.ForumTopicPage
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.FindForumReplyReceiptQuery
import br.com.ajadigital.core.forum.application.models.SaveForumReplyReceiptCommand



interface TopicStoreOutputPort {
    fun authorizeAndFindReplay(request: FindTopicReplayQuery): ForumTopicView?
    fun authorizeReply(request: AuthorizeTopicReplyCommand)
    fun findReplyReceipt(request: FindForumReplyReceiptQuery): ForumTopicView?
    fun saveReplyReceipt(request: SaveForumReplyReceiptCommand)
    fun create(request: CreateTopicRecord): ForumTopicView
    fun topics(request: ListStoredTopicsQuery): ForumTopicPage
    fun dashboardTopics(request: ListDashboardStoredTopicsQuery): List<ForumDashboardTopic>
    fun topic(request: GetStoredTopicQuery): ForumTopicView?
    fun reply(request: CreateTopicReplyRecord): ForumTopicView?
    fun read(request: MarkTopicReadRecord): ForumTopicView?
    fun solution(request: MarkTopicSolutionRecord): ForumTopicView?
    fun withdraw(request: WithdrawTopicRecord): ForumTopicView?
    fun vote(request: VoteTopicReplyRecord): ForumTopicView?
    fun withdrawReply(request: WithdrawTopicReplyRecord): ForumTopicView?
}
