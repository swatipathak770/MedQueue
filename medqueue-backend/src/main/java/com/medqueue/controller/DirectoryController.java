package com.medqueue.controller;

import com.medqueue.dto.request.*;
import com.medqueue.dto.response.*;
import com.medqueue.service.DirectoryService;
import com.medqueue.service.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
public class DirectoryController {
    private final DirectoryService directory;
    private final AnalyticsService analytics;
    public DirectoryController(DirectoryService directory, AnalyticsService analytics) { this.directory = directory; this.analytics = analytics; }

    @GetMapping("/api/departments")
    public List<DepartmentResponse> departments() { return directory.departments().stream().map(DepartmentResponse::from).toList(); }
    @PostMapping("/api/admin/departments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public DepartmentResponse createDepartment(@Valid @RequestBody DepartmentRequest r) { return DepartmentResponse.from(directory.createDepartment(r)); }
    @PutMapping("/api/admin/departments/{id}") @PreAuthorize("hasRole('ADMIN')")
    public DepartmentResponse updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest r) { return DepartmentResponse.from(directory.updateDepartment(id, r)); }
    @DeleteMapping("/api/admin/departments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void deleteDepartment(@PathVariable Long id) { directory.deleteDepartment(id); }

    @GetMapping("/api/doctors")
    public List<DoctorResponse> doctors(@RequestParam(required = false) String department) {
        return directory.doctorsByDepartment(department).stream().map(DoctorResponse::from).toList();
    }
    @GetMapping("/api/doctors/{id}/slots")
    public List<com.medqueue.dto.response.AvailableSlotResponse> doctorSlots(@PathVariable Long id, @RequestParam LocalDate date) {
        return directory.availableSlots(id, date);
    }
    @GetMapping("/api/admin/doctors") @PreAuthorize("hasRole('ADMIN')")
    public List<DoctorResponse> adminDoctors() { return directory.doctors((Long) null).stream().map(DoctorResponse::from).toList(); }
    @PostMapping("/api/admin/doctors") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse createDoctor(@Valid @RequestBody DoctorRequest r) { return DoctorResponse.from(directory.createDoctor(r)); }
    @PutMapping("/api/admin/doctors/{id}") @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse updateDoctor(@PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest r) { return DoctorResponse.from(directory.updateDoctor(id, r)); }
    @DeleteMapping("/api/admin/doctors/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void deleteDoctor(@PathVariable Long id) { directory.deleteDoctor(id); }

    @GetMapping("/api/admin/slots") @PreAuthorize("hasRole('ADMIN')")
    public List<SlotResponse> slots(@RequestParam(required = false) Long doctorId) { return directory.allSlots(doctorId).stream().map(SlotResponse::from).toList(); }
    @PostMapping("/api/admin/slots") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public SlotResponse createSlot(@Valid @RequestBody SlotRequest r) { return SlotResponse.from(directory.createSlot(r)); }
    @PutMapping("/api/admin/slots/{id}") @PreAuthorize("hasRole('ADMIN')")
    public SlotResponse updateSlot(@PathVariable Long id, @Valid @RequestBody SlotRequest r) { return SlotResponse.from(directory.updateSlot(id, r)); }
    @DeleteMapping("/api/admin/slots/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void deleteSlot(@PathVariable Long id) { directory.deleteSlot(id); }

    @GetMapping("/api/admin/analytics") @PreAuthorize("hasRole('ADMIN')")
    public List<com.medqueue.dto.response.DoctorAnalyticsResponse> analytics() { return analytics.today(); }
}
