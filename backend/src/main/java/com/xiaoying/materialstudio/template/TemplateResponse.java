package com.xiaoying.materialstudio.template;

import java.util.List;

public record TemplateResponse(String id, String title, String category, String coverUrl,
                               String description, int requiredImageCount,
                               List<String> acceptedMimeTypes, long maxFileSizeBytes) {
}
