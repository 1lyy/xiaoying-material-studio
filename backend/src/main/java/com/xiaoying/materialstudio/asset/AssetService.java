package com.xiaoying.materialstudio.asset;

import com.xiaoying.materialstudio.identity.SessionService;
import com.xiaoying.materialstudio.template.TemplateResponse;
import com.xiaoying.materialstudio.template.TemplateService;
import com.xiaoying.materialstudio.web.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {
    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private final SessionService sessions;
    private final TemplateService templates;
    private final PrivateImageStorage storage;
    private final AssetRecordWriter writer;
    private final AssetMapper mapper;
    private final Clock clock;

    public AssetService(SessionService sessions, TemplateService templates, PrivateImageStorage storage,
                        AssetRecordWriter writer, AssetMapper mapper, Clock clock) {
        this.sessions = sessions;
        this.templates = templates;
        this.storage = storage;
        this.writer = writer;
        this.mapper = mapper;
        this.clock = clock;
    }

    public AssetResponse upload(String token, String templateId, MultipartFile file) throws IOException {
        String userId = sessions.requireUserId(token);
        TemplateResponse template = templates.detail(templateId);
        // One multipart request carries one image. Exact slot count is checked when tasks are submitted.
        PrivateImageStorage.StoredImage saved = storage.save(file, template.maxFileSizeBytes(),
                template.acceptedMimeTypes());
        String id = UUID.randomUUID().toString();
        AssetRow row = new AssetRow(id, userId, template.id(), "UPLOAD", saved.storagePath(),
                saved.mimeType(), saved.sizeBytes(), saved.width(), saved.height(),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        try {
            writer.insert(row);
        } catch (RuntimeException exception) {
            boolean cleaned = storage.deleteNewFile(saved.storagePath());
            log.error("Asset insert failed; new file cleanup={} assetId={}", cleaned, id, exception);
            throw exception;
        }
        return new AssetResponse(id, "/api/assets/" + id + "/content", saved.mimeType(),
                saved.sizeBytes(), saved.width(), saved.height());
    }

    public ResponseEntity<Resource> content(String token, String assetId) throws IOException {
        String userId = sessions.requireUserId(token);
        String id;
        try {
            id = UUID.fromString(assetId).toString();
            if (!id.equalsIgnoreCase(assetId)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        AssetRow row = mapper.findOwned(id, userId);
        if (row == null || !(row.mimeType().equals("image/png") || row.mimeType().equals("image/jpeg"))) {
            throw notFound();
        }
        Path path = storage.resolveOwnedFile(row.storagePath());
        try {
            if (Files.size(path) != row.sizeBytes()) throw notFound();
        } catch (IOException exception) {
            throw notFound();
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(row.mimeType()))
                .contentLength(row.sizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "素材不存在");
    }
}
