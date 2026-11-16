package com.medqueue;

import com.medqueue.config.SecurityConfig;
import com.medqueue.security.JwtAuthenticationFilter;
import com.medqueue.security.JwtService;
import com.medqueue.service.MedQueueUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the actual prod profile and SecurityConfig without creating a datasource. */
@WebMvcTest(controllers = OpenApiProductionSecurityIntegrationTest.MarkerController.class,
        properties = "CORS_ALLOWED_ORIGINS=https://app.example.test", excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@ActiveProfiles("prod")
@ContextConfiguration(classes = OpenApiProductionSecurityIntegrationTest.TestBootConfiguration.class)
@Import({SecurityConfig.class, OpenApiProductionSecurityIntegrationTest.TestJwtFilterConfiguration.class})
class OpenApiProductionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;

    @Test
    void usesProductionWithoutDevelopmentProfile() {
        assertThat(environment.acceptsProfiles(Profiles.of("prod"))).isTrue();
        assertThat(environment.acceptsProfiles(Profiles.of("dev"))).isFalse();
    }

    @Test
    void swaggerAndOpenApiRoutesReceiveProductionAuthenticationDenial() throws Exception {
        for (String path : new String[]{
                "/swagger-ui.html",
                "/swagger-ui/",
                "/swagger-ui/index.html",
                "/swagger-ui/oauth2-redirect.html",
                "/v3/api-docs",
                "/v3/api-docs.yaml",
                "/v3/api-docs/swagger-config"
        }) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentication is required"));
        }
    }

    @Test
    void protectedRestApiStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/appointments/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @RestController
    static class MarkerController { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtFilterConfiguration {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            byte[] bytes = new byte[32];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 73 + 31);
            String testSigningKey = Base64.getEncoder().encodeToString(bytes);
            JwtService jwt = new JwtService(testSigningKey, 3600);
            return new JwtAuthenticationFilter(jwt, new MedQueueUserDetailsService(null));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestBootConfiguration { }
}
