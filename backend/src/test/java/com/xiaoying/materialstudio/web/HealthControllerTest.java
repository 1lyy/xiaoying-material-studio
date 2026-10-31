package com.xiaoying.materialstudio.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class HealthControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new HealthController(), new ErrorProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void healthMatchesFrozenContract() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void apiExceptionsHaveUniformErrorBody() throws Exception {
        mvc.perform(get("/api/fail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    static class ErrorProbeController {
        @GetMapping("/api/fail")
        public void fail() {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在");
        }
    }
}
