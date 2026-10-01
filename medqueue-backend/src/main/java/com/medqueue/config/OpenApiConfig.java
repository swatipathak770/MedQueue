package com.medqueue.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI medQueueOpenApi() {
        String scheme = "Bearer Authentication";
        return new OpenAPI().info(new Info().title("MedQueue API").version("v1")
                        .description("Smart OPD appointment and queue management API. Use a JWT from /api/auth/login for protected operations."))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
