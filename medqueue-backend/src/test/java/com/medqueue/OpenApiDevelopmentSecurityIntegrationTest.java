package com.medqueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_TEST_URL:jdbc:mysql://localhost:3306/medqueue_test?useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true}",
        "spring.datasource.username=${DB_TEST_USERNAME:root}",
        "spring.datasource.password=${DB_TEST_PASSWORD:root}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class OpenApiDevelopmentSecurityIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void developmentProfileKeepsSwaggerAndOpenApiAvailableWithoutAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
