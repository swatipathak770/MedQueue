package com.medqueue.dto.response;
import com.medqueue.entity.Doctor;
public record DoctorResponse(Long id, Long userId, String name, String email, Long departmentId,
        String department, String specialization, int avgConsultMinutes) {
    public static DoctorResponse from(Doctor d) { return new DoctorResponse(d.getId(), d.getUser().getId(), d.getUser().getName(),
            d.getUser().getEmail(), d.getDepartment().getId(), d.getDepartment().getName(), d.getSpecialization(), d.getAvgConsultMinutes()); }
}
