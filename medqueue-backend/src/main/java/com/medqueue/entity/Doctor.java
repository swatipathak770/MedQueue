package com.medqueue.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "doctors", uniqueConstraints = @UniqueConstraint(name = "uk_doctors_user", columnNames = "user_id"))
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false)
    private Department department;
    @Column(nullable = false, length = 120) private String specialization;
    @Column(name = "avg_consult_minutes", nullable = false) private int avgConsultMinutes;
    @Column(nullable = false) private boolean available = true;
    protected Doctor() { }
    public Doctor(User user, Department department, String specialization, int avgConsultMinutes) {
        this.user = user; this.department = department; this.specialization = specialization; this.avgConsultMinutes = avgConsultMinutes;
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public Department getDepartment() { return department; }
    public String getSpecialization() { return specialization; }
    public int getAvgConsultMinutes() { return avgConsultMinutes; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public void update(Department department, String specialization, int avgConsultMinutes) {
        this.department = department; this.specialization = specialization; this.avgConsultMinutes = avgConsultMinutes;
    }
}
