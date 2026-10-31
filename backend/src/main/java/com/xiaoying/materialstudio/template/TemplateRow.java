package com.xiaoying.materialstudio.template;

public record TemplateRow(String id, String title, String category, String description,
                          String coverPath, Integer requiredImageCount,
                          String acceptedMimeTypesJson, Long maxFileSizeBytes) {
}
