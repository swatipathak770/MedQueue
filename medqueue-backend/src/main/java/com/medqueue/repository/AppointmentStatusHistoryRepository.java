package com.medqueue.repository;

import com.medqueue.entity.AppointmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {
    List<AppointmentStatusHistory> findByAppointmentIdOrderByChangedAtAscIdAsc(Long appointmentId);
    void deleteByAppointmentId(Long appointmentId);
}
