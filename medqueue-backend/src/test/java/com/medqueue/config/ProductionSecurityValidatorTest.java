package com.medqueue.config;

import org.junit.jupiter.api.Test;
import com.medqueue.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.User;

import java.util.Base64;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {
    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x91, 0x2a, (byte) 0xe7, 0x43, 0x15, (byte) 0xb9, 0x5c, 0x03,
            (byte) 0xd2, 0x68, 0x31, (byte) 0xf4, 0x0b, (byte) 0xa6, 0x7d, 0x22,
            (byte) 0xc1, 0x4f, (byte) 0x88, 0x19, (byte) 0xed, 0x56, 0x34, (byte) 0xa3,
            0x0e, (byte) 0xd7, 0x62, 0x2c, (byte) 0xf1, 0x47, (byte) 0x9a, 0x05
    });

    @Test
    void acceptsExplicitHttpsOriginsStrongSecretAndDisabledBootstrap() {
        assertThatCode(() -> ProductionSecurityValidator.validate(
                "https://clinic.example.com,https://portal.example.com", VALID_SECRET, 3600, "", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsFreshlyGeneratedProductionSigningSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String generatedSecret = Base64.getEncoder().encodeToString(bytes);

        assertThatCode(() -> ProductionSecurityValidator.validate(
                "https://clinic.example.com", generatedSecret, 3600, "", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankWildcardHttpAndNonOriginCorsValues() {
        for (String origins : new String[] {"", "*", "https://*.example.com", "http://clinic.example.com",
                "https://clinic.example.com/path", "https://clinic.example.com,"}) {
            assertThatThrownBy(() -> ProductionSecurityValidator.validate(origins, VALID_SECRET, 3600, "", ""))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsMissingPlaceholderWeakAndLowEntropyJwtSecretsWithoutEchoingThem() {
        assertThatThrownBy(() -> ProductionSecurityValidator.validate(
                "https://clinic.example.com", "", 3600, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-placeholder");
        for (String secret : new String[] {"REPLACE_WITH_SECRET", Base64.getEncoder().encodeToString(new byte[32])}) {
            assertThatThrownBy(() -> ProductionSecurityValidator.validate(
                    "https://clinic.example.com", secret, 3600, "", ""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(secret);
        }
    }

    @Test
    void rejectsNonPositiveJwtExpiration() {
        assertThatThrownBy(() -> ProductionSecurityValidator.validate(
                "https://clinic.example.com", VALID_SECRET, 0, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiration");
    }

    @Test
    void jwtServiceSelectsMatchingHmacAlgorithmAndAppliesConfiguredExpiration() {
        var user = User.withUsername("patient@example.test").password("unused").authorities("ROLE_PATIENT").build();
        for (int keyLength : new int[] {32, 48, 64}) {
            String secret = secretOfLength(keyLength);
            JwtService jwt = new JwtService(secret, 3600);
            String token = jwt.generateToken(user);
            var parsed = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                    .build().parseSignedClaims(token);
            String expectedAlgorithm = keyLength >= 64 ? "HS512" : keyLength >= 48 ? "HS384" : "HS256";

            assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(expectedAlgorithm);
            assertThat(parsed.getPayload().getExpiration().getTime() - parsed.getPayload().getIssuedAt().getTime())
                    .isEqualTo(3_600_000L);
        }
    }

    private static String secretOfLength(int size) {
        byte[] bytes = new byte[size];
        for (int index = 0; index < size; index++) bytes[index] = (byte) (index * 73 + 31);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void bootstrapRequiresBothExplicitValuesAndRejectsShortOrPlaceholderPasswords() {
        for (String[] credentials : new String[][] {
                {"admin@example.com", ""}, {"", "long-enough-password-but-not-allowed"},
                {"admin@example.com", "short"}, {"admin@example.com", "REPLACE_WITH_A_LONG_PASSWORD"}
        }) {
            assertThatThrownBy(() -> ProductionSecurityValidator.validate(
                    "https://clinic.example.com", VALID_SECRET, 3600, credentials[0], credentials[1]))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatCode(() -> ProductionSecurityValidator.validate(
                "https://clinic.example.com", VALID_SECRET, 3600,
                "bootstrap@example.com", "S7!vQ2@rN9#kL4$wB8^x"))
                .doesNotThrowAnyException();
    }
}
