package com.xiaoying.materialstudio.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.identity.SessionService;
import com.xiaoying.materialstudio.template.TemplateResponse;
import com.xiaoying.materialstudio.template.TemplateService;
import com.xiaoying.materialstudio.web.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final SessionService sessions;
    private final TemplateService templates;
    private final TaskAssetValidator validator;
    private final TaskAcceptance acceptance;
    private final TaskMapper tasks;
    private final ObjectMapper json;
    private final boolean demoMode;
    private final int defaultDelay;

    public TaskService(SessionService sessions, TemplateService templates, TaskAssetValidator validator,
                       TaskAcceptance acceptance, TaskMapper tasks, ObjectMapper json,
                       @Value("${app.demo-mode:true}") boolean demoMode,
                       @Value("${app.simulation.default-stage-delay-ms:2000}") int defaultDelay) {
        this.sessions = sessions;
        this.templates = templates;
        this.validator = validator;
        this.acceptance = acceptance;
        this.tasks = tasks;
        this.json = json;
        this.demoMode = demoMode;
        this.defaultDelay = defaultDelay;
    }

    public TaskResponse create(String token, CreateTaskRequest request) {
        String userId = sessions.requireUserId(token);
        if (request == null || request.templateId() == null) throw invalid();
        TemplateResponse template = templates.detail(request.templateId());
        if (request.assetIds() == null || request.assetIds().size() != template.requiredImageCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSET_COUNT_MISMATCH", "素材数量与模板要求不一致");
        }
        List<String> ids = new ArrayList<>();
        for (String raw : request.assetIds()) {
            if (raw == null) throw invalid();
            try {
                String id = UUID.fromString(raw).toString();
                if (!id.equalsIgnoreCase(raw)) throw new IllegalArgumentException();
                ids.add(id);
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_ASSET", "不能重复使用同一张素材");
        }
        SimulationSnapshot simulation = SimulationSnapshot.parse(request.simulation(), demoMode, defaultDelay);
        validator.validate(userId, template, ids, true);
        TaskRow row = acceptance.accept(userId, template, ids, simulation.toJson(json));
        List<TaskStepRow> steps = new ArrayList<>();
        for (TaskStage stage : TaskStage.values()) steps.add(new TaskStepRow(stage.name(), "PENDING", null, null, null));
        return TaskResponse.from(row, steps);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(String token, String rawId) {
        String userId = sessions.requireUserId(token);
        String id;
        try {
            id = UUID.fromString(rawId).toString();
            if (!id.equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        TaskRow row = tasks.findOwned(id, userId);
        if (row == null) throw notFound();
        return TaskResponse.from(row, tasks.steps(id));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> history(String token) {
        String userId = sessions.requireUserId(token);
        return tasks.history(userId).stream().map(row -> TaskResponse.from(row, tasks.steps(row.id()))).toList();
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "任务参数无效");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
    }
}
