package com.xiaoying.materialstudio.identity;

import com.xiaoying.materialstudio.web.ApiException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final SessionService service;
    private final boolean demoMode;
    private final boolean cookieSecure;

    public SessionController(SessionService service,
                             @Value("${app.demo-mode:true}") boolean demoMode,
                             @Value("${app.session.cookie-secure:false}") boolean cookieSecure) {
        this.service = service;
        this.demoMode = demoMode;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/demo-users")
    public List<DemoUserResponse> demoUsers() {
        requireDemoMode();
        return service.listDemoUsers();
    }

    @PostMapping("/session")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request,
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String priorToken) {
        requireDemoMode();
        if (request == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求体无效");
        SessionService.LoginResult result = service.login(request.demoUserKey(), priorToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(result.token(), result.ttl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.user());
    }

    @GetMapping("/session")
    public ResponseEntity<UserResponse> current(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.current(token));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        service.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(SessionService.COOKIE_NAME, value)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private void requireDemoMode() {
        if (!demoMode) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在");
    }
}
