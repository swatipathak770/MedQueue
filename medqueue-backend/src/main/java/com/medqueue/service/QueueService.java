package com.medqueue.service;

import com.medqueue.entity.*;
import com.medqueue.event.QueueChangedEvent;
import com.medqueue.exception.ConflictException;
import com.medqueue.exception.ResourceNotFoundException;
import com.medqueue.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class QueueService {
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final QueueStateRepository states;
    private final ApplicationEventPublisher events;
    public QueueService(DoctorRepository doctors, AppointmentRepository appointments, QueueStateRepository states, ApplicationEventPublisher events) {
        this.doctors = doctors; this.appointments = appointments; this.states = states; this.events = events;
    }
    @Transactional(readOnly = true)
    public com.medqueue.dto.response.QueueSummaryResponse today(String email) {
        Doctor doctor = doctorByEmail(email); LocalDate today = LocalDate.now();
        QueueState state = states.findByDoctorIdAndQueueDate(doctor.getId(), today).orElse(null);
        long position = 0, activeAhead = 0;
        var entries = new java.util.ArrayList<com.medqueue.dto.response.AppointmentResponse>();
        for (var a : appointments.findByDoctorIdAndAppointmentDateOrderByTokenNumber(doctor.getId(), today)) {
            long currentPosition = a.getStatus() == AppointmentStatus.WAITING ? ++position : 0;
            long wait = a.getStatus() == AppointmentStatus.WAITING ? (currentPosition - 1 + activeAhead) * doctor.getAvgConsultMinutes() : 0;
            entries.add(com.medqueue.dto.response.AppointmentResponse.from(a, currentPosition, wait));
            if (a.getStatus() == AppointmentStatus.CALLED || a.getStatus() == AppointmentStatus.IN_PROGRESS) activeAhead++;
        }
        return new com.medqueue.dto.response.QueueSummaryResponse(doctor.getId(), today, doctor.isAvailable(), state == null || state.isOpen(),
                state == null ? 0 : state.getCurrentTokenNumber(), List.copyOf(entries));
    }
    // A fresh read after acquiring the doctor row lock avoids stale REPEATABLE_READ snapshots.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Appointment callNext(String email) {
        Doctor doctor = lockedDoctor(email); LocalDate today = LocalDate.now(); QueueState state = stateFor(doctor, today);
        if (!doctor.isAvailable()) throw new ConflictException("Doctor is marked unavailable");
        if (!state.isOpen()) throw new ConflictException("Queue is closed for today");
        if (appointments.existsByDoctorIdAndAppointmentDateAndStatusIn(doctor.getId(), today, List.of(AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS)))
            throw new ConflictException("Complete or skip the current patient before calling the next patient");
        Appointment next = appointments.findFirstByDoctorIdAndAppointmentDateAndStatusOrderByTokenNumberAsc(doctor.getId(), today, AppointmentStatus.WAITING)
                .orElseThrow(() -> new ConflictException("Queue is empty"));
        next.call(); state.setCurrentTokenNumber(next.getTokenNumber());
        events.publishEvent(new QueueChangedEvent(doctor.getId(), today));
        return next;
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Appointment complete(String email, Long appointmentId) {
        Doctor doctor = lockedDoctor(email); Appointment appointment = appointment(appointmentId);
        verifyOwner(doctor, appointment);
        if (appointment.getStatus() != AppointmentStatus.CALLED && appointment.getStatus() != AppointmentStatus.IN_PROGRESS)
            throw new ConflictException("Only a called or in-progress appointment can be completed");
        appointment.complete(); events.publishEvent(new QueueChangedEvent(doctor.getId(), appointment.getAppointmentDate())); return appointment;
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Appointment skip(String email, Long appointmentId) {
        Doctor doctor = lockedDoctor(email); Appointment appointment = appointment(appointmentId);
        verifyOwner(doctor, appointment);
        if (appointment.getStatus() != AppointmentStatus.WAITING && appointment.getStatus() != AppointmentStatus.CALLED)
            throw new ConflictException("Only a waiting or called appointment can be skipped");
        appointment.skip(); events.publishEvent(new QueueChangedEvent(doctor.getId(), appointment.getAppointmentDate())); return appointment;
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void setAvailability(String email, boolean available) {
        Doctor doctor = lockedDoctor(email); doctor.setAvailable(available); events.publishEvent(new QueueChangedEvent(doctor.getId(), LocalDate.now()));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void closeQueue(String email) {
        Doctor doctor = lockedDoctor(email); LocalDate today = LocalDate.now(); stateFor(doctor, today).setOpen(false);
        events.publishEvent(new QueueChangedEvent(doctor.getId(), today));
    }
    private QueueState stateFor(Doctor doctor, LocalDate date) {
        return states.findByDoctorIdAndQueueDate(doctor.getId(), date).orElseGet(() -> states.save(new QueueState(doctor, date)));
    }
    private Doctor lockedDoctor(String email) {
        Doctor found = doctorByEmail(email);
        return doctors.findByIdForUpdate(found.getId()).orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
    }
    private Doctor doctorByEmail(String email) { return doctors.findByUserEmail(email).orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found")); }
    private Appointment appointment(Long id) { return appointments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Appointment not found")); }
    private void verifyOwner(Doctor doctor, Appointment appointment) {
        if (!appointment.getDoctor().getId().equals(doctor.getId())) throw new org.springframework.security.access.AccessDeniedException("Appointment belongs to another doctor");
    }
}
