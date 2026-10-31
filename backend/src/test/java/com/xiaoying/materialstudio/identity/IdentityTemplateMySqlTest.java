package com.xiaoying.materialstudio.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.UUID;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.xiaoying.materialstudio.template.TemplateMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class IdentityTemplateMySqlTest {
    private static final String ORIGIN = "http://127.0.0.1:3000";
    private static final String ALICE_ID = "a1111111-1111-4111-8111-111111111111";
    private static final String BOB_ID = "b2222222-2222-4222-8222-222222222222";
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"),
            "identity-template-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("app.demo-mode", () -> true);
        registry.add("app.security.allowed-origins", () -> ORIGIN);
        registry.add("app.storage.root", STORAGE::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TemplateMapper templates;

    @Test
    void flywaySeedsTemplatesAndCoversWithExactCategoryFiltering() throws Exception {
        assertEquals(3, templates.findAll().size());
        assertEquals(7, (int) jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('users','sessions','templates','assets','tasks','task_assets','task_steps')
                """, Integer.class));
        mvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].acceptedMimeTypes").isArray());

        mvc.perform(get("/api/templates").param("category", "旅行影像"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requiredImageCount").value(2));

        mvc.perform(get("/api/templates").param("category", "不存在的分类"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/templates").param("category", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));

        mvc.perform(get("/api/templates/c1111111-1111-4111-8111-111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value("/api/templates/c1111111-1111-4111-8111-111111111111/cover"));

        mvc.perform(get("/api/templates/c1111111-1111-4111-8111-111111111111/cover"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"));

        mvc.perform(get("/api/templates/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE_ID"));
        mvc.perform(get("/api/templates/c9999999-9999-4999-8999-999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void aliceAndBobSessionsStaySeparateAndOnlyTokenHashesAreStored() throws Exception {
        mvc.perform(get("/api/demo-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        Cookie alice = login("alice", ALICE_ID);
        Cookie bob = login("bob", BOB_ID);

        mvc.perform(get("/api/session").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALICE_ID));
        mvc.perform(get("/api/session").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOB_ID));

        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(alice.getValue().getBytes(StandardCharsets.US_ASCII)));
        assertNotEquals(alice.getValue(), hash);
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE token_hash = ?", Integer.class, hash));
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE token_hash = ?", Integer.class, alice.getValue()));

        jdbc.update("""
                UPDATE sessions
                SET created_at = UTC_TIMESTAMP(6) - INTERVAL 2 HOUR,
                    expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR
                WHERE token_hash = ?
                """, hash);
        mvc.perform(get("/api/session").cookie(alice))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/session").header("Origin", ORIGIN).cookie(alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/session").cookie(alice))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/session").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOB_ID));
    }

    @Test
    void originAndRequestShapeAreCheckedBeforeCreatingSession() throws Exception {
        mvc.perform(post("/api/session").contentType("application/json")
                        .content("{\"demoUserKey\":\"alice\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

        mvc.perform(post("/api/session").header("Origin", "https://outside.example")
                        .contentType("application/json").content("{\"demoUserKey\":\"alice\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/session").header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content("{\"demoUserKey\":\"alice\",\"userId\":\"" + BOB_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/session").header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content("{\"demoUserKey\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEMO_USER"));
    }

    private Cookie login(String demoKey, String expectedId) throws Exception {
        String setCookie = mvc.perform(post("/api/session").header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content("{\"demoUserKey\":\"" + demoKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expectedId))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertTrue(setCookie.startsWith(SessionService.COOKIE_NAME + "="));
        return new Cookie(SessionService.COOKIE_NAME, setCookie.split(";", 2)[0].split("=", 2)[1]);
    }
}
