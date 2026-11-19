package com.medqueue.config;

import com.medqueue.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final String allowedOrigins;
    private final ObjectMapper objectMapper;
    private final boolean openApiPublic;
    private final boolean production;
    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper,
                          @Value("${medqueue.cors.allowed-origins}") String allowedOrigins,
                          Environment environment) {
        this.jwtFilter = jwtFilter; this.objectMapper = objectMapper; this.allowedOrigins = allowedOrigins;
        this.production = isProduction(environment);
        this.openApiPublic = isOpenApiPublic(environment);
    }
    static boolean isProduction(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
    static boolean isOpenApiPublic(Environment environment) {
        return !isProduction(environment) && environment.acceptsProfiles(Profiles.of("dev"));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/api/auth/**", "/api/doctors/**", "/api/departments", "/ws/**", "/actuator/health", "/error").permitAll();
                    if (production) {
                        a.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").denyAll();
                    } else if (openApiPublic) {
                        a.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    a.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(request, response, 401, "Unauthorized", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(request, response, 403, "Forbidden", "Access is not allowed")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
    private void writeSecurityError(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                                    int status, String error, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now()); body.put("status", status); body.put("error", error);
        body.put("message", message); body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
    @Bean CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration restCors = corsConfiguration(false, false);
        CorsConfiguration webSocketCors = corsConfiguration(true, true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", webSocketCors);
        source.registerCorsConfiguration("/**", restCors);
        return source;
    }
    private CorsConfiguration corsConfiguration(boolean allowCredentials, boolean webSocket) {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (webSocket && !origins.contains("https://medqueue-red.vercel.app")) {
            origins.add("https://medqueue-red.vercel.app");
        }
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setAllowCredentials(allowCredentials);
        return cors;
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
