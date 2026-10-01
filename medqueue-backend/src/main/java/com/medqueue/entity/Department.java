package com.medqueue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(name = "uk_departments_name", columnNames = "name"))
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    protected Department() { }
    public Department(String name) { this.name = name; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public void rename(String name) { this.name = name; }
}
