package br.com.ajadigital.core.curriculum
import br.com.ajadigital.core.CoreIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.get
import jakarta.servlet.http.Cookie
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden

class CurriculumIntegrationTests : CoreIntegrationTestBase() {
    @Test
    fun userAndClassReadAndPatchOperationsReturnTheirCurrentRepresentations() {
        val (adminCookie, _) = adminSession()
        val (_, studentId) = userSession("student")
        val initialClassId = createB06Class(adminCookie)

        val user = liveHttp("GET", "/users/$studentId", adminCookie)
        assertEquals(200, user.statusCode(), user.body())
        val userPatch = liveHttp("PATCH", "/users/$studentId", adminCookie, """{"expectedRevision":1,"name":"Updated student"}""")
        assertEquals(200, userPatch.statusCode(), userPatch.body())
        assertEquals("Updated student", tools.jackson.databind.json.JsonMapper.builder().build().readTree(userPatch.body()).get("name").asText())

        val classPatch = liveHttp("PATCH", "/classes/$initialClassId", adminCookie, """{"expectedRevision":1,"name":"Updated class"}""")
        assertEquals(200, classPatch.statusCode(), classPatch.body())
        assertEquals("Updated class", tools.jackson.databind.json.JsonMapper.builder().build().readTree(classPatch.body()).get("name").asText())

        val email = "preview-${UUID.randomUUID()}@example.test"
        assertEquals(200, liveHttp("GET", "/enrollments/preview?classId=$initialClassId&email=$email", adminCookie).statusCode())
        val enrollment = liveHttp("POST", "/enrollments", adminCookie, """{"classId":"$initialClassId","email":"$email"}""", "enrollment-${UUID.randomUUID()}")
        assertEquals(201, enrollment.statusCode(), enrollment.body())
        val enrollmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(enrollment.body()).get("id").asText()
        assertEquals(200, liveHttp("GET", "/enrollments/$enrollmentId", adminCookie).statusCode())
    }

    @Test
    fun academicOperationsRequireAnActiveSession() {
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
        }.andExpect { status { isUnauthorized() } }
        mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Hidden","code":"HIDDEN","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun assignedTeachersCanCollaborateOnSubjectsAndUnassignmentRevokesWrites() {
        val (adminCookie, _) = adminSession()
        val (teacherOneCookie, teacherOneId) = userSession("teacher")
        val (teacherTwoCookie, teacherTwoId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (otherStudentCookie, otherStudentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classResponse = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Turma B06","code":"B06-${UUID.randomUUID()}","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResponse.response.contentAsString).get("id").asText()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        listOf(studentId, otherStudentId).forEach { id ->
            val email = jdbc.queryForObject("select email from core.users where id=?", String::class.java, id)!!
            jdbc.update("INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by) VALUES(?,1,?,?,?,?,?,?,?,'active',?)", UUID.randomUUID().toString(), now, now, teacherOneId, classId, email, email.lowercase(), id, teacherOneId)
        }

        val subjectKey = "subject-${UUID.randomUUID()}"
        val subjectResponse = mvc.post("/api/v1/subjects") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", subjectKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","name":"Matemática","teacherIds":["$teacherOneId","$teacherTwoId"],"color":"violet","description":""}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.teacherIds.length()") { value(2) }
            jsonPath("$.className") { doesNotExist() }
            jsonPath("$.teachers") { doesNotExist() }
            jsonPath("$.canManageContent") { doesNotExist() }
            jsonPath("$.publicationIds") { doesNotExist() }
            jsonPath("$.hasUpdates") { doesNotExist() }
        }.andReturn()
        val subjectId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(subjectResponse.response.contentAsString).get("id").asText()

