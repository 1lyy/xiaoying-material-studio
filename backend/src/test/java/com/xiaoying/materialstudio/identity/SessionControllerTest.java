package com.xiaoying.materialstudio.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xiaoying.materialstudio.web.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SessionControllerTest {
    @Test
    void nonDemoModeHidesUserSelection() {
        SessionService service = mock(SessionService.class);
        SessionController controller = new SessionController(service, false, true);

        ApiException users = assertThrows(ApiException.class, controller::demoUsers);
        ApiException login = assertThrows(ApiException.class,
                () -> controller.login(new LoginRequest("alice"), null));
        assertEquals(HttpStatus.NOT_FOUND, users.status());
        assertEquals(HttpStatus.NOT_FOUND, login.status());
        verifyNoInteractions(service);
    }

    @Test
    void secureCookieHasHttpOnlyLaxAndPath() {
        SessionService service = mock(SessionService.class);
        when(service.login("alice", null)).thenReturn(new SessionService.LoginResult(
                new UserResponse("a1111111-1111-4111-8111-111111111111", "Alice", "alice"),
                "test-token", Duration.ofHours(24)));
        SessionController controller = new SessionController(service, true, true);

        var response = controller.login(new LoginRequest("alice"), null);
        String cookie = response.getHeaders().getFirst("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("Path=/"));
    }
}
