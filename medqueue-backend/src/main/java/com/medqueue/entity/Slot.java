package com.medqueue.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "slots", indexes = @Index(name = "idx_slots_doctor_day", columnList = "doctor_id, day_of_week"))
public class Slot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false, length = 10) private DayOfWeek dayOfWeek;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Column(name = "slot_capacity", nullable = false) private int slotCapacity;
    protected Slot() { }
    public Slot(Doctor doctor, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int slotCapacity) {
        this.doctor = doctor; this.dayOfWeek = dayOfWeek; this.startTime = startTime; this.endTime = endTime; this.slotCapacity = slotCapacity;
    }
    public Long getId() { return id; }
    public Doctor getDoctor() { return doctor; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getSlotCapacity() { return slotCapacity; }
    public void update(Doctor doctor, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int slotCapacity) {
        this.doctor = doctor; this.dayOfWeek = dayOfWeek; this.startTime = startTime; this.endTime = endTime; this.slotCapacity = slotCapacity;
    }
}
