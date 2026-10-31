package com.xiaoying.materialstudio.task;

import java.time.Instant;
import java.time.ZoneOffset;

public record TaskStepResponse(String stage, String status, Instant startedAt,
                               Instant finishedAt, String errorMessage) {
    static TaskStepResponse from(TaskStepRow row) {
        return new TaskStepResponse(row.stage(), row.status(), utc(row.startedAt()),
                utc(row.finishedAt()), row.errorMessage());
    }

    private static Instant utc(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
