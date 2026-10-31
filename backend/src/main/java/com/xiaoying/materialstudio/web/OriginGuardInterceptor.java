package com.xiaoying.materialstudio.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OriginGuardInterceptor implements HandlerInterceptor {
    private final Set<String> allowedOrigins;

    public OriginGuardInterceptor(@Value("${app.security.allowed-origins}") String configuredOrigins) {
        allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(OriginGuardInterceptor::validateOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed browser origin is required");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if (method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")) return true;

        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORIGIN_NOT_ALLOWED", "请求来源不被允许");
        }
        return true;
    }

    private static String validateOrigin(String value) {
        URI uri = URI.create(value);
        if (uri.getScheme() == null || uri.getHost() == null
                || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("APP_ALLOWED_ORIGINS must contain bare http(s) origins");
        }
        return value;
    }
}
