package com.medqueue.service;

import com.medqueue.dto.request.RegisterRequest;
import com.medqueue.entity.Role;
import com.medqueue.entity.User;
import com.medqueue.exception.ConflictException;
import com.medqueue.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users; this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new ConflictException("An account with this email already exists");
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.PATIENT,
                request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        try {
            return users.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("An account with this email already exists");
        }
    }
}
