package com.medqueue.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fails production startup when security-sensitive settings are absent or unsafe. */
@Component
@Profile("prod")
public class ProductionSecurityValidator implements InitializingBean {
    private final String allowedOrigins;
    private final String jwtSecret;
    private final long jwtExpirationSeconds;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public ProductionSecurityValidator(
            @Value("${medqueue.cors.allowed-origins}") String allowedOrigins,
            @Value("${medqueue.jwt.secret}") String jwtSecret,
            @Value("${medqueue.jwt.expiration-seconds}") long jwtExpirationSeconds,
            @Value("${medqueue.bootstrap-admin.email:}") String bootstrapEmail,
            @Value("${medqueue.bootstrap-admin.password:}") String bootstrapPassword) {
        this.allowedOrigins = allowedOrigins;
        this.jwtSecret = jwtSecret;
        this.jwtExpirationSeconds = jwtExpirationSeconds;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void afterPropertiesSet() {
        validate(allowedOrigins, jwtSecret, jwtExpirationSeconds, bootstrapEmail, bootstrapPassword);
    }

    static void validate(String allowedOrigins, String jwtSecret, long jwtExpirationSeconds,
                         String bootstrapEmail, String bootstrapPassword) {
        validateOrigins(allowedOrigins);
        validateJwt(jwtSecret, jwtExpirationSeconds);
        validateBootstrap(bootstrapEmail, bootstrapPassword);
    }

    private static void validateOrigins(String configuredOrigins) {
        if (!StringUtils.hasText(configuredOrigins)) {
            throw new IllegalStateException("Production requires at least one explicit HTTPS CORS origin");
        }
        String[] origins = configuredOrigins.split(",", -1);
        for (String value : origins) {
            String origin = value.trim();
            if (origin.isEmpty() || origin.contains("*")) {
                throw new IllegalStateException("Production CORS origins must be explicit HTTPS origins without wildcards");
            }
            try {
                URI uri = URI.create(origin);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                        || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || uri.getPort() == 0 || uri.getPort() > 65535) {
                    throw new IllegalStateException("Production CORS origins must be HTTPS origins without paths or credentials");
                }
            } catch (IllegalArgumentException malformed) {
                throw new IllegalStateException("Production CORS origins must be valid HTTPS origins");
            }
        }
    }

    private static void validateJwt(String secret, long expirationSeconds) {
        if (!StringUtils.hasText(secret) || containsPlaceholder(secret)) {
            throw new IllegalStateException("Production requires a non-placeholder JWT signing secret");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("Production JWT signing secret must be valid Base64 encoding at least 32 strong bytes");
        }
        if (decoded.length < 32 || distinctBytes(decoded) < 16 || shannonEntropy(decoded) < 3.5
                || containsPlaceholder(new String(decoded, StandardCharsets.UTF_8)) || hasLongSequence(decoded)) {
            throw new IllegalStateException("Production JWT signing secret must be valid Base64 encoding at least 32 strong bytes");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("Production JWT expiration must be a positive number of seconds");
        }
    }

    private static void validateBootstrap(String email, String password) {
        boolean hasEmail = StringUtils.hasText(email);
        boolean hasPassword = StringUtils.hasText(password);
        if (hasEmail != hasPassword) {
            throw new IllegalStateException("Admin bootstrap requires both explicit email and password values");
        }
        if (hasPassword && (password.trim().length() < 20 || containsPlaceholder(password))) {
            throw new IllegalStateException("Production admin bootstrap password must be explicit, non-placeholder, and at least 20 characters");
        }
    }

    private static boolean containsPlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("replace") || normalized.contains("placeholder")
                || normalized.contains("change-me") || normalized.contains("changeme")
                || normalized.contains("default") || normalized.contains("example")
                || normalized.contains("development") || normalized.contains("password")
                || normalized.contains("medqueue");
    }

    private static int distinctBytes(byte[] value) {
        return (int) java.util.stream.IntStream.range(0, 256)
                .filter(candidate -> contains(value, (byte) candidate)).count();
    }

    private static boolean contains(byte[] value, byte candidate) {
        for (byte item : value) if (item == candidate) return true;
        return false;
    }

    private static double shannonEntropy(byte[] value) {
        Map<Byte, Integer> frequencies = new HashMap<>();
        for (byte item : value) frequencies.merge(item, 1, Integer::sum);
        double entropy = 0;
        for (int frequency : frequencies.values()) {
            double probability = (double) frequency / value.length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy;
    }

    private static boolean hasLongSequence(byte[] value) {
        int ascending = 1;
        int descending = 1;
        for (int index = 1; index < value.length; index++) {
            ascending = value[index] == value[index - 1] + 1 ? ascending + 1 : 1;
            descending = value[index] == value[index - 1] - 1 ? descending + 1 : 1;
            if (ascending >= 12 || descending >= 12) return true;
        }
        return false;
    }
}
