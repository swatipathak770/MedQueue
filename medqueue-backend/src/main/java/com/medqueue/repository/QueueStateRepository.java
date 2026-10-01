package com.medqueue.repository;
import com.medqueue.entity.QueueState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
public interface QueueStateRepository extends JpaRepository<QueueState, Long> {
    Optional<QueueState> findByDoctorIdAndQueueDate(Long doctorId, LocalDate queueDate);
    java.util.List<QueueState> findByQueueDate(LocalDate queueDate);
}
