package com.xiaoying.materialstudio.asset;

public record AssetResponse(String id, String previewUrl, String mimeType,
                            long sizeBytes, int width, int height) {
}
