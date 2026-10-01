package com.medqueue.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 190)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Column(length = 30)
    private String phone;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() { }
    public User(String name, String email, String passwordHash, Role role, String phone) {
        this.name = name; this.email = email; this.passwordHash = passwordHash; this.role = role; this.phone = phone;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
}
