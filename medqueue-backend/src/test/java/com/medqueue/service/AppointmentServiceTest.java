package com.medqueue.service;

import com.medqueue.dto.request.BookingRequest;
import com.medqueue.entity.*;
import com.medqueue.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentServiceTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final DoctorRepository doctors = mock(DoctorRepository.class);
    private final SlotRepository slots = mock(SlotRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AppointmentService service = new AppointmentService(appointments, doctors, slots, users,
            mock(QueueStateRepository.class), mock(org.springframework.context.ApplicationEventPublisher.class));
    

    @Test void assignsNextTokenAfterLockingDoctorForWalkIn() {
        LocalDate date = LocalDate.now().plusDays(1);
        User patient = mock(User.class);
        Doctor doctor = mock(Doctor.class);
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(patient));
        when(doctors.findByIdForUpdate(8L)).thenReturn(Optional.of(doctor));
        when(doctor.getId()).thenReturn(8L);
        when(doctor.isAvailable()).thenReturn(true);
        when(appointments.maxToken(8L, date)).thenReturn(14);
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Appointment created = service.book("patient@example.com", new BookingRequest(8L, date, null, true));

        assertEquals(15, created.getTokenNumber());
        verify(doctors).findByIdForUpdate(8L);
        verify(appointments).saveAndFlush(any(Appointment.class));
    }
}
