package com.medqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final String PRODUCTION_INGRESS_HEADER = "X-MedQueue-Ingress-Peer";
    private static final String UNRESOLVED_PRODUCTION_PEER = "unresolved-production-ingress";
    private static final String MESSAGE = "Too many authentication attempts. Please wait before trying again.";

    private final InMemoryAuthRateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean production;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int registrationLimit;
    private final Duration registrationWindow;

    @Autowired
    public AuthRateLimitFilter(InMemoryAuthRateLimiter limiter, ObjectMapper objectMapper, Environment environment,
            @Value("${medqueue.auth-rate-limit.enabled:true}") boolean enabled,
            @Value("${medqueue.auth-rate-limit.login.limit:300}") int loginLimit,
            @Value("${medqueue.auth-rate-limit.login.window-seconds:300}") long loginWindowSeconds,
            @Value("${medqueue.auth-rate-limit.registration.limit:120}") int registrationLimit,
            @Value("${medqueue.auth-rate-limit.registration.window-seconds:3600}") long registrationWindowSeconds) {
        this(limiter, objectMapper, enabled, environment.acceptsProfiles(Profiles.of("prod")), loginLimit,
                Duration.ofSeconds(loginWindowSeconds), registrationLimit, Duration.ofSeconds(registrationWindowSeconds));
    }

    AuthRateLimitFilter(InMemoryAuthRateLimiter limiter, ObjectMapper objectMapper, boolean enabled, boolean production,
            int loginLimit, Duration loginWindow, int registrationLimit, Duration registrationWindow) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.production = production;
        this.loginLimit = loginLimit;
        this.loginWindow = loginWindow;
        this.registrationLimit = registrationLimit;
        this.registrationWindow = registrationWindow;
        if (enabled && (loginLimit < 1 || loginWindow.isZero() || loginWindow.isNegative()
                || registrationLimit < 1 || registrationWindow.isZero() || registrationWindow.isNegative())) {
            throw new IllegalArgumentException("Authentication rate limits and windows must be positive");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"/api/auth/login".equals(path) && !"/api/auth/register".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean login = "/api/auth/login".equals(path);
        String address = resolveAddress(request);
        int limit = login ? loginLimit : registrationLimit;
        Duration window = login ? loginWindow : registrationWindow;
        InMemoryAuthRateLimiter.Decision decision = limiter.acquire(path + "|" + address, limit, window);
        if (!decision.allowed()) {
            writeTooManyRequests(request, response, decision.retryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveAddress(HttpServletRequest request) {
        String candidate;
        if (production) {
            // Nginx overwrites this private header with its socket peer address. Never trust browser
            // X-Forwarded-For or fall back to a forwarded address if the trusted header is absent.
            candidate = request.getHeader(PRODUCTION_INGRESS_HEADER);
            if (candidate == null) return UNRESOLVED_PRODUCTION_PEER;
        } else {
            // Development and test use the direct socket peer; forwarding headers are ignored.
            candidate = request.getRemoteAddr();
        }
        return normalizeAddress(candidate).orElse(production ? UNRESOLVED_PRODUCTION_PEER : "unresolved-local-peer");
    }

    private java.util.Optional<String> normalizeAddress(String candidate) {
        if (candidate == null || candidate.length() > 45 || !candidate.matches("[0-9a-fA-F:.]+")) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(InetAddress.getByName(candidate).getHostAddress());
        } catch (IOException invalidAddress) {
            return java.util.Optional.empty();
        }
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", MESSAGE);
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
