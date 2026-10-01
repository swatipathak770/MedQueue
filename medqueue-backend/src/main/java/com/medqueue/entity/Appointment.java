package com.medqueue.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "appointments",
        uniqueConstraints = @UniqueConstraint(name = "uk_appointments_doctor_date_token", columnNames = {"doctor_id", "appointment_date", "token_number"}),
        indexes = @Index(name = "idx_appointments_doctor_date_status", columnList = "doctor_id, appointment_date, status"))
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "patient_id", nullable = false) private User patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    @Column(name = "appointment_date", nullable = false) private LocalDate appointmentDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "slot_id") private Slot slot;
    @Column(name = "token_number", nullable = false) private int tokenNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AppointmentStatus status = AppointmentStatus.WAITING;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "called_at") private Instant calledAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private long version;
    protected Appointment() { }
    public Appointment(User patient, Doctor doctor, LocalDate date, Slot slot, int tokenNumber) {
        this.patient = patient; this.doctor = doctor; this.appointmentDate = date; this.slot = slot; this.tokenNumber = tokenNumber;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public User getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public Slot getSlot() { return slot; }
    public int getTokenNumber() { return tokenNumber; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void call() { status = AppointmentStatus.CALLED; calledAt = Instant.now(); }
    public void complete() { status = AppointmentStatus.DONE; completedAt = Instant.now(); }
    public void skip() { status = AppointmentStatus.SKIPPED; completedAt = Instant.now(); }
}
