package com.medqueue.service;

import com.medqueue.dto.request.*;
import com.medqueue.entity.*;
import com.medqueue.exception.ConflictException;
import com.medqueue.exception.ResourceNotFoundException;
import com.medqueue.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class DirectoryService {
    private static final java.util.Set<AppointmentStatus> SLOT_OCCUPYING = java.util.EnumSet.of(AppointmentStatus.WAITING,
            AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS, AppointmentStatus.DONE, AppointmentStatus.SKIPPED);
    private final DepartmentRepository departments;
    private final DoctorRepository doctors;
    private final SlotRepository slots;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AppointmentRepository appointments;
    private final QueueStateRepository queueStates;
    public DirectoryService(DepartmentRepository departments, DoctorRepository doctors, SlotRepository slots,
                            UserRepository users, PasswordEncoder passwords, AppointmentRepository appointments, QueueStateRepository queueStates) {
        this.departments = departments; this.doctors = doctors; this.slots = slots; this.users = users; this.passwords = passwords;
        this.appointments = appointments; this.queueStates = queueStates;
    }
    public List<Department> departments() { return departments.findAll(org.springframework.data.domain.Sort.by("name")); }
    @Transactional public Department createDepartment(DepartmentRequest request) {
        String name = request.name().trim();
        if (departments.existsByNameIgnoreCase(name)) throw new ConflictException("Department already exists");
        return departments.save(new Department(name));
    }
    @Transactional public Department updateDepartment(Long id, DepartmentRequest request) {
        Department d = department(id); String name = request.name().trim();
        if (!d.getName().equalsIgnoreCase(name) && departments.existsByNameIgnoreCase(name)) throw new ConflictException("Department already exists");
        d.rename(name); return d;
    }
    @Transactional public void deleteDepartment(Long id) {
        Department d = department(id);
        try { departments.delete(d); departments.flush(); } catch (DataIntegrityViolationException e) { throw new ConflictException("Department is assigned to doctors and cannot be deleted"); }
    }
    public List<Doctor> doctors(Long departmentId) {
        if (departmentId == null) return doctors.findAllByOrderByUserNameAsc();
        return doctors.findByDepartmentIdOrderByUserNameAsc(departmentId);
    }
    public List<Doctor> doctorsByDepartment(String departmentName) {
        return departmentName == null || departmentName.isBlank() ? doctors((Long) null)
                : doctors.findByDepartmentNameIgnoreCaseOrderByUserNameAsc(departmentName.trim());
    }
    @Transactional public Doctor createDoctor(DoctorRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new ConflictException("An account with this email already exists");
        User u = users.saveAndFlush(new User(r.name().trim(), email, passwords.encode(r.password()), Role.DOCTOR,
                r.phone() == null || r.phone().isBlank() ? null : r.phone().trim()));
        return doctors.save(new Doctor(u, department(r.departmentId()), r.specialization().trim(), r.avgConsultMinutes()));
    }
    @Transactional public Doctor updateDoctor(Long id, DoctorUpdateRequest r) {
        Doctor d = doctor(id); d.update(department(r.departmentId()), r.specialization().trim(), r.avgConsultMinutes()); return d;
    }
    @Transactional public void deleteDoctor(Long id) {
        Doctor d = doctor(id);
        try { doctors.delete(d); doctors.flush(); } catch (DataIntegrityViolationException e) { throw new ConflictException("Doctor has scheduled slots or appointments and cannot be deleted"); }
        users.delete(d.getUser());
    }
    public List<Slot> doctorSlots(Long doctorId, LocalDate date) {
        doctor(doctorId);
        return slots.findByDoctorIdAndDayOfWeekOrderByStartTime(doctorId, date.getDayOfWeek());
    }
    public List<com.medqueue.dto.response.AvailableSlotResponse> availableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = doctor(doctorId);
        boolean open = doctor.isAvailable() && (!date.equals(LocalDate.now()) || queueStates.findByDoctorIdAndQueueDate(doctorId, date).map(s -> s.isOpen()).orElse(true));
        var bookedBySlot = appointments.countSlotBookings(doctorId, date, SLOT_OCCUPYING).stream()
                .collect(java.util.stream.Collectors.toMap(com.medqueue.repository.SlotBookingCount::getSlotId,
                        com.medqueue.repository.SlotBookingCount::getTotal));
        return slots.findByDoctorIdAndDayOfWeekOrderByStartTime(doctorId, date.getDayOfWeek()).stream().map(slot -> {
            long booked = bookedBySlot.getOrDefault(slot.getId(), 0L);
            return com.medqueue.dto.response.AvailableSlotResponse.from(slot, Math.max(0, slot.getSlotCapacity() - booked), open);
        }).toList();
    }
    public List<Slot> allSlots(Long doctorId) {
        if (doctorId != null) doctor(doctorId);
        return doctorId == null ? slots.findAll() : slots.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
    }
    @Transactional public Slot createSlot(SlotRequest r) {
        Doctor d = doctor(r.doctorId()); validateSlot(r, d.getId(), null);
        return slots.save(new Slot(d, r.dayOfWeek(), r.startTime(), r.endTime(), r.slotCapacity()));
    }
    @Transactional public Slot updateSlot(Long id, SlotRequest r) {
        Slot s = slots.findById(id).orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        Doctor d = doctor(r.doctorId()); validateSlot(r, d.getId(), id);
        s.update(d, r.dayOfWeek(), r.startTime(), r.endTime(), r.slotCapacity());
        return s;
    }
    @Transactional public void deleteSlot(Long id) {
        Slot slot = slots.findById(id).orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        try { slots.delete(slot); slots.flush(); } catch (DataIntegrityViolationException e) { throw new ConflictException("Slot has appointments and cannot be deleted"); }
    }
    private void validateSlot(SlotRequest r, Long doctorId, Long excludeId) {
        if (!r.startTime().isBefore(r.endTime())) throw new ConflictException("Slot start time must be before end time");
        boolean overlaps = slots.findByDoctorIdAndDayOfWeekOrderByStartTime(doctorId, r.dayOfWeek()).stream()
                .anyMatch(s -> !s.getId().equals(excludeId) && s.getStartTime().isBefore(r.endTime()) && s.getEndTime().isAfter(r.startTime()));
        if (overlaps) throw new ConflictException("Slot overlaps an existing template for this doctor and day");
    }
    private Department department(Long id) { return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found")); }
    private Doctor doctor(Long id) { return doctors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Doctor not found")); }
}
