package com.xiaoying.materialstudio.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.asset.AssetRow;
import com.xiaoying.materialstudio.asset.PrivateImageStorage;
import com.xiaoying.materialstudio.template.TemplateResponse;
import com.xiaoying.materialstudio.template.TemplateService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

class TaskPipelineTest {
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void successRunsThreeStagesInOrderAndProducesIndependentImage() throws Exception {
        Fixture fixture = fixture(null);
        fixture.pipeline.run("task");
        InOrder order = inOrder(fixture.transactions);
        order.verify(fixture.transactions).startStage("task", TaskStage.ASSET_CHECK);
        order.verify(fixture.transactions).completeStage("task", TaskStage.ASSET_CHECK);
        order.verify(fixture.transactions).startStage("task", TaskStage.GENERATE);
        order.verify(fixture.transactions).completeStage("task", TaskStage.GENERATE);
        order.verify(fixture.transactions).startStage("task", TaskStage.FINALIZE);
        order.verify(fixture.transactions).completeFinal(eq("task"), any(AssetRow.class));
        assertEquals(3, fixture.pauses[0]);
        try (var files = Files.list(root.resolve("case-null/uploads"))) {
            assertEquals(2, files.count()); // One input and one separately persisted result.
        }
    }

    @Test
    void eachConfiguredFailureStopsLaterStages() throws Exception {
        for (TaskStage failed : TaskStage.values()) {
            Fixture fixture = fixture(failed);
            fixture.pipeline.run("task");
            verify(fixture.transactions).failStage("task", failed, "SIMULATED_FAILURE",
                    "模拟在“" + failed.displayName() + "”阶段失败");
            verify(fixture.transactions, never()).completeFinal(eq("task"), any());
            for (TaskStage later : TaskStage.values()) {
                if (later.order() > failed.order()) {
                    verify(fixture.transactions, never()).startStage("task", later);
                }
            }
            try (var files = Files.list(root.resolve("case-" + failed + "/tmp"))) {
                assertFalse(files.findAny().isPresent());
            }
        }
    }

    @Test
    void finalDatabaseFailureCleansNewResultFile() throws Exception {
        Fixture fixture = fixture(null);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(fixture.transactions).completeFinal(eq("task"), any(AssetRow.class));
        fixture.pipeline.run("task");
        try (var files = Files.list(root.resolve("case-null/uploads"))) {
            assertEquals(1, files.count()); // Only the preexisting input remains.
        }
    }

    private Fixture fixture(TaskStage failAt) throws Exception {
        TaskMapper tasks = mock(TaskMapper.class);
        TemplateService templates = mock(TemplateService.class);
        TaskAssetValidator validator = mock(TaskAssetValidator.class);
        TaskTransactions transactions = mock(TaskTransactions.class);
        PrivateImageStorage storage = new PrivateImageStorage(root.resolve("case-" + failAt).toString());
        Path input = storage.createGeneratedTemp();
        Files.write(input, tinyPng());
        var saved = storage.persistGenerated(input);
        AssetRow asset = new AssetRow("asset", "user", "template", "UPLOAD", saved.storagePath(),
                saved.mimeType(), saved.sizeBytes(), saved.width(), saved.height(), LocalDateTime.now());
        TemplateResponse template = new TemplateResponse("template", "title", "category", "/cover", "desc",
                1, List.of("image/png"), 5242880);
        SimulationSnapshot simulation = SimulationSnapshot.parse(json.readTree("""
                {"stageDelayMs":{"ASSET_CHECK":0,"GENERATE":0,"FINALIZE":0}}
                """), true, 0);
        simulation = new SimulationSnapshot(failAt, simulation.stageDelayMs());
        when(tasks.findById("task")).thenReturn(new TaskRow("task", "user", "template", "title", "RUNNING",
                null, simulation.toJson(json), null, null, null, null, LocalDateTime.now(),
                LocalDateTime.now(), null, 1));
        when(tasks.assetIds("task")).thenReturn(List.of("asset"));
        when(templates.detail("template")).thenReturn(template);
        when(validator.validate("user", template, List.of("asset"), true)).thenReturn(List.of(asset));
        int[] pauses = {0};
        TaskDelay delay = milliseconds -> pauses[0]++;
        TaskImageGenerator generator = (inputs, output) -> Files.write(output, tinyPng());
        TaskPipeline pipeline = new TaskPipeline(tasks, templates, validator, storage, generator,
                delay, transactions, json, Clock.systemUTC());
        return new Fixture(pipeline, transactions, pauses, storage);
    }

    private static byte[] tinyPng() throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private record Fixture(TaskPipeline pipeline, TaskTransactions transactions, int[] pauses,
                           PrivateImageStorage storage) { }
}
