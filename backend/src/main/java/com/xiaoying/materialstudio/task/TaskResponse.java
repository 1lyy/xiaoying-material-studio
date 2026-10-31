package com.xiaoying.materialstudio.task;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record TaskResponse(String id, String templateId, String templateTitle, String status,
                           String stage, Instant createdAt, Instant startedAt, Instant finishedAt,
                           String resultUrl, String failedStage, String errorCode,
                           String errorMessage, long version, List<TaskStepResponse> steps) {
    static TaskResponse from(TaskRow row, List<TaskStepRow> steps) {
        return new TaskResponse(row.id(), row.templateId(), row.templateTitle(), row.status(),
                row.stage(), utc(row.createdAt()), utc(row.startedAt()), utc(row.finishedAt()),
                row.resultAssetId() == null ? null : "/api/assets/" + row.resultAssetId() + "/content",
                row.failedStage(), row.errorCode(), row.errorMessage(), row.version(),
                steps.stream().map(TaskStepResponse::from).toList());
    }

    private static Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
