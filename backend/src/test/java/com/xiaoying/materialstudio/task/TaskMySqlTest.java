package com.xiaoying.materialstudio.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.asset.AssetMapper;
import com.xiaoying.materialstudio.asset.AssetResponse;
import com.xiaoying.materialstudio.asset.AssetRow;
import com.xiaoying.materialstudio.asset.AssetService;
import com.xiaoying.materialstudio.asset.PrivateImageStorage;
import com.xiaoying.materialstudio.identity.SessionService;
import com.xiaoying.materialstudio.template.TemplateService;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class TaskMySqlTest {
    private static final String ORIGIN = "http://127.0.0.1:3000";
    private static final String TEMPLATE = "c2222222-2222-4222-8222-222222222222";
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "task-mysql-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("app.storage.root", ROOT::toString);
        registry.add("app.worker.enabled", () -> false);
        registry.add("app.demo-mode", () -> true);
        registry.add("app.security.allowed-origins", () -> ORIGIN);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SessionService sessions;
    @Autowired AssetService assetService;
    @Autowired AssetMapper assets;
    @Autowired PrivateImageStorage storage;
    @Autowired TaskMapper tasks;
    @Autowired TaskTransactions transactions;
    @Autowired TaskPipeline pipeline;
    @Autowired TemplateService templates;
    @Autowired TaskAssetValidator validator;
    @Autowired TaskImageGenerator generator;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    private final List<String> assetIds = new ArrayList<>();
    private final List<String> taskIds = new ArrayList<>();
    private final List<String> sessionHashes = new ArrayList<>();

    @AfterEach
    void cleanupFiles() throws Exception {
        List<AssetRow> resultAssets = new ArrayList<>();
        for (String taskId : taskIds) {
            TaskRow row = tasks.findById(taskId);
            if (row != null && row.resultAssetId() != null) {
                AssetRow result = assets.findOwned(row.resultAssetId(), row.userId());
                if (result != null) resultAssets.add(result);
            }
            jdbc.update("DELETE FROM task_steps WHERE task_id = ?", taskId);
            jdbc.update("DELETE FROM task_assets WHERE task_id = ?", taskId);
            jdbc.update("DELETE FROM tasks WHERE id = ?", taskId);
        }
        for (AssetRow row : resultAssets) {
            Files.deleteIfExists(storage.resolveOwnedFile(row.storagePath()));
            jdbc.update("DELETE FROM assets WHERE id = ?", row.id());
        }
        for (String assetId : assetIds) {
            AssetRow row = assets.findOwned(assetId, "a1111111-1111-4111-8111-111111111111");
            if (row != null) {
                Files.deleteIfExists(storage.resolveOwnedFile(row.storagePath()));
                jdbc.update("DELETE FROM assets WHERE id = ?", assetId);
            }
        }
        for (String hash : sessionHashes) jdbc.update("DELETE FROM sessions WHERE token_hash = ?", hash);
    }

    @Test
    void acceptedTaskReturns202ThenThreeRealTransactionsProducePrivateResult() throws Exception {
        var login = login("alice");
        String input = upload(login.token());
        JsonNode accepted = submit(login.token(), input, null);
        String id = accepted.get("id").asText();
        assertEquals("QUEUED", accepted.get("status").asText());
        assertTrue(accepted.get("stage").isNull());
        assertEquals(3, accepted.get("steps").size());
        assertEquals(0, tasks.findById(id).version());
        mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        mvc.perform(get("/api/tasks").cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("QUEUED"));
        assertEquals(0, tasks.findById(id).version());
        assertEquals(List.of("PENDING", "PENDING", "PENDING"),
                tasks.steps(id).stream().map(TaskStepRow::status).toList());
        assertEquals(id, transactions.claimHead());
        pipeline.run(id);
        TaskRow finished = tasks.findById(id);
        assertEquals("SUCCEEDED", finished.status());
        assertEquals("FINALIZE", finished.stage());
        assertEquals(7, finished.version());
        assertNotNull(finished.resultAssetId());
        assertNotEquals(input, finished.resultAssetId());
        AssetRow inputRow = assets.findOwned(input, finished.userId());
        AssetRow resultRow = assets.findOwned(finished.resultAssetId(), finished.userId());
        assertNotEquals(inputRow.storagePath(), resultRow.storagePath());
        assertEquals("RESULT", resultRow.purpose());
        assertEquals(900, resultRow.width());
        assertEquals(600, resultRow.height());
        assertEquals(List.of("SUCCEEDED", "SUCCEEDED", "SUCCEEDED"),
                tasks.steps(id).stream().map(TaskStepRow::status).toList());
        long version = finished.version();
        mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultUrl").value("/api/assets/" + finished.resultAssetId() + "/content"));
        mvc.perform(get("/api/tasks").cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        assertEquals(version, tasks.findById(id).version());
        byte[] resultBytes = mvc.perform(get("/api/assets/{id}/content", finished.resultAssetId()).cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(resultBytes));
        assertNotNull(decoded);
        assertEquals(900, decoded.getWidth());
        assertEquals(600, decoded.getHeight());
        var bob = login("bob");
        mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(bob.token())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").cookie(cookie(bob.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/assets/{id}/content", finished.resultAssetId()).cookie(cookie(bob.token())))
                .andExpect(status().isNotFound());
        assertEquals(0, tasks.claim(id, LocalDateTime.now(ZoneOffset.UTC)));
        pipeline.run(id);
        assertEquals(version, tasks.findById(id).version());
    }

    @Test
    void httpAcceptanceReturnsWhileWorkerStageIsBlockedByControlledDelay() throws Exception {
        var login = login("alice");
        String input = upload(login.token());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TaskDelay controlled = ignored -> {
            if (entered.getCount() == 0) return;
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("stage release timed out");
        };
        TaskPipeline controlledPipeline = new TaskPipeline(tasks, templates, validator, storage,
                generator, controlled, transactions, json, clock);
        TaskWorker worker = new TaskWorker(transactions, controlledPipeline);
        worker.start();
        try {
            // submit() asserts HTTP 202 and Location before the worker can complete ASSET_CHECK.
            String id = submit(login.token(), input, null).get("id").asText();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            TaskRow during = tasks.findById(id);
            assertEquals("RUNNING", during.status());
            assertEquals("ASSET_CHECK", during.stage());
            assertEquals(List.of("RUNNING", "PENDING", "PENDING"),
                    tasks.steps(id).stream().map(TaskStepRow::status).toList());
            mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(login.token())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"));
            assertEquals(during.version(), tasks.findById(id).version());
            release.countDown();
            awaitStatus(id, "SUCCEEDED");
        } finally {
            release.countDown();
            worker.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(TaskStage.class)
    void specifiedStageFailureSkipsLaterSteps(TaskStage failed) throws Exception {
        var login = login("alice");
        String input = upload(login.token());
        String id = submit(login.token(), input, failed).get("id").asText();
        assertEquals(id, transactions.claimHead());
        pipeline.run(id);
        TaskRow row = tasks.findById(id);
        assertEquals("FAILED", row.status());
        assertEquals(failed.name(), row.failedStage());
        assertEquals("SIMULATED_FAILURE", row.errorCode());
        assertNotNull(row.errorMessage());
        assertTrue(!row.errorMessage().isBlank());
        assertNotNull(row.finishedAt());
        assertNull(row.resultAssetId());
        for (TaskStage stage : TaskStage.values()) {
            String expected = stage.order() < failed.order() ? "SUCCEEDED"
                    : stage == failed ? "FAILED" : "SKIPPED";
            assertEquals(expected, tasks.steps(id).get(stage.order()).status());
        }
        assertNotNull(tasks.steps(id).get(failed.order()).errorMessage());
        long version = row.version();
        mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedStage").value(failed.name()));
        assertEquals(version, tasks.findById(id).version());
        mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultUrl").doesNotExist());
        assertEquals(version, tasks.findById(id).version());
        assertEquals(0, tasks.claim(id, LocalDateTime.now(ZoneOffset.UTC)));
        pipeline.run(id);
        assertEquals(version, tasks.findById(id).version());
    }

    @Test
    void concurrentConditionalClaimIsSingleWinnerAndTasksRemainIndependent() throws Exception {
        var login = login("alice");
        String input = upload(login.token());
        String first = submit(login.token(), input, null).get("id").asText();
        String second = submit(login.token(), input, TaskStage.GENERATE).get("id").asText();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var claim = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("claim start timed out");
                return tasks.claim(first, LocalDateTime.now(ZoneOffset.UTC));
            };
            var a = pool.submit(claim);
            var b = pool.submit(claim);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(1, a.get(5, TimeUnit.SECONDS) + b.get(5, TimeUnit.SECONDS));
        } finally {
            go.countDown();
        }
        assertEquals("RUNNING", tasks.findById(first).status());
        pipeline.run(first);
        assertEquals(second, transactions.claimHead());
        pipeline.run(second);
        assertEquals("SUCCEEDED", tasks.findById(first).status());
        assertEquals("FAILED", tasks.findById(second).status());
        assertEquals("GENERATE", tasks.findById(second).failedStage());
        assertNotNull(tasks.findById(first).resultAssetId());
        assertNull(tasks.findById(second).resultAssetId());
        assertEquals(List.of("SUCCEEDED", "SUCCEEDED", "SUCCEEDED"),
                tasks.steps(first).stream().map(TaskStepRow::status).toList());
        assertEquals(List.of("SUCCEEDED", "FAILED", "SKIPPED"),
                tasks.steps(second).stream().map(TaskStepRow::status).toList());
    }

    @Test
    void mapperVersionAdvancesAtEveryBoundaryAndTerminalUpdatesAreRejected() throws Exception {
        var login = login("alice");
        String input = upload(login.token());
        String id = submit(login.token(), input, null).get("id").asText();
        assertEquals(0, tasks.findById(id).version());
        assertEquals(id, transactions.claimHead());
        assertEquals(1, tasks.findById(id).version());
        transactions.startStage(id, TaskStage.ASSET_CHECK);
        assertEquals(2, tasks.findById(id).version());
        transactions.completeStage(id, TaskStage.ASSET_CHECK);
        assertEquals(3, tasks.findById(id).version());
        transactions.startStage(id, TaskStage.GENERATE);
        assertEquals(4, tasks.findById(id).version());
        transactions.completeStage(id, TaskStage.GENERATE);
        assertEquals(5, tasks.findById(id).version());
        transactions.startStage(id, TaskStage.FINALIZE);
        assertEquals(6, tasks.findById(id).version());
        transactions.failStage(id, TaskStage.FINALIZE, "SIMULATED_FAILURE", "test failure");
        assertEquals(7, tasks.findById(id).version());
        assertEquals(0, tasks.claim(id, LocalDateTime.now(ZoneOffset.UTC)));
        assertEquals(0, tasks.startTaskStage(id, "FINALIZE", "FINALIZE"));
        assertEquals(0, tasks.failTask(id, "FINALIZE", "SIMULATED_FAILURE", "again",
                LocalDateTime.now(ZoneOffset.UTC)));
        assertEquals(7, tasks.findById(id).version());
        mvc.perform(get("/api/tasks/{id}", id).cookie(cookie(login.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertEquals(7, tasks.findById(id).version());
    }

    @Test
    void realMySqlConstraintsRejectInvalidStateAndDuplicatePosition() throws Exception {
        String serverVersion = jdbc.queryForObject("SELECT VERSION()", String.class);
        assertNotNull(serverVersion);
        assertTrue(serverVersion.startsWith("8.4."), "Expected MySQL 8.4, got " + serverVersion);
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2','3') AND success=1",
                Integer.class));
        var login = login("alice");
        String input = upload(login.token());
        String id = submit(login.token(), input, null).get("id").asText();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE tasks SET status='INVALID' WHERE id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> tasks.insertAsset(id, input, 0));
        assertEquals("QUEUED", tasks.findById(id).status());
        assertEquals(List.of(input), tasks.assetIds(id));
    }

    @Test
    void startupConvergenceFailsRunningAndKeepsQueued() throws Exception {
        var login = login("alice");
        String input = upload(login.token());
        String succeeded = submit(login.token(), input, null).get("id").asText();
        assertEquals(succeeded, transactions.claimHead());
        pipeline.run(succeeded);
        String failed = submit(login.token(), input, TaskStage.ASSET_CHECK).get("id").asText();
        assertEquals(failed, transactions.claimHead());
        pipeline.run(failed);
        long succeededVersion = tasks.findById(succeeded).version();
        long failedVersion = tasks.findById(failed).version();
        String running = submit(login.token(), input, null).get("id").asText();
        String betweenStages = submit(login.token(), input, null).get("id").asText();
        String queued = submit(login.token(), input, null).get("id").asText();
        assertEquals(running, transactions.claimHead());
        transactions.startStage(running, TaskStage.ASSET_CHECK);
        assertEquals(betweenStages, transactions.claimHead());
        transactions.startStage(betweenStages, TaskStage.ASSET_CHECK);
        transactions.completeStage(betweenStages, TaskStage.ASSET_CHECK);
        transactions.convergeInterrupted();
        assertEquals("FAILED", tasks.findById(running).status());
        assertEquals("PROCESS_INTERRUPTED", tasks.findById(running).errorCode());
        assertEquals(List.of("FAILED", "SKIPPED", "SKIPPED"),
                tasks.steps(running).stream().map(TaskStepRow::status).toList());
        assertEquals("QUEUED", tasks.findById(queued).status());
        assertEquals("SUCCEEDED", tasks.findById(succeeded).status());
        assertEquals("FAILED", tasks.findById(failed).status());
        assertEquals(succeededVersion, tasks.findById(succeeded).version());
        assertEquals(failedVersion, tasks.findById(failed).version());
        assertEquals("PROCESS_INTERRUPTED", tasks.findById(betweenStages).errorCode());
        assertEquals("GENERATE", tasks.findById(betweenStages).failedStage());
        assertEquals("GENERATE", tasks.findById(betweenStages).stage());
        assertEquals(List.of("SUCCEEDED", "FAILED", "SKIPPED"),
                tasks.steps(betweenStages).stream().map(TaskStepRow::status).toList());
        long version = tasks.findById(running).version();
        transactions.convergeInterrupted();
        assertEquals(version, tasks.findById(running).version());
        pipeline.run(running);
        assertEquals(version, tasks.findById(running).version());
        assertEquals(queued, transactions.claimHead());
        assertEquals("FAILED", tasks.findById(running).status());
        assertEquals("RUNNING", tasks.findById(queued).status());
    }

    @Test
    void twoImageTemplatePreservesSlotsAndRejectsDuplicateOrForeignAssets() throws Exception {
        var alice = login("alice");
        var bob = login("bob");
        String twoImageTemplate = "c1111111-1111-4111-8111-111111111111";
        String first = upload(alice.token(), twoImageTemplate);
        String second = upload(alice.token(), twoImageTemplate);
        String request = json.writeValueAsString(new CreateTaskRequest(twoImageTemplate,
                List.of(second, first), null));
        String response = mvc.perform(post("/api/tasks").cookie(cookie(alice.token()))
                        .header("Origin", ORIGIN).contentType("application/json").content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();
        taskIds.add(id);
        assertEquals(List.of(second, first), tasks.assetIds(id));
        mvc.perform(post("/api/tasks").cookie(cookie(alice.token())).header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content(json.writeValueAsString(new CreateTaskRequest(twoImageTemplate,
                                List.of(first, first), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ASSET"));
        mvc.perform(post("/api/tasks").cookie(cookie(alice.token())).header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content(json.writeValueAsString(new CreateTaskRequest(twoImageTemplate,
                                List.of(first), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSET_COUNT_MISMATCH"));
        mvc.perform(post("/api/tasks").cookie(cookie(bob.token())).header("Origin", ORIGIN)
                        .contentType("application/json").content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    @Test
    void taskAcceptanceRejectsMissingTemplateTamperedImageAndInvalidStoredSizeOrDimensions() throws Exception {
        var alice = login("alice");
        String input = upload(alice.token());
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE user_id=?", Integer.class,
                "a1111111-1111-4111-8111-111111111111");
        String missingTemplate = "c9999999-9999-4999-8999-999999999999";
        assertRejectedTask(alice.token(), missingTemplate, input, 404, "TEMPLATE_NOT_FOUND");

        AssetRow row = assets.findOwned(input, "a1111111-1111-4111-8111-111111111111");
        Path path = storage.resolveOwnedFile(row.storagePath());
        byte[] original = Files.readAllBytes(path);
        Files.write(path, new byte[original.length]);
        assertRejectedTask(alice.token(), TEMPLATE, input, 404, "ASSET_NOT_FOUND");
        Files.write(path, original);

        jdbc.update("UPDATE assets SET size_bytes=? WHERE id=?", PrivateImageStorage.MAX_BYTES + 1, input);
        assertRejectedTask(alice.token(), TEMPLATE, input, 404, "ASSET_NOT_FOUND");
        jdbc.update("UPDATE assets SET size_bytes=?,width=? WHERE id=?", row.sizeBytes(), row.width() + 1, input);
        assertRejectedTask(alice.token(), TEMPLATE, input, 404, "ASSET_NOT_FOUND");
        jdbc.update("UPDATE assets SET width=? WHERE id=?", row.width(), input);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE user_id=?", Integer.class,
                "a1111111-1111-4111-8111-111111111111"));
    }

    private void assertRejectedTask(String token, String templateId, String input,
                                    int expectedStatus, String code) throws Exception {
        String body = json.writeValueAsString(new CreateTaskRequest(templateId, List.of(input), null));
        mvc.perform(post("/api/tasks").cookie(cookie(token)).header("Origin", ORIGIN)
                        .contentType("application/json").content(body))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String upload(String token) throws Exception {
        return upload(token, TEMPLATE);
    }

    private SessionService.LoginResult login(String key) throws Exception {
        var result = sessions.login(key, null);
        sessionHashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(result.token().getBytes(StandardCharsets.US_ASCII))));
        return result;
    }

    private String upload(String token, String templateId) throws Exception {
        AssetResponse uploaded = assetService.upload(token, templateId,
                new MockMultipartFile("file", "sample.png", "image/png", tinyPng()));
        assetIds.add(uploaded.id());
        return uploaded.id();
    }

    private JsonNode submit(String token, String input, TaskStage failure) throws Exception {
        var simulation = json.createObjectNode();
        if (failure == null) simulation.putNull("failAtStage");
        else simulation.put("failAtStage", failure.name());
        var delays = simulation.putObject("stageDelayMs");
        for (TaskStage stage : TaskStage.values()) delays.put(stage.name(), 0);
        String body = json.writeValueAsString(new CreateTaskRequest(TEMPLATE, List.of(input), simulation));
        var response = mvc.perform(post("/api/tasks").cookie(cookie(token)).header("Origin", ORIGIN)
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/tasks/")))
                .andReturn().getResponse();
        JsonNode node = json.readTree(response.getContentAsString());
        taskIds.add(node.get("id").asText());
        return node;
    }

    private static Cookie cookie(String token) { return new Cookie(SessionService.COOKIE_NAME, token); }

    private void awaitStatus(String id, String status) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (status.equals(tasks.findById(id).status())) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertEquals(status, tasks.findById(id).status());
    }

    private static byte[] tinyPng() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
