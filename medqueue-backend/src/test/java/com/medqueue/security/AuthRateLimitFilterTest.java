package com.medqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void allowsRequestsBelowLimitAndReturnsConsistent429WithRetryAfterWhenExceeded() throws Exception {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = filter(clock, true, false, 2, 60, 10, 3600);
        AtomicInteger forwarded = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = run(filter, loginRequest("192.0.2.10", null), forwarded);
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse limited = run(filter, loginRequest("192.0.2.10", null), forwarded);

        assertEquals(2, forwarded.get());
        assertEquals(429, limited.getStatus());
        assertEquals("60", limited.getHeader("Retry-After"));
        assertEquals("application/json", limited.getContentType());
        assertEquals("Too many authentication attempts. Please wait before trying again.",
                JSON.readTree(limited.getContentAsString()).get("message").asText());
    }

    @Test
    void resetsTheWindowAfterItsConfiguredDuration() throws Exception {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = filter(clock, true, false, 1, 30, 1, 3600);
        AtomicInteger forwarded = new AtomicInteger();

        assertEquals(200, run(filter, loginRequest("192.0.2.11", null), forwarded).getStatus());
        assertEquals(429, run(filter, loginRequest("192.0.2.11", null), forwarded).getStatus());
        clock.advance(Duration.ofSeconds(30));
        assertEquals(200, run(filter, loginRequest("192.0.2.11", null), forwarded).getStatus());
    }

    @Test
    void loginAndRegistrationHaveIndependentLimits() throws Exception {
        AuthRateLimitFilter filter = filter(new MutableClock(), true, false, 1, 60, 1, 3600);
        AtomicInteger forwarded = new AtomicInteger();

        assertEquals(200, run(filter, loginRequest("192.0.2.12", null), forwarded).getStatus());
        assertEquals(429, run(filter, loginRequest("192.0.2.12", null), forwarded).getStatus());
        assertEquals(200, run(filter, registrationRequest("192.0.2.12", null), forwarded).getStatus());
        assertEquals(429, run(filter, registrationRequest("192.0.2.12", null), forwarded).getStatus());
    }

    @Test
    void malformedRequestsAreCountedWithoutReadingOrLoggingTheirBodies() throws Exception {
        AuthRateLimitFilter filter = filter(new MutableClock(), true, false, 1, 60, 5, 3600);
        AtomicInteger forwarded = new AtomicInteger();
        MockHttpServletRequest malformed = loginRequest("192.0.2.13", null);
        malformed.setContentType("application/json");
        malformed.setContent("{not-json-password-body".getBytes());

        assertEquals(200, run(filter, malformed, forwarded).getStatus());
        assertEquals(429, run(filter, loginRequest("192.0.2.13", null), forwarded).getStatus());
        assertEquals(1, forwarded.get());
    }

    @Test
    void productionIgnoresSpoofedForwardingHeadersWhenTrustedNginxHeaderIsAbsent() throws Exception {
        AuthRateLimitFilter filter = filter(new MutableClock(), true, true, 1, 60, 5, 3600);
        AtomicInteger forwarded = new AtomicInteger();

        assertEquals(200, run(filter, loginRequest("10.0.0.4", "198.51.100.1"), forwarded).getStatus());
        assertEquals(429, run(filter, loginRequest("10.0.0.5", "203.0.113.88"), forwarded).getStatus());
        assertEquals(1, forwarded.get());
    }

    @Test
    void disabledConfigurationPassesRequestsThrough() throws Exception {
        AuthRateLimitFilter filter = filter(new MutableClock(), false, false, 1, 60, 1, 60);
        AtomicInteger forwarded = new AtomicInteger();

        assertEquals(200, run(filter, loginRequest("192.0.2.14", null), forwarded).getStatus());
        assertEquals(200, run(filter, loginRequest("192.0.2.14", null), forwarded).getStatus());
        assertEquals(2, forwarded.get());
    }

    @Test
    void developmentTestAndProductionProfilesHaveSeparateRateLimitDefaults() throws Exception {
        Properties development = loadProperties("/application-dev.properties");
        Properties test = loadProperties("/application-test.properties");
        Properties production = loadProperties("/application-prod.properties");

        assertEquals("${AUTH_RATE_LIMIT_ENABLED:true}", development.getProperty("medqueue.auth-rate-limit.enabled"));
        assertEquals("${AUTH_RATE_LIMIT_ENABLED:false}", test.getProperty("medqueue.auth-rate-limit.enabled"));
        assertEquals("true", production.getProperty("medqueue.auth-rate-limit.enabled"));
        assertEquals("${AUTH_LOGIN_RATE_LIMIT:300}", production.getProperty("medqueue.auth-rate-limit.login.limit"));
        assertEquals("${AUTH_REGISTER_RATE_LIMIT:120}", production.getProperty("medqueue.auth-rate-limit.registration.limit"));
    }

    private static Properties loadProperties(String resource) throws Exception {
        Properties properties = new Properties();
        try (var stream = AuthRateLimitFilterTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing configuration resource " + resource);
            properties.load(stream);
        }
        return properties;
    }

    private static AuthRateLimitFilter filter(MutableClock clock, boolean enabled, boolean production,
            int loginLimit, long loginWindowSeconds, int registrationLimit, long registrationWindowSeconds) {
        return new AuthRateLimitFilter(new InMemoryAuthRateLimiter(clock), JSON, enabled, production,
                loginLimit, Duration.ofSeconds(loginWindowSeconds), registrationLimit,
                Duration.ofSeconds(registrationWindowSeconds));
    }

    private static MockHttpServletRequest loginRequest(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private static MockHttpServletRequest registrationRequest(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private static MockHttpServletResponse run(AuthRateLimitFilter filter, MockHttpServletRequest request,
            AtomicInteger forwarded) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> forwarded.incrementAndGet());
        return response;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
