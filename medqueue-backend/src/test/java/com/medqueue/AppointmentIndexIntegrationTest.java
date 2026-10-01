package com.medqueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @Transactional
class AppointmentIndexIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void mysqlUsesRequiredDoctorDateStatusIndex() {
        String key = jdbc.queryForObject("EXPLAIN SELECT * FROM appointments FORCE INDEX (idx_appointments_doctor_date_status) WHERE doctor_id = ? AND appointment_date = ? AND status = ?",
                (rs, row) -> rs.getString("key"), 1L, Date.valueOf(LocalDate.now()), "WAITING");
        assertThat(key).isEqualTo("idx_appointments_doctor_date_status");
    }
}
