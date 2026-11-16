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
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    private final QueueService service = new QueueService(doctors, appointments, states, events, statusAudit);

    @Test void reopensOnlyTheAuthenticatedDoctorsClosedQueueAndPreservesItsToken() {
        LocalDate today = LocalDate.now();
        Doctor doctor = lockedDoctor("doctor@example.com", 105L);
        QueueState state = new QueueState(doctor, today);
        state.setOpen(false);
        state.setCurrentTokenNumber(17);
        when(states.findByDoctorIdAndQueueDate(105L, today)).thenReturn(Optional.of(state));

        service.reopenQueue("doctor@example.com");

        assertTrue(state.isOpen());
        assertEquals(17, state.getCurrentTokenNumber());
        verify(states).findByDoctorIdAndQueueDate(105L, today);
        verifyNoInteractions(appointments);
        verify(events).publishEvent(new com.medqueue.event.QueueChangedEvent(105L, today));
    }

    @Test void refusesToReopenAQueueThatIsAlreadyOpen() {
        LocalDate today = LocalDate.now();
        Doctor doctor = lockedDoctor("doctor@example.com", 5L);
        QueueState state = new QueueState(doctor, today);
        when(states.findByDoctorIdAndQueueDate(5L, today)).thenReturn(Optional.of(state));

        ConflictException error = assertThrows(ConflictException.class, () -> service.reopenQueue("doctor@example.com"));

        assertEquals("Today's queue is already open", error.getMessage());
        verify(events, never()).publishEvent(any());
    }

    @Test void refusesToReopenWhenTodaysQueueWasNeverClosedWithoutCreatingAState() {
        LocalDate today = LocalDate.now();
        lockedDoctor("doctor@example.com", 5L);
        when(states.findByDoctorIdAndQueueDate(5L, today)).thenReturn(Optional.empty());

        ConflictException error = assertThrows(ConflictException.class, () -> service.reopenQueue("doctor@example.com"));

        assertEquals("Today's queue has not been closed", error.getMessage());
        verify(states, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test void previousDaysClosedQueueIsNotUsedForTodaysReopen() {
        LocalDate today = LocalDate.now();
        LocalDate previousDay = today.minusDays(1);
        Doctor doctor = lockedDoctor("doctor@example.com", 5L);
        QueueState previousState = new QueueState(doctor, previousDay);
        previousState.setOpen(false);
        when(states.findByDoctorIdAndQueueDate(5L, today)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service.reopenQueue("doctor@example.com"));

        assertFalse(previousState.isOpen());
        verify(states).findByDoctorIdAndQueueDate(5L, today);
        verify(states, never()).findByDoctorIdAndQueueDate(5L, previousDay);
    }

    @Test void doctorCannotReopenAnotherDoctorsQueue() {
        LocalDate today = LocalDate.now();
        Doctor authenticatedDoctor = lockedDoctor("doctor-a@example.com", 5L);
        Doctor otherDoctor = mock(Doctor.class);
        QueueState ownQueue = new QueueState(authenticatedDoctor, today);
        QueueState otherQueue = new QueueState(otherDoctor, today);
        otherQueue.setOpen(false);
        when(states.findByDoctorIdAndQueueDate(5L, today)).thenReturn(Optional.of(ownQueue));
        when(states.findByDoctorIdAndQueueDate(9L, today)).thenReturn(Optional.of(otherQueue));

        assertThrows(ConflictException.class, () -> service.reopenQueue("doctor-a@example.com"));

        assertFalse(otherQueue.isOpen());
        verify(states).findByDoctorIdAndQueueDate(5L, today);
        verify(states, never()).findByDoctorIdAndQueueDate(9L, today);
    }

    private Doctor lockedDoctor(String email, Long doctorId) {
        Doctor doctor = mock(Doctor.class);
        when(doctor.getId()).thenReturn(doctorId);
        when(doctors.findByUserEmail(email)).thenReturn(Optional.of(doctor));
        when(doctors.findByIdForUpdate(doctorId)).thenReturn(Optional.of(doctor));
        return doctor;
    }

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
