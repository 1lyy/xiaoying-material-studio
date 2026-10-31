package com.xiaoying.materialstudio.task;

import java.time.LocalDateTime;

public record TaskStepRow(String stage, String status, LocalDateTime startedAt,
                          LocalDateTime finishedAt, String errorMessage) {
}
