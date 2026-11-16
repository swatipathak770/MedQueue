package com.medqueue.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigProfileTest {
    @Test
    void swaggerIsPublicOnlyInDevelopmentAndNeverWhenProductionIsActive() {
        MockEnvironment development = new MockEnvironment();
        MockEnvironment production = new MockEnvironment();
        MockEnvironment combined = new MockEnvironment();
        development.setActiveProfiles("dev");
        production.setActiveProfiles("prod");
        combined.setActiveProfiles("dev", "prod");

        assertThat(SecurityConfig.isOpenApiPublic(development)).isTrue();
        assertThat(SecurityConfig.isOpenApiPublic(production)).isFalse();
        assertThat(SecurityConfig.isOpenApiPublic(combined)).isFalse();
        assertThat(SecurityConfig.isProduction(combined)).isTrue();
    }
}
