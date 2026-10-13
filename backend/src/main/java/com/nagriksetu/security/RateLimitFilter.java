package com.nagriksetu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nagriksetu.common.ErrorResponse;
import com.nagriksetu.config.NagrikProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, in-memory rate limiting for sign-in/registration, complaint submission and other writes.
 * Suitable for a single backend instance; use a shared store (e.g. Redis) when scaling horizontally.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Window(long startMillis, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final NagrikProperties.RateLimit limits;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(NagrikProperties properties, ObjectMapper objectMapper) {
        this.limits = properties.security().rateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        UserPrincipal user = CurrentUser.orNull();
        String who = user != null ? "u" + user.id() : "ip" + request.getRemoteAddr();

        boolean allowed = true;
        if ("POST".equals(method) && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
            allowed = tryAcquire("auth:" + request.getRemoteAddr(), limits.authPerMinute(), Duration.ofMinutes(1));
        } else if ("POST".equals(method) && path.equals("/api/complaints")) {
            allowed = tryAcquire("complaint:" + who, limits.complaintsPerHour(), Duration.ofHours(1));
        } else if (!"GET".equals(method) && !"OPTIONS".equals(method) && !"HEAD".equals(method)
                && path.startsWith("/api/")) {
            allowed = tryAcquire("write:" + who, limits.writesPerMinute(), Duration.ofMinutes(1));
        }

        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(429, "RATE_LIMITED",
                    "Too many requests. Please wait a moment and try again."));
            return;
        }
        chain.doFilter(request, response);
    }

    boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) ->
                existing == null || now - existing.startMillis() >= window.toMillis()
                        ? new Window(now, new AtomicInteger())
                        : existing);
        return w.count().incrementAndGet() <= limit;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void evictStaleWindows() {
        long cutoff = System.currentTimeMillis() - Duration.ofHours(2).toMillis();
        windows.entrySet().removeIf(e -> e.getValue().startMillis() < cutoff);
    }
}
