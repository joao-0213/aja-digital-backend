package br.com.ajadigital.core.forum
import br.com.ajadigital.core.CoreIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import jakarta.servlet.http.Cookie
import java.sql.DriverManager
import java.net.URI
import com.sun.net.httpserver.HttpServer
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.Duration

class ForumIntegrationTests : CoreIntegrationTestBase() {
    @Test
    fun concurrentTopicRepliesAreBothPersisted() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Help","body":"Need help","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Teacher reply"}""", "reply-teacher-${UUID.randomUUID()}") }
            val second = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", studentCookie, """{"body":"Student reply"}""", "reply-student-${UUID.randomUUID()}") }
            assertEquals(201, first.get(10, TimeUnit.SECONDS).statusCode())
            assertEquals(201, second.get(10, TimeUnit.SECONDS).statusCode())
        } finally {
            pool.shutdownNow()
        }
        val topic = liveHttp("GET", "/topics/$topicId", studentCookie)
        assertEquals(200, topic.statusCode())
        assertEquals(2, tools.jackson.databind.json.JsonMapper.builder().build().readTree(topic.body()).get("replies").size())
    }

    @Test
    fun forumReadIsPerUserNewRepliesRenewUnreadAndVotesAreIdempotent() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (authorCookie, authorId) = userSession("student")
        val (readerCookie, readerId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        createB07Enrollment(classId, readerId, teacherId)
        val title = "Du\u0301vida inicial"
        val created = liveHttp("POST", "/topics", authorCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"$title","body":"Como resolver?","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        fun topicPage(unread: Boolean) = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/topics?classId=$classId&q=duvida&unread=$unread", readerCookie).body(),
        )
        assertEquals(422, liveHttp("GET", "/topics?unknown=value", readerCookie).statusCode())
        assertEquals(401, liveHttp("GET", "/topics?unknown=value", "invalid-session").statusCode())
        assertEquals(1, topicPage(true).get("total").asInt())
        assertEquals(200, liveHttp("POST", "/topics/$topicId/read", readerCookie, "{}").statusCode())
        assertEquals(0, topicPage(true).get("total").asInt())

        val reply = liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Try this method"}""", "reply-${UUID.randomUUID()}")
        assertEquals(201, reply.statusCode(), reply.body())
        val replyId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(reply.body()).get("replies").get(0).get("id").asText()
        assertEquals(403, liveHttp("POST", "/topics/$topicId/replies/$replyId/vote", teacherCookie, """{"voted":true}""").statusCode())
        assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("read").asBoolean())
        assertEquals(1, topicPage(true).get("total").asInt())
        val voted = liveHttp("POST", "/topics/$topicId/replies/$replyId/vote", readerCookie, """{"voted":true}""")
        assertEquals(200, voted.statusCode(), voted.body())
        assertEquals(200, liveHttp("POST", "/topics/$topicId/replies/$replyId/vote", readerCookie, """{"voted":true}""").statusCode())
        val vote = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("replies").get(0)
        assertEquals(1, vote.get("voteCount").asInt())
        assertEquals(true, vote.get("votedByMe").asBoolean())

        val solved = liveHttp("POST", "/topics/$topicId/solution", authorCookie, """{"expectedRevision":1,"replyId":"$replyId"}""")
        assertEquals(200, solved.statusCode(), solved.body())
        assertEquals(true, tools.jackson.databind.json.JsonMapper.builder().build().readTree(solved.body()).get("replies").get(0).get("accepted").asBoolean())
        val withdrawn = liveHttp("POST", "/topics/$topicId/replies/$replyId/withdraw", teacherCookie, """{"expectedRevision":1}""")
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(withdrawn.body()).get("replies").size())
        assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?classId=$classId&solved=true", readerCookie).body()).get("total").asInt())
    }

    @Test
    fun forumReplyRequiresIdempotencyAndReplaysOnlyTheSameIntent() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Reply key","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val body = """{"body":"One answer"}"""
        assertEquals(422, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body).statusCode())
        val key = "reply-${UUID.randomUUID()}"
        val pool = Executors.newFixedThreadPool(2)
        val firstFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body, key) }
        val replayFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body, key) }
        val first = firstFuture.get(10, TimeUnit.SECONDS)
        val replay = replayFuture.get(10, TimeUnit.SECONDS)
        pool.shutdownNow()
        assertEquals(201, first.statusCode(), first.body())
        assertEquals(first.body(), replay.body())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topic_replies WHERE topic_id=?", Int::class.java, topicId))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE event_type='academic.topic_reply_created' AND subject_user_id=(SELECT id FROM core.topic_replies WHERE topic_id=?)", Int::class.java, topicId))
        assertEquals(409, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Different answer"}""", key).statusCode())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topic_replies WHERE topic_id=?", Int::class.java, topicId))
        jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(teacherCookie))
        assertEquals(401, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body, key).statusCode())
    }

    @Test
    fun forumSearchMatchesOnlyVisibleReplyTextAndWithdrawalKeepsAudit() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"General question","body":"No matching phrase","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val reply = liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Distinctive reply phrase"}""", "reply-${UUID.randomUUID()}")
        assertEquals(201, reply.statusCode(), reply.body())
        val replyId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(reply.body()).get("replies").get(0).get("id").asText()
        assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?q=distinctive", studentCookie).body()).get("total").asInt())
        assertEquals(200, liveHttp("POST", "/topics/$topicId/replies/$replyId/withdraw", teacherCookie, """{"expectedRevision":1}""").statusCode())
        assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?q=distinctive", studentCookie).body()).get("total").asInt())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE event_type='academic.topic_reply_withdrawn' AND subject_user_id=?", Int::class.java, replyId))
    }

    @Test
    fun forumReadHoldsTheTopicSnapshotUntilItsWatermarkCommits() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (readerCookie, readerId) = userSession("student")
        val (authorCookie, authorId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, readerId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        val created = liveHttp("POST", "/topics", authorCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Snapshot race","body":"Initial","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val gate = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        gate.createStatement().use { it.execute("SELECT pg_advisory_lock(7712345)") }
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE OR REPLACE FUNCTION core.pause_forum_read() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.topic_id='$topicId' AND NEW.user_id='$readerId' THEN PERFORM pg_advisory_xact_lock(7712345); END IF; RETURN NEW; END ${'$'}${'$'}""")
                statement.execute("CREATE TRIGGER pause_forum_read BEFORE INSERT OR UPDATE ON core.topic_reads FOR EACH ROW EXECUTE FUNCTION core.pause_forum_read()")
            }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val readFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/read", readerCookie, "{}") }
            awaitLockWait("topic_reads")
            val replyFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Arrived after snapshot"}""", "reply-${UUID.randomUUID()}") }
            awaitLockWaitCount(2)
            gate.createStatement().use { it.execute("SELECT pg_advisory_unlock(7712345)") }
            val read = readFuture.get(10, TimeUnit.SECONDS)
            val reply = replyFuture.get(10, TimeUnit.SECONDS)
            assertEquals(200, read.statusCode(), read.body())
            assertEquals(201, reply.statusCode(), reply.body())
            assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(read.body()).get("replies").size())
            assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("read").asBoolean())
        } finally {
            gate.createStatement().use { it.execute("SELECT pg_advisory_unlock(7712345)") }
            gate.close()
            pool.shutdownNow()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP TRIGGER IF EXISTS pause_forum_read ON core.topic_reads")
                    statement.execute("DROP FUNCTION IF EXISTS core.pause_forum_read()")
                }
            }
        }
    }

    @Test
    fun forumMaterialReferenceCannotCrossClassOrSubjectContext() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classA, moduleA) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (classB, moduleB) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val subjectA = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleA)!!
        val subjectB = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleB)!!
        val validMaterialId = UUID.randomUUID().toString()
        val otherMaterialId = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES(?,1,?,?,?,?,?,?, 'lesson',1,'',NULL,NULL,0)", validMaterialId, now, now, teacherId, teacherId, moduleA, "Local material")
        jdbc.update("INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES(?,1,?,?,?,?,?,?, 'lesson',1,'',NULL,NULL,0)", otherMaterialId, now, now, teacherId, teacherId, moduleB, "Other material")
        val validKey = "topic-material-${UUID.randomUUID()}"
        val validBody = """{"classId":"$classA","subjectId":"$subjectA","title":"Local reference","body":"Body","tags":[],"target":{"type":"material","id":"$validMaterialId"},"attachmentId":null}"""
        val valid = liveHttp("POST", "/topics", adminCookie, validBody, validKey)
        assertEquals(201, valid.statusCode(), valid.body())
        val crossContext = liveHttp("POST", "/topics", adminCookie,
            """{"classId":"$classA","subjectId":"$subjectA","title":"Foreign reference","body":"Body","tags":[],"target":{"type":"material","id":"$otherMaterialId"},"attachmentId":null}""",
            "topic-material-${UUID.randomUUID()}")
        assertEquals(422, crossContext.statusCode(), crossContext.body())
        jdbc.update("DELETE FROM core.materials WHERE id=?", validMaterialId)
        val replayAfterMaterialWithdrawn = liveHttp("POST", "/topics", adminCookie, validBody, validKey)
        assertEquals(201, replayAfterMaterialWithdrawn.statusCode(), replayAfterMaterialWithdrawn.body())
        assertEquals(valid.body(), replayAfterMaterialWithdrawn.body())
        assertEquals(classA, tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.body()).get("classId").asText())
        assertTrue(subjectA != subjectB && classA != classB)
    }

    @Test
    fun forumReadWatermarkNeverAdvancesBeyondTheVisibleActivity() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Read watermark","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()

        mutableClock.advance(Duration.ofMinutes(1))
        mutableClock.freeze()
        try {
            assertEquals(200, liveHttp("POST", "/topics/$topicId/read", studentCookie, "{}").statusCode())
            assertEquals(201, liveHttp("POST", "/topics/$topicId/replies", teacherCookie,
                """{"body":"Reply at the same clock instant"}""", "reply-${UUID.randomUUID()}").statusCode())
            val afterReply = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", studentCookie).body())
            assertEquals(false, afterReply.get("read").asBoolean())
            assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?unread=true", studentCookie).body()).get("total").asInt())
        } finally {
            mutableClock.reset()
        }
    }

    @Test
    fun forumReplyActivityAdvancesWhenClockDoesNotMove() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (authorCookie, authorId) = userSession("student")
        val (readerCookie, readerId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        createB07Enrollment(classId, readerId, teacherId)
        mutableClock.freeze()
        try {
            val created = liveHttp("POST", "/topics", authorCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Equal clock","body":"Question","tags":[],"target":null,"attachmentId":null}""",
                "topic-${UUID.randomUUID()}")
            val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
            val initialActivity = jdbc.queryForObject("SELECT last_activity_at FROM core.topics WHERE id=?", java.time.Instant::class.java, topicId)!!
            assertEquals(200, liveHttp("POST", "/topics/$topicId/read", readerCookie, "{}").statusCode())
            assertEquals(201, liveHttp("POST", "/topics/$topicId/replies", teacherCookie,
                """{"body":"At unchanged clock"}""", "reply-${UUID.randomUUID()}").statusCode())
            val replyActivity = jdbc.queryForObject("SELECT last_activity_at FROM core.topics WHERE id=?", java.time.Instant::class.java, topicId)!!
            assertTrue(replyActivity.isAfter(initialActivity), "A reply must advance activity even when the clock is frozen")
            assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("read").asBoolean())
        } finally {
            mutableClock.reset()
        }
    }

    @Test
    fun topicWithdrawalHidesTheTopicFromPublicReads() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp(
            "POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Withdraw probe","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-withdraw-${UUID.randomUUID()}",
        )
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()

        val withdrawn = liveHttp("POST", "/topics/$topicId/withdraw", studentCookie, """{"expectedRevision":1}""")

        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(404, liveHttp("GET", "/topics/$topicId", studentCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/topics/$topicId", teacherCookie).statusCode())
    }

    @Test
    fun replyWithdrawalDoesNotHideFutureSameClockReplyAsRead() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (authorCookie, authorId) = userSession("student")
        val (secondStudentCookie, secondStudentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        createB07Enrollment(classId, secondStudentId, teacherId)
        val created = liveHttp("POST", "/topics", authorCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Withdrawal watermark","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val firstReply = liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Withdraw me"}""", "reply-${UUID.randomUUID()}")
        val replyId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(firstReply.body()).get("replies").get(0).get("id").asText()
        mutableClock.advance(Duration.ofMinutes(1))
        mutableClock.freeze()
        try {
            assertEquals(200, liveHttp("POST", "/topics/$topicId/replies/$replyId/withdraw", teacherCookie, """{"expectedRevision":1}""").statusCode())
            assertEquals(201, liveHttp("POST", "/topics/$topicId/replies", secondStudentCookie,
                """{"body":"New reply after withdrawal"}""", "reply-${UUID.randomUUID()}").statusCode())
            val afterReply = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", teacherCookie).body())
            assertEquals(false, afterReply.get("read").asBoolean())
        } finally {
            mutableClock.reset()
        }
    }

    @Test
    fun questionSnapshotRpcDoesNotBlockCoreAuthMutationAndCreateRechecksAfterward() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        questionSnapshotProvider.blockNextSnapshot(started, release)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val topicRequest = pool.submit<HttpResponse<String>> {
                liveHttp("POST", "/topics", studentCookie,
                    """{"classId":"$classId","subjectId":"$subjectId","title":"Blocked snapshot","body":"Question","tags":[],"target":{"type":"question","id":"bank-blocked"},"attachmentId":null}""",
                    "topic-blocked-${UUID.randomUUID()}")
            }
            assertTrue(started.await(10, TimeUnit.SECONDS), "Question provider did not enter the blocking gate")
            val logout = pool.submit<HttpResponse<String>> { liveHttp("POST", "/auth/logout", studentCookie) }
            val logoutResponse = logout.get(3, TimeUnit.SECONDS)
            assertEquals(204, logoutResponse.statusCode(), logoutResponse.body())
            release.countDown()
            val topicResponse = topicRequest.get(10, TimeUnit.SECONDS)
            assertEquals(403, topicResponse.statusCode(), topicResponse.body())
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.topics WHERE title='Blocked snapshot'", Int::class.java))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun topicReplayRechecksSessionAfterWaitingForIdempotencyLock() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val body = """{"classId":"$classId","subjectId":"$subjectId","title":"Replay auth race","body":"Question","tags":[],"target":null,"attachmentId":null}"""
        val key = "topic-auth-race-${UUID.randomUUID()}"
        val created = liveHttp("POST", "/topics", studentCookie, body, key)
        assertEquals(201, created.statusCode(), created.body())

        val lockConnection = jdbc.dataSource!!.connection
        val actorLock = lockConnection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")
        lockConnection.autoCommit = false
        actorLock.setString(1, "$studentId|createTopic|/topics|$key")
        actorLock.execute()
        val lockPid = lockConnection.createStatement().executeQuery("SELECT pg_backend_pid()").use { rs -> rs.next(); rs.getInt(1) }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val replay = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics", studentCookie, body, key) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var waiting = false
            while (System.nanoTime() < deadline && !waiting) {
                waiting = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_stat_activity a WHERE ?=ANY(pg_blocking_pids(a.pid)) AND a.wait_event_type='Lock')",
                    Boolean::class.java, lockPid,
                ) ?: false
                if (!waiting) Thread.sleep(25)
            }
            assertTrue(waiting, "Replay did not block on the held per-key idempotency lock")
            jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(studentCookie))
            lockConnection.commit()
            val replayResponse = replay.get(10, TimeUnit.SECONDS)
            assertTrue(replayResponse.statusCode() == 401 || replayResponse.statusCode() == 403, replayResponse.body())
        } finally {
            runCatching { lockConnection.rollback() }
            actorLock.close()
            lockConnection.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun concurrentQuestionTopicCreateWithSameKeyCommitsOnlyOnce() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val body = """{"classId":"$classId","subjectId":"$subjectId","title":"Concurrent question create","body":"Question","tags":[],"target":{"type":"question","id":"bank-race"},"attachmentId":null}"""
        val key = "topic-race-${UUID.randomUUID()}"
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        questionSnapshotProvider.blockNextSnapshot(started, release)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val blockedRequest = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics", studentCookie, body, key) }
            assertTrue(started.await(10, TimeUnit.SECONDS), "First create did not enter the provider")
            val concurrentRequest = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics", studentCookie, body, key) }
            val concurrent = concurrentRequest.get(3, TimeUnit.SECONDS)
            assertEquals(201, concurrent.statusCode(), concurrent.body())
            release.countDown()
            val replay = blockedRequest.get(10, TimeUnit.SECONDS)
            assertEquals(201, replay.statusCode(), replay.body())
            assertEquals(concurrent.body(), replay.body())
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topics WHERE title='Concurrent question create'", Int::class.java))
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE event_type='academic.topic_created' AND subject_user_id=(SELECT id FROM core.topics WHERE title='Concurrent question create')", Int::class.java))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun closedClassAllowsTeamReadOnlyAndRevocationHidesTopicAndAttachment() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val subjectId = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleId)!!
        val enrollmentId = createB07Enrollment(classId, studentId, teacherId)
        val upload = mvc.perform(multipart("/api/v1/files")
            .file(MockMultipartFile("file", "forum.pdf", "application/pdf", "%PDF-1.4\n".toByteArray()))
            .param("purpose", "forum").param("parentId", subjectId)
            .cookie(Cookie("aja_session", studentCookie)).header("Origin", "https://aja.example.test")
            .header("Idempotency-Key", "forum-file-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(upload.response.contentAsString).get("id").asText()
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Attachment","body":"See file","tags":[],"target":null,"attachmentId":"$fileId"}""",
            "topic-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        assertEquals(200, liveHttp("GET", "/files/$fileId/content", teacherCookie).statusCode())

        jdbc.update("UPDATE core.classes SET active=false WHERE id=?", classId)
        assertEquals(200, liveHttp("GET", "/topics/$topicId", teacherCookie).statusCode())
        assertEquals(403, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Closed"}""", "reply-closed-${UUID.randomUUID()}").statusCode())
        assertEquals(404, liveHttp("GET", "/topics/$topicId", studentCookie).statusCode())
        jdbc.update("UPDATE core.classes SET active=true WHERE id=?", classId)
        jdbc.update("UPDATE core.enrollments SET status='ended' WHERE id=?", enrollmentId)
        assertEquals(404, liveHttp("GET", "/topics/$topicId", studentCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/files/$fileId/content", studentCookie).statusCode())
        assertEquals(200, liveHttp("GET", "/files/$fileId/content", teacherCookie).statusCode())
    }

    @Test
    fun questionReferenceUsesAuthenticatedSnapshotHttpProviderInFreshCoreProcess() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val tokens = java.util.concurrent.CopyOnWriteArrayList<String?>()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/internal/v1/snapshots") { exchange ->
            tokens += exchange.requestHeaders.getFirst("X-AJA-Service-Token")
            val payload = exchange.requestBody.use { it.readAllBytes().decodeToString() }
            requests += payload
            val missing = payload.contains("missing-question") || payload.contains("withdrawn-question")
            val response = if (missing) """{"error":{"code":"NOT_FOUND","message":"Missing","requestId":"fixture"}}"""
                else """{"snapshots":[{"questionId":"q-core-process","revision":1,"contentHash":"${"a".repeat(64)}","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"correctOption":0,"explanation":"","image":null}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (missing) 404 else if (tokens.last() == "fixture-questions-core-token-1234567890") 200 else 403, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        var child: CoreChild? = null
        try {
            child = startCoreProcess("http://127.0.0.1:${server.address.port}/internal/v1/snapshots")
            val client = HttpClient.newHttpClient()
            val callsBeforeDeniedReference = tokens.size
            val denied = coreHttp(client, child, "POST", "/topics", outsiderCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Hidden reference","body":"Body","tags":[],"target":{"type":"question","id":"missing-question"},"attachmentId":null}""",
                "topic-hidden-${UUID.randomUUID()}")
            assertEquals(404, denied.statusCode(), denied.body())
            assertEquals(callsBeforeDeniedReference, tokens.size)
            val missing = coreHttp(client, child, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Missing reference","body":"Body","tags":[],"target":{"type":"question","id":"missing-question"},"attachmentId":null}""",
                "topic-missing-${UUID.randomUUID()}")
            assertEquals(404, missing.statusCode(), missing.body())
            val withdrawn = coreHttp(client, child, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Withdrawn reference","body":"Body","tags":[],"target":{"type":"question","id":"withdrawn-question"},"attachmentId":null}""",
                "topic-withdrawn-${UUID.randomUUID()}")
            assertEquals(404, withdrawn.statusCode(), withdrawn.body())
            val created = coreHttp(client, child, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Question reference","body":"Body","tags":[],"target":{"type":"question","id":"q-core-process"},"attachmentId":null}""",
                "topic-question-${UUID.randomUUID()}")
            assertEquals(201, created.statusCode(), created.body())
            assertEquals("q-core-process", tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("target").get("id").asText())
            assertEquals(List(3) { "fixture-questions-core-token-1234567890" }, tokens)
            assertTrue(requests.all { it.contains("\"ids\":[\"missing-question\"]") || it.contains("\"ids\":[\"withdrawn-question\"]") || it.contains("\"ids\":[\"q-core-process\"]") })
        } finally {
            child?.let(::stopCoreProcess)
            server.stop(0)
        }
    }

    @Test
    fun coreProcessUsesRealQuestionsProcessForTopicReferencesAndStableReplay() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outageCookie, outageStudentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        createB07Enrollment(classId, outageStudentId, teacherId)
        val jdbcUrl = "${postgres.jdbcUrl}&currentSchema=questions"
        val questions = startQuestionsProcess()
        var core: CoreChild? = null
        try {
            DriverManager.getConnection(jdbcUrl, "questions", "questions-test").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("""INSERT INTO questions.question_bank(id,revision,created_at,subject,topic,difficulty,institution,year,statement,options,correct_option,explanation)
                        VALUES ('b12-real-question',1,now(),'Math','Algebra','easy','AJA',2026,'2 + 2?',ARRAY['4','5'],0,'Because'),
                               ('b12-real-withdrawn',1,now(),'Math','Algebra','easy','AJA',2026,'Withdrawn?',ARRAY['A','B'],0,'Hidden'),
                               ('b12-revoke-question',1,now(),'Math','Algebra','easy','AJA',2026,'Revoke?',ARRAY['A','B'],0,'Hidden'),
                               ('b12-outage-question',1,now(),'Math','Algebra','easy','AJA',2026,'Outage?',ARRAY['A','B'],0,'Hidden')""")
                    statement.execute("UPDATE questions.question_bank SET withdrawn_at=now() WHERE id='b12-real-withdrawn'")
                }
            }
            core = startCoreProcess("http://127.0.0.1:${questions.port}/internal/v1/snapshots")
            val client = HttpClient.newHttpClient()
            val snapshotUri = URI("http://127.0.0.1:${questions.port}/internal/v1/snapshots")
            val badToken = client.send(HttpRequest.newBuilder(snapshotUri).header("Content-Type", "application/json")
                .header("X-AJA-Service-Token", "wrong-questions-token-1234567890").POST(HttpRequest.BodyPublishers.ofString("""{"ids":["b12-real-question"]}""")).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(403, badToken.statusCode(), badToken.body())
            val exactToken = client.send(HttpRequest.newBuilder(snapshotUri).header("Content-Type", "application/json")
                .header("X-AJA-Service-Token", "fixture-questions-core-token-1234567890").POST(HttpRequest.BodyPublishers.ofString("""{"ids":["b12-real-question"]}""")).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, exactToken.statusCode(), exactToken.body())
            val validKey = "topic-real-${UUID.randomUUID()}"
            val validBody = """{"classId":"$classId","subjectId":"$subjectId","title":"Real question ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-question"},"attachmentId":null}"""
            val valid = coreHttp(client, core, "POST", "/topics", studentCookie, validBody, validKey)
            assertEquals(201, valid.statusCode(), valid.body())
            val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.body()).get("id").asText()
            assertEquals("b12-real-question", tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.body()).get("target").get("id").asText())

            val unauthorized = coreHttp(client, core, "POST", "/topics", outsiderCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Unauthorized ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-question"},"attachmentId":null}""",
                "topic-real-denied-${UUID.randomUUID()}")
            assertEquals(404, unauthorized.statusCode(), unauthorized.body())

            val missing = coreHttp(client, core, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Missing ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-missing"},"attachmentId":null}""",
                "topic-real-missing-${UUID.randomUUID()}")
            assertEquals(404, missing.statusCode(), missing.body())
            val withdrawn = coreHttp(client, core, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Withdrawn ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-withdrawn"},"attachmentId":null}""",
                "topic-real-withdrawn-${UUID.randomUUID()}")
            assertEquals(404, withdrawn.statusCode(), withdrawn.body())

            DriverManager.getConnection(jdbcUrl, "questions", "questions-test").use { connection ->
                connection.createStatement().use { it.execute("UPDATE questions.question_bank SET withdrawn_at=now() WHERE id='b12-real-question'") }
            }
            val replayAfterWithdrawal = coreHttp(client, core, "POST", "/topics", studentCookie, validBody, validKey)
            assertEquals(201, replayAfterWithdrawal.statusCode(), replayAfterWithdrawal.body())
            assertEquals(valid.body(), replayAfterWithdrawal.body())
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topics WHERE id=?", Int::class.java, topicId))

            val revokedKey = "topic-real-revoke-${UUID.randomUUID()}"
            val revokedBody = validBody.replace("Real question ref", "Revoked replay ref")
                .replace("b12-real-question", "b12-revoke-question")
            val revokedCreate = coreHttp(client, core, "POST", "/topics", studentCookie, revokedBody, revokedKey)
            assertEquals(201, revokedCreate.statusCode(), revokedCreate.body())
            jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(studentCookie))
            val revokedReplay = coreHttp(client, core, "POST", "/topics", studentCookie, revokedBody, revokedKey)
            assertEquals(401, revokedReplay.statusCode(), revokedReplay.body())

            val outageKey = "topic-real-outage-${UUID.randomUUID()}"
            val outageBody = validBody.replace("Real question ref", "Outage replay ref")
                .replace("b12-real-question", "b12-outage-question")
            val outageCreate = coreHttp(client, core, "POST", "/topics", outageCookie, outageBody, outageKey)
            assertEquals(201, outageCreate.statusCode(), outageCreate.body())
            stopQuestionsProcess(questions)
            val outageReplay = coreHttp(client, core, "POST", "/topics",
                // The authorized session and request remain identical while the provider is offline.
                outageCookie, outageBody, outageKey)
            assertEquals(201, outageReplay.statusCode(), outageReplay.body())
            assertEquals(outageCreate.body(), outageReplay.body())
        } finally {
            core?.let(::stopCoreProcess)
            stopQuestionsProcess(questions)
        }
    }
}