        val firstModuleKey = "module-${UUID.randomUUID()}"
        val firstModule = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", firstModuleKey)
            cookie(Cookie("aja_session", teacherOneCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo A","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val secondModule = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo B","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val adminModule = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo C","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val json = tools.jackson.databind.json.JsonMapper.builder().build()
        val firstModuleId = json.readTree(firstModule.response.contentAsString).get("id").asText()
        val secondModuleId = json.readTree(secondModule.response.contentAsString).get("id").asText()
        val adminModuleId = json.readTree(adminModule.response.contentAsString).get("id").asText()

        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":3,"items":[{"id":"$secondModuleId","expectedRevision":1},{"id":"$firstModuleId","expectedRevision":1},{"id":"$adminModuleId","expectedRevision":1}]}"""
        }.andExpect { status { isConflict() } }
        val subjectBeforeReorder = liveHttp("GET", "/subjects/$subjectId", teacherTwoCookie)
        assertEquals(200, subjectBeforeReorder.statusCode(), subjectBeforeReorder.body())
        val subjectBeforeReorderBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(subjectBeforeReorder.body())
        assertEquals(3, subjectBeforeReorderBody.get("modules").size())
        assertEquals(firstModuleId, subjectBeforeReorderBody.at("/modules/0/id").asText())
        val reorderedModules = liveHttp(
            "POST", "/subjects/$subjectId/reorder-modules?page=2&pageSize=1", teacherTwoCookie,
            """{"expectedRevision":4,"items":[{"id":"$secondModuleId","expectedRevision":1},{"id":"$firstModuleId","expectedRevision":1},{"id":"$adminModuleId","expectedRevision":1}]}""",
        )
        assertEquals(200, reorderedModules.statusCode(), reorderedModules.body())
        val reorderBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(reorderedModules.body())
        assertEquals(2, reorderBody.get("page").asInt())
        assertEquals(1, reorderBody.get("pageSize").asInt())
        assertEquals(3, reorderBody.get("total").asInt())
        assertEquals(1, reorderBody.get("items").size())
        assertEquals(firstModuleId, reorderBody.at("/items/0/id").asText())
        val moduleList = liveHttp("GET", "/modules?subjectId=$subjectId&page=1&pageSize=1&sort=order", teacherTwoCookie)
        assertEquals(200, moduleList.statusCode(), moduleList.body())
        val moduleListBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleList.body())
        assertEquals(1, moduleListBody.get("pageSize").asInt())
        assertEquals(3, moduleListBody.get("total").asInt())
        assertEquals(1, moduleListBody.get("items").size())
        val moduleRead = liveHttp("GET", "/modules/$firstModuleId", teacherTwoCookie)
        assertEquals(200, moduleRead.statusCode(), moduleRead.body())
        assertEquals("Módulo A", tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleRead.body()).get("title").asText())
        val moduleUpdate = liveHttp("PATCH", "/modules/$firstModuleId", teacherTwoCookie, """{"expectedRevision":2,"title":"Módulo A editado"}""")
        assertEquals(200, moduleUpdate.statusCode(), moduleUpdate.body())
        val moduleUpdateBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleUpdate.body())
        assertEquals(3, moduleUpdateBody.get("revision").asInt())
        assertEquals("", moduleUpdateBody.get("description").asText())

        mvc.patch("/api/v1/subjects/$subjectId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":5,"teacherIds":["$teacherTwoId"]}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(6) }; jsonPath("$.className") { doesNotExist() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherOneCookie))
            content = """{"subjectId":"$subjectId","title":"Revoked","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", firstModuleKey)
            cookie(Cookie("aja_session", teacherOneCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo A","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/subjects/$subjectId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.modules.length()") { value(3) }; jsonPath("$.projections.materials.count") { value(0) }; jsonPath("$.subject.teachers.length()") { value(1) }; jsonPath("$.subject.canManageContent") { value(true) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(subjectId) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) }; jsonPath("$.items.length()") { value(0) } }
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "material:observed")
        val firstVisit = liveHttp("GET", "/subjects/$subjectId/visits", studentCookie)
        assertEquals(200, firstVisit.statusCode(), firstVisit.body())
        val firstVisitBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(firstVisit.body())
        assertTrue(firstVisitBody.get("seenAt").isNull)
        assertEquals(1, firstVisitBody.get("publicationIds").size())
        assertTrue(firstVisitBody.get("hasUpdates").asBoolean())
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "notice:later")
        val recordedVisit = liveHttp("POST", "/subjects/$subjectId/visits", studentCookie, """{"publicationIds":["material:observed"]}""")
        assertEquals(200, recordedVisit.statusCode(), recordedVisit.body())
        val recordedVisitBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(recordedVisit.body())
        assertTrue(recordedVisitBody.hasNonNull("seenAt"))
        assertEquals("material:observed", recordedVisitBody.get("seenPublicationIds").first().asText())
        assertEquals(2, recordedVisitBody.get("publicationIds").size())
        assertTrue(recordedVisitBody.get("hasUpdates").asBoolean())
        val visitPool = Executors.newFixedThreadPool(2)
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "material:concurrent-a")
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "notice:concurrent-b")
        try {
            val concurrentVisits = listOf("material:concurrent-a", "notice:concurrent-b").map { publicationId ->
                visitPool.submit<Int> {
                    mvc.post("/api/v1/subjects/$subjectId/visits") {
                        contentType = org.springframework.http.MediaType.APPLICATION_JSON
                        header("Origin", "https://aja.example.test")
                        cookie(Cookie("aja_session", studentCookie))
                        content = json.writeValueAsString(mapOf("publicationIds" to listOf(publicationId)))
                    }.andReturn().response.status
                }
            }
            assertEquals(listOf(200, 200), concurrentVisits.map { it.get(10, TimeUnit.SECONDS) })
        } finally { visitPool.shutdownNow() }
        mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.seenPublicationIds.length()") { value(3) }; jsonPath("$.seenPublicationIds[0]") { value("material:concurrent-a") }; jsonPath("$.seenPublicationIds[2]") { value("notice:concurrent-b") } }
        mvc.post("/api/v1/subjects/$subjectId/visits") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"publicationIds":["material:unknown"]}"""
        }.andExpect { status { isUnprocessableEntity() } }
        val manyPublications = (1..100).map { "material:batch-$it" } + ("material:" + "x".repeat(130))
        manyPublications.forEach { jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, it) }
        val manyVisit = mvc.post("/api/v1/subjects/$subjectId/visits") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = json.writeValueAsString(mapOf("publicationIds" to manyPublications))
        }.andExpect { status { isOk() }; jsonPath("$.seenPublicationIds.length()") { value(104) } }.andReturn()
        mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", otherStudentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.seenAt") { value(null) }; jsonPath("$.seenPublicationIds.length()") { value(0) }; jsonPath("$.hasUpdates") { value(true) } }
        assertTrue(firstVisit.body().contains("\"seenAt\":null"))

        mvc.post("/api/v1/classes/$classId/close") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "close-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"subjectId":"$subjectId","title":"Closed teacher write","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"subjectId":"$subjectId","title":"Closed admin write","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.patch("/api/v1/modules/$firstModuleId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":3,"title":"Closed teacher edit"}"""
        }.andExpect { status { isForbidden() } }
        mvc.patch("/api/v1/modules/$secondModuleId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":2,"title":"Closed admin edit"}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":6,"items":[{"id":"$secondModuleId","expectedRevision":2},{"id":"$firstModuleId","expectedRevision":3},{"id":"$adminModuleId","expectedRevision":2}]}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":6,"items":[{"id":"$secondModuleId","expectedRevision":2},{"id":"$firstModuleId","expectedRevision":3},{"id":"$adminModuleId","expectedRevision":2}]}"""
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() } }
        mvc.get("/api/v1/subjects/$subjectId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.subject.canManageContent") { value(false) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].canManageContent") { value(false) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].canManageContent") { value(false) } }
    }

    @Test
    fun concurrentModuleCreateAndReorderPreserveOrderAndReturnBoundedPage() {
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Turma concorrente","code":"RACE-${UUID.randomUUID()}","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val json = tools.jackson.databind.json.JsonMapper.builder().build()
        val classId = json.readTree(classResult.response.contentAsString).get("id").asText()
        val subjectResult = mvc.post("/api/v1/subjects") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "subject-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","name":"Concorrência","teacherIds":["$teacherId"],"color":"blue","icon":"book","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val subjectId = json.readTree(subjectResult.response.contentAsString).get("id").asText()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        repeat(101) { index ->
            jdbc.update("INSERT INTO core.modules(id,revision,created_at,updated_at,updated_by,subject_id,title,description,order_index) VALUES(?,1,?,?,?,?,?,?,?)", "module-${index.toString().padStart(3, '0')}", now, now, teacherId, subjectId, "Module $index", "", index)
        }
        val itemsBefore = jdbc.query("SELECT id,revision FROM core.modules WHERE subject_id=? ORDER BY order_index", { rs, _ -> mapOf("id" to rs.getString(1), "expectedRevision" to rs.getInt(2)) }, subjectId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.prepareStatement("SELECT id FROM core.subjects WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, subjectId)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val create = pool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Racing module","description":""}"""
                }.andReturn().response.status
            }
            awaitLockWait("core.subjects")
            val reorder = pool.submit<Int> {
                mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", adminCookie))
                    content = json.writeValueAsString(mapOf("expectedRevision" to 1, "items" to itemsBefore))
                }.andReturn().response.status
            }
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(201, create.get(10, TimeUnit.SECONDS))
            assertEquals(409, reorder.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(102, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL", Int::class.java, subjectId))
        val allItems = jdbc.query("SELECT id,revision FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY order_index,id", { rs, _ -> mapOf("id" to rs.getString(1), "expectedRevision" to rs.getInt(2)) }, subjectId)
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules?page=2&pageSize=100") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = json.writeValueAsString(mapOf("expectedRevision" to 2, "items" to allItems))
        }.andExpect { status { isOk() }; jsonPath("$.page") { value(2) }; jsonPath("$.pageSize") { value(100) }; jsonPath("$.total") { value(102) }; jsonPath("$.items.length()") { value(2) } }
        val orderedIds = jdbc.query("SELECT id FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY order_index", { rs, _ -> rs.getString(1) }, subjectId)
        assertEquals(102, orderedIds.distinct().size)
        assertTrue(orderedIds.contains("module-100"))

        val heldForTeamUpdate = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        heldForTeamUpdate.autoCommit = false
        heldForTeamUpdate.prepareStatement("SELECT id FROM core.subjects WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, subjectId)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val teamPool = Executors.newFixedThreadPool(2)
        try {
            val teacherWrite = teamPool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Concurrent teacher write","description":""}"""
                }.andReturn().response.status
            }
            awaitLockWait("core.subjects")
            val teamUpdate = teamPool.submit<Int> {
                mvc.patch("/api/v1/subjects/$subjectId") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", adminCookie))
                    content = """{"expectedRevision":3,"teacherIds":["$teacherId"]}"""
                }.andReturn().response.status
            }
            awaitLockWaitCount(2)
            heldForTeamUpdate.commit()
            assertEquals(201, teacherWrite.get(10, TimeUnit.SECONDS))
            assertEquals(409, teamUpdate.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { heldForTeamUpdate.rollback() }
            heldForTeamUpdate.close()
            teamPool.shutdownNow()
        }
        assertEquals(103, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL", Int::class.java, subjectId))
    }

    @Test
    fun sessionRevocationQueuedBeforeModuleWriteWinsSharedAuthGate() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(812734)") }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val logout = pool.submit<Int> {
                mvc.post("/api/v1/auth/logout") {
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", teacherCookie))
                }.andReturn().response.status
            }
            awaitLockWaitCount(1)
            val create = pool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Revoked while queued","description":""}"""
                }.andReturn().response.status
            }
            repeat(80) { if (!create.isDone) Thread.sleep(25) }
            assertFalse(create.isDone, "Module write must wait for the auth invariant gate held by the earlier revocation")
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(204, logout.get(10, TimeUnit.SECONDS))
            assertEquals(403, create.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND title='Revoked while queued'", Int::class.java, subjectId))
    }

    @Test
    fun assessmentCreationQueuedBehindRevocationCannotCommit() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(812734)") }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val logout = pool.submit<Int> {
                mvc.post("/api/v1/auth/logout") { header("Origin", "https://aja.example.test"); cookie(Cookie("aja_session", teacherCookie)) }.andReturn().response.status
            }
            awaitLockWaitCount(1)
            val create = pool.submit<Int> {
                mvc.post("/api/v1/assessments") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "revoked-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"moduleId":"$moduleId","title":"Revoked activity","description":"","kind":"work","maxGrade":null,"deadline":null,"required":false,"allowLate":false,"questions":[]}"""
                }.andReturn().response.status
            }
            repeat(80) { if (!create.isDone) Thread.sleep(25) }
            assertFalse(create.isDone, "Assessment writes wait for the Core authorization gate")
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(204, logout.get(10, TimeUnit.SECONDS))
            assertEquals(403, create.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.assessments WHERE title='Revoked activity'", Int::class.java))
    }

    @Test
    fun moduleWriteThatOwnsAuthGateCommitsBeforeQueuedLogout() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.prepareStatement("SELECT id FROM core.classes WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, classId)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val create = pool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Write before logout","description":""}"""
                }.andReturn().response.status
            }
            awaitLockWaitCount(1)
            val logout = pool.submit<Int> {
                mvc.post("/api/v1/auth/logout") {
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", teacherCookie))
                }.andReturn().response.status
            }
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(201, create.get(10, TimeUnit.SECONDS))
            assertEquals(204, logout.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND title='Write before logout'", Int::class.java, subjectId))
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"After logout","description":""}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun teacherProvisioningAndEmailConfirmationActivatePendingEnrollmentAtomically() {
        val adminCookie = adminSession().first
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Turma teste","code":"C-${UUID.randomUUID()}","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        mvc.get("/api/v1/enrollments/preview?classId=$classId&email=not-an-email") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isUnprocessableEntity() } }

        val teacherEmail = "teacher-${UUID.randomUUID()}@example.test"
        val outboxBefore = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)!!
        val teacher = liveHttp("POST", "/users", adminCookie, """{"name":"Teacher AJA","email":"$teacherEmail","role":"teacher"}""", "user-${UUID.randomUUID()}")
        assertEquals(201, teacher.statusCode(), teacher.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(teacher.body()).get("emailConfirmedAt").isNull)
        assertEquals(outboxBefore + 2, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        assertEquals(2, jdbc.queryForObject("select count(*) from core.auth_tokens where email_at_issue=?", Int::class.java, teacherEmail))

        val studentEmail = "student-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Student AJA","email":"$studentEmail","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val enrollmentKey = "enrollment-${UUID.randomUUID()}"
        val enrollment = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", enrollmentKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$studentEmail"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.status") { value("pending") } }.andReturn()
        val enrollmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(enrollment.response.contentAsString).get("id").asText()
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        val confirmation = mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$token"}"""
        }.andReturn()
        assertEquals(200, confirmation.response.status, confirmation.response.contentAsString)
        val activated = jdbc.queryForMap("select status,student_id from core.enrollments where id=?", enrollmentId)
        assertEquals("active", activated["status"])
        val studentId = jdbc.queryForObject("select id from core.users where normalized_email=?", String::class.java, studentEmail)!!
        assertEquals(studentId, activated["student_id"])
        mvc.get("/api/v1/enrollments?classId=$classId&status=active&q=$studentEmail&page=1&pageSize=1&sort=-id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(enrollmentId) } }
        mvc.get("/api/v1/students?classId=$classId&history=false") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].email") { value(studentEmail) }; jsonPath("$.items[0].enrollment.id") { value(enrollmentId) } }
        mvc.get("/api/v1/students?q=$studentEmail") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].enrollment") { value(null) } }
        val studentDetail = liveHttp("GET", "/students/$studentId", adminCookie)
        assertEquals(200, studentDetail.statusCode(), studentDetail.body())
        assertEquals(enrollmentId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(studentDetail.body()).at("/enrollments/0/id").asText())

        val login = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$studentEmail","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val studentCookie = login.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
        val classes = liveHttp("GET", "/classes", studentCookie)
        assertEquals(200, classes.statusCode(), classes.body())
        val classesBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classes.body())
        assertEquals(1, classesBody.get("total").asInt())
        assertEquals(classId, classesBody.at("/items/0/id").asText())
        assertFalse(classesBody.at("/items/0").has("studentIds"))
        val classRead = liveHttp("GET", "/classes/$classId", studentCookie)
        assertEquals(200, classRead.statusCode(), classRead.body())
        assertEquals(classId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(classRead.body()).get("id").asText())
        mvc.get("/api/v1/students?classId=$classId&q=$studentEmail&history=true") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/students/$studentId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/enrollments") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(enrollmentId) } }
        mvc.get("/api/v1/me/enrollments") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].id") { value(enrollmentId) } }
        val repeated = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", enrollmentKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$studentEmail"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        assertEquals(enrollmentId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(repeated.response.contentAsString).get("id").asText())
        mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", enrollmentKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"other-${UUID.randomUUID()}@example.test"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.code") { value("IDEMPOTENCY_CONFLICT") } }
        val closedClass = liveHttp("POST", "/classes/$classId/close", adminCookie, """{"expectedRevision":1}""", "close-${UUID.randomUUID()}")
        assertEquals(200, closedClass.statusCode(), closedClass.body())
        assertEquals("ended", jdbc.queryForObject("select status from core.enrollments where id=?", String::class.java, enrollmentId))
        val reopenedClass = liveHttp("POST", "/classes/$classId/reopen", adminCookie, """{"expectedRevision":2}""", "reopen-${UUID.randomUUID()}")
        assertEquals(200, reopenedClass.statusCode(), reopenedClass.body())
        assertEquals("ended", jdbc.queryForObject("select status from core.enrollments where id=?", String::class.java, enrollmentId))
    }

    @Test
    fun concurrentAdminDeactivationCannotRemoveTheLastAdministrator() {
        jdbc.update("UPDATE core.sessions SET revoked_at=now() WHERE user_id IN (SELECT id FROM core.users WHERE role='admin') AND revoked_at IS NULL")
        jdbc.update("UPDATE core.users SET active=false WHERE role='admin'")
        val first = adminSession(); val second = adminSession()
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val requests = listOf(first, second).map { (cookie, id) -> executor.submit<Int> {
                start.await()
                liveHttp("POST", "/users/$id/deactivate", cookie, """{"expectedRevision":1}""").statusCode()
            } }
            start.countDown()
            val statuses = requests.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), statuses.sorted())
            assertEquals(1, jdbc.queryForObject("select count(*) from core.users where role='admin' and active", Int::class.java))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun userWithdrawalRevokesTheTargetUsersSession() {
        val (adminCookie, _) = adminSession()
        val (studentCookie, studentId) = userSession("student")

        val withdrawn = liveHttp("POST", "/users/$studentId/withdraw", adminCookie, """{"expectedRevision":1}""")

        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(withdrawn.body()).get("active").asBoolean())
        assertEquals(401, liveHttp("GET", "/me", studentCookie).statusCode())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.sessions WHERE user_id=? AND revoked_at IS NOT NULL", Int::class.java, studentId))
    }

    @Test
    fun roleChangesRevokeSessionsAndStaleRevisionsConflict() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        mvc.get("/api/v1/students") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        mvc.get("/api/v1/students/${UUID.randomUUID()}") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/enrollments") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        val users = liveHttp("GET", "/users?role=teacher&active=true&page=1&pageSize=100", adminCookie)
        assertEquals(200, users.statusCode(), users.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(users.body()).get("items").any { it.get("id").asText() == teacherId })
        mvc.get("/api/v1/users?role=teacher&active=true&q=Admin%20AJA") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items.length()") { value(0) } }
        val roleChange = liveHttp("POST", "/users/$teacherId/role", adminCookie, """{"expectedRevision":1,"role":"student"}""")
        assertEquals(200, roleChange.statusCode(), roleChange.body())
        assertEquals(2, tools.jackson.databind.json.JsonMapper.builder().build().readTree(roleChange.body()).get("revision").asInt())
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isUnauthorized() } }
        mvc.post("/api/v1/users/$teacherId/role") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1,"role":"teacher"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.details.currentRevision") { value(2) } }
    }

    @Test
    fun emailConfirmationRollsBackWhenPendingEnrollmentsConflict() {
        val adminCookie = adminSession().first
        val classes = (1..2).map { number ->
            val result = mvc.post("/api/v1/classes") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", "class-${UUID.randomUUID()}")
                cookie(Cookie("aja_session", adminCookie))
                content = """{"name":"Turma $number","code":"R-${UUID.randomUUID()}","year":2026,"shift":"Tarde"}"""
            }.andReturn()
            tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString).get("id").asText()
        }
        val email = "atomic-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Atomic Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val enrollments = classes.map { classId ->
            mvc.post("/api/v1/enrollments") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", "enrollment-${UUID.randomUUID()}")
                cookie(Cookie("aja_session", adminCookie))
                content = """{"classId":"$classId","email":"$email"}"""
            }.andExpect { status { isCreated() } }.andReturn()
        }.map { tools.jackson.databind.json.JsonMapper.builder().build().readTree(it.response.contentAsString).get("id").asText() }
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        val failed = mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$token"}"""
        }.andReturn()
        assertEquals(409, failed.response.status)
        assertEquals(0, jdbc.queryForObject("select count(*) from core.users where normalized_email=? and email_confirmed_at is not null", Int::class.java, email))
        assertEquals(2, jdbc.queryForObject("select count(*) from core.enrollments where id in (?,?) and status='pending'", Int::class.java, enrollments[0], enrollments[1]))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is not null", Int::class.java, sha256(token)))
    }

    @Test
    fun administrativeActivationLinksOnlyAnAlreadyConfirmedAccount() {
        val (adminCookie, adminId) = adminSession()
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Activation class","code":"A-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val studentId = UUID.randomUUID().toString()
        val enrollmentId = UUID.randomUUID().toString()
        val email = "Confirmed-${UUID.randomUUID()}@Example.Test"
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at)
            VALUES(?,1,?,?,?,?,?,?,?,'student',true,?)""", studentId, now, now, studentId, "Confirmed Student", email, email.lowercase(), "unused", now)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by)
            VALUES(?,1,?,?,?,?,?,?,NULL,'pending',?)""", enrollmentId, now, now, adminId, classId, email, email.lowercase(), adminId)
        val activated = liveHttp("POST", "/enrollments/$enrollmentId/activate", adminCookie, """{"expectedRevision":1}""", "activate-${UUID.randomUUID()}")
        assertEquals(200, activated.statusCode(), activated.body())
        val activatedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(activated.body())
        assertEquals(studentId, activatedBody.get("studentId").asText())
        assertEquals("active", activatedBody.get("status").asText())
        assertEquals(1, jdbc.queryForObject("select revision from core.users where id=?", Int::class.java, studentId))
        assertTrue(jdbc.queryForObject("select active and email_confirmed_at is not null from core.users where id=?", Boolean::class.java, studentId)!!)
        val ended = liveHttp("POST", "/enrollments/$enrollmentId/end", adminCookie, """{"expectedRevision":2}""", "end-${UUID.randomUUID()}")
        assertEquals(200, ended.statusCode(), ended.body())
        assertEquals("ended", tools.jackson.databind.json.JsonMapper.builder().build().readTree(ended.body()).get("status").asText())
        val renewed = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "enrollment-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$email"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.status") { value("active") } }.andReturn()
        val renewedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(renewed.response.contentAsString).get("id").asText()
        val cancelled = liveHttp(
            "POST", "/enrollments/$renewedId/cancel", adminCookie,
            """{"expectedRevision":1}""", "cancel-${UUID.randomUUID()}",
        )
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals("cancelled", tools.jackson.databind.json.JsonMapper.builder().build().readTree(cancelled.body()).get("status").asText())
    }

    @Test
    fun academicMutationsWriteMinimalAuditAndIdempotentReplayDoesNotDuplicateIt() {
        val (adminCookie, adminId) = adminSession()
        val before = jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java)!!
        val key = "class-audit-${UUID.randomUUID()}"
        val code = "AU-${UUID.randomUUID()}"
        val body = """{"name":"Audited class","code":"$code","year":2026,"shift":"Tarde"}"""
        val first = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", adminCookie))
            content = body
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(first.response.contentAsString).get("id").asText()
        assertEquals(before + 1, jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java))
        assertEquals("academic.class_created", jdbc.queryForObject("select event_type from core.audit_events where subject_user_id=?", String::class.java, classId))
        assertEquals(adminId, jdbc.queryForObject("select actor_user_id from core.audit_events where subject_user_id=?", String::class.java, classId))
        mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", adminCookie))
            content = body
        }.andExpect { status { isCreated() } }
        assertEquals(before + 1, jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java))
        mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "different-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Duplicate class","code":"$code","year":2026,"shift":"Tarde"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.code") { value("BUSINESS_CONFLICT") } }
    }

    @Test
    fun adminWriteRevalidatesCurrentSessionAndRoleInsideTheCommandBoundary() {
        val (cookie, adminId) = adminSession()
        val sessionHash = sha256(cookie)
        jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sessionHash)
        val actor = AuthenticatedActor(adminId, "admin", true, mutableClock.instant(), sessionHash)
        val before = jdbc.queryForObject("select count(*) from core.classes", Int::class.java)
        assertThrows(AcademicForbidden::class.java) {
            createClassUseCase.createClass(br.com.ajadigital.core.academic.application.models.CreateClassDto(actor, "Rejected class", "REJECT-${UUID.randomUUID()}", 2026, "Tarde", "rejected-${UUID.randomUUID()}"))
        }
        assertEquals(before, jdbc.queryForObject("select count(*) from core.classes", Int::class.java))
    }

    @Test
    fun emailConfirmationAndAdministrativeActivationUseConsistentPostgresLockOrder() {
        val (adminCookie, adminId) = adminSession()
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Concurrent activation","code":"CA-${UUID.randomUUID()}","year":2026,"shift":"Tarde"}"""
        }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val email = "concurrent-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Concurrent Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val enrollmentResult = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "enrollment-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$email"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val enrollmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(enrollmentResult.response.contentAsString).get("id").asText()
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val confirmation = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/auth/email-confirmations") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    content = """{"token":"$token"}"""
                }.andReturn().response.status
            }
            val activation = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/enrollments/$enrollmentId/activate") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "activate-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", adminCookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            }
            start.countDown()
            assertEquals(200, confirmation.get(15, TimeUnit.SECONDS))
            assertTrue(activation.get(15, TimeUnit.SECONDS) in setOf(200, 409))
            assertEquals("active", jdbc.queryForObject("select status from core.enrollments where id=?", String::class.java, enrollmentId))
            assertEquals(adminId, jdbc.queryForObject("select created_by from core.enrollments where id=?", String::class.java, enrollmentId))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun sameAdminAccountConfirmationAndClassCloseUseConsistentPostgresLockOrder() {
        val (adminCookie, adminId) = adminSession()
        val email = jdbc.queryForObject("select email from core.users where id=?", String::class.java, adminId)!!
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Same actor race","code":"SAR-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val enrollmentId = UUID.randomUUID().toString()
        val token = "same-actor-token-${UUID.randomUUID()}"
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("UPDATE core.users SET email_confirmed_at=NULL WHERE id=?", adminId)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by)
            VALUES(?,1,?,?,?,?,?,?,NULL,'pending',?)""", enrollmentId, now, now, adminId, classId, email, email.lowercase(), adminId)
        jdbc.update("""INSERT INTO core.auth_tokens(id,user_id,purpose,token_hash,email_at_issue,created_at,expires_at)
            VALUES(?,?,'email_confirmation',?,?,?,?)""", UUID.randomUUID().toString(), adminId, sha256(token), email, now, java.sql.Timestamp.from(mutableClock.instant().plusSeconds(1800)))
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val confirmation = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/auth/email-confirmations") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    content = """{"token":"$token"}"""
                }.andReturn().response.status
            }
            val close = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/classes/$classId/close") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "close-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", adminCookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            }
            start.countDown()
            assertEquals(200, confirmation.get(15, TimeUnit.SECONDS))
            assertEquals(200, close.get(15, TimeUnit.SECONDS))
            assertTrue(jdbc.queryForObject("select status in ('cancelled','ended') from core.enrollments where id=?", Boolean::class.java, enrollmentId)!!)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun enrollmentSearchUsesLinkedIdentityBeforeHistoricalEmailFallback() {
        val (adminCookie, adminId) = adminSession()
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Search class","code":"S-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val original = "old-${UUID.randomUUID()}@example.test"
        val replacement = "new-${UUID.randomUUID()}@example.test"
        val linkedId = UUID.randomUUID().toString()
        val otherId = UUID.randomUUID().toString()
        val enrollmentId = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES(?,1,?,?,?,?,?,?,?,'student',true,?)""", linkedId, now, now, linkedId, "Linked Student", original, original, "unused", now)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by) VALUES(?,1,?,?,?,?,?,?,?,'active',?)""", enrollmentId, now, now, adminId, classId, original, original, linkedId, adminId)
        jdbc.update("UPDATE core.users SET email=?,normalized_email=?,revision=revision+1 WHERE id=?", replacement, replacement, linkedId)
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES(?,1,?,?,?,?,?,?,?,'student',true,?)""", otherId, now, now, otherId, "New Owner", original, original, "unused", now)
        mvc.get("/api/v1/enrollments?q=$original") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].studentId") { value(linkedId) } }
    }
}
