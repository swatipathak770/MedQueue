package com.medqueue.repository;

import com.medqueue.entity.Appointment;
import com.medqueue.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("select coalesce(max(a.tokenNumber), 0) from Appointment a where a.doctor.id=:doctorId and a.appointmentDate=:date")
    int maxToken(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
    long countByDoctorIdAndAppointmentDateAndSlotIdAndStatusIn(Long doctorId, LocalDate appointmentDate, Long slotId, Collection<AppointmentStatus> statuses);
    List<Appointment> findByPatientEmailOrderByAppointmentDateDescTokenNumberDesc(String email);
    List<Appointment> findByDoctorIdAndAppointmentDateOrderByTokenNumber(Long doctorId, LocalDate appointmentDate);
    long countByDoctorIdAndAppointmentDateAndStatusInAndTokenNumberLessThan(Long doctorId, LocalDate appointmentDate,
            Collection<AppointmentStatus> statuses, int tokenNumber);
    java.util.Optional<Appointment> findFirstByDoctorIdAndAppointmentDateAndStatusOrderByTokenNumberAsc(Long doctorId, LocalDate date, AppointmentStatus status);
    boolean existsByDoctorIdAndAppointmentDateAndStatusIn(Long doctorId, LocalDate date, Collection<AppointmentStatus> statuses);
    List<Appointment> findByAppointmentDateOrderByDoctor_IdAscTokenNumberAsc(LocalDate appointmentDate);
    @Query("select a.slot.id as slotId, count(a.id) as total from Appointment a where a.doctor.id=:doctorId and a.appointmentDate=:date and a.slot is not null and a.status in :statuses group by a.slot.id")
    List<SlotBookingCount> countSlotBookings(@Param("doctorId") Long doctorId, @Param("date") LocalDate date,
                                             @Param("statuses") Collection<AppointmentStatus> statuses);
}
