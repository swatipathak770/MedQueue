package com.medqueue.controller;

import com.medqueue.dto.response.AdminQueueOverviewResponse;
import com.medqueue.service.AdminQueueOverviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminQueueOverviewController {
    private final AdminQueueOverviewService overview;

    public AdminQueueOverviewController(AdminQueueOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/api/admin/queues")
    public List<AdminQueueOverviewResponse> today() {
        return overview.today();
    }
}
