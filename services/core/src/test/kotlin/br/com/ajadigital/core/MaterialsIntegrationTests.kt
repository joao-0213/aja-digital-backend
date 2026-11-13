package br.com.ajadigital.core
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.get
import jakarta.servlet.http.Cookie
import java.util.UUID
import java.time.Duration
import java.security.MessageDigest

class MaterialsIntegrationTests : CoreIntegrationTestBase() {
    @Test
    fun stagedMaterialFileIsStoredAndReadableOnlyByItsAuthorizedOwner() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (outsiderCookie, _) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"File module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val bytes = "%PDF-1.4\nAJA fixture\n%%EOF".toByteArray()
        val upload = MockMultipartFile("file", "lesson.pdf", "application/pdf", bytes)
        mvc.perform(multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId).header("Origin", "https://aja.example.test").header("Idempotency-Key", "unauth-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized)
        val invalidPdf = MockMultipartFile("file", "lesson.pdf", "application/pdf", "not a PDF".toByteArray())
        mvc.perform(multipart("/api/v1/files").file(invalidPdf).param("purpose", "material").param("parentId", moduleId).cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", "invalid-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity)
        val uploadKey = "upload-${UUID.randomUUID()}"
        val uploaded = mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test")
                .header("Idempotency-Key", uploadKey),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(uploaded.response.contentAsString).get("id").asText()

        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("staged") }
            jsonPath("$.size") { value(bytes.size) }
            jsonPath("$.path") { doesNotExist() }
            jsonPath("$.bytes") { doesNotExist() }
        }
        mvc.get("/api/v1/files/$fileId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect {
            status { isOk() }
            header { string("Content-Type", "application/pdf") }
            header { string("X-Content-Type-Options", "nosniff") }
            content { bytes(bytes) }
        }
        assertEquals(bytes.size, jdbc.queryForObject("SELECT size FROM core.files WHERE id=?", Long::class.java, fileId)?.toInt())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, jdbc.queryForObject("SELECT sha256 FROM core.files WHERE id=?", String::class.java, fileId))
        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isNotFound() } }
        val discarded = liveHttp("POST", "/files/$fileId/discard", teacherCookie, "{}")
        assertEquals(204, discarded.statusCode(), discarded.body())
        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", uploadKey),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    fun missingModuleRejectsMaterialPreparationWithoutCreatingAFile() {
        val (teacherCookie, _) = userSession("teacher")
        val bytes = "%PDF-1.4\\nmissing parent\\n%%EOF".toByteArray()
        val upload = MockMultipartFile("file", "missing.pdf", "application/pdf", bytes)
        val before = jdbc.queryForObject("SELECT count(*) FROM core.files", Int::class.java)

        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", "missing-module-${UUID.randomUUID()}")
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", "missing-${UUID.randomUUID()}"),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)

        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM core.files", Int::class.java))
    }

    @Test
    fun failedMaterialIdempotencySaveRollsBackPublicationAndAllowsRetry() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Atomic batch","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val command = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"Lesson","kind":"lesson","duration":10,"content":"Body"}]}"""
        val key = "atomic-${UUID.randomUUID()}"
        jdbc.execute("""CREATE OR REPLACE FUNCTION core.fail_b07_idempotency() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'forced idempotency failure'; END; ${'$'}${'$'}""")
        jdbc.execute("CREATE TRIGGER fail_b07_idempotency BEFORE INSERT OR UPDATE ON core.command_idempotency FOR EACH ROW EXECUTE FUNCTION core.fail_b07_idempotency()")
        try {
            mvc.post("/api/v1/materials/batch") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", key)
                cookie(Cookie("aja_session", teacherCookie))
                content = command
            }.andExpect { status { isInternalServerError() } }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_b07_idempotency ON core.command_idempotency")
            jdbc.execute("DROP FUNCTION IF EXISTS core.fail_b07_idempotency()")
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.materials WHERE module_id=?", Int::class.java, moduleId))
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM core.modules WHERE id=?", Int::class.java, moduleId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.command_idempotency WHERE actor_id=? AND operation='publishMaterials' AND target='/materials/batch' AND idempotency_key=?", Int::class.java, teacherId, key))

        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() }; jsonPath("$.total") { value(1) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.materials WHERE module_id=?", Int::class.java, moduleId))
    }

    @Test
    fun materialBatchReplayRechecksCurrentTeacherAssignment() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Replay scope","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val command = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"Lesson","kind":"lesson","duration":10,"content":"Body"}]}"""
        val key = "replay-${UUID.randomUUID()}"
        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() } }
        jdbc.update("DELETE FROM core.subject_teachers WHERE subject_id=? AND teacher_id=?", subjectId, teacherId)

        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun materialBatchIsAtomicAndProgressConclusionIsRepeatable() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Material module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val upload = MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF-1.4\nmaterial\n%%EOF".toByteArray())
        val uploadResult = mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test")
                .header("Idempotency-Key", "upload-${UUID.randomUUID()}"),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(uploadResult.response.contentAsString).get("id").asText()
        val command = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"PDF lesson","kind":"pdf","duration":10,"content":"","fileId":"$fileId"}]}"""

        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "batch-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = command.replace("\"fileId\":\"$fileId\"", "\"fileId\":\"missing-file\"")
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/materials?moduleId=$moduleId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        val file = liveHttp("GET", "/files/$fileId", teacherCookie)
        assertEquals(200, file.statusCode(), file.body())
        assertEquals("staged", tools.jackson.databind.json.JsonMapper.builder().build().readTree(file.body()).get("status").asText())

        val batchKey = "batch-${UUID.randomUUID()}"
        val published = mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", batchKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() }; jsonPath("$.total") { value(1) } }.andReturn()
        val materialId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(published.response.contentAsString).get("items").get(0).get("id").asText()
        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", batchKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() }; jsonPath("$.items[0].id") { value(materialId) } }
        val material = liveHttp("GET", "/materials/$materialId", teacherCookie)
        assertEquals(200, material.statusCode(), material.body())
        val materialBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(material.body())
        assertEquals(fileId, materialBody.at("/material/fileId").asText())
        assertFalse(materialBody.at("/material/file").has("path"))
        assertEquals(moduleId, materialBody.at("/module/id").asText())
        assertEquals(subjectId, materialBody.at("/subject/id").asText())
        val progress = liveHttp("PATCH", "/me/progress", teacherCookie, """{"materialId":"$materialId","completed":true}""")
        assertEquals(200, progress.statusCode(), progress.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(progress.body()).get("completed").asBoolean())
        mutableClock.advance(Duration.ofMinutes(1))
        mvc.patch("/api/v1/me/progress") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"materialId":"$materialId"}"""
        }.andExpect { status { isOk() }; jsonPath("$.completed") { value(true) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.material_progress WHERE user_id=? AND material_id=? AND completed", Int::class.java, teacherId, materialId))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.materials WHERE id=?", Int::class.java, materialId))
    }

    @Test
    fun materialsCanBeUpdatedThroughThePublicHttpContract() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val module = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("POST", "/modules", teacherCookie, """{"subjectId":"$subjectId","title":"Update probe","description":""}""", "module-update-${UUID.randomUUID()}").body(),
        )
        val moduleId = module.get("id").asText()
        val created = liveHttp(
            "POST", "/materials/batch", teacherCookie,
            """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"Original","kind":"lesson","duration":10,"content":"Body"}]}""",
            "materials-update-${UUID.randomUUID()}",
        )
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("items").get(0).get("id").asText()

        val updated = liveHttp("PATCH", "/materials/$id", teacherCookie, """{"expectedRevision":1,"title":"Updated","duration":12,"content":"Revised body"}""")

        assertEquals(200, updated.statusCode(), updated.body())
        assertEquals("Updated", tools.jackson.databind.json.JsonMapper.builder().build().readTree(updated.body()).get("title").asText())
    }

    @Test
    fun materialReorderRequiresTheCompleteCurrentRevisionSet() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResult = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Order module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResult.response.contentAsString).get("id").asText()
        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "batch-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"First","kind":"lesson","duration":1,"content":"first"},{"moduleId":"$moduleId","title":"Second","kind":"lesson","duration":1,"content":"second"}]}"""
        }.andExpect { status { isCreated() } }
        val page = mvc.get("/api/v1/materials?moduleId=$moduleId&sort=order") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(2) } }.andReturn()
        val items = tools.jackson.databind.json.JsonMapper.builder().build().readTree(page.response.contentAsString).get("items")
        val firstId = items.get(0).get("id").asText()
        val secondId = items.get(1).get("id").asText()
        val reorder = """{"expectedRevision":2,"items":[{"id":"$secondId","expectedRevision":1},{"id":"$firstId","expectedRevision":1}]}"""
        val reordered = liveHttp("POST", "/modules/$moduleId/reorder-materials", teacherCookie, reorder)
        assertEquals(200, reordered.statusCode(), reordered.body())
        assertEquals(secondId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(reordered.body()).at("/items/0/id").asText())
        mvc.post("/api/v1/modules/$moduleId/reorder-materials") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = reorder
        }.andExpect { status { isConflict() } }
        assertEquals(0, jdbc.queryForObject("SELECT order_index FROM core.materials WHERE id=?", Int::class.java, secondId))
        assertEquals(1, jdbc.queryForObject("SELECT order_index FROM core.materials WHERE id=?", Int::class.java, firstId))
    }

    @Test
    fun questionImageImportIsPrivateAndCurrentImageBytesAreReadableByAnActiveSession() {
        val (studentCookie, _) = userSession("student")
        val imageBytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) + "test-image".toByteArray()
        val image = MockMultipartFile("file", "question.png", "image/png", imageBytes)
        mvc.perform(multipart("/internal/v1/question-files/question-image-1").file(image).param("questionId", "q-image-1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden)
        mvc.perform(multipart("/internal/v1/question-files/question-image-1").file(image).param("questionId", "q-image-1").header("X-AJA-Service-Token", "fixture-questions-core-token-1234567890"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated)
        mvc.get("/api/v1/files/question-image-1/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect {
            status { isOk() }
            header { string("Content-Type", "image/png") }
            header { string("X-Content-Type-Options", "nosniff") }
            content { bytes(imageBytes) }
        }
    }
}
