package com.xiaoying.materialstudio.asset;

import java.time.LocalDateTime;

public record AssetRow(String id, String userId, String templateId, String purpose,
                       String storagePath, String mimeType, long sizeBytes,
                       int width, int height, LocalDateTime createdAt) {
}
