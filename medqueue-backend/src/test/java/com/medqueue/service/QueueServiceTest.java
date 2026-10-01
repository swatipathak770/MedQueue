package com.medqueue.service;

import com.medqueue.entity.*;
import com.medqueue.exception.ConflictException;
import com.medqueue.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueueServiceTest {
    private final DoctorRepository doctors = mock(DoctorRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final QueueStateRepository states = mock(QueueStateRepository.class);
    private final AppointmentStatusAuditService statusAudit = mock(AppointmentStatusAuditService.class);
    private final QueueService service = new QueueService(doctors, appointments, states, mock(org.springframework.context.ApplicationEventPublisher.class), statusAudit);

    @Test void callsExactlyTheNextWaitingPatient() {
        LocalDate today = LocalDate.now(); Doctor doctor = mock(Doctor.class); QueueState state = mock(QueueState.class); Appointment patient = mock(Appointment.class);
        when(doctors.findByUserEmail("doctor@example.com")).thenReturn(Optional.of(doctor));
        when(doctor.getId()).thenReturn(5L); when(doctors.findByIdForUpdate(5L)).thenReturn(Optional.of(doctor));
        when(doctor.isAvailable()).thenReturn(true); when(states.findByDoctorIdAndQueueDate(5L, today)).thenReturn(Optional.of(state));
        when(state.isOpen()).thenReturn(true);
        when(appointments.existsByDoctorIdAndAppointmentDateAndStatusIn(5L, today, List.of(AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS))).thenReturn(false);
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusOrderByTokenNumberAsc(5L, today, AppointmentStatus.WAITING)).thenReturn(Optional.of(patient));

        when(doctor.getUser()).thenReturn(mock(User.class));
        when(doctor.getUser().getId()).thenReturn(4L);
        assertSame(patient, service.callNext("doctor@example.com"));
        verify(statusAudit).transition(patient, AppointmentStatus.CALLED, 4L);
    }

    @Test void refusesToAdvanceWhileAnotherPatientIsActive() {
        LocalDate today = LocalDate.now(); Doctor doctor = mock(Doctor.class); QueueState state = mock(QueueState.class);
        when(doctors.findByUserEmail("doctor@example.com")).thenReturn(Optional.of(doctor));
        when(doctor.getId()).thenReturn(5L); when(doctors.findByIdForUpdate(5L)).thenReturn(Optional.of(doctor));
        when(doctor.isAvailable()).thenReturn(true); when(states.findByDoctorIdAndQueueDate(5L, today)).thenReturn(Optional.of(state));
        when(state.isOpen()).thenReturn(true);
        when(appointments.existsByDoctorIdAndAppointmentDateAndStatusIn(5L, today, List.of(AppointmentStatus.CALLED, AppointmentStatus.IN_PROGRESS))).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.callNext("doctor@example.com"));
        verify(appointments, never()).findFirstByDoctorIdAndAppointmentDateAndStatusOrderByTokenNumberAsc(any(), any(), any());
    }
}
