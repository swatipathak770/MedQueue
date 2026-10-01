-- Run after the application has created the schema.
-- The composite index supports this common per-doctor, per-day queue lookup.
EXPLAIN
SELECT id, patient_id, doctor_id, appointment_date, token_number, status
FROM appointments
FORCE INDEX (idx_appointments_doctor_date_status)
WHERE doctor_id = 1
  AND appointment_date = CURRENT_DATE
  AND status = 'WAITING';

-- The forced plan must show idx_appointments_doctor_date_status in the key column.
-- Without FORCE INDEX, MySQL may prefer the unique doctor/date/token index on a
-- tiny or empty local table; compare natural plans after representative data exists.
