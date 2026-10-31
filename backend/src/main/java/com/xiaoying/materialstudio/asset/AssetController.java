package com.xiaoying.materialstudio.asset;

import com.xiaoying.materialstudio.identity.SessionService;
import com.xiaoying.materialstudio.web.ApiException;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetResponse> upload(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestParam("templateId") List<String> templateIds,
            @RequestParam("file") List<MultipartFile> files) throws IOException {
        if (templateIds.size() != 1 || files.size() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "每次只能上传一张图片并指定一个模板");
        }
        return ResponseEntity.status(201)
                .header("Cache-Control", "private, no-store")
                .body(service.upload(token, templateIds.getFirst(), files.getFirst()));
    }

    @GetMapping("/{assetId}/content")
    public ResponseEntity<Resource> content(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @PathVariable String assetId) throws IOException {
        return service.content(token, assetId);
    }
}
