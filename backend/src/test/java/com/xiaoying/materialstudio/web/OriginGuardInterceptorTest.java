package com.xiaoying.materialstudio.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginGuardInterceptorTest {
    private final OriginGuardInterceptor guard = new OriginGuardInterceptor("http://127.0.0.1:3000");

    @Test
    void unsafeRequestRequiresConfiguredOrigin() {
        MockHttpServletRequest missing = new MockHttpServletRequest("POST", "/api/session");
        ApiException denied = assertThrows(ApiException.class,
                () -> guard.preHandle(missing, new MockHttpServletResponse(), new Object()));
        assertEquals(HttpStatus.FORBIDDEN, denied.status());

        MockHttpServletRequest allowed = new MockHttpServletRequest("POST", "/api/session");
        allowed.addHeader("Origin", "http://127.0.0.1:3000");
        assertDoesNotThrow(() -> guard.preHandle(allowed, new MockHttpServletResponse(), new Object()));
    }
}
