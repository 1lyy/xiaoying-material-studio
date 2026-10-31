package com.xiaoying.materialstudio.task;

import com.xiaoying.materialstudio.asset.AssetMapper;
import com.xiaoying.materialstudio.asset.AssetRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskTransactions {
    private static final String INTERRUPTED = "服务重启中断了制作，请重新提交任务";
    private final TaskMapper tasks;
    private final AssetMapper assets;
    private final Clock clock;

    public TaskTransactions(TaskMapper tasks, AssetMapper assets, Clock clock) {
        this.tasks = tasks;
        this.assets = assets;
        this.clock = clock;
    }

    @Transactional
    public String claimHead() {
        String id = tasks.queueHead();
        if (id == null) return null;
        return tasks.claim(id, now()) == 1 ? id : null;
    }

    @Transactional
    public void startStage(String id, TaskStage stage) {
        List<TaskStepRow> steps = tasks.steps(id);
        if (steps.size() != 3) throw new IllegalStateException("Task steps are incomplete");
        for (int i = 0; i < stage.order(); i++) {
            if (!"SUCCEEDED".equals(steps.get(i).status())) throw new IllegalStateException("Prior stage incomplete");
        }
        String previous = stage.order() == 0 ? null : TaskStage.values()[stage.order() - 1].name();
        TaskAcceptance.one(tasks.startTaskStage(id, previous, stage.name()));
        TaskAcceptance.one(tasks.startStep(id, stage.name(), now(), stage.order()));
    }

    @Transactional
    public void completeStage(String id, TaskStage stage) {
        if (stage == TaskStage.FINALIZE) throw new IllegalArgumentException("Use completeFinal");
        TaskAcceptance.one(tasks.completeStep(id, stage.name(), now()));
        TaskAcceptance.one(tasks.bumpAfterStep(id, stage.name()));
    }

    @Transactional
    public void completeFinal(String id, AssetRow result) {
        TaskRow task = tasks.findById(id);
        if (task == null || !task.userId().equals(result.userId())
                || !"RUNNING".equals(task.status()) || !"FINALIZE".equals(task.stage())) {
            throw new IllegalStateException("Result owner or stage mismatch");
        }
        TaskAcceptance.one(assets.insert(result.id(), result.userId(), result.templateId(),
                result.purpose(), result.storagePath(), result.mimeType(), result.sizeBytes(),
                result.width(), result.height(), result.createdAt()));
        TaskAcceptance.one(tasks.completeStep(id, TaskStage.FINALIZE.name(), now()));
        TaskAcceptance.one(tasks.succeed(id, result.id(), now()));
    }

    @Transactional
    public void failStage(String id, TaskStage stage, String code, String message) {
        LocalDateTime time = now();
        TaskAcceptance.one(tasks.failStep(id, stage.name(), message, time));
        int skipped = tasks.skipLater(id, stage.order());
        if (skipped != 2 - stage.order()) throw new TaskStateConflict("Unexpected later step state");
        TaskAcceptance.one(tasks.failTask(id, stage.name(), code, message, time));
    }

    @Transactional
    public void convergeInterrupted() {
        for (TaskRow row : tasks.running()) {
            TaskStage stage = row.stage() == null ? TaskStage.ASSET_CHECK : TaskStage.valueOf(row.stage());
            List<TaskStepRow> steps = tasks.steps(row.id());
            if (steps.size() != TaskStage.values().length) {
                throw new TaskStateConflict("Interrupted task has incomplete steps");
            }
            // A process can stop after one step commits and before the next starts.
            // Attribute the interruption to the first unfinished step, not the completed one.
            if ("SUCCEEDED".equals(steps.get(stage.order()).status())) {
                if (stage == TaskStage.FINALIZE) {
                    throw new TaskStateConflict("Interrupted task has no unfinished step");
                }
                stage = TaskStage.values()[stage.order() + 1];
            }
            LocalDateTime time = now();
            TaskAcceptance.one(tasks.interruptStep(row.id(), stage.name(), INTERRUPTED, time));
            tasks.skipLater(row.id(), stage.order());
            TaskAcceptance.one(tasks.interruptTask(row.id(), stage.name(), INTERRUPTED, time));
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
