package com.medqueue.service;

import com.medqueue.dto.response.QueueUpdateResponse;
import com.medqueue.entity.AppointmentStatus;
import com.medqueue.exception.ResourceNotFoundException;
import com.medqueue.repository.AppointmentRepository;
import com.medqueue.repository.DoctorRepository;
import com.medqueue.repository.QueueStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class QueueSnapshotReader {
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final QueueStateRepository states;
    public QueueSnapshotReader(DoctorRepository doctors, AppointmentRepository appointments, QueueStateRepository states) {
        this.doctors = doctors; this.appointments = appointments; this.states = states;
    }
    @Transactional(readOnly = true)
    public QueueUpdateResponse snapshot(Long doctorId, LocalDate date) {
        var doctor = doctors.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        var state = states.findByDoctorIdAndQueueDate(doctorId, date).orElse(null);
        var list = appointments.findByDoctorIdAndAppointmentDateOrderByTokenNumber(doctorId, date);
        long waiting = list.stream().filter(a -> a.getStatus() == AppointmentStatus.WAITING).count();
        long before = 0, activeAhead = 0;
        var entries = new java.util.ArrayList<QueueUpdateResponse.QueueEntry>();
        for (var a : list) {
            long position = a.getStatus() == AppointmentStatus.WAITING ? ++before : 0;
            entries.add(new QueueUpdateResponse.QueueEntry(a.getId(), a.getTokenNumber(), a.getStatus(), position,
                    position == 0 ? 0 : (position - 1 + activeAhead) * doctor.getAvgConsultMinutes()));
            if (a.getStatus() == AppointmentStatus.CALLED || a.getStatus() == AppointmentStatus.IN_PROGRESS) activeAhead++;
        }
        return new QueueUpdateResponse(doctorId, date, Instant.now(), doctor.isAvailable(), state == null || state.isOpen(),
                state == null ? 0 : state.getCurrentTokenNumber(), waiting, List.copyOf(entries));
    }
}
