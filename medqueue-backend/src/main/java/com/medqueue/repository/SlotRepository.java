package com.medqueue.repository;
import com.medqueue.entity.Slot;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByDoctorIdAndDayOfWeekOrderByStartTime(Long doctorId, DayOfWeek dayOfWeek);
    List<Slot> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);
    boolean existsByDoctorIdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(Long doctorId, DayOfWeek dayOfWeek, java.time.LocalTime end, java.time.LocalTime start);
}
