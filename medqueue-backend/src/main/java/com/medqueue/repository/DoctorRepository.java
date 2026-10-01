package com.medqueue.repository;
import com.medqueue.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findAllByOrderByUserNameAsc();
    List<Doctor> findByDepartmentIdOrderByUserNameAsc(Long departmentId);
    List<Doctor> findByDepartmentNameIgnoreCaseOrderByUserNameAsc(String departmentName);
    boolean existsByUserId(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    java.util.Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
    java.util.Optional<Doctor> findByUserEmail(String email);
}
