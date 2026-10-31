package com.xiaoying.materialstudio.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.asset.AssetRow;
import com.xiaoying.materialstudio.asset.PrivateImageStorage;
import com.xiaoying.materialstudio.template.TemplateResponse;
import com.xiaoying.materialstudio.template.TemplateService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskPipeline {
    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);
    private final TaskMapper tasks;
    private final TemplateService templates;
    private final TaskAssetValidator validator;
    private final PrivateImageStorage storage;
    private final TaskImageGenerator generator;
    private final TaskDelay delay;
    private final TaskTransactions transactions;
    private final ObjectMapper json;
    private final Clock clock;

    public TaskPipeline(TaskMapper tasks, TemplateService templates, TaskAssetValidator validator,
                        PrivateImageStorage storage, TaskImageGenerator generator, TaskDelay delay,
                        TaskTransactions transactions, ObjectMapper json, Clock clock) {
        this.tasks = tasks;
        this.templates = templates;
        this.validator = validator;
        this.storage = storage;
        this.generator = generator;
        this.delay = delay;
        this.transactions = transactions;
        this.json = json;
        this.clock = clock;
    }

    public void run(String id) {
        String traceId = UUID.randomUUID().toString();
        Path generated = null;
        try {
            TaskRow task = tasks.findById(id);
            if (task == null || !"RUNNING".equals(task.status())) return;
            SimulationSnapshot simulation = SimulationSnapshot.fromJson(task.simulationJson(), json);
            TemplateResponse template = templates.detail(task.templateId());
            List<String> assetIds = tasks.assetIds(id);
            List<AssetRow> inputs = null;
            for (TaskStage stage : TaskStage.values()) {
                if (Thread.currentThread().isInterrupted()) return;
                transactions.startStage(id, stage);
                try {
                    delay.pause(simulation.stageDelayMs().get(stage));
                    if (simulation.failAtStage() == stage) {
                        throw new StageFailure("SIMULATED_FAILURE", "模拟在“" + stage.displayName() + "”阶段失败");
                    }
                    switch (stage) {
                        case ASSET_CHECK -> {
                            if (assetIds.size() != template.requiredImageCount()) {
                                throw new StageFailure("ASSET_CHECK_FAILED", "素材数量已不符合模板要求");
                            }
                            inputs = validator.validate(task.userId(), template, assetIds, true);
                        }
                        case GENERATE -> {
                            generated = storage.createGeneratedTemp();
                            List<Path> paths = inputs.stream()
                                    .map(asset -> storage.resolveOwnedFile(asset.storagePath())).toList();
                            generator.generate(paths, generated);
                        }
                        case FINALIZE -> {
                            PrivateImageStorage.StoredImage saved = storage.persistGenerated(generated);
                            generated = null;
                            AssetRow result = new AssetRow(UUID.randomUUID().toString(), task.userId(),
                                    task.templateId(), "RESULT", saved.storagePath(), saved.mimeType(),
                                    saved.sizeBytes(), saved.width(), saved.height(),
                                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
                            try {
                                transactions.completeFinal(id, result);
                            } catch (RuntimeException exception) {
                                boolean cleaned = storage.deleteNewFile(saved.storagePath());
                                log.error("Final transaction failed taskId={} traceId={} cleanup={}",
                                        id, traceId, cleaned, exception);
                                throw exception;
                            }
                        }
                    }
                    if (stage != TaskStage.FINALIZE) transactions.completeStage(id, stage);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    log.info("Worker interrupted taskId={} traceId={}", id, traceId);
                    return; // Next startup converges this RUNNING task; no resumable execution claim.
                } catch (StageFailure exception) {
                    transactions.failStage(id, stage, exception.code, exception.getMessage());
                    log.warn("Task failed taskId={} traceId={} stage={} code={}",
                            id, traceId, stage, exception.code);
                    return;
                } catch (Exception exception) {
                    if (exception instanceof org.springframework.dao.DataAccessException
                            || exception instanceof org.springframework.transaction.TransactionException
                            || exception instanceof TaskStateConflict) {
                        throw (RuntimeException) exception;
                    }
                    String code = switch (stage) {
                        case ASSET_CHECK -> "ASSET_CHECK_FAILED";
                        case GENERATE -> "GENERATION_FAILED";
                        case FINALIZE -> "FINALIZE_FAILED";
                    };
                    transactions.failStage(id, stage, code, "制作在“" + stage.displayName() + "”阶段失败，请重新提交任务");
                    log.error("Task stage error taskId={} traceId={} stage={}", id, traceId, stage, exception);
                    return;
                }
            }
        } catch (Exception exception) {
            log.error("Worker stopped taskId={} traceId={}; startup convergence will handle RUNNING state",
                    id, traceId, exception);
        } finally {
            storage.deleteGeneratedTemp(generated);
        }
    }

    private static final class StageFailure extends RuntimeException {
        private final String code;
        private StageFailure(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
