package com.xiaoying.materialstudio.task;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record CreateTaskRequest(String templateId, List<String> assetIds, JsonNode simulation) {
}
