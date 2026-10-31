package com.xiaoying.materialstudio.task;

import com.xiaoying.materialstudio.template.TemplateResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAcceptance {
    private final TaskMapper tasks;
    private final TaskAssetValidator validator;
    private final Clock clock;

    public TaskAcceptance(TaskMapper tasks, TaskAssetValidator validator, Clock clock) {
        this.tasks = tasks;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public TaskRow accept(String userId, TemplateResponse template, List<String> assetIds,
                          String simulationJson) {
        validator.validate(userId, template, assetIds, false);
        TaskRow row = new TaskRow(UUID.randomUUID().toString(), userId, template.id(), template.title(),
                "QUEUED", null, simulationJson, null, null, null, null,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), null, null, 0);
        Map<String, Object> fields = new HashMap<>();
        fields.put("id", row.id());
        fields.put("userId", row.userId());
        fields.put("templateId", row.templateId());
        fields.put("templateTitle", row.templateTitle());
        fields.put("simulationJson", row.simulationJson());
        fields.put("createdAt", row.createdAt());
        one(tasks.insert(fields));
        for (int i = 0; i < assetIds.size(); i++) one(tasks.insertAsset(row.id(), assetIds.get(i), i));
        for (TaskStage stage : TaskStage.values()) one(tasks.insertStep(row.id(), stage.name(), stage.order()));
        return row;
    }

    static void one(int changed) {
        if (changed != 1) throw new TaskStateConflict("Expected exactly one database row to change");
    }
}
