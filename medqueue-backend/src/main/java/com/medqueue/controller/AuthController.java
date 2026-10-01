package com.medqueue.controller;

import com.medqueue.dto.request.LoginRequest;
import com.medqueue.dto.request.RegisterRequest;
import com.medqueue.dto.response.AuthResponse;
import com.medqueue.dto.response.UserResponse;
import com.medqueue.service.AuthService;
import com.medqueue.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginService loginService;
    public AuthController(AuthService authService, LoginService loginService) {
        this.authService = authService; this.loginService = loginService;
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request));
    }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return loginService.login(request); }
}
