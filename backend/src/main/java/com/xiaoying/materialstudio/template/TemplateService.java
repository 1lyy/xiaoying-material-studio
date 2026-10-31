package com.xiaoying.materialstudio.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.web.ApiException;
import com.xiaoying.materialstudio.asset.PrivateImageStorage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {
    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png");
    private final TemplateMapper mapper;
    private final ObjectMapper json;

    public TemplateService(TemplateMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(String category) {
        if (category != null && (category.isBlank() || category.length() > 80
                || category.codePoints().anyMatch(Character::isISOControl))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "模板分类参数无效");
        }
        List<TemplateRow> rows = category == null ? mapper.findAll() : mapper.findByCategory(category);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse detail(String id) {
        return toResponse(findRow(id));
    }

    @Transactional(readOnly = true)
    public byte[] cover(String id) throws IOException {
        TemplateRow row = findRow(id);
        String path = row.coverPath();
        if (path == null || !path.matches("demo/covers/[a-z0-9-]+\\.svg")) {
            throw new IllegalStateException("Invalid template cover path");
        }
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) throw new IllegalStateException("Template cover is missing");
        try (var input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }

    private TemplateRow findRow(String id) {
        String canonical;
        try {
            canonical = UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_ID", "模板 ID 无效");
        }
        if (!canonical.equalsIgnoreCase(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE_ID", "模板 ID 无效");
        }
        TemplateRow row = mapper.findById(canonical);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "模板不存在");
        return row;
    }

    private TemplateResponse toResponse(TemplateRow row) {
        try {
            List<String> mimeTypes = json.readValue(row.acceptedMimeTypesJson(), new TypeReference<List<String>>() {});
            if (mimeTypes.isEmpty() || !mimeTypes.stream().allMatch(ALLOWED_MIME::contains)) {
                throw new IllegalStateException("Invalid template MIME configuration");
            }
            if (row.requiredImageCount() < 1 || row.requiredImageCount() > 4
                    || row.maxFileSizeBytes() < 1 || row.maxFileSizeBytes() > PrivateImageStorage.MAX_BYTES) {
                throw new IllegalStateException("Invalid template upload limits");
            }
            return new TemplateResponse(row.id(), row.title(), row.category(),
                    "/api/templates/" + row.id() + "/cover", row.description(),
                    row.requiredImageCount(), List.copyOf(mimeTypes), row.maxFileSizeBytes());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid template MIME JSON", exception);
        }
    }
}
