package com.medqueue.service;

import com.medqueue.dto.request.RegisterRequest;
import com.medqueue.entity.Role;
import com.medqueue.entity.User;
import com.medqueue.exception.ConflictException;
import com.medqueue.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(users, encoder);

    @Test void registersNormalizedEmailAsPatientAndHashesPassword() {
        when(users.existsByEmail("patient@example.com")).thenReturn(false);
        when(encoder.encode("long-password")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.register(new RegisterRequest(" Pat ", " PATIENT@Example.com ", "long-password", null));

        assertEquals("Pat", user.getName());
        assertEquals("patient@example.com", user.getEmail());
        assertEquals("bcrypt-hash", user.getPasswordHash());
        assertEquals(Role.PATIENT, user.getRole());
        verify(users).saveAndFlush(any(User.class));
    }

    @Test void rejectsDuplicateEmail() {
        when(users.existsByEmail("patient@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.register(
                new RegisterRequest("Pat", "patient@example.com", "long-password", null)));
        verify(users, never()).saveAndFlush(any());
    }

    @Test void handlesConcurrentDuplicateEmailInsert() {
        when(users.existsByEmail("patient@example.com")).thenReturn(false);
        when(encoder.encode(any())).thenReturn("hash");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(ConflictException.class, () -> service.register(
                new RegisterRequest("Pat", "patient@example.com", "long-password", null)));
    }
}
