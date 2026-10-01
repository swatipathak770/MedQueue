package com.medqueue.service;

import com.medqueue.dto.request.LoginRequest;
import com.medqueue.dto.response.AuthResponse;
import com.medqueue.dto.response.UserResponse;
import com.medqueue.repository.UserRepository;
import com.medqueue.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LoginService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;
    public LoginService(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService) {
        this.authenticationManager = authenticationManager; this.users = users; this.jwtService = jwtService;
    }
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        var account = users.findByEmail(authentication.getName()).orElseThrow();
        return new AuthResponse(jwtService.generateToken((org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal()),
                "Bearer", jwtService.getExpirationSeconds(), UserResponse.from(account));
    }
}
