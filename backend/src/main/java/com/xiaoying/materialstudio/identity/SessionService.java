package com.xiaoying.materialstudio.identity;

import com.xiaoying.materialstudio.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    public static final String COOKIE_NAME = "material_session";
    private static final int TOKEN_BYTES = 32;

    private final UserMapper users;
    private final SessionMapper sessions;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public SessionService(UserMapper users, SessionMapper sessions, Clock clock,
                          @Value("${app.session.ttl-hours:24}") long ttlHours) {
        if (ttlHours < 1 || ttlHours > 168) throw new IllegalArgumentException("Session TTL must be 1-168 hours");
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Transactional(readOnly = true)
    public List<DemoUserResponse> listDemoUsers() {
        return users.findAll().stream().map(DemoUserResponse::from).toList();
    }

    @Transactional
    public LoginResult login(String demoUserKey, String priorToken) {
        if (demoUserKey == null || demoUserKey.isBlank() || demoUserKey.length() > 32) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEMO_USER", "请选择有效的演示用户");
        }
        UserRow user = users.findByDemoKey(demoUserKey);
        if (user == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEMO_USER", "演示用户不存在");
        }
        if (validTokenShape(priorToken)) sessions.deleteByHash(hash(priorToken));

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        sessions.insert(hash(token), user.id(), utc(now), utc(expiresAt));
        return new LoginResult(UserResponse.from(user), token, ttl);
    }

    @Transactional(readOnly = true)
    public UserResponse current(String token) {
        if (!validTokenShape(token)) throw unauthenticated();
        String userId = sessions.findValidUserId(hash(token), utc(clock.instant()));
        if (userId == null) throw unauthenticated();
        UserRow user = users.findById(userId);
        if (user == null) throw unauthenticated();
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public String requireUserId(String token) {
        return current(token).id();
    }

    @Transactional
    public void logout(String token) {
        if (validTokenShape(token)) sessions.deleteByHash(hash(token));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static boolean validTokenShape(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先选择演示用户");
    }

    public record LoginResult(UserResponse user, String token, Duration ttl) {
    }
}
