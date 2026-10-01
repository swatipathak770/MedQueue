package com.medqueue.dto.response;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) { }
