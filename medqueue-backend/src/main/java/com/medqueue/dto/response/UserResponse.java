package com.medqueue.dto.response;

import com.medqueue.entity.Role;
import com.medqueue.entity.User;
import java.time.Instant;

public record UserResponse(Long id, String name, String email, Role role, String phone, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getPhone(), user.getCreatedAt());
    }
}
