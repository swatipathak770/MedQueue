package com.medqueue.config;

import com.medqueue.entity.Role;
import com.medqueue.entity.User;
import com.medqueue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import java.util.Locale;

@Configuration
public class AdminBootstrapRunner {
    @Bean CommandLineRunner bootstrapAdmin(UserRepository users, PasswordEncoder passwords,
            @Value("${medqueue.bootstrap-admin.email:}") String email,
            @Value("${medqueue.bootstrap-admin.password:}") String password) {
        return args -> {
            if (!StringUtils.hasText(email) && !StringUtils.hasText(password)) return;
            if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || password.length() < 12)
                throw new IllegalStateException("Set both BOOTSTRAP_ADMIN_EMAIL and a BOOTSTRAP_ADMIN_PASSWORD of at least 12 characters");
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            var existing = users.findByEmail(normalized);
            if (existing.isPresent()) {
                if (existing.get().getRole() != Role.ADMIN) throw new IllegalStateException("Bootstrap admin email is already used by a non-admin account");
                return;
            }
            users.save(new User("MedQueue Administrator", normalized, passwords.encode(password), Role.ADMIN, null));
        };
    }
}
