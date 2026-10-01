package com.medqueue.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "queue_states", uniqueConstraints = @UniqueConstraint(name = "uk_queue_state_doctor_date", columnNames = {"doctor_id", "queue_date"}))
public class QueueState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    @Column(name = "queue_date", nullable = false) private LocalDate queueDate;
    @Column(name = "current_token_number", nullable = false) private int currentTokenNumber;
    @Column(name = "queue_open", nullable = false) private boolean open = true;
    @Column(name = "last_updated", nullable = false) private Instant lastUpdated;
    protected QueueState() { }
    public QueueState(Doctor doctor, LocalDate queueDate) { this.doctor = doctor; this.queueDate = queueDate; }
    @PrePersist @PreUpdate void updateTimestamp() { lastUpdated = Instant.now(); }
    public int getCurrentTokenNumber() { return currentTokenNumber; }
    public Doctor getDoctor() { return doctor; }
    public boolean isOpen() { return open; }
    public void setCurrentTokenNumber(int value) { currentTokenNumber = value; }
    public void setOpen(boolean open) { this.open = open; }
}
