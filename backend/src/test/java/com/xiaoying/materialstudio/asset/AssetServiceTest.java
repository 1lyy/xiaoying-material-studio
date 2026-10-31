package com.xiaoying.materialstudio.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xiaoying.materialstudio.identity.SessionService;
import com.xiaoying.materialstudio.template.TemplateResponse;
import com.xiaoying.materialstudio.template.TemplateService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetServiceTest {
    @TempDir Path root;

    @Test
    void databaseFailureDeletesNewlyMovedImage() throws Exception {
        SessionService sessions = mock(SessionService.class);
        TemplateService templates = mock(TemplateService.class);
        AssetRecordWriter writer = mock(AssetRecordWriter.class);
        when(sessions.requireUserId("token")).thenReturn("user-id");
        when(templates.detail("template-id")).thenReturn(new TemplateResponse("template-id", "title",
                "category", "/cover", "description", 1, List.of("image/png"), 5242880));
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(writer).insert(any());
        AssetService service = new AssetService(sessions, templates, new PrivateImageStorage(root.toString()),
                writer, mock(AssetMapper.class), Clock.systemUTC());
        assertThrows(IllegalStateException.class, () -> service.upload("token", "template-id",
                PrivateImageStorageTest.file("sample.png", "image/png", PrivateImageStorageTest.image("png"))));
        try (var uploads = Files.list(root.resolve("uploads"));
             var temporary = Files.list(root.resolve("tmp"))) {
            assertEquals(0, uploads.count());
            assertEquals(0, temporary.count());
        }
    }
}
