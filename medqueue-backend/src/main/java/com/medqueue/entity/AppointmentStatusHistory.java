package com.medqueue.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "appointment_status_history", indexes =
        @Index(name = "idx_appointment_status_history_appointment_changed", columnList = "appointment_id, changed_at"))
public class AppointmentStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointment_status_history_appointment"))
    private Appointment appointment;
    @Enumerated(EnumType.STRING) @Column(name = "old_status", length = 20) private AppointmentStatus oldStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 20) private AppointmentStatus newStatus;
    @Column(name = "changed_at", nullable = false, updatable = false) private Instant changedAt;
    // USER:<users.id> identifies an authenticated actor without copying their email into the audit table.
    @Column(name = "changed_by", nullable = false, length = 40) private String changedBy;

    protected AppointmentStatusHistory() { }
    public AppointmentStatusHistory(Appointment appointment, AppointmentStatus oldStatus, AppointmentStatus newStatus, String changedBy) {
        this.appointment = appointment; this.oldStatus = oldStatus; this.newStatus = newStatus; this.changedBy = changedBy;
    }
    @PrePersist void onCreate() { if (changedAt == null) changedAt = Instant.now(); }
    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public AppointmentStatus getOldStatus() { return oldStatus; }
    public AppointmentStatus getNewStatus() { return newStatus; }
    public Instant getChangedAt() { return changedAt; }
    public String getChangedBy() { return changedBy; }
}
