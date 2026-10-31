package com.xiaoying.materialstudio.task;

import java.time.LocalDateTime;

public record TaskRow(String id, String userId, String templateId, String templateTitle,
                      String status, String stage, String simulationJson, String resultAssetId,
                      String failedStage, String errorCode, String errorMessage,
                      LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt,
                      long version) {
}
