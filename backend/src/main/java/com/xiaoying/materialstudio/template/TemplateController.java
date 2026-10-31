package com.xiaoying.materialstudio.template;

import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");
    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemplateResponse> list(@RequestParam(required = false) String category) {
        return service.list(category);
    }

    @GetMapping("/{templateId}")
    public TemplateResponse detail(@PathVariable String templateId) {
        return service.detail(templateId);
    }

    @GetMapping("/{templateId}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable String templateId) throws IOException {
        return ResponseEntity.ok()
                .contentType(SVG)
                .cacheControl(CacheControl.noStore())
                .body(service.cover(templateId));
    }
}
