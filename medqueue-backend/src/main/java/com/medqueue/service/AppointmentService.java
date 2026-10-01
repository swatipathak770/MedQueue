package com.medqueue.service;

import com.medqueue.dto.request.BookingRequest;
import com.medqueue.entity.*;
import com.medqueue.event.QueueChangedEvent;
import com.medqueue.exception.ConflictException;
import com.medqueue.exception.ResourceNotFoundException;
import com.medqueue.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

@Service
public class AppointmentService {
    private static final EnumSet<AppointmentStatus> SLOT_OCCUPYING = EnumSet.of(AppointmentStatus.WAITING, AppointmentStatus.CALLED,
            AppointmentStatus.IN_PROGRESS, AppointmentStatus.DONE, AppointmentStatus.SKIPPED);
    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final SlotRepository slots;
    private final UserRepository users;
    private final QueueStateRepository queueStates;
    private final ApplicationEventPublisher events;
    private final AppointmentStatusAuditService statusAudit;
    public AppointmentService(AppointmentRepository appointments, DoctorRepository doctors, SlotRepository slots, UserRepository users,
                              QueueStateRepository queueStates,
                              ApplicationEventPublisher events, AppointmentStatusAuditService statusAudit) {
        this.appointments = appointments; this.doctors = doctors; this.slots = slots; this.users = users; this.queueStates = queueStates; this.events = events;
        this.statusAudit = statusAudit;
    }
    // READ_COMMITTED ensures reads after the doctor lock see the transaction that just released it.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Appointment book(String patientEmail, BookingRequest request) {
        if (request.appointmentDate().isBefore(LocalDate.now())) throw new ConflictException("Appointment date cannot be in the past");
        User patient = users.findByEmail(patientEmail).orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        Doctor doctor = doctors.findByIdForUpdate(request.doctorId()).orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        if (!doctor.isAvailable()) throw new ConflictException("Doctor is currently unavailable");
        if (request.appointmentDate().equals(LocalDate.now())
                && queueStates.findByDoctorIdAndQueueDate(doctor.getId(), request.appointmentDate()).map(s -> !s.isOpen()).orElse(false))
            throw new ConflictException("Queue is closed for this date");
        Slot slot = null;
        if (!request.walkIn() && request.slotId() == null) throw new ConflictException("A slot is required for an appointment booking");
        if (request.slotId() != null) {
            slot = slots.findById(request.slotId()).orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
            if (!slot.getDoctor().getId().equals(doctor.getId())) throw new ConflictException("Slot does not belong to the selected doctor");
            if (slot.getDayOfWeek() != request.appointmentDate().getDayOfWeek()) throw new ConflictException("Slot is not available on the selected date");
            if (request.appointmentDate().equals(LocalDate.now()) && !slot.getEndTime().isAfter(java.time.LocalTime.now()))
                throw new ConflictException("This slot has already ended");
            long booked = appointments.countByDoctorIdAndAppointmentDateAndSlotIdAndStatusIn(
                    doctor.getId(), request.appointmentDate(), slot.getId(), SLOT_OCCUPYING);
            if (booked >= slot.getSlotCapacity()) throw new ConflictException("Slot is fully booked");
        }
        int token = appointments.maxToken(doctor.getId(), request.appointmentDate()) + 1;
        Appointment created = appointments.saveAndFlush(new Appointment(patient, doctor, request.appointmentDate(), slot, token));
        statusAudit.recordInitial(created, patient.getId());
        events.publishEvent(new QueueChangedEvent(doctor.getId(), request.appointmentDate()));
        return created;
    }
    @Transactional(readOnly = true)
    public List<Appointment> history(String patientEmail) { return appointments.findByPatientEmailOrderByAppointmentDateDescTokenNumberDesc(patientEmail); }
    @Transactional(readOnly = true)
    public List<Appointment> doctorQueue(Long doctorId, LocalDate date) {
        return appointments.findByDoctorIdAndAppointmentDateOrderByTokenNumber(doctorId, date);
    }
    @Transactional(readOnly = true)
    public long position(Appointment a) {
        if (a.getStatus() != AppointmentStatus.WAITING) return 0;
        return appointments.countByDoctorIdAndAppointmentDateAndStatusInAndTokenNumberLessThan(a.getDoctor().getId(),
                a.getAppointmentDate(), List.of(AppointmentStatus.WAITING), a.getTokenNumber()) + 1;
    }
    @Transactional(readOnly = true)
    public long estimatedWaitMinutes(Appointment a, long position) {
        if (a.getStatus() != AppointmentStatus.WAITING || position == 0) return 0;
        long ahead = appointments.countByDoctorIdAndAppointmentDateAndStatusInAndTokenNumberLessThan(a.getDoctor().getId(),
                a.getAppointmentDate(), List.of(AppointmentStatus.WAITING, AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS), a.getTokenNumber());
        return ahead * a.getDoctor().getAvgConsultMinutes();
    }
}
