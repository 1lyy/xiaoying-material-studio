package com.xiaoying.materialstudio.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.materialstudio.identity.SessionService;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class AssetMySqlTest {
    private static final String ORIGIN = "http://127.0.0.1:3000";
    private static final String TEMPLATE = "c2222222-2222-4222-8222-222222222222";
    private static final String ALICE = "a1111111-1111-4111-8111-111111111111";
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "asset-http-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("app.storage.root", ROOT::toString);
        registry.add("app.security.allowed-origins", () -> ORIGIN);
        registry.add("app.demo-mode", () -> true);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final List<String> assetIds = new ArrayList<>();
    private final List<String> sessionHashes = new ArrayList<>();

    @AfterEach
    void cleanup() throws Exception {
        for (String id : assetIds) {
            List<String> paths = jdbc.queryForList("SELECT storage_path FROM assets WHERE id = ?", String.class, id);
            jdbc.update("DELETE FROM assets WHERE id = ?", id);
            for (String relative : paths) {
                if (relative.startsWith("uploads/") && !relative.contains("..")) {
                    Files.deleteIfExists(ROOT.resolve(relative));
                }
            }
        }
        for (String hash : sessionHashes) jdbc.update("DELETE FROM sessions WHERE token_hash = ?", hash);
    }

    @Test
    void validUploadPersistsOwnerAndPrivateContentIsOnlyVisibleToOwner() throws Exception {
        Cookie alice = login("alice");
        Cookie bob = login("bob");
        byte[] png = PrivateImageStorageTest.image("png");
        String responseBody = mvc.perform(multipart("/api/assets")
                        .file(PrivateImageStorageTest.file("sample.png", "image/png", png))
                        .param("templateId", TEMPLATE).cookie(alice).header("Origin", ORIGIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.width").value(3))
                .andExpect(jsonPath("$.height").value(2))
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode asset = json.readTree(responseBody);
        String id = asset.get("id").asText();
        assetIds.add(id);
        assertEquals("/api/assets/" + id + "/content", asset.get("previewUrl").asText());
        assertEquals(ALICE, jdbc.queryForObject("SELECT user_id FROM assets WHERE id = ?", String.class, id));
        assertEquals("UPLOAD", jdbc.queryForObject("SELECT purpose FROM assets WHERE id = ?", String.class, id));
        assertEquals("image/png", jdbc.queryForObject("SELECT mime_type FROM assets WHERE id = ?", String.class, id));
        assertEquals((long) png.length, (long) jdbc.queryForObject("SELECT size_bytes FROM assets WHERE id = ?", Long.class, id));
        assertEquals(3, (int) jdbc.queryForObject("SELECT width FROM assets WHERE id = ?", Integer.class, id));
        assertEquals(2, (int) jdbc.queryForObject("SELECT height FROM assets WHERE id = ?", Integer.class, id));
        String relative = jdbc.queryForObject("SELECT storage_path FROM assets WHERE id = ?", String.class, id);
        assertTrue(relative.startsWith("uploads/"));
        assertTrue(Files.exists(ROOT.resolve(relative)));

        byte[] fetched = mvc.perform(get("/api/assets/{id}/content", id).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, fetched);
        mvc.perform(get("/api/assets/{id}/content", id).cookie(bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
        mvc.perform(get("/api/assets/{id}/content", UUID.randomUUID()).cookie(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
        mvc.perform(get("/api/assets/{id}/content", id))
                .andExpect(status().isUnauthorized());
        Files.delete(ROOT.resolve(relative));
        mvc.perform(get("/api/assets/{id}/content", id).cookie(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    @Test
    void uploadErrorsKeepUniformBodyAndDoNotInsertAssets() throws Exception {
        Cookie alice = login("alice");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE user_id = ?", Integer.class, ALICE);
        assertUploadError(alice, PrivateImageStorageTest.file("empty.png", "image/png", new byte[0]), 400, "EMPTY_FILE");
        assertUploadError(alice, PrivateImageStorageTest.file("fake.png", "image/png", new byte[] {1, 2, 3}), 415, "INVALID_IMAGE");
        assertUploadError(alice, PrivateImageStorageTest.file("wrong.jpg", "image/jpeg", PrivateImageStorageTest.image("png")), 415, "INVALID_IMAGE");
        assertUploadError(alice, PrivateImageStorageTest.file("wrong.gif", "image/png", PrivateImageStorageTest.image("png")), 415, "INVALID_IMAGE");
        assertUploadError(alice, PrivateImageStorageTest.file("large.png", "image/png",
                new byte[(int) PrivateImageStorage.MAX_BYTES + 1]), 413, "FILE_TOO_LARGE");
        mvc.perform(multipart("/api/assets")
                        .file(PrivateImageStorageTest.file("sample.png", "image/png", PrivateImageStorageTest.image("png")))
                        .param("templateId", UUID.randomUUID().toString()).cookie(alice).header("Origin", ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
        mvc.perform(multipart("/api/assets")
                        .file(PrivateImageStorageTest.file("sample.png", "image/png", PrivateImageStorageTest.image("png")))
                        .param("templateId", TEMPLATE).header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(multipart("/api/assets")
                        .file(PrivateImageStorageTest.file("sample.png", "image/png", PrivateImageStorageTest.image("png")))
                        .param("templateId", TEMPLATE).cookie(alice))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        mvc.perform(multipart("/api/assets")
                        .file(PrivateImageStorageTest.file("one.png", "image/png", PrivateImageStorageTest.image("png")))
                        .file(PrivateImageStorageTest.file("two.png", "image/png", PrivateImageStorageTest.image("png")))
                        .param("templateId", TEMPLATE).cookie(alice).header("Origin", ORIGIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE user_id = ?", Integer.class, ALICE));
    }

    @Test
    void pathEscapeInStoredRowReturnsNotFound() throws Exception {
        Cookie alice = login("alice");
        String id = UUID.randomUUID().toString();
        assetIds.add(id);
        jdbc.update("""
                INSERT INTO assets (id,user_id,template_id,purpose,storage_path,mime_type,size_bytes,width,height,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                """, id, ALICE, TEMPLATE, "UPLOAD", "uploads/../../secret.png", "image/png", 1, 1, 1);
        mvc.perform(get("/api/assets/{id}/content", id).cookie(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    private void assertUploadError(Cookie cookie, MockMultipartFile file, int expectedStatus, String code)
            throws Exception {
        mvc.perform(multipart("/api/assets").file(file).param("templateId", TEMPLATE)
                        .cookie(cookie).header("Origin", ORIGIN))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.traceId").isString());
    }

    private Cookie login(String key) throws Exception {
        String cookie = mvc.perform(post("/api/session").header("Origin", ORIGIN)
                        .contentType("application/json").content("{\"demoUserKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        String token = cookie.split(";", 2)[0].split("=", 2)[1];
        sessionHashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII))));
        return new Cookie(SessionService.COOKIE_NAME, token);
    }
}
